# Functional Design Questions — U4 `dialects`

Unit: **U4 `dialects`**（kind: `library`）
Stage: Functional Design（3.1）/ Construction
Depth: Standard（`aidlc-state.md`）

## Sources

- `unit-of-work.md` — U4 の責務「MySQL / Oracle の方言経路を新経路に載せ、その経路上に存在する既存欠陥を是正する」、境界（汎用経路 `Dao` / `Search` / `QueryImpl` に触れない、`MySQLSearch` / `MySQLCondition` / `PostgreSQLCondition` は変更なし）、依存 U2、実装上の制約（`SQL_CALC_FOUND_ROWS` の `replaceFirst` は不変、LIMIT / rownum の値は WHERE より後ろ）
- `unit-of-work-story-map.md` — U4 が担う FR-5.1 / 5.2 / 5.3 / 6.1 / 6.3 / 6.4 と FR-8.2 の該当行、判定する AC-9 / AC-10、Unit 内の実装順序 1〜4
- `requirements.md` — FR-5.1〜FR-5.3、FR-6.1（`searchListForOracle` の修正と有効化）、FR-6.3（LIMIT の `int` 直接連結、**Should**）、FR-6.4（`OracleValueConvertFilter` の null ガード）、AC-9 / AC-10、NFR-1 / NFR-3 / NFR-5 / NFR-6 / NFR-7、CON-1（Java 8）
- `components.md` / `component-methods.md` — M-7 方言コンポーネント（`MySQLDao` / `OracleDao` / `OracleSearch`）
- `services.md` — R-2 JDBC セッション管理（`PreparedStatement` はプールされた `Connection` から都度生成される）、R-3（`PreparedStatement` キャッシュは行わない）、R-5 実行の観測
- **U1 `bind-foundation` の 3.1 成果物** — `Param` / `PreparedSql` / `BindValue` の契約、`PreparedStatementBinder.bind`（**値は一律 `setString`**、ADR-007）、`DBAccessManager.executeQuery(PreparedSql, ResultSetHandler<R>)`
- **U2 `select-path` の 3.1 成果物** — `QueryImpl.getSelectPreparedSql()`、`Dao.executeQuery(PreparedSql, ResultSetHandler<R>)`、`Dao.search` / `searchList` のコールバック形、および § 11 引き継ぎ 5（**`MySQLDao.searchList` は `Dao.executeQuery` を経由していない**）
- 実ソース — `MySQLDao.java`、`OracleDao.java`、`OracleSearch.java`、`MySQLSearch.java`、`Dao.java`、`DBAccessManager.java`、`MySQLDaoTest.java`

設問は 3 件。**Q1 と Q3 は上流が「載せ替える」「有効化する」としか書いておらず、実行の形とページングの窓を本ステージで決める必要がある点**、Q2 は **ADR-007（値は一律 `setString`）と LIMIT / rownum のバインドが衝突する可能性**である。

FR-6.4（`OracleValueConvertFilter` の null ガード）は設問にしない。`Search.DefaultValueConvertFilter`（`Search.java:99-107`）と `MySQLSearch.MySQLValueConvertFilter`（`MySQLSearch.java:11-20`）がいずれも `if (value != null) { ... } else { return value; }` の形であり、AC-10 が「他 2 実装と同じ結果」を要求している以上、形は一意に決まる。

FR-5.2（PostgreSQL 専用クラスを追加しない）も設問にしない。**不作為の確認**であり、決めることがない。

---

## Q1. `MySQLDao.searchList` を新経路に載せ替えるとき、`SQL_CALC_FOUND_ROWS` / `FOUND_ROWS()` の 2 回実行をどう構成するか

### 現行

`MySQLDao.searchList` は **`Dao.executeQuery` を経由していない**（U2 `business-logic-model.md` § 11 引き継ぎ 5）。生の `Statement` を自前で取り、同じ `Statement` インスタンスで 2 回 `executeQuery` している。

```java
// MySQLDao.java:39-78（抜粋）
public Collection<T> searchList(Query<T> query, int start, int max) {
    Collection<Column> columns = query.getSelectColumns();
    String sql = query.getSelectSQL();
    if (start > 0 && max > 0) {
        if (useHitCount) {
            sql = sql.replaceFirst("SELECT ", "SELECT SQL_CALC_FOUND_ROWS ");
        }
        sql = sql + " limit " + (start - 1) + "," + max;
    }
    DaoEvent event = createDaoEvent(sql);
    getExecuteHandler().handleBeforeExecuteQuery(event);
    Statement stmt = dbm.createStatement();          // ← 生の Statement を自前で取得
    getExecuteHandler().handleAfterExecuteQuery(event);
    ArrayList<T> list = new ArrayList<>();
    try {
        ResultSet rs = stmt.executeQuery(sql);       // ← 1 回目
        ...
        if (useHitCount && start > 0 && max > 0) {
            rs = stmt.executeQuery("SELECT FOUND_ROWS()");   // ← 2 回目。同じ Statement
            if (rs.next()) { setHitCount(rs.getLong(1)); }
        }
        dbm.close(rs);
    } catch (SQLException e) {
        handleException(e);
    } finally {
        dbm.close(stmt);
    }
    return list;
}
```

### 新経路が課す制約

U1 が確定した `DBAccessManager.executeQuery(PreparedSql, ResultSetHandler<R>)` は、`PreparedStatement` と `ResultSet` の両方を try-with-resources で閉じる（U1 `business-logic-model.md` § 7.1）。**コールバックの外に `Statement` も `ResultSet` も出ない。** したがって「同じ `Statement` で 2 回実行する」という現行の形はそのままでは書けない。

`FOUND_ROWS()` が参照するのは **接続（セッション）単位の状態**であり、同じ `Statement` である必要はない。`services.md` R-3 のとおり `PreparedStatement` はプールされた同一 `Connection` から都度生成されるため、2 本目を別の文として実行してもセッションは同じである。

### 選択肢

**A. 2 回とも新経路で実行する（推奨）**

```java
@Override
public Collection<T> searchList(Query<T> query, int start, int max) {
    Collection<Column> columns = query.getSelectColumns();
    PreparedSql sql = buildPagedSql(query, start, max);   // SQL_CALC_FOUND_ROWS + limit ?,?
    Collection<T> list = executeQuery(sql, rs -> { /* mapping ループ */ });
    setHitCount(list.size());
    if (useHitCount && start > 0 && max > 0) {
        long hit = executeQuery(
            PreparedSql.of("SELECT FOUND_ROWS()", Collections.<BindValue>emptyList()),
            rs -> rs.next() ? rs.getLong(1) : 0L);
        setHitCount(hit);
    }
    return list;
}
```

`Dao.executeQuery(PreparedSql, ResultSetHandler<R>)`（U2 が追加）を 2 回呼ぶ。`DaoEvent` の発火・実行記録（`getExecutedQuery()` / `getExecutedStatements()`）も 2 本とも `Dao` / `DBAccessManager` の共通経路に載る。

**B. 本体だけ新経路にし、`FOUND_ROWS()` は旧 `dbm.executeQuery(String)` のまま残す**

`FOUND_ROWS()` は値を持たない固定文字列であり注入経路ではないため、リテラル経路のまま残しても SM-1 に穴は開かない。`DBAccessManager.getStatement()` がキャッシュする `ThreadLocal` の `Statement` を使うことになる。

**C. 現行どおり生の `Statement` を自前で取り続け、SQL だけ `PreparedSql` から取り出して実行する**

`dbm.createStatement()` を残す。ただし `PreparedSql.getSql()` は `?` を含むため、`Statement.executeQuery` に渡すと**未バインドの `?` を含む SQL がそのまま DB に渡る**——これは FR-6.1 が Oracle について是正しようとしている欠陥そのものである。

**X.** Other (please specify)

### 論点

| 観点 | A | B | C |
|---|---|---|---|
| 実行記録（FR-4.1）に 2 本とも載るか | ✅ 両方 | ⚠️ 本体のみ `ExecutedStatement`、`FOUND_ROWS()` は `getExecutedQuery()` のみ | ❌ どちらも載らない |
| `DaoEvent` の発火 | 共通経路（`Dao.executeQuery`）。**現行と発火位置が変わる**（現行は `createStatement()` の前後） | 本体のみ共通経路 | 現行のまま |
| セッションの同一性（`FOUND_ROWS()` の正しさ） | ✅ 同一 `Connection`（`services.md` R-3） | ✅ 同一 `Connection` | ✅ |
| NFR-1（実行経路に値の文字列連結が 0 件） | ✅ | ✅（`FOUND_ROWS()` は値を持たない） | ❌ |
| `handleException` の funnel | U2 Q3 = A と同じ形（`DaoException` を捕まえて `handleException`） | 混在する | 現行のまま |

**A を採る場合に決まる帰結**: `getExecutedQuery()` に記録される件数が 1 件から 2 件に増える（`useHitCount` かつページング時）。現行は `FOUND_ROWS()` が `dbm` を通らないため記録されていない。**観測可能な変化**であり `business-rules.md` に記録する。

[Answer]: A（本体と `FOUND_ROWS()` の 2 回とも `Dao.executeQuery(PreparedSql, ResultSetHandler)` で実行する）— 2026-08-09、**Mode:** guided

---

## Q2. ページング境界値（MySQL の LIMIT、Oracle の rownum）をバインド変数にするか

**ADR-007 と衝突しうる。** U1 が確定した `PreparedStatementBinder.bind` は、`OBJECT` と NULL を除くすべての値を **`setString`** で適用する（U1 `business-logic-model.md` § 6、ADR-007「現行の DB 側の型変換を維持する」）。

```java
// U1 business-logic-model.md § 6
} else {
    stmt.setString(pos, v.getValue());               // BR-16
}
```

FR-6.3 が求める `" limit ?,?"` に対してこれを適用すると、バインド値は文字列として渡る。

```
sql    = "SELECT ... FROM users limit ?,?"
values = [BindValue(NUMERIC,"0"), BindValue(NUMERIC,"5")]
→ setString(1,"0"); setString(2,"5")
```

**MySQL の LIMIT 句のパラメータは整数でなければならない。** Connector/J のクライアントサイド prepared statement（既定）では `setString` が引用符付きで展開されるため `limit '0','5'` となり構文エラーになる。サーバサイド prepared statement でも LIMIT のパラメータ型は整数が要求される。**この失敗は本リポジトリのモックスタックでは検出できない**——`MockPreparedStatement.setString` は値を記録するだけで、SQL を実際に解釈する DB がいない。最初の検出機会は Build and Test（3.6）の FR-8.3（実 DB）である。

Oracle の rownum についても同じ論点が立つ（`where rownum_ <= ? and rownum_ > ?`）。Oracle の JDBC ドライバは数値文字列を暗黙変換することが多いが、保証されているわけではない。

### 選択肢

**A. バインドする。`setString` のまま進め、実 DB での検証を 3.6 に委ねる**

FR-6.3 と AC-9（「実行される SQL に未バインドの `?` が残っていない」）を額面どおり満たす。ただし**上記の理由で MySQL では動かない可能性が高く、それが判明するのが 3.6 になる**。

**B. `PreparedStatementBinder` に整数バインドの分岐を追加し、`DataType.NUMERIC` を `setLong` で適用する**

ADR-007 の変更であり、**U1 の完了済み成果物（`business-logic-model.md` § 6、BR-16）に波及する**。かつ NUMERIC 列全体の挙動が変わるため、`SQLParserTest` が固定している現行の DB 側型変換の前提にも触れる。U4 単独の判断で行える範囲を超える。

**C. ページング境界値だけを整数として扱う専用の経路を設ける（推奨）**

`BindValue` に新しい形を足さず、**`MySQLDao` / `OracleDao` がページング境界値だけ `PreparedSql` に載せずリテラルとして連結する**。値は `int` 引数（`start` / `max`）であり呼び出し側が渡すページング指定であって、実行時のユーザー入力ではない（`requirements.md` FR-6.3 の位置づけ）。`int` 型を経由する時点で SQL 構文を注入できない。

```java
// C の形（MySQL）
sql = sql + " limit " + (start - 1) + "," + max;   // 現行のまま。? を増やさない
```

FR-6.3 は **Should Have** であり、SM-1（NFR-1）の判定対象でもない。C を採る場合、FR-6.3 は「型により注入不能であることを根拠に、リテラル連結を維持する」という判断で**閉じる**（Won't ではなく、要件の意図＝注入不能化を別手段で満たす）。Oracle の rownum も同じ扱いにする。

**D. C と同じくリテラル連結を維持するが、FR-6.3 を Won't Have に落として記録する**

C との違いは要件の帰着先だけである。

**X.** Other (please specify)

### 論点

| 観点 | A | B | C / D |
|---|---|---|---|
| MySQL で実際に動くか | ⚠️ **動かない可能性が高い**。判明は 3.6 | ✅ | ✅ 現行と同じ |
| U1 の完了済み成果物への波及 | なし | **あり**（ADR-007 / BR-16 の変更） | なし |
| AC-9「未バインドの `?` が残っていない」 | ✅ | ✅ | ✅（`?` を作らないため未バインドも生じない） |
| FR-6.3（Should）の充足 | ✅ 文字どおり | ✅ | ⚠️ 手段が異なる（型による安全化） |
| NFR-1（実行経路に値の文字列連結 0 件） | ✅ | ✅ | ⚠️ `int` の連結が 1 か所残る。**ただし `int` は値ではなくページング指定であり、NFR-1 の「値」に当たらないという読みが必要** |
| 検証可能性 | モックでは検証不能 | モックでは検証不能 | 現行テストがそのまま通る |

**この設問が Q3 に先行する理由**: Oracle の rownum ページング（Q3）の SQL 形は、境界値をバインドするかどうかで変わる。A / B なら `where rownum_ <= ? and rownum_ > ?`、C / D なら `where rownum_ <= 5 and rownum_ > 0` になる。

[Answer]: C（ページング境界値は `PreparedSql` に載せず `int` のリテラル連結を維持し、型により注入不能とする。FR-6.3 は手段を変えて充足する）— 2026-08-09、**Mode:** guided

**C を採ることの帰結（本設計が扱う）**:

1. **`OracleDao.searchListForOracle` の `?` は「バインドする」ではなく「消える」形で解消される。** AC-9 の「実行される SQL に未バインドの `?` が残っていない」は、`?` を作らないことによって満たされる。AC-9 の文言はどちらの機構でも判定できる形になっている
2. **FR-6.3 は Won't Have に落とさない**（D との違い）。要件の意図は「LIMIT 句の値が SQL 構文として解釈されうる形で連結されない」ことであり、`int` 引数を経由する時点でそれは成立している。`business-rules.md` に判断とその根拠を記録し、Build and Test（3.6）の FR-8.2 対応表にも「LIMIT はバインドしない」ことを明記する
3. **NFR-1 の判定に読みが要る。** `MySQLDao.java:46` の `" limit " + (start-1) + "," + max` は形式上「実行経路に残る文字列連結」である。ADR-003 が NFR-1 の判定を「実行経路に**値**の文字列連結が 0 件」に改めていることを踏まえ、**`int` 型のページング指定は NFR-1 が言う「値」に当たらない**という読みを本ステージの決定として明記する。この読みは Build and Test（3.6）が SM-1 を判定するときの前提になる
4. **U1 の完了済み成果物に波及しない**（B との違い）。ADR-007 と `PreparedStatementBinder.bind`（U1 § 6、BR-16）は 1 文字も変わらない
5. **`MySQLDaoTest` の既存アサートは無変更で通る。** LIMIT 経路のテキストが変わらないためである

---

## Q3. Oracle の rownum ページングの窓をどう定義するか（FR-6.1 / AC-9）

### 現行（`OracleDao.java:30-73`）

```java
/**
 * TODO: bugfix
 */
public Collection<T> searchListForOracle(Query<T> query, int start, int max) {
    String sql = query.getSelectSQL();
    if (start > 0) {
        boolean forUpdate = sql.toLowerCase().endsWith("for update");
        StringBuilder q = new StringBuilder();
        if (start > 1) {
            q.append("select * from ( select row_.*, rownum rownum_ from ( ");
        } else if (start > 0) {
            q.append("select * from ( ");
        }
        q.append(sql);
        if (start > 1) {
            q.append(" ) row_ ) where rownum_ <= ? and rownum_ > ?");   // ← 未バインドの ?
        } else if (start > 0) {
            q.append(" ) where rownum <= ?");                            // ← 未バインドの ?
        }
        if (forUpdate) q.append(" for update");
        sql = q.toString();
    }
    ResultSet rs = executeQuery(sql);      // Statement 経路。? は未バインドのまま DB に渡る
    ...
    //if (start > 0) { for (int i=1; i<start; i++) rs.next(); }   ← コメントアウト済み
    int add = 0;
    while (rs.next()) { ... if (max > 0 && add >= max) break; }
}
```

**3 つの欠陥がある**: (1) `?` が未バインドのまま `Statement` で実行される、(2) `max` がページング範囲の決定に一切使われていない、(3) このメソッドはリポジトリのどこからも呼ばれずテストもない。

### 汎用経路 / MySQL 経路が確立している `start` / `max` の意味

| 経路 | 挙動 |
|---|---|
| `Dao.searchList`（`Dao.java:177-197`） | `start > 0` のとき `for (int i=1; i<start; i++) rs.next();` で **`start - 1` 行を読み飛ばす**。すなわち `start` は **1 始まり**。以降 `max` 件で打ち切る |
| `MySQLDao.searchList`（`:46`） | `" limit " + (start - 1) + "," + max` — オフセット `start - 1`、件数 `max`。同じ 1 始まりの意味 |

したがって Oracle の窓は **`rownum_ > start - 1` かつ `rownum_ <= start - 1 + max`** が汎用 / MySQL と一致する定義である。

### 選択肢（窓の定義と `max <= 0` の扱い）

**A. 常にラップし、上限は `start - 1 + max`。`max <= 0` のときは上限を付けない（推奨）**

| 条件 | 生成する SQL |
|---|---|
| `start > 0 && max > 0` | `select * from ( select row_.*, rownum rownum_ from ( <sql> ) row_ ) where rownum_ > {start-1} and rownum_ <= {start-1+max}` |
| `start > 0 && max <= 0` | `select * from ( select row_.*, rownum rownum_ from ( <sql> ) row_ ) where rownum_ > {start-1}` |
| `start <= 0 && max > 0` | `select * from ( <sql> ) where rownum <= {max}` |
| `start <= 0 && max <= 0` | ラップしない（`<sql>` のまま） |

現行の `start > 1` / `start == 1` の分岐は廃し、`start >= 1` を同じ形（内側 rownum_ ラップ）に統一する。`start == 1` のときの `rownum_ > 0` は無害である。

**B. 現行の分岐構造（`start > 1` / `start == 1`）を保ったまま `max` を上限に足す**

`start == 1` のときは 1 段ラップ（`where rownum <= {max}`）、`start > 1` のときは 2 段ラップ。生成される SQL の形が現行に近い。

**C. `max <= 0` のときも常に上限をバインドする（`Integer.MAX_VALUE` を上限にする）**

SQL の形が 1 つに固定され、分岐が減る。

**X.** Other (please specify)

### いずれの案でも決める必要がある付随事項

| # | 事項 | 本設計の扱い |
|---|---|---|
| 1 | `searchList(Query,int,int)` を override するか | **する。** AC-9 が「`searchList(query, start, max)` が呼ばれる」を Given にしている。旧 public `searchListForOracle` はシグネチャ・可視性とも残し（NFR-3）、`searchList` から委譲する |
| 2 | コールバック内の行読み飛ばし | **行わない。** rownum が既に読み飛ばし済みである。現行のコメントアウトを削除して「行わない」ことを明示する。`Dao.searchList` の `for (int i=1; i<start; i++) rs.next();` を継承しない形になる |
| 3 | コールバック内の `if (max > 0 && add >= max) break;` | **残す。** rownum 上限と二重の防御になる。`max <= 0` のときは無効（現行と同じ） |
| 4 | `for update` の位置 | 現行どおり最外側の末尾に付ける。`sql.toLowerCase().endsWith("for update")` の判定も現行のまま |
| 5 | `getHitCount()` | Oracle 経路は現行も設定していない。**設定しない**（MySQL の `FOUND_ROWS()` に相当する機構がなく、追加は FR の範囲外） |

### 挙動が変わることの明示

FR-6.1 の有効化により、**Oracle のページングが汎用経路の全行スキャン（`Dao.java:181-184`）から rownum 方式に変わる**。public API の破壊ではないが観測可能な挙動の変更であり、性能特性も変わる（`requirements.md` NFR-7 の補足、`unit-of-work.md` U4「挙動が変わる点」）。

[Answer]: A（`start >= 1` を内側 rownum_ ラップに統一し、`rownum_ > start-1` かつ `rownum_ <= start-1+max`。`max <= 0` のときは上限を付けない）— 2026-08-09、**Mode:** guided

**Q2 = C と組み合わせた最終形**（境界値はリテラル）:

| 条件 | 生成する SQL |
|---|---|
| `start > 0 && max > 0` | `select * from ( select row_.*, rownum rownum_ from ( <sql> ) row_ ) where rownum_ > {start-1} and rownum_ <= {start-1+max}` |
| `start > 0 && max <= 0` | `select * from ( select row_.*, rownum rownum_ from ( <sql> ) row_ ) where rownum_ > {start-1}` |
| `start <= 0 && max > 0` | `select * from ( <sql> ) where rownum <= {max}` |
| `start <= 0 && max <= 0` | ラップしない |

---

## Ambiguity and Contradiction Analysis

`stage-protocol.md` §3 が必須とする回答分析。

**曖昧な回答**: なし。3 件とも単一の選択肢が確定している。

**矛盾**: なし。ただし **上流成果物に対する修正が 2 件**生じる（下表 3・4）。

| # | 組み合わせ | 確認内容 | 判定 |
|---|---|---|---|
| 1 | Q1=A ＋ U1 § 7.1 | `DBAccessManager.executeQuery(PreparedSql, ResultSetHandler<R>)` は `PreparedStatement` と `ResultSet` を try-with-resources で閉じる。`FOUND_ROWS()` を別の文として実行してよいか | **矛盾なし。** `FOUND_ROWS()` は接続（セッション）単位の状態を参照する。`services.md` R-3 のとおり `PreparedStatement` はプールされた同一 `Connection` から都度生成されるため、文が別でもセッションは同じである |
| 2 | Q1=A ＋ Q2=C | 本体 SQL の `PreparedSql` は `getSelectPreparedSql()`（U2）の結果に LIMIT のリテラルを連結して作る。`?` の個数は変わらないため `PreparedSql.of` の個数検査（U1 BR-24）を通るか | **矛盾なし。** LIMIT のリテラル連結は `?` を増やさない（`unit-of-work.md` U4 の実装上の制約と同じ論理）。値リストの順序も変わらない |
| 3 | Q2=C ＋ `component-methods.md` M-7 | M-7 は「LIMIT を `" limit ?,?"` にしてオフセットと件数をバインドする（FR-6.3）」と規定している | **2.6 への修正。** MySQL の LIMIT パラメータは整数でなければならず、ADR-007 の `setString` 一律適用と両立しない。バインドせず `int` のリテラル連結を維持する |
| 4 | Q3=A ＋ `component-methods.md` M-7 | M-7 は「rownum の `?` をバインドし、`max` をページング範囲の決定に使う（FR-6.1）」と規定している | **2.6 への部分修正。** `max` をページング範囲の決定に使う点は採る。`?` のバインドは Q2=C により行わず、リテラルで埋める。**AC-9 の「未バインドの `?` が残っていない」は `?` を作らないことで満たされる** |
| 5 | Q2=C ＋ NFR-1 | `MySQLDao.java:46` の `int` 連結が実行経路に残る | **読みを明記して解消。** ADR-003 が NFR-1 を「実行経路に**値**の文字列連結が 0 件」に改めており、`int` 型のページング指定は「値」に当たらないという読みを本ステージの決定とする（Q2 の帰結 3） |
| 6 | Q3=A ＋ NFR-3 | `OracleDao.searchList(Query,int,int)` を override し、旧 `searchListForOracle` を残す | **矛盾なし。** 既存 public シグネチャの削除・変更を伴わない。`searchList` は `Dao` の既存メソッドの override であり新規メンバではない |
| 7 | Q3=A ＋ `Dao.searchList` の行スキップ | rownum が既に読み飛ばしているため、コールバック内で `for (int i=1; i<start; i++) rs.next();` を行わない | **矛盾なし。** `OracleDao.searchList` は `Dao.searchList` を override するため親の実装は実行されない。現行の `searchListForOracle` も同じ判断でコメントアウトしていた |
| 8 | Q1=A ＋ FR-4.1 / 現行挙動 | `getExecutedQuery()` の件数が 1 件から 2 件に増える（`useHitCount` かつページング時） | **観測可能な挙動変更。** 記録を**失う**方向の変化はない。`business-rules.md` に記録する |
| 9 | Q1=A ＋ `DaoExecuteHandler` の発火位置 | 現行は `createStatement()` の前後で `handleBeforeExecuteQuery` / `handleAfterExecuteQuery` を発火している（実行の前後ではない） | **観測可能な挙動変更。** 共通経路（`Dao.executeQuery`）に載せると発火が実行の前後になり、かつ 2 本ぶん発火する。既定の `NoneDaoExecuteHandler` では差が出ない |
| 10 | 全体 ＋ CON-1（Java 8） | 追加するのはラムダ（`ResultSetHandler`）と既存 API のみ | **矛盾なし。** post-8 の言語機能・API を使わない |

**上流成果物は編集しない。** 表 3・4 の修正は本ステージの `business-logic-model.md`「2.6 / 2.7 契約からの差分」節に記録する。U1 / U2 で確立した扱いと同じ方針である。

**追加の設問**: 不要。

---

## Consolidated Summary Confirmation

**Prompt**: この内容で `business-logic-model.md` / `business-rules.md` / `domain-entities.md` を生成してよいか。

**Options**:
- Looks correct — この回答から成果物を生成する
- Request changes — 生成前に回答を修正する

**回答の要約**

| # | 決定 | 帰結 |
|---|---|---|
| Q1 | **A** — 本体と `FOUND_ROWS()` の 2 回とも `Dao.executeQuery(PreparedSql, ResultSetHandler)` | 実行記録に 2 本とも載る。`getExecutedQuery()` の件数と `DaoExecuteHandler` の発火位置が変わる |
| Q2 | **C** — ページング境界値はリテラル連結を維持し、型により注入不能とする | ADR-007 / U1 の Binder に波及しない。`component-methods.md` M-7 の「`limit ?,?`」を修正。NFR-1 の読みを明記 |
| Q3 | **A** — `rownum_ > start-1` / `<= start-1+max` に統一ラップ、`max <= 0` は上限なし | 汎用 / MySQL 経路と `start` / `max` の意味が一致する。`searchList` を override し行スキップは行わない |

[Answer]: Looks correct — 2026-08-09

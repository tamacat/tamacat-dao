# Business Logic Model — U4 `dialects`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U4 の責務・境界・所有コンポーネント（M-7 `MySQLDao` / `OracleDao` / `OracleSearch.OracleValueConvertFilter`）、FR-6.1 / 6.3 / 6.4 を同居させる理由（Q4 = B）、「挙動が変わる点」（Oracle のページングが全行スキャンから rownum 方式に変わる）、実装上の制約（`SQL_CALC_FOUND_ROWS` の `replaceFirst` は不変、LIMIT / rownum は値リストの末尾、`OracleDao` の `createSearch` / `createQuery` の非対称）。
- **`unit-of-work-story-map.md`**（同上）— U4 が担う FR-5.1 / 5.2 / 5.3 / 6.1 / 6.3 / 6.4 と FR-8.2 の該当行、判定する AC-9 / AC-10、および「Unit 内の実装順序」1〜4（本文書の § 7 がそれに対応づく）。
- **`requirements.md`**（requirements-analysis, 2.3）— FR-5.1〜5.3、FR-6.1（`max` が使われていない点を含む）、FR-6.3（**Should**）、FR-6.4、AC-9 / AC-10、NFR-1（ADR-003 による読み替え後）／ NFR-3 / NFR-5 / NFR-6 / NFR-7、CON-1（Java 8）。
- **`components.md`**（application-design, 2.6）— M-7 方言コンポーネントの変更範囲、および `MySQLSearch` / `MySQLCondition` / `PostgreSQLCondition` を変更しないこと、PostgreSQL 専用クラスを追加しないこと（FR-5.2）。
- **`component-methods.md`**（同上）— M-7 の変更内容表。本文書はそのうち 2 点を修正する（§ 8）。
- **`services.md`**（同上）— R-2 JDBC セッション管理、R-3 接続プーリング（`PreparedStatement` はプールされた `Connection` から都度生成される。`FOUND_ROWS()` の同一セッション性の根拠）、R-5 実行の観測（`getExecutedQuery()` / `getExecutedStatements()`）。

U1 `bind-foundation` が定義した `PreparedSql` / `BindValue` / `ResultSetHandler` / `PreparedStatementBinder` / `DBAccessManager` の契約、および U2 `select-path` が定義した `QueryImpl.getSelectPreparedSql()` / `Dao.executeQuery(PreparedSql, ResultSetHandler)` / `Dao.search` / `Dao.searchList` の形は**本 Unit の依存先**である。状態の定義は本 Unit の `domain-entities.md`、規則の列挙は `business-rules.md` にある。本文書は**アルゴリズムと処理順序**を扱う。`BR-n` は本 Unit の規則、`U1 BR-n` / `U2 BR-n` はそれぞれの Unit の規則を指す。

---

## この文書の範囲

U4 は「MySQL / Oracle の方言経路を新経路に載せ、その経路上に存在する既存欠陥を是正する」。U1 が作った型と実行機構、U2 が通した汎用経路の**上に方言の差分を載せる**のが仕事である。

| # | 処理 | 担当 | 節 |
|---|---|---|---|
| 1 | ページング窓を `(start, max)` から導出する | 両方言で共通の規則 | § 1 |
| 2 | 汎用の `PreparedSql` に方言接尾辞を合成する | 両方言に重複する private ヘルパ | § 2 |
| 3 | MySQL の `searchList` を新経路に載せ替え、`FOUND_ROWS()` を 2 本目として実行する | `MySQLDao` | § 3 |
| 4 | Oracle の rownum ページングを修正して有効化する | `OracleDao` | § 4 |
| 5 | `OracleValueConvertFilter` に null ガードを入れる | `OracleSearch` | § 5 |
| 6 | 方言ごとの `Conditions` が同じ述語を生成することの確認（FR-5.3） | 不作為の確認 | § 6 |

**U4 が触らないもの**: 汎用経路の `Dao` / `Search` / `QueryImpl`（U2 / U3）、`Sort`（U5）、`MySQLSearch` / `MySQLCondition` / `PostgreSQLCondition`（不変）、U1 の型と機構。

---

## § 1. ページング窓の導出

`domain-entities.md` § 1 が定めた導出規則をアルゴリズムにする。**両方言で同一の式を使う**（BR-1）。

```java
// start は 1 始まり（Dao.java:181-184 / MySQLDao.java:46 と同じ意味）
int offset = start > 0 ? start - 1 : 0;      // 読み飛ばす行数
boolean hasOffset = start > 0;
boolean hasLimit  = max > 0;
int upperBound = offset + max;               // hasOffset && hasLimit のときだけ意味を持つ
```

| 状態 | 条件 | MySQL の実効窓 | Oracle の実効窓 |
|---|---|---|---|
| W-1 | `hasOffset && hasLimit` | `(offset, offset+max]`。`limit {offset},{max}` ＋（`useHitCount` なら）`SQL_CALC_FOUND_ROWS` | `(offset, offset+max]`。2 段ラップ（`rownum_ > {offset} and rownum_ <= {upperBound}`） |
| W-2 | `hasOffset && !hasLimit` | **`[1, ∞)`。`offset` は適用されない**（現行維持） | `(offset, ∞)`。2 段ラップ（`rownum_ > {offset}` のみ） |
| W-3 | `!hasOffset && hasLimit` | `[1, max]`。SQL に LIMIT は付かない（現行維持）が、コールバックの `if (max > 0 && add >= max) break;`（`MySQLDao.java:60-61`）が `paged`（`start>0 && max>0`）と無関係に `max > 0` だけで発火するため、結果的に先頭 `max` 件に切り詰まる | `[1, max]`。1 段ラップ（`where rownum <= {max}`） |
| W-4 | どちらもなし | 全件 | ラップしない |

**BR-1 の適用範囲を正確にする。** 窓の導出式（`offset = start-1`、上限 `offset+max`）は両方言で共通だが、**W-2 に限り、その式が方言によって適用されたりされなかったりする**——`MySQLDao.searchList` に `Dao.searchList`（`Dao.java:181-184`）相当の行スキップがないためである。W-3 は SQL レベルの機構（LIMIT の有無、ラップの有無）こそ異なるが、**行の集合としては両方言とも `[1, max]` で一致する**——反証の過程で W-3 も非対称だと誤って記録した回があったが、`MySQLDao.java:60-61` の break 条件を再確認し訂正した。

- **W-2（`start>0 && max<=0`）**: `MySQLDao.java:39-78` には `Dao.searchList`（`Dao.java:181-184`）に相当する行スキップが**存在しない**。かつ `max<=0` のためコールバックの break も発火しない。したがって MySQL の W-2 は LIMIT も付けず行も読み飛ばさず打ち切りもしないため、実効窓は `offset` を無視した `[1, ∞)` になる。Oracle は W-2 でも `rownum_ > {offset}` を適用するため `offset` が効く。**同じ `(start, max)` に対して 2 方言が異なる行集合を返すのは W-2 だけである。**
- **W-3（`start<=0 && max>0`）**: MySQL は LIMIT 句こそ付けないが（`paged = start>0 && max>0` が偽のため）、コールバックの break 条件は `max > 0` のみで判定され `paged` を参照しない。`start<=0` のときオフセットは 0 であり読み飛ばす行もないため、**MySQL・Oracle とも `[1, max]` で行集合は一致する**。

**W-2 の非対称は本設計が意図的に残したものである。** MySQL 側で条件を広げて `offset` 適用を追加することもできるが、それは FR-6.3 の範囲（LIMIT の**連結方法**の是正）を超えて行スキップ機構を新設することになり、現行と異なる SQL を生成して NFR-5（全テストがグリーン）に対する不要なリスクを負う（BR-2）。**この非対称は `business-rules.md`「残存リスク」に記録する（R-6、W-2 のみが対象）。**

---

## § 2. `PreparedSql` の合成

### 2.1 なぜ合成が必要か

U2 が `QueryImpl.getSelectPreparedSql()` で返す `PreparedSql` は「SELECT 句 + FROM/JOIN + WHERE + GROUP BY + ORDER BY」で完結している（U2 § 4.2）。方言はその**外側**（Oracle）または**後ろ**（MySQL）にテキストを足す。`PreparedSql` は不変であるため、足すのではなく**新しいインスタンスを作る**。

### 2.2 由来を保つヘルパ

```java
// MySQLDao / OracleDao それぞれの private static メソッド（BR-4）
private static PreparedSql compose(PreparedSql base, String sql) {
    // sql は base.getSql() を加工したテキスト。? の個数は base と同一（DS-2）
    return base.hasUnboundPlaceholders()
        ? PreparedSql.ofLiteral(sql)                      // 由来を unchecked のまま保つ
        : PreparedSql.of(sql, base.getValues());          // checked。値リストは不変（DS-1）
}
```

**分岐条件は `hasUnboundPlaceholders()` である。** これは入力の由来（checked / unchecked）そのものではなく、その由来と `?` の状態から導かれる**観測可能な信号**である——checked な `PreparedSql` は常に `hasUnboundPlaceholders() == false`（U1 PS-2 が個数を保証する）。unchecked な `PreparedSql` は `?` を持たなければ（例えば外部 `Query` の `default` 実装がリテラル SQL を返す最も普通のケース）同じく `false` を返し、この分岐では checked と同じ枝（`PreparedSql.of(...)`）を通る——**この場合に限り、値ゼロ・プレースホルダ 0 個のまま unchecked から checked へ実質的に切り替わる**が、`getPlaceholderCount() == 0` かつ `values.size() == 0` であるため U1 PS-2 / PS-3 も `checkBindable`（U1 § 7.2）も通り、実害はない。

**分岐が必要になるのは `hasUnboundPlaceholders() == true` の入力に対してである**——外部の `Query` 実装が `getSelectPreparedSql()` を override せず `default` 実装（`PreparedSql.ofLiteral(getSelectSQL())`、U2 § 6.1）を使い、かつ `where(Param)` でリテラル側に `?` を持ち込んだ場合（U2 § 3.3 の帰結）。この入力に `PreparedSql.of(...)` を呼ぶと、**生成時の個数検査（U1 PS-2）が `InvalidParameterException` を投げる**。

汎用経路（`Dao.searchList`）では、この入力は `DBAccessManager.checkBindable`（U1 § 7.2）に到達し、BLOB 経路への案内を含む診断メッセージ付きの `DaoException` になる。**方言経路だけが別の例外型（`IllegalArgumentException` の派生）で、しかも実行前の別の場所で落ちるのは避ける**（BR-5）。

`InvalidParameterException extends IllegalArgumentException`、`DaoException extends RuntimeException` であり、両者に継承関係はない。利用側が `catch (DaoException e)` で受けている場合、分岐がなければ例外がすり抜ける。

### 2.3 なぜ 2 クラスに重複させるのか

`compose` を共通化するには `Dao` に protected メソッドを追加することになる。これは 2 つの理由で採らない。

1. `unit-of-work.md` U4 の境界「汎用経路（`Dao` / `Search` / `QueryImpl`）には触れない」を破る
2. 新規 protected メンバは public クラスの互換維持対象であり、**AC-11 の判定対象を増やす**（U2 § 10 の Pr-table と同じ扱い）

3 行の重複を受け入れる。**重複が乖離するリスクは `business-rules.md` BR-4 で規則として押さえ、Build and Test（3.6）で両方言に同じテストを当てる**ことで検出する。

---

## § 3. MySQL — `searchList` を新経路に載せ替える（Q1 = A、FR-5.1 / FR-6.3）

### 3.1 現行の何が変わるか

現行（`MySQLDao.java:39-78`）は **`Dao.executeQuery` を経由していない**（U2 § 11 引き継ぎ 5）。`dbm.createStatement()` で生の `Statement` を取り、同じインスタンスで 2 回 `executeQuery(String)` を呼び、`DaoEvent` も自前で発火している。

| 項目 | 現行 | 変更後 |
|---|---|---|
| 実行 | `Statement.executeQuery(String)` × 2（同一 `Statement`） | `Dao.executeQuery(PreparedSql, ResultSetHandler)` × 2（U2 が追加） |
| `Statement` の寿命 | `finally { dbm.close(stmt); }` | `DBAccessManager` の try-with-resources（U1 § 7.1） |
| `DaoEvent` の発火 | `createStatement()` の**前後**、1 回 | `Dao.executeQuery` の内側、実行の**前後**、2 回 |
| 実行記録 | どちらも `dbm` を通らないため**記録されない** | 2 本とも `getExecutedQuery()` / `getExecutedStatements()` に載る |
| 例外 | `catch (SQLException e) { handleException(e); }` | `catch (DaoException e) { handleException(cause); }`（U2 BR-16 と同形） |

### 3.2 アルゴリズム

```java
@Override
public Collection<T> searchList(Query<T> query, int start, int max) {
    Collection<Column> columns = query.getSelectColumns();
    PreparedSql base = query.getSelectPreparedSql();          // U2

    String text = base.getSql();
    boolean paged = start > 0 && max > 0;                     // W-1。現行 :42 と同じ条件（BR-2）
    if (paged) {
        if (useHitCount) {
            text = text.replaceFirst("SELECT ", "SELECT SQL_CALC_FOUND_ROWS ");  // :44 のまま（BR-3）
        }
        text = text + " limit " + (start - 1) + "," + max;    // Q2 = C。? を作らない（BR-6）
    }
    PreparedSql sql = compose(base, text);                    // § 2.2

    try {
        Collection<T> list = executeQuery(sql, rs -> {        // 1 本目
            ArrayList<T> l = new ArrayList<>();
            int add = 0;
            while (rs.next()) {
                l.add(mapping(columns, rs));
                add++;
                if (max > 0 && add >= max) break;             // :60-61 のまま（BR-7）
            }
            return l;
        });
        setHitCount(list.size());                             // :63 のまま
        if (useHitCount && paged) {
            long hit = executeQuery(                          // 2 本目。値ゼロ（BR-8）
                PreparedSql.of("SELECT FOUND_ROWS()", Collections.<BindValue>emptyList()),
                rs -> rs.next() ? rs.getLong(1) : 0L);
            setHitCount(hit);                                 // :64-69 のまま
        }
        return list;
    } catch (DaoException e) {
        handleException(e.getCause() != null ? e.getCause() : e);   // U2 BR-16。必ず throw する
        return null;                                                 // 到達しない
    }
}
```

### 3.3 `FOUND_ROWS()` を別の文として実行してよい理由

`SQL_CALC_FOUND_ROWS` が算出した行数は **接続（セッション）単位の状態**であり、同じ `Statement` インスタンスである必要はない。`services.md` R-3 のとおり `PreparedStatement` はプールされた `Connection` から都度生成され、`DBAccessManager.getConnection()` は `ThreadLocal` の同一 `Connection` を返す（`services.md` R-2）。したがって 2 本目も**同じセッション**で走る（BR-9）。

**順序の制約は守られる**——`FOUND_ROWS()` は「直前に実行された `SQL_CALC_FOUND_ROWS` 付きクエリ」の結果を返す。1 本目のコールバックが完了してから 2 本目を発行するため、間に別の文が挟まらない（同一スレッド、同一メソッド内）。

### 3.4 `SQL_CALC_FOUND_ROWS` の置換が安全である理由

`replaceFirst("SELECT ", "SELECT SQL_CALC_FOUND_ROWS ")` はテキスト置換であり **`?` を増やさない**（DS-2）。値リストも変わらない。`unit-of-work.md` U4 が「順序に影響しないため不変」とした判断のとおりである。

**現行から引き継ぐ既知の脆さ**: `replaceFirst` の正規表現は `"SELECT "` という**大文字・末尾スペース**に一致する。`QueryImpl.getSelectSQL()` が生成する SQL は必ず `"SELECT "` で始まる（`QueryImpl.java:141`、`select.append(SELECT + " ")`。宣言自体は `:138`）ため実害はないが、外部の `Query` 実装が小文字で組み立てた場合は置換されず、`FOUND_ROWS()` が別のクエリの値を返す。**現行と同じ挙動であり、本取り組みの回帰ではない**（BR-3）。

### 3.5 `getExecutedQuery()` の件数が増えること

**観測可能な挙動変更である**（BR-10）。現行は 2 本とも `dbm` を通らないため 0 件、変更後は W-1 かつ `useHitCount` のとき 2 件（本体 + `FOUND_ROWS()`）が記録される。**記録を失う方向の変化はない。** FR-4.1 の意図（実行記録にバインド値を含める）にも沿う。

`UserDaoTest` 系は `MySQLDao` を使っていないため影響しない。`MySQLDaoTest` は現在 `searchList` を呼んでいない（`MySQLDaoTest.java:39` はコメントアウト）ため、既存テストへの影響もない。

---

## § 4. Oracle — rownum ページングの修正と有効化（Q3 = A、FR-6.1）

### 4.1 現行の 3 つの欠陥

| # | 欠陥 | 所在 |
|---|---|---|
| 1 | `?` が未バインドのまま `Statement` で実行される | `OracleDao.java:44`、`:46`、`:51` |
| 2 | `max` がページング範囲の決定に一切使われていない（`start` だけがラッピングを制御する） | `:33`、`:37-47` |
| 3 | どこからも呼ばれずテストもない（`searchList` の override ではなく別名メソッド） | メソッド名 `searchListForOracle` |

### 4.2 生成する SQL

`domain-entities.md` § 1 の 4 状態に対応する。境界値はリテラル（Q2 = C）であり **`?` を 1 つも作らない**（BR-6）。`core` は `base` から末尾の `for update` を剥がしたテキスト（§ 4.3、BR-21）。`[for update]` は `forUpdate` が真のときだけ付く最外側の接尾辞を表す。

| 状態 | SQL |
|---|---|
| W-1（`start>0 && max>0`） | `select * from ( select row_.*, rownum rownum_ from ( <core> ) row_ ) where rownum_ > {offset} and rownum_ <= {offset+max} [for update]` |
| W-2（`start>0 && max<=0`） | `select * from ( select row_.*, rownum rownum_ from ( <core> ) row_ ) where rownum_ > {offset} [for update]` |
| W-3（`start<=0 && max>0`） | `select * from ( <core> ) where rownum <= {max} [for update]` |
| W-4 | `<base>`（ラップしない。`for update` は元の位置に残る） |

**現行の `start > 1` / `start == 1` の分岐を廃す**（BR-11）。`start == 1` は W-1 / W-2 の 2 段ラップに含まれ、`rownum_ > 0` という無害な述語になる。分岐を残すと `start == 1` だけ `rownum_` 列が付かない SQL になり、テストで確認すべき形が 2 種類に増える。

**W-3 が新しい**。現行は `start <= 0` のとき一切ラップせず `max` を無視していた（欠陥 2）。W-3 は `max` をページング範囲の決定に使うという FR-6.1 の要求そのものである。

### 4.3 アルゴリズム

```java
@Override
public Collection<T> searchList(Query<T> query, int start, int max) {
    return searchListForOracle(query, start, max);            // BR-12。旧メソッドは残す
}

public Collection<T> searchListForOracle(Query<T> query, int start, int max) {
    Collection<Column> columns = query.getSelectColumns();
    PreparedSql base = query.getSelectPreparedSql();          // U2
    String text = wrapRownum(base.getSql(), start, max);
    PreparedSql sql = compose(base, text);                    // § 2.2

    try {
        return executeQuery(sql, rs -> {
            ArrayList<T> list = new ArrayList<>();
            // 行の読み飛ばしは行わない。rownum が既に読み飛ばしている（BR-13）
            int add = 0;
            while (rs.next()) {
                list.add(mapping(columns, rs));
                add++;
                if (max > 0 && add >= max) break;             // 二重の防御（BR-7）
            }
            return list;
        });
    } catch (DaoException e) {
        handleException(e.getCause() != null ? e.getCause() : e);
        return null;
    }
}

private static final String FOR_UPDATE = "for update";

private static String wrapRownum(String base, int start, int max) {
    boolean forUpdate = base.toLowerCase().endsWith(FOR_UPDATE);     // :36 と同じ判定（BR-14）
    // for update を core から剥がしてからラップする（BR-21）。
    // 剥がさずに base をそのまま内側へ append すると、最外側で付け直す
    // " for update" と合わせて 2 か所に現れ、実行できない SQL になる。
    String core = forUpdate
        ? base.substring(0, base.length() - FOR_UPDATE.length()).trim()
        : base;
    int offset = start > 0 ? start - 1 : 0;
    StringBuilder q = new StringBuilder();
    if (start > 0) {
        q.append("select * from ( select row_.*, rownum rownum_ from ( ")
         .append(core)
         .append(" ) row_ ) where rownum_ > ").append(offset);
        if (max > 0) q.append(" and rownum_ <= ").append(offset + max);
    } else if (max > 0) {
        q.append("select * from ( ").append(core).append(" ) where rownum <= ").append(max);
    } else {
        return base;                                                  // W-4。ラップしない。for update は元の位置に残る
    }
    if (forUpdate) q.append(" ").append(FOR_UPDATE);                  // 最外側にちょうど 1 回だけ付ける
    return q.toString();
}
```

**`core` を剥がす理由（BR-21）**: `base`（`query.getSelectPreparedSql().getSql()`）が `for update` で終わる場合、現行 `OracleDao.java:37-48` は元の SQL 全体（`for update` を含む）をそのままラップの内側に `append(sql)` し、さらに最外側にも `q.append(" for update")` を付けていた。これは**現行のままでも 2 か所に `for update` が現れる形**であり、`searchListForOracle` が誰からも呼ばれない死んだコードだったために顕在化していなかった（§ 4.1 欠陥 3）。FR-6.1 / BR-12 によりこの経路が `searchList` から生きた経路になる以上、「現行と同じ」は免責にならない。`core` を内側に使い `for update` を最外側にのみ 1 回付けることで二重化を解消する。

**W-3 でも同じ規則を適用する**: `wrapRownum` は `forUpdate` を関数の先頭で無条件に評価し、W-3（`start<=0 && max>0`）の枝でも `core` を使ったうえで最外側に 1 回だけ付ける。現行の `searchListForOracle` は `if (start > 0)` の外側では `forUpdate` の判定自体を行わず、`start<=0` のときは常にラップしない（`OracleDao.java:33`）ため、W-3 は本設計が新設する状態であり比較対象となる「現行の挙動」がない（§ 4.2「W-3 が新しい」）。したがって W-3 で `for update` 付きクエリが渡された場合も、最外側に 1 回だけ付ける同じ規則で正しく動作させる。

### 4.4 行の読み飛ばしを行わないこと

`Dao.searchList`（`Dao.java:181-184`）は `for (int i = 1; i < start; i++) rs.next();` で `start - 1` 行を読み飛ばす。**`OracleDao.searchList` はこれを override するため親の実装は実行されない**。rownum のラップが同じ読み飛ばしを DB 側で行っているため、コールバック内で重ねると `2 × (start-1)` 行が飛ぶ（BR-13）。

現行の `searchListForOracle` も同じ判断でこの処理をコメントアウトしている（`OracleDao.java:54-56`）。**コメントアウトを削除し、行わないことを積極的に記述する。**

### 4.5 `for update` の扱い（BR-14、BR-21）

`for update` の**判定**（`sql.toLowerCase().endsWith("for update")`、`:36`）と**付与位置**（最外側の末尾、`:48`）は現行の意図をそのまま踏襲する。ただし § 4.3 の是正により、**付与の中身は現行と異なる**——現行はラップの内側にも `for update` を残したまま外側にも付けていたが（実行不能な SQL を生成する既存の欠陥。死んだコードのため未発覚だった）、`wrapRownum` は `core` から `for update` を剥がしてラップし、最外側にちょうど 1 回だけ付け直す。

**ラップしない W-4 では `base` をそのまま返す**ため、`for update` は元の位置（唯一の位置）に残る。現行も `start <= 0` のときはラップせず `forUpdate` の判定自体を行っていない（`:33` の `if (start > 0)` の内側にある）ため、W-4 の挙動は現行と一致する。**W-1 / W-2 / W-3 は「現行と一致」ではなく「現行の欠陥を是正した新しい挙動」であり、そのように記録する。**

### 4.6 `hitCount` を設定しない

Oracle 経路は現行も `setHitCount` を呼んでいない。MySQL の `SQL_CALC_FOUND_ROWS` / `FOUND_ROWS()` に相当する機構がなく、総件数を得るには別途 `count(*)` を実行する必要がある。**FR-6.1 の範囲外であり、追加しない**（`domain-entities.md` § 3、BR-15）。現行と同じであるため挙動の変化ではない。

### 4.7 挙動が変わることの明示

**Oracle のページングが汎用経路の全行スキャンから rownum 方式に変わる**（`unit-of-work.md` U4「挙動が変わる点」、`requirements.md` NFR-7 の補足）。public API の破壊ではないが観測可能な挙動の変更であり、性能特性も変わる。AC-9 が判定する対象そのものである。

**`OracleDao` は `createQuery()` を override しない**という現行の非対称は維持する。バインド経路では `ValueConvertFilter` が適用されない（FR-2.2）ため、当該経路では非対称が解消される（`requirements.md` FR-5.1 の補足、`unit-of-work.md` U4 の実装上の制約）。

---

## § 5. `OracleValueConvertFilter` の null ガード（FR-6.4）

```java
// OracleSearch.java:11-15
static class OracleValueConvertFilter implements Search.ValueConvertFilter {
    public String convertValue(String value) {
        if (value != null) {
            return value.replace("'", "''");
        } else {
            return value;
        }
    }
}
```

`Search.DefaultValueConvertFilter`（`Search.java:99-107`）と `MySQLSearch.MySQLValueConvertFilter`（`MySQLSearch.java:11-20`）の形をそのまま採る。**AC-10 が「汎用実装および MySQL 実装と同じ結果が返る」を要求している以上、形は一意に決まる**（BR-16）。

**この修正はバインド経路とは無関係である。** バインド経路では `ValueConvertFilter` が適用されない（FR-2.2、U1 BR-18）。修正が必要なのは、旧リテラル経路（`SQLParser` 経由）が FR-7.1 により残り続けるためである（`components.md` M-7）。

**`OracleSearch` に他の変更はない。** クラス階層、コンストラクタ、パッケージいずれも不変である。

---

## § 6. FR-5.1 / FR-5.2 / FR-5.3 — 不作為の確認

| FR | 内容 | U4 の作業 |
|---|---|---|
| FR-5.1 | MySQL / Oracle / 汎用の 3 経路すべてで FR-1 が成立 | **§ 3 / § 4 がこれを実現する。** 汎用経路は U2 / U3 が担当済み |
| FR-5.2 | PostgreSQL は汎用フォールバックとして扱い、専用クラスを追加しない | **不作為。** 追加しないことを確認するだけである（`components.md`、OOS-4） |
| FR-5.3 | 方言ごとの `Conditions` が同じ述語を生成 | **不作為。** `Condition` / `MySQLCondition` / `PostgreSQLCondition` は変更しない |

### FR-5.3 が変更なしで成立する理由

`BindSqlBuilder.value(Column, Conditions, String...)`（U1 § 4.2）は、`SQLParser.value(...)` と**同じ `Conditions` の同じメソッド**（`getCondition()` / `getReplaceHolder()`）を使って述語を組み立てる。演算子と値テンプレートの供給元が同一であるため、リテラル版とバインド版で述語の**形**が一致する（値の位置が `'...'` か `?` かだけが異なる）。`Conditions` 実装に手を入れる余地はない（BR-17）。

**`MySQLCondition` / `PostgreSQLCondition` の内容そのものは検証しない。** それらが定義する演算子が現行どおり動くことは既存テスト（`MySQLSearchTest` 等）が固定しており、U4 は 1 行も変更しない。

---

## § 7. 実装順序

`unit-of-work-story-map.md`「U4 `dialects`」の 4 段に本文書の節を対応づける。

| 段 | 内容 | 本文書 | 完了の確認 |
|---|---|---|---|
| 1 | `OracleSearch.OracleValueConvertFilter` の null ガード（FR-6.4） | § 5 | **AC-10**。`OracleSearchTest` に null ケースを追加。他の作業と独立で最も小さい |
| 2 | `MySQLDao.searchList` の新経路化と LIMIT の扱い（FR-6.3 / FR-5.1） | § 1、§ 2、§ 3 | `MySQLDaoTest` が**無変更で緑**（`searchList` を呼ぶテストが現在存在しないため、これは自明に成立する） |
| 3 | `OracleDao.searchListForOracle` の修正・有効化（FR-6.1） | § 1、§ 2、§ 4 | **AC-9**。W-1〜W-4 の 4 状態それぞれで生成 SQL を確認。未バインドの `?` が 0 個 |
| 4 | 移行（Q7 = B） | BR-18 | `MySQLDaoTest` にページングアサートを追加し、mock の `getLastPreparedStatement().getPreparedSql()`（`component-methods.md` C-8、`:387` が規定するアクセサ）に `limit 0,5` がリテラルで現れ、バインド値は WHERE 由来のものだけであることを確認する。FR-8.2 対応表の該当行も起こす |

**3 が最後である理由**（`unit-of-work-story-map.md`）: `searchListForOracle` は現在どこからも呼ばれずテストもない。有効化は観測可能な挙動の変更（全行スキャン → rownum ページング）を伴うため、他の 2 件が済んで方言経路が安定してから行う。

**2 と 3 で `compose`（§ 2.2）を 2 回書くことになる。** 段 2 で書いた形を段 3 でそのまま写す。乖離させないことが BR-4 である。

---

## § 8. 2.6 / 2.7 契約からの差分

**この節が差分の全量である。** `domain-entities.md` の同名節は型・状態に関する差分だけを扱っており、本節を参照する。件数を要約せず 1 件ずつ列挙する。

| # | 上流の記述 | 修正後 | 由来 |
|---|---|---|---|
| **D-1** | `component-methods.md` M-7「`MySQLDao.searchList(Query,int,int)` は LIMIT を `" limit ?,?"` にしてオフセットと件数をバインドする（FR-6.3）」 | バインドしない。`" limit " + (start-1) + "," + max` の `int` リテラル連結を維持する。MySQL の LIMIT パラメータは整数でなければならず、ADR-007（値は一律 `setString`、U1 § 6）と両立しない。FR-6.3 は「`int` 型を経由する時点で SQL 構文を注入できない」という手段で充足する（§ 3.2、BR-6）。**FR-6.3 の本文（`requirements.md:103`）が求める「`int` の直接連結を解消すること」という文言そのものは満たさない**——連結の形は現行のまま残る。手段を変えて要件の意図を満たしたのであって、文面どおりの達成ではないことを 3.6 の FR-8.2 集計に引き継ぐ（§ 9-8） | Q2 = C |
| **D-2** | `component-methods.md` M-7「`OracleDao.searchListForOracle` は rownum の `?` をバインドし、`max` をページング範囲の決定に使う（FR-6.1）」 | `max` を使う点は採る。rownum の境界値は D-1 と同じ理由でリテラルで埋める。**AC-9 の「実行される SQL に未バインドの `?` が残っていない」は、`?` を作らないことで満たされる**（§ 4.2、BR-6） | Q2 = C ＋ Q3 = A |
| **D-3** | `unit-of-work.md` U4 の所有コンポーネント一覧が `OracleDao` について「`searchListForOracle` の修正と有効化」とのみ述べ、`searchList` の override に言及していない。**上流に規定がなかったのは 2.7（`unit-of-work.md`）だけであり、2.6（`component-methods.md` M-7、`:375`）は「修正して有効化し、`searchList` から呼ばれるようにする」と既に明記している。** D-3 は 2.7 の記述が 2.6 より狭かったことの補完であり、設計側の逸脱ではない | `OracleDao.searchList(Query,int,int)` を override して `searchListForOracle` に委譲する。**AC-9 が「`searchList(query, start, max)` が呼ばれる」を Given にしているため、override なしでは AC-9 を判定できない**（§ 4.3、BR-12） | `component-methods.md` M-7 ＋ AC-9 |
| **D-4** | `component-methods.md` M-5（U1 が確定）に、方言が `PreparedSql` を合成する経路の規定がない | 由来（checked / unchecked）を保つ private static ヘルパ `compose(PreparedSql, String)` を両方言に置く（§ 2.2、BR-4 / BR-5）。上流に規定がなかったのは、2.6 が「方言も `PreparedSql` 経路を使う」としか述べておらず、外部 `Query` 実装の `default` 実装が返す unchecked な `PreparedSql` を方言が受け取る場合を扱っていなかったためである | Q1 = A の帰結 |

**新規 public メンバ**（FR-3.1 の互換維持対象に加わるもの）: **なし。**

`OracleDao.searchList(Query,int,int)` は `Dao` の既存 public メソッドの override であり、新規メンバではない。`MySQLDao.searchList(Query,int,int)` も同様に既存の override である。

**新規 protected メンバ**（AC-11 の判定対象）: **なし。**

`compose` / `wrapRownum` はいずれも `private static` である。**`Dao` に共通ヘルパを置かない判断（§ 2.3）は、この行を「なし」に保つためでもある。**

**削除・シグネチャ変更**: **なし。** `OracleDao.searchListForOracle(Query,int,int)` は public のまま、シグネチャも戻り値の型も変えずに残す（NFR-3）。`useHitCount(boolean)` / `getHitCount()` / `setHitCount(long)` / `createSearch()` / `createQuery()` も不変である。

---

## § 9. 後続ステージへの引き継ぎ事項

| # | 引き継ぎ先 | 内容 |
|---|---|---|
| 1 | **Build and Test（3.6）** | **`limit '0','5'` 問題を実 DB で確認する必要はもうない**（Q2 = C によりバインドしないため）。代わりに確認すべきは、`MySQLDao.searchList` が生成する SQL に `limit 0,5` がリテラルで現れ、かつバインド値リストが WHERE 由来のものだけであることである。mock の `getLastPreparedStatement()` と、そこから SQL を取り出す `getPreparedSql()`（**`component-methods.md` C-8、`:387` が規定するアクセサ**。U1 の追加メンバ一覧 A-8〜A-12 には含まれないが、それより前の 2.6 の契約に既にある）で判定できる |
| 2 | **Build and Test（3.6）** | AC-9 の判定には W-1〜W-4 の 4 状態すべての生成 SQL を確認する必要がある。`OracleDao` は現在テストが 1 本もない（`OracleSearchTest` は `OracleSearch` のみ）ため、`OracleDaoTest` を新規に起こす。NFR-6（テスト件数を減らさない）に対しては純増になる |
| 3 | **Build and Test（3.6）** | `SQL_CALC_FOUND_ROWS` の `replaceFirst("SELECT ", ...)` が大文字・末尾スペースにしか一致しない脆さは現行から不変（§ 3.4）。固定するテストは置いていない |
| 4 | **Build and Test（3.6）** | FR-8.2 の対応表に U4 の該当行を起こす。対象は `MySQLDaoTest` のページング系アサートと、新設する `OracleDaoTest` の rownum 系アサートである |
| 5 | **Build and Test（3.6）** | `FOUND_ROWS()` が別の `PreparedStatement` で正しい値を返すこと（§ 3.3）は、セッション単位の状態に依存するため mock では検証できない。実 DB（FR-8.3 / OQ-6）が最初の確認機会になる。**mock で確認できるのは「2 本の文が同じ `Connection` から生成されたこと」までである** |
| 6 | **U5 `identifier-safety`** | `wrapRownum` が組み立てる `row_` / `rownum_` は識別子だが、いずれも本設計が固定した文字列であり呼び出し側から渡されない。FR-1.7 の対象外である |
| 7 | **Build and Test（3.6）** | NFR-1（実行経路に**値**の文字列連結が 0 件）の判定にあたり、`MySQLDao.java` の `" limit " + (start-1) + "," + max` と `OracleDao` の rownum 境界値の連結を**「値」に当たらない**と扱う読みが必要である（Q2 = C の帰結 3、BR-6）。**この読みの典拠は ADR-003 ではなく `requirements.md:108`**——「LIMIT の値は…実行時のユーザー入力ではなく呼び出し側が渡すページング指定である。したがって SM-1 の達成には必須ではなく」——である。ADR-003（`requirements.md:384`）は非推奨 API 経由でのみ到達できるリテラル生成経路を対象外にしたものであり、LIMIT の連結は非推奨経路ではなく生きた実行経路にあるため、ADR-003 の免除はそのままでは及ばない。SM-1 の測定手段を決めるときはこの正しい典拠を前提にすること |
| 8 | **Build and Test（3.6）** | **FR-6.3 は手段を変えて充足しており、文面（`int` の直接連結の解消）そのものは満たしていない**（§ 8 D-1）。FR-8.2 対応表・SM 集計で FR-6.3 を「文面どおり達成」と報告しないこと |
| 9 | **Build and Test（3.6）** | **MySQL と Oracle でページング窓が W-2 に限り揃わない**（`domain-entities.md` § 1、`business-rules.md` R-6）。同じ `(start, max)` に対し MySQL は `offset` を適用せず（実効窓 `[1, ∞)`）、Oracle は適用する。W-2 のテストは両方言で**別々の期待値**を書くこと。W-3 は両方言とも `[1, max]` で一致するため、共通の期待値で構わない |
| 10 | **Build and Test（3.6）** | `for update` の検出（`endsWith("for update")`）は `for update nowait` / `for update of ...` に一致しない（`business-rules.md` 既知の欠陥 5）。現行から不変の脆さであり本 Unit では是正しないが、Oracle 経路が生きた経路になる以上、`for update` 系の亜種を渡すテストケースがあれば挙動を確認しておくこと |

---

## Review

NOT-READY

**Reviewer:** aidlc-architecture-reviewer-agent（3.1 Functional Design / U4 `dialects`、iteration 2 — final）

参照した実ソース: `MySQLDao.java`、`OracleDao.java`、`OracleSearch.java`、`MySQLSearch.java`、`Dao.java`、`Search.java`、`QueryImpl.java`、`DBAccessManager.java`、`MySQLDaoTest.java`、`OracleSearchTest.java`。参照した契約: 本ステージの Q&A、`unit-of-work.md` / `unit-of-work-story-map.md` / `requirements.md` / `components.md` / `component-methods.md` / `services.md`、および U1 / U2 の 3.1 成果物（統合点の照合に限る）。

### iteration 1 の 2 件の blocking の検証結果

**B-1（`for update` の二重化）— 是正済み。手で追って確認した。**

`FOR_UPDATE = "for update"`、`core = base.substring(0, base.length()-10).trim()` として、`base = "SELECT ... for update"` を 4 状態それぞれに通した結果:

| 状態 | 生成される SQL | `for update` の出現 |
|---|---|---|
| W-1 (`start=3, max=5`) | `select * from ( select row_.*, rownum rownum_ from ( SELECT ... ) row_ ) where rownum_ > 2 and rownum_ <= 7 for update` | 最外側に 1 回 |
| W-2 (`start=3, max=0`) | `select * from ( select row_.*, rownum rownum_ from ( SELECT ... ) row_ ) where rownum_ > 2 for update` | 最外側に 1 回 |
| W-3 (`start=-1, max=5`) | `select * from ( SELECT ... ) where rownum <= 5 for update` | 最外側に 1 回 |
| W-4 (`start=-1, max=0`) | `SELECT ... for update`（`base` をそのまま返す） | 元の位置に 1 回 |

いずれも 1 回のみ、かつインラインビューの外側という構文上妥当な位置である。`substring` の長さ計算は `toLowerCase()` が文字数を変えないため大文字混在でも正しい。**この形は Hibernate `Oracle9iDialect.getLimitString` が採る形（`" for update"` を剥がしてからラップし、最外側に付け直す）と一致しており**、現行 `OracleDao.java:37-48` はその剥がし処理を落とした写し損ないである、という § 4.3 / BR-21 の読みは妥当である。B-1 は解消と判定する。

**B-2（W-2 / W-3 の方言間非対称）— 部分的にしか是正されていない。W-2 は正しくなったが、W-3 の記述が実挙動と食い違う（下記 B-1 として再提起）。**

### Blocking

**B-1 — MySQL の W-3 実効窓が 3 成果物すべてで誤っている。`MySQLDao.java:60-61` と矛盾し、BR-2 とも内部矛盾する**

W-3 は `start <= 0 && max > 0` である。`MySQLDao.searchList` はこの条件で LIMIT を付けないが、**コールバック内の `if (max > 0 && add >= max) break;`（`MySQLDao.java:60-61`）は `max > 0` なので確実に発火する**。したがって MySQL の W-3 で返る行は先頭から `max` 件、すなわち実効窓は **`[1, max]`** である。これは Oracle の W-3（`where rownum <= {max}`）と**同一の行集合**であり、W-3 に行集合レベルの方言間非対称は存在しない（違いは打ち切りが DB 側か クライアント側かだけで、行集合ではなく性能特性の差である）。

現状の記述:

| 箇所 | 記述 | 実挙動 |
|---|---|---|
| 本文書 § 1 表 W-3 行 | MySQL の実効窓 = **`[1, ∞)`** | `[1, max]` |
| 本文書 § 1 本文（W-3 の箇条書き） | 「MySQL は現行のまま何も付けない（`[1, ∞)`、**= 全件**）」 | 全件は返らない。`max` 件で打ち切られる |
| `domain-entities.md` § 1 表 W-3 行 | MySQL の実効窓 = **`[1, ∞)`** | `[1, max]` |
| `domain-entities.md` § 1 本文 | 「コールバック内の `add >= max` だけで打ち切る、**W-3 なら打ち切りも起きない**」 | 逆。打ち切りが起きないのは W-2（`max <= 0`）であり、W-3 では起きる |
| `business-rules.md`「現行から引き継ぐ既知の欠陥」4 | 「**W-3 では `max` による打ち切りも起きない**」 | 同上、逆 |
| `business-rules.md` R-6 | 「W-2 / W-3 で揃わない。MySQL は `offset` を適用せず実効窓が `[1, ∞)`」 | 揃わないのは W-2 のみ。W-3 は `start <= 0` なので両方言とも `offset = 0` であり、`offset` の適用/不適用という論点自体が成立しない |
| BR-1 | 「W-2 / W-3 では MySQL は `offset` を適用しない」 | W-3 について空虚（`offset` は 0）。W-3 の実質的な論点は `max` の扱いであって `offset` ではない |
| § 9-9（3.6 への引き継ぎ） | 「W-2 / W-3 で…両方言のテストは**別々の期待値**で書く必要があり」 | W-3 で別々の期待値を書くと誤る。MySQL W-3 に「全件が返る」を期待するテストは必ず落ちる |

**内部矛盾も生じている。** BR-2 は「それ以外はコールバック内の `add >= max` **だけで打ち切る**現行の挙動を維持する」と正しく書いており、新設された W-3 行（`[1, ∞)` = 打ち切りなし）と真っ向から矛盾する。iteration 1 の B-2 は「窓の表が `MySQLDao` の実挙動と合っていない」ことを指摘したものであり、**是正パスは W-2 を直す一方で W-3 に新しい誤りを持ち込んだ**。

`business-rules.md` は編集対象外のため本文書に記録するにとどめるが、是正時に必要な範囲は次のとおり:
1. 両文書の W-3 行の MySQL 実効窓を `[1, max]` に直し、「全件」「打ち切りも起きない」の記述を削る。
2. R-6 の適用範囲を **W-2 のみ**に絞る（W-3 は行集合が一致し、残るのは「DB 側で絞るか全件フェッチしてから捨てるか」という性能差である。残すならその性質で書く）。
3. BR-1 の但し書きを W-2 に限定する。
4. § 9-9 を「W-2 についてのみ方言別の期待値。W-3 は両方言とも先頭 `max` 件で一致する」に改める。

### Non-blocking

**N-1 — § 4.2「生成する SQL」表がまだ `<base>` を埋め込む形で書かれている。** B-1 の是正後は内側に入るのは `core`（`for update` を剥がした後）である。§ 4.3 のコードと BR-21 が正であることは § 4.5 が明記しているが、表だけを見て実装すると是正前の形に戻る。表の `<base>` を `<core>` に改め、最外側の `for update` を表にも 1 列足すこと。

**N-2 — `for update` の判定が `endsWith("for update")` である以上、`for update nowait` / `for update of t.c` / 末尾に空白が残る形では `forUpdate == false` になり、`for update` が**インラインビューの内側**に取り残されて実行不能な SQL になる。** これは W-1 / W-2 / W-3 のすべてで起きる。現行 `OracleDao.java:36` から引き継ぐ制約だが、B-1 の是正で採った論法（「死んだコードだったから免責にはならない」）はこの変種にもそのまま当てはまる。写し元の Hibernate は `sql = sql.trim()` を先に行っており、少なくとも空白差分は防いでいる。`business-rules.md`「現行から引き継ぐ既知の欠陥」に 1 行として記録するか、`trim()` を入れること。なお `for update` を生成するコードはリポジトリ内に存在しない（`src/main` の grep で `OracleDao` の 3 行のみ）ため、この経路は外部 `Query` 実装からのみ到達する。

**N-3 — iteration 1 の N-3 が `domain-entities.md` 側に残っている。** § 2.2 と BR-5 は「分岐条件は `hasUnboundPlaceholders()` であって由来そのものではない」と正しく書き直されたが、`domain-entities.md` § 2 の見出し「**由来（provenance）を保つ規則**」、不変条件 **DS-3「合成が入力の由来（checked / unchecked）を変えない」**、および本文書 § 8 **D-4「由来（checked / unchecked）を保つ private static ヘルパ」**は旧文面のままである。しかも BR-5 は自らが否定した DS-3 を「由来」列で引いている。DS-3 は不変条件（＝守るべき規範）として書かれている以上、実装者は BR-5 と DS-3 のどちらが正かを判断できない。DS-3 を「合成は `hasUnboundPlaceholders()` の値を変えない」に改めるのが実態と一致する。

**N-4 — `getPreparedSql()` の典拠ファイルが誤っている。** § 7 段 2 と § 9-1 は「`components.md` C-8 が規定するアクセサ」と書くが、`components.md` C-8（`:154-162`）は「SQL を保持してテストから取り出せるようにする」としか書いておらずメソッド名を規定していない。`getPreparedSql()` を名指しで規定しているのは **`component-methods.md` C-8（`:387`）**である。U1 `business-logic-model.md`（`:631`）も同じ帰属で書いている。実体は存在する（U1 § 8.1 が `MockPreparedStatement.getPreparedSql()` の追加を明記）ため iteration 1 の N-2 は解消済みだが、典拠は差し替えること。

**N-5 — 規則の「由来」列がレビュー所見 ID を指している。** BR-21 の由来が「B-1」、BR-5 の由来が「N-3 の是正」、R-6 の扱い列が「B-2 の是正」、本文書 § 1 / § 4.3 / § 4.5 の本文も「B-1 の是正」「B-2 の是正」と書く。これらの ID が定義されているのは本 `## Review` 節だけであり、**その節はイテレーションごとに上書きされる**（現に iteration 1 の B-1 / B-2 / N-3 は本節の書き換えで消えた）。3.6 以降の読者にとってダングリング参照になる。由来列には根拠となる上流（`OracleDao.java:37-48` の欠陥、`MySQLDao.java:60-61` の実挙動、Q2 = C 等）を書き、レビュー所見 ID は本文の脚注に留めること。

**N-6 — `domain-entities.md` § 1 の「（欠陥 2、§ 1「本ステージが解決する既存欠陥」参照）」が存在しない節を指している。** `domain-entities.md` に当該見出しはなく、Oracle の 3 欠陥を列挙しているのは本文書 § 4.1「現行の 3 つの欠陥」である。参照先を差し替えること。

**N-7 — § 8 D-3 が「上流に規定がない差分」として提示しているものは、2.6 に既にある。** D-3 は `unit-of-work.md` U4 が `searchList` の override に言及していないことを根拠にしているが、`component-methods.md` M-7（`:375`）は「修正して有効化し、**`searchList` から呼ばれるようにする**」と明記している。D-3 は「2.7 の記述が 2.6 より狭い」ことの補完であって設計側の逸脱ではない。差分の全量を主張する節である以上、性質を正確に書き分けること。

**N-8 — § 7 段 2 の完了条件が段 4 に依存したままである（iteration 1 N-4 の残り）。** 自己矛盾（「無変更で緑」と「新しいアサートが通る」の同時要求）は解消されたが、段 2 の「完了の確認」列は依然として「段 4 で追加するテストで…確認する」を含んでおり、段 2 単体では閉じられない。段 2 の確認は「`MySQLDaoTest` が無変更で緑」に限り、リテラル `limit 0,5` の確認は段 4 の行に移すのが素直である。

### 反証を試みたが妥当だった主張（記録）

- `wrapRownum` の 4 状態トレース（上表）。`core` の剥がし、最外側 1 回の付与、W-4 の `base` 素通しはいずれも正しい。W-1 の `rownum_ > offset and rownum_ <= offset+max` を最外側にまとめる形は Hibernate の「内側に上限、外側に下限」より stop-key の効きが弱いが、NFR-7（性能目標を置かない）の下では欠陥ではない。
- W-1 / W-2 の MySQL 実効窓（`(offset, offset+max]` / `[1, ∞)`）は `MySQLDao.java:42-62` と一致する。W-2 の非対称（MySQL は `offset` を無視、Oracle は適用）は実在し、R-6 に記録する判断は妥当である。
- BR-9（`FOUND_ROWS()` の同一セッション性）は `DBAccessManager.java:37`（`ThreadLocal<Connection>`）と `:49-63` / `:88-95` で裏が取れる。`preparedStatement(String)` が `getExecutedQuery().add(sql)` を行う（`:90`）ため、BR-10 の「0 件 → 2 件」も正しい（現行は `createStatement()`（`:80-86`）が記録しないため 0 件）。
- iteration 1 N-1 の是正: `QueryImpl.java:141` が `select.append(SELECT + " ")`、`:138` が `StringBuilder select = new StringBuilder();` であることを確認。§ 3.4 と `business-rules.md` 欠陥 1 の両方で直っている。
- iteration 1 N-5 の是正: `requirements.md:103` が FR-6.3 本文、`:108` が「FR-6.3 の位置づけ」（「SM-1 の達成には必須ではなく」）であることを確認。§ 9-7 / `business-rules.md` R-1 直下 / NFR-1 行の典拠差し替えは正しい。
- iteration 1 N-6 の是正: § 8 D-1 と § 9-8 が「FR-6.3 の文面（`int` 連結の解消）は満たさない」を明示しており、3.6 の誤報告を防ぐ。
- FR-6.4 の形は `Search.java:99-107` / `MySQLSearch.java:11-20` と一致（`@Override` の有無を除く）。VF-2 の変換内容（MySQL のみ `\` → `\\`）も `MySQLSearch.java:15` と一致。`OracleSearchTest.java` は現状 null ケースを持たないため、段 1 の追加は純増である。
- U1 / U2 との統合点: `PreparedSql.of(String, List<BindValue>)` / `ofLiteral(String)` / `hasUnboundPlaceholders()` は U1 `domain-entities.md` の形と一致し、unchecked かつ `?` 0 個で偽になる点も U1 の判定表（`:185-186`）と一致する。`Dao.executeQuery(PreparedSql, ResultSetHandler<R>)` は U2 § 8.2 で protected として定義されており、`MySQLDao` / `OracleDao` は `Dao` のサブクラスなので到達できる。`catch (DaoException e) { handleException(cause) }` の形も U2 BR-16 と同形。`MockPreparedStatement.getPreparedSql()` は U1 § 8.1（`:560`）に実在する。
- 「U4 は新規 public / protected メンバを 1 つも追加しない」は成立（`compose` / `wrapRownum` は private static、`OracleValueConvertFilter` は package-private、`searchList` は `Dao.java:177` の override）。AC-11 の判定対象は増えない。
- ID の衝突なし: BR-21 / R-6 はいずれも新規で既存 ID と衝突しない。BR-1〜BR-21 / PW-1〜PW-4 / DS-1〜DS-4 / VF-1〜VF-2 / W-1〜W-4 / D-1〜D-4 / R-1〜R-6 に未定義参照はない（N-5 のレビュー ID と N-6 の節参照を除く）。

---

**適用記録（オーケストレータ、2026-08-09。レビュアーの iteration 上限到達後）**

`reviewer_max_iterations: 2` に達したため、iteration 2 の指摘（blocking 1 件、non-blocking 8 件）は**レビュアーの再検証を受けずに**適用した。いずれも修正内容が一意に定まり、実ソース（`MySQLDao.java:60-61` 他）で裏を取れるものである。

| 指摘 | 適用した修正 | 実ソースでの検証 |
|---|---|---|
| **blocking** — MySQL の W-3 実効窓が 3 成果物すべてで誤っており（`[1,∞)` と記載）、`MySQLDao.java:60-61` の break 条件（`max>0` のみで判定、`paged` を参照しない）と矛盾し、BR-2 自身の記述とも矛盾していた | `business-logic-model.md` § 1・§ 9-9、`domain-entities.md` § 1、`business-rules.md` BR-1・R-6・既知の欠陥 4 の W-3 記述を `[1, max]`（両方言一致、非対称なし）に訂正。R-6 の適用範囲を **W-2 のみ**に絞った | `MySQLDao.java:60-61` の `if (max > 0 && add >= max) break;` が `start` と無関係に `max>0` だけで発火することを確認 |
| non-blocking N-1 — § 4.2 の SQL 表が是正前の `<base>` のまま | 表を `<core>` ＋ `[for update]` 接尾辞の形に改めた | § 4.3 の `wrapRownum` 実装と整合 |
| non-blocking N-2 — `endsWith("for update")` が `for update nowait` 等に一致しない | `business-rules.md`「現行から引き継ぐ既知の欠陥」に項目 5 として記録（是正はしない。現行 `OracleDao.java:36` から不変の脆さであるため） | `OracleDao.java:36` が同じ単純比較であることを確認 |
| non-blocking N-3 — `domain-entities.md` DS-3 / § 2 見出しが「由来を変えない」という旧文面のまま、BR-5 の書き直しと矛盾 | DS-3 と § 2 冒頭を「`hasUnboundPlaceholders()` の値を変えない（稀なケースを除く）」に改めた | — |
| non-blocking N-4 — `getPreparedSql()` の典拠が `components.md` C-8 と誤記（メソッド名を規定しているのは `component-methods.md` C-8） | § 7 段 4・§ 9-1 の典拠を `component-methods.md` C-8（`:387`）に差し替えた | `component-methods.md:387` で確認 |
| non-blocking N-5 — 規則の「由来」列や本文がレビュー所見 ID（B-1/B-2/N-3 等）を指しており、`## Review` 節の上書きでダングリング参照になる | 該当箇所を実ソース・上流契約への直接参照に差し替えた | — |
| non-blocking N-6 — `domain-entities.md` § 1 が存在しない節「本ステージが解決する既存欠陥」を参照 | `business-logic-model.md` § 4.1「現行の 3 つの欠陥」への参照に差し替えた | — |
| non-blocking N-7 — § 8 D-3 が「2.7 に規定がない」ことのみを根拠にしていたが、2.6（`component-methods.md` M-7:375）には既に `searchList` からの呼び出しが明記されていた | D-3 に 2.6 の既存規定を明記し、性質を「2.7 の記述が 2.6 より狭かったことの補完」に修正 | `component-methods.md:375` で確認 |
| non-blocking N-8 — § 7 段 2 の完了条件が段 4 のテストに依存していた | 段 2 の確認を「`MySQLDaoTest` が無変更で緑」のみに絞り、mock アサートの確認は段 4 の行に移した | — |

**未解消の指摘はない。** ただし上表の 9 件はいずれもレビュアーの再検証を経ていない。

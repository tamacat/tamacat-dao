# Functional Design Questions — U1 `bind-foundation`

Unit: **U1 `bind-foundation`**（`unit-of-work.md`）
Stage: Functional Design（3.1）/ Construction
Depth: Standard（`aidlc-state.md`）

## Sources

- `unit-of-work.md` — U1 の責務・境界・実装上の制約、Unit 内の実装順序 1〜8
- `unit-of-work-story-map.md` — U1 が担う FR-2.1〜2.4 / 4.1 / 4.2 / 8.1 と AC-5 / AC-6 / AC-8
- `requirements.md` — FR-2、FR-4、FR-8.1、NFR-3（再コンパイル不要）、NFR-7（性能目標なし）、CON-1（Java 8）、CON-6（新規依存なし）
- `components.md` — C-1〜C-8、M-5 `DBAccessManager`、M-6 `SQLParser`
- `component-methods.md` — 各コンポーネントのシグネチャ。C-2 の `ofLiteral`、C-6 の setter 対応表、C-8 の mock 拡張
- `services.md` — R-2 JDBC セッション管理、R-5 実行の観測
- `decisions.md` — ADR-007（setter 選択。`setNull` の SQL 型は「Functional Design（3.1）の担当」と明記）、ADR-012（`?` の走査規則は「Functional Design（3.1）で確定する」と明記）、ADR-009（mock 拡張）
- 実ソース — `SQLParser.java`、`Search.java`、`DBAccessManager.java`、`Dao.java`、`MockDriver.java`、`MockConnection.java`、`MockStatement.java`、`MockPreparedStatement.java`、`SQLParserTest.java`

本ステージが決めるのは U1 の内部アルゴリズムと規則である。設問は 2.6 が明示的に 3.1 に委ねた 2 点（Q2 / Q4）と、実コードを読んで初めて判明した 2 点（Q1 / Q3）に絞った。

---

## Q1. `DBAccessManager.executeQuery(PreparedSql)` が作った `PreparedStatement` を誰が閉じるか

**現状（`DBAccessManager.java:88-104`）**

```java
public PreparedStatement preparedStatement(String sql) {
    getExecutedQuery().add(sql);
    return getConnection().prepareStatement(sql);   // 毎回新規。誰も close しない
}

public ResultSet executeQuery(String sql) {
    getExecutedQuery().add(sql);
    return getStatement().executeQuery(sql);        // ThreadLocal に 1 本だけキャッシュした Statement
}
```

`getStatement()` は `ThreadLocal<Statement>` に 1 本をキャッシュし、`release()`（`:167-179`）が閉じる。だから現行の `executeQuery(String)` は何度呼んでも `Statement` が増えない。

**新経路の差分**: `PreparedSql` 版は SQL ごとに `prepareStatement` が要るためキャッシュが効かない。呼び出し側（`Dao.search`、`Dao.java:157`）は `ResultSet` しか閉じない。

```java
// Dao.java:157（U2 で PreparedSql 版に切り替わる）
try (ResultSet rs = executeQuery(query.getSelectSQL())) { ... }   // Statement は閉じられない
```

`component-methods.md` M-5 は「`prepareStatement` → `bind` → `executeQuery`」までしか述べておらず、後始末を規定していない。SELECT を N 回実行するとスレッドあたり `PreparedStatement` が N 本残る。

- **A.** `DBAccessManager` が生成した `PreparedStatement` を `ThreadLocal<List<Statement>>` に積み、既存の `release()` / `stop()` / `shutdown()` で一括 close する。`executeUpdate(PreparedSql)` は `ResultSet` を返さないので finally で即 close する（積まない）
- **B.** `PreparedStatement.closeOnCompletion()`（JDBC 4.1 / Java 7+）を呼び、`ResultSet` の close に連動させる。未対応ドライバの `SQLFeatureNotSupportedException` は握りつぶす
- **C.** A と B の併用。`closeOnCompletion()` を best-effort で呼んだうえで、`release()` でも確実に閉じる（対応ドライバでは早く解放され、非対応ドライバでもセッション終了時には必ず解放される）
- **D.** 閉じない。現行 `preparedStatement(String)`（BLOB 経路）が閉じていないのと挙動を揃え、後始末は別途扱う
- **X.** Other (please specify)

補足: `MockStatement.closeOnCompletion()`（`:196-197`）は空実装のため、B / C を採ってもモックテストの挙動は変わらない。実効果は実ドライバでのみ現れる。C は A に対して数行しか増えない。

### Q1 補足（2026-08-09、ユーザーからの確認に対する回答）

**確認 1: そもそも `PreparedStatement` は再利用できるのか。**

`PreparedStatement` は生成時に 1 本の SQL テキストに束縛される（`connection.prepareStatement(sql)`）。再利用とは「同じ SQL テキストのまま `clearParameters()` して値を入れ替え再実行する」ことだけを指す。新経路は呼び出しごとに SQL テキストが変わる（`Search` の条件が変われば WHERE の形が変わる）ため、現行 `getStatement()` のような ThreadLocal に 1 本キャッシュする方式は成立しない。`Map<String, PreparedStatement>` を持てば再利用できるが、これは `requirements.md` OOS-2 / NFR-7 が明示的にスコープ外とした「PreparedStatement キャッシュ」そのものである。**したがって再利用はしない。**

**確認 2: メソッド内で try-with-resources で閉じると問題があるのか。**

`executeUpdate(PreparedSql)` は問題ない。`ResultSet` を返さないため、try-with-resources が正解である。

```java
public int executeUpdate(PreparedSql sql) {
    getExecutedQuery().add(sql.getSql());
    getExecutedStatements().add(new ExecutedStatement(sql));
    try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
        PreparedStatementBinder.bind(ps, sql.getValues());
        return ps.executeUpdate();
    } catch (SQLException e) { throw new DaoException(e); }
}
```

`executeQuery(PreparedSql)` は壊れる。JDBC 仕様上、`Statement` を閉じるとその `ResultSet` も閉じられる。

```java
public ResultSet executeQuery(PreparedSql sql) {
    try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
        PreparedStatementBinder.bind(ps, sql.getValues());
        return ps.executeQuery();   // ps が閉じられ、返した ResultSet も閉じ済みになる
    }                               // 呼び出し側の rs.next() が SQLException
}
```

呼び出し側 `Dao.search`（`Dao.java:157`）/ `Dao.searchList`（`:180`）は `try (ResultSet rs = ...)` で `ResultSet` だけを閉じており、`Statement` の handle を持たない。

**したがって Q1 は「`executeQuery` の `PreparedStatement` をどう閉じるか」に絞られる。** 選択肢 A〜D に加え、確認 2 を受けて選択肢 E を追加する。

- **E.** `executeQuery` を `ResultSet` を返さない形に変え、try-with-resources でメソッド内に閉じ込める。`DBAccessManager` に `<R> R executeQuery(PreparedSql, ResultSetHandler<R>)`（コールバック）を追加し、`Dao.search` / `searchList` がマッピング処理をコールバックとして渡す

  ```java
  public <R> R executeQuery(PreparedSql sql, ResultSetHandler<R> handler) {
      getExecutedQuery().add(sql.getSql());
      getExecutedStatements().add(new ExecutedStatement(sql));
      try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
          PreparedStatementBinder.bind(ps, sql.getValues());
          try (ResultSet rs = ps.executeQuery()) { return handler.handle(rs); }
      } catch (SQLException e) { throw new DaoException(e); }
  }
  ```

  リークが構造的に起きない。ただし `ResultSetHandler` という public 型が 1 つ増え、`Dao.search` / `searchList`（U2 の担当範囲）の内部構造を書き換える必要がある。`Dao` の public シグネチャは変わらないため NFR-3 / FR-3.1 は満たす。

[Answer]: E（コールバック化して try-with-resources でメソッド内に閉じ込める）— 2026-08-09、**Mode:** guided

**この選択は 2.6 の `component-methods.md` M-5 / M-4 の契約を変更する。** M-5 が宣言していた `public ResultSet executeQuery(PreparedSql sql)` は**追加しない**。代わりに `public <R> R executeQuery(PreparedSql sql, ResultSetHandler<R> handler)` を追加し、`org.tamacat.sql.ResultSetHandler`（public functional interface）を新設する。M-4 の `protected ResultSet executeQuery(PreparedSql sql)` も同様にコールバック形になる。この差分は `business-logic-model.md` の「2.6 契約からの差分」節に記録し、U2 への引き継ぎ事項とする。

U2 に残す論点（本 Unit では決めない）: 現行 `Dao.search`（`Dao.java:155-167`）は `catch (SQLException e) { handleException(e); }` を持つが、`dbm.executeQuery(String)` が既に `SQLException` を `DaoException` に包んでいるため、この catch が実際に捕まえるのは `rs.next()` / `mapping()` の失敗だけである。コールバック化すると `rs.next()` / `mapping()` も `DBAccessManager` の内側で走るため、この funnel をどう維持するか（維持すると実行失敗まで `handleException` を通るようになり、現行より広がる）を U2 の Functional Design が決める必要がある。

---

## Q2. `setNull(int, int sqlType)` の第 2 引数に何を渡すか

**ADR-007 が 3.1 に委ねた点**（Neutral 節）: 「`setNull` を使う場合、JDBC は `setNull(int, int sqlType)` の第 2 引数に SQL 型が必要である。`DataType` からの対応付けが要る。この詳細は Functional Design（3.1）の担当とする。」

**確定済みの部分（ADR-007 / `component-methods.md` C-6）**

```java
// PreparedStatementBinder（org.tamacat.sql、package-private）
static void bind(PreparedStatement stmt, List<BindValue> values) {
    for (int i = 0; i < values.size(); i++) {
        BindValue v = values.get(i);
        int pos = i + 1;
        if (v.isNull()) {
            stmt.setNull(pos, sqlTypeOf(v.getType()));   // ← ★ ここが未決
        } else if (v.getType() == DataType.OBJECT) {
            stmt.setBinaryStream(pos, v.getStream());
        } else {
            stmt.setString(pos, v.getValue());           // NUMERIC / FLOAT / DATE / TIME も setString（ADR-007）
        }
    }
}
```

**NULL になる経路（`SQLParser.java:93-111` の現行挙動）**: NUMERIC / FLOAT に空文字または null、DATE / TIME に空文字・null・`"NULL"`。現行はいずれもリテラル `null` をテキストに埋め込んでいる。

- **A.** `DataType` の意味に沿って割り当てる — STRING / BOOLEAN → `Types.VARCHAR`、NUMERIC / FLOAT → `Types.NUMERIC`、DATE → `Types.DATE`、TIME → `Types.TIMESTAMP`、OBJECT → `Types.BLOB`
- **B.** 値と同じく一律 `Types.VARCHAR`（OBJECT のみ `Types.BLOB`）。ADR-007 の「すべて `setString`」と形を揃える
- **C.** 一律 `Types.NULL`
- **X.** Other (please specify)

論点: ADR-007 が値に `setString` を選んだ根拠は「現行のリテラル経路は数値を引用符なしで埋め込み、型変換を DB エンジンに委ねている。その挙動を変えない」ことにある。NULL には変換すべきテキストがないので、この根拠は `setNull` には及ばない。一方、Oracle など型に厳格なドライバは NUMERIC カラムへの `Types.VARCHAR` を拒否しうる。B は形が揃うが、その拒否リスクを負う。C は JDBC 仕様上は認められるがドライバ差が最も大きい。

いずれの案も**モックテストでは差が出ない**（`MockPreparedStatement.setNull` は空実装）。実 DB での確認は FR-8.3（`UserDaoTest2` / Derby）が最初の機会になる（ADR-007 の Negative、OQ-6）。

TIME → `Types.TIMESTAMP` とする根拠: `MappingUtils.TIME_FORMAT`（`:24`）が `"yyyy-MM-dd HH:mm:ss"` であり、TIME カラムが時刻ではなく日時を運んでいる。

[Answer]: A（DataType ごとの SQL 型）— 2026-08-09、**Mode:** guided

---

## Q3. テストが `MockPreparedStatement` に到達する経路をどう用意するか

**FR-8.1 が求めること**: 「テストから、各バインドパラメータの**位置と値**をアサートできること。」Bolt 1 の Definition of Done も「拡張した `MockPreparedStatement` からアサートできる」としている。

**実コードで判明した問題**

```java
// MockConnection.java:168-170
public PreparedStatement prepareStatement(String sql) throws SQLException {
    return new MockPreparedStatement(this);   // 毎回新規。呼び出し側（DBAccessManager）にしか渡らない
}

// MockDriver.java:29-43
public MockDriver() { this.connection = new MockConnection(); }   // インスタンスごとに 1 本
public Connection connect(String url, Properties info) { return connection; }   // 常に同じものを返す
```

既存の e2e テストは `dao.getExecutedQuery()` 経由でしかアサートしていない（`UserDaoTest.java:41-44` ほか）。`MockPreparedStatement` に `getBoundValues()` を足しても、テストがそのインスタンスを取得する経路が現状ない。`component-methods.md` C-8 はこの点を規定していない。

- **A.** `MockDriver` に static 自己参照を追加し、`MockDriver.getInstance().connect(null, null)` で登録済みインスタンスの共有 `MockConnection` を取得できるようにする。`MockConnection` に `getPreparedStatements()` / `getLastPreparedStatement()` / `clearPreparedStatements()` を追加する

  ```java
  // before（MockDriver.java:21-31）
  static { try { DriverManager.registerDriver(new MockDriver()); } catch (SQLException e) { e.printStackTrace(); } }

  // after
  private static MockDriver INSTANCE;
  static { try { INSTANCE = new MockDriver(); DriverManager.registerDriver(INSTANCE); } catch (SQLException e) { e.printStackTrace(); } }
  public static MockDriver getInstance() { return INSTANCE; }

  // テスト側
  MockConnection con = (MockConnection) MockDriver.getInstance().connect(null, null);
  MockPreparedStatement ps = con.getLastPreparedStatement();
  assertEquals("admin", ps.getBoundValue(1));
  ```

- **B.** 新規 API を足さない。テスト側が `DriverManager.getConnection("jdbc:mock://localhost/test", "test", "test")` を呼び、返る `MockConnection` にキャストする（`connect()` が常に同じインスタンスを返すため、DAO が使ったものと同一）
- **C.** `MockPreparedStatement` は記録するが取得口を用意せず、FR-8.1 のアサートは `DBAccessManager.getExecutedStatements()`（C-7、ADR-008）だけで満たすことにする
- **X.** Other (please specify)

論点: A は production jar の public API を 1 つ増やす（以後 FR-3.1 の互換維持対象になる。ただし追加のみで NFR-3 は満たす）。B は新規 API ゼロだが、`MockDriver.acceptsURL` が URL を問わず常に `true` を返す（`MockDriver.java:37-39`）ため、FR-8.3 / OQ-6 で Derby を classpath に加えると `DriverManager` が返すドライバが登録順に依存しうる。C は「バインド値をアサートできる」という要件の文面は満たすが、`getExecutedStatements()` が記録するのは**渡そうとした値**であり、`PreparedStatement.setXxx` に**実際に適用された値**ではない。`PreparedStatementBinder`（C-6）の正しさを直接検証できるのは A / B だけである（`unit-of-work.md` U1「mock 拡張を含める理由」）。

[Answer]: A（`MockDriver.getInstance()` を追加）— 2026-08-09、**Mode:** guided

---

## Q4. `hasUnboundPlaceholders()` の走査が判定不能なとき、実行を通すか拒否するか

**ADR-012 が 3.1 に委ねた点**: 「`ofLiteral` で作られた `PreparedSql`（値ゼロかつ検査未実施）に限り、引用符の外にある `?` のみを数える。この走査規則は Functional Design（3.1）で確定する。」

**本ステージが起こす走査規則（案）**

```java
// 引用符の外にある ? だけを数える。'' は引用符内のエスケープされた ' として読み飛ばす
int count = 0; boolean inQuote = false;
for (int i = 0; i < sql.length(); i++) {
    char c = sql.charAt(i);
    if (c == '\'') {
        if (inQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') { i++; continue; }
        inQuote = !inQuote;
    } else if (c == '?' && !inQuote) {
        count++;
    }
}
```

この規則が現行の 3 つの `ValueConvertFilter` すべてに対して正確であることは確認済み: `DefaultValueConvertFilter`（`Search.java:99-107`）と `OracleValueConvertFilter`（`OracleSearch.java:11-15`）は `'` → `''` のみ、`MySQLValueConvertFilter`（`MySQLSearch.java:11-20`）は `'` → `''` を先に適用してから `\` → `\\` を適用するため、**単独の `\'` が生成されることがない**。

**残る穴**: 走査が終わった時点で `inQuote` が真（引用符が閉じていない）の場合、数えた値は信頼できない。

- **A.** `inQuote` が真で終わったら「未束縛なし」（`false`）を返して実行に進める。ドライバがパラメータ未設定を報告する現行相当の失敗に落ちる
- **B.** `inQuote` が真で終わったら `DaoException` で拒否する
- **X.** Other (please specify)

論点: 誤検出（本当は問題ないのに拒否）は、これまで動いていた呼び出しを `DaoException` で落とす回帰であり NFR-3 に反する。検出漏れ（未束縛の `?` を見逃す）はドライバのパラメータ未設定報告に落ちるだけで、ADR-012 導入前と同じである。コストが非対称なので A は検出漏れ側に倒す案、B は安全側に倒す案である。

[Answer]: B（判定不能なら `DaoException` で拒否）— 2026-08-09、**Mode:** guided

**推奨（A）と異なる選択である。** 本設計は B に従い、走査が `inQuote = true` で終わった場合を「未束縛あり」と同様に扱って `DaoException` を投げる。安全側に倒すというユーザーの判断であり、押し返さない。ただし本文の論点で述べた回帰リスク（これまで動いていた呼び出しが落ちうる）は消えないため、`business-rules.md` に (1) 現行 3 実装の `ValueConvertFilter` が閉じない引用符を生成しないこと、(2) それでも到達した場合のエラーメッセージが走査失敗であることを明示すること、の 2 点を規則として記録する。

---

## Ambiguity and Contradiction Analysis

`stage-protocol.md` §3 が必須とする回答分析。

**曖昧な回答**: なし。4 件とも単一の選択肢が確定している。

**矛盾**: なし。相互作用を確認した組み合わせは次のとおり。

| 組み合わせ | 確認内容 | 判定 |
|---|---|---|
| Q1=E ＋ Q3=A | どちらも production jar の public 表面を増やす（`ResultSetHandler`、`MockDriver.getInstance()`）。FR-3.1 の互換維持対象が 2 つ増える | **矛盾なし。** いずれも追加のみで既存シグネチャの削除・変更がないため NFR-3 を満たす。CON-6（新規第三者依存）にも該当しない |
| Q2=A ＋ ADR-007 | ADR-007 は値に一律 `setString` を選んだが、A は `setNull` の型を DataType ごとに分ける | **矛盾なし。** ADR-007 の根拠は「現行の DB 側の型変換を維持する」ことであり、NULL には変換すべきテキストがないためこの根拠が及ばない。`decisions.md` の決定を覆していない |
| Q4=B ＋ NFR-3 | B は走査が判定不能なとき既存の呼び出しを `DaoException` で落としうる | **実質的な回帰リスクは確認できなかった。** この経路は `ofLiteral` で作られた `PreparedSql`（旧 override 経由）にしか適用されず、かつ現行 3 実装の `ValueConvertFilter`（`Search.DefaultValueConvertFilter`、`MySQLSearch.MySQLValueConvertFilter`、`OracleSearch.OracleValueConvertFilter`）はいずれも `'` を `''` に倍化するため、閉じない引用符を生成しない。利用側が独自の `ValueConvertFilter` を実装している場合のみ到達しうる |
| Q1=E ＋ `bolt-plan.md` Bolt 1 | E は `Dao.search` の内部を書き換える。`Dao.search` は U2 の所有だが Bolt 1 の縦切りに含まれる | **矛盾なし。** Bolt 1 の範囲に `Dao.search` と `DBAccessManager.executeQuery(PreparedSql)` の両方が含まれており、同一 Bolt 内で整合が取れる |

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
| Q1 | **E** — `DBAccessManager` に `<R> R executeQuery(PreparedSql, ResultSetHandler<R>)` を追加し、`ResultSet` をメソッド外に出さない | 2.6 の M-5 / M-4 が宣言していた `ResultSet executeQuery(PreparedSql)` は追加しない。`org.tamacat.sql.ResultSetHandler` を新設。`executeUpdate(PreparedSql)` は try-with-resources。U2 に `Dao.handleException` funnel の扱いを引き継ぐ |
| Q2 | **A** — `setNull` の SQL 型を DataType ごとに割り当てる | STRING/BOOLEAN→VARCHAR、NUMERIC/FLOAT→NUMERIC、DATE→DATE、TIME→TIMESTAMP、OBJECT→BLOB。実 DB での確認は FR-8.3（Derby）が最初の機会 |
| Q3 | **A** — `MockDriver` に static 自己参照と `getInstance()` を追加 | `MockConnection` に `getPreparedStatements()` / `getLastPreparedStatement()` / `clearPreparedStatements()` を追加。FR-8.1 のアサートが `PreparedStatementBinder` の適用結果に対して直接書ける |
| Q4 | **B** — 走査が判定不能なら `DaoException` で拒否 | 推奨（A）と異なる選択。安全側に倒す。到達経路と回帰リスクは上の分析表のとおり |

[Answer]: Looks correct — 2026-08-09、**Mode:** guided

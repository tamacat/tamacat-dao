# Code Summary — U1 `bind-foundation`

## Result

Both hard gates passed. `mvn -q compile` succeeds; full `mvn -o test` runs **231/231 green** (0 failures, 0 errors — 230 at first pass, +1 from the iteration-1 review fix `testQuoteIsNotDoubledOnBindPath`). `SQLParserTest` is unmodified and 10/10 green. No new compile dependency was added (`pom.xml` diff is pre-existing/unrelated — version bump `1.6.1`→`2.0` and a test-scope `slf4j-api` patch bump, both already present before this session started). Java 8 only throughout.

## Files created

`org.tamacat.dao` (public API surface, per ADR-001):
- `src/main/java/org/tamacat/dao/BindValue.java` — C-3. Three-form value carrier (`of`/`ofStream`/`ofNull`).
- `src/main/java/org/tamacat/dao/Param.java` — C-1. `?`-bearing predicate fragment, count-checked at construction, `and`/`or` concatenation.
- `src/main/java/org/tamacat/dao/PreparedSql.java` — C-2. Executable SQL + values; `of` (checked) / `ofLiteral` (unchecked, lazy count); `getBindIndexOf`; `hasUnboundPlaceholders`.
- `src/main/java/org/tamacat/dao/PlaceholderScanner.java` — package-private shared quote-aware `?` scanner used by both `Param.of` and `PreparedSql`.

`org.tamacat.sql`:
- `src/main/java/org/tamacat/sql/ValueRules.java` — C-4. Value classification/validation/LIKE-escape rules extracted verbatim from `SQLParser.java:39-150`.
- `src/main/java/org/tamacat/sql/LikeEscape.java` — `escapeLike`'s return type.
- `src/main/java/org/tamacat/sql/ExecutedStatement.java` — C-7. Execution record (SQL + values).
- `src/main/java/org/tamacat/sql/ResultSetHandler.java` — `@FunctionalInterface`, Q1=E callback type.
- `src/main/java/org/tamacat/sql/BindSqlBuilder.java` — C-5. `value`/`placeholder`/`sqlFunction`, mirrors `SQLParser.value(...)` structure with `?` tokens instead of literals.
- `src/main/java/org/tamacat/sql/PreparedStatementBinder.java` — C-6 (package-private). `bind(PreparedStatement, List<BindValue>)`, `sqlTypeOf(DataType)`.

Tests (JUnit 4, mirroring main package layout):
- `src/test/java/org/tamacat/dao/BindValueTest.java` (8)
- `src/test/java/org/tamacat/dao/ParamTest.java` (9)
- `src/test/java/org/tamacat/dao/PreparedSqlTest.java` (7)
- `src/test/java/org/tamacat/sql/ExecutedStatementTest.java` (4)
- `src/test/java/org/tamacat/sql/LikeEscapeTest.java` (4)
- `src/test/java/org/tamacat/sql/ValueRulesTest.java` (15)
- `src/test/java/org/tamacat/sql/BindSqlBuilderTest.java` (19, incl. the 3 preserved-defect regression tests and the iteration-1-review AC-6/BR-18 test)
- `src/test/java/org/tamacat/mock/sql/MockPreparedStatementTest.java` (7)
- `src/test/java/org/tamacat/mock/sql/MockConnectionTest.java` (5)
- `src/test/java/org/tamacat/sql/PreparedStatementBinderTest.java` (7)
- `src/test/java/org/tamacat/sql/DBAccessManagerTest.java` (9, extends the pre-existing 1-test file; includes 1 integration stub using `ResultSetHandler`)

Total new tests: 94 (plan called for "5-8 per component"; several components landed above that because the design's decision tables — e.g. ValueRules §2.7's branches, the scan-table in §5.2, BindSqlBuilder's 3 preserved defects — each warranted its own assertion to stay traceable to a specific BR-n).

## Files modified

- `src/main/java/org/tamacat/sql/SQLParser.java` — delegated `isNumeric`, the NUMERIC/FLOAT validation branch, the empty/NULL judgment, the `current_timestamp` judgment, `parseLikeStringValue`, and the required-column check to `ValueRules`/`LikeEscape`. Removed now-unused `Matcher`/`Pattern`/`StringUtils` imports. **`SQLParserTest` required zero changes** — Gate 1 confirmed green (10/10).
- `src/main/java/org/tamacat/sql/DBAccessManager.java` — added `executedStatements` `ThreadLocal`, `getExecutedStatements()`, `executeQuery(PreparedSql, ResultSetHandler<R>)`, `executeUpdate(PreparedSql)`, private `checkBindable`/`record`; added `dba.executedStatements.remove()` to `shutdown()`. All pre-existing public methods unchanged in signature and behavior.
- `src/main/java/org/tamacat/mock/sql/MockConnection.java` — `prepareStatement(String)` now retains `sql` and records the created `MockPreparedStatement`; added `getPreparedStatements()`/`getLastPreparedStatement()`/`clearPreparedStatements()`.
- `src/main/java/org/tamacat/mock/sql/MockPreparedStatement.java` — added `(Connection, String)` constructor (1-arg form retained), `getPreparedSql()`, `getBoundValue(int)`, `getBoundValues()`; every `setXxx(int, ...)` now records position→value (later set wins). `executeUpdate()` still returns `0` unconditionally.
- `src/main/java/org/tamacat/mock/sql/MockDriver.java` — added static `getInstance()` returning the instance the static initializer registered with `DriverManager`.

## Key implementation decisions (judgment calls beyond the literal design text)

1. **`countPlaceholders` placement.** The design's pseudocode shows it as "a private static helper," referenced identically from both `Param.of` and `PreparedSql`. Rather than duplicating the scanner in two files (risking future divergence — the exact failure mode BR-21 warns against), I extracted it into a package-private `PlaceholderScanner` class shared by both. Both call sites produce byte-identical results by construction, not by copy-paste discipline.
2. **`PreparedSql`'s lazy `placeholderCount` field is not `volatile`.** The design doesn't specify thread-safety for the `ofLiteral` lazy-evaluation path, and NFR-7 explicitly discourages introducing optimization/complexity not called for by requirements. A `PreparedSql` is typically constructed and consumed within a single request/thread; I judged that redundant re-scans from a benign race (worst case: the scan runs twice, both yielding the same deterministic answer) were an acceptable trade against added complexity. Flagging this as an open question for review since it's a plausible latent concern in unusual concurrent-reuse scenarios.
3. **`MockPreparedStatement.setXxx` recording covers every `setXxx(int, ...)` method**, not just the three `PreparedStatementBinder` actually calls (`setNull`, `setString`, `setBinaryStream`). BR-34 says "each `setXxx`" without qualification, and future units (U2/U3/U4) may call other setters directly (e.g. via `BlobUtils` or custom overrides), so recording only the three currently-used ones would silently under-serve FR-8.1 for those paths.
4. **`DBAccessManagerTest`** extends the pre-existing single-test file rather than creating a new one, since the file already existed with a `testGetConnection` test that had to keep passing unmodified — consistent with "no breaking changes to existing tests" even though this file wasn't `SQLParserTest`.
5. **`sqlFunction`'s design note "`function` に `?` を含めてはならない"** is enforced automatically (not specially coded) because it routes through `Param.of`'s existing placeholder-count check — a `?` with zero values throws `InvalidParameterException` for free. I added a regression test (`testSqlFunctionRejectsPlaceholderInFunctionText`) to pin this behavior since it's easy to accidentally break if `sqlFunction` were ever changed to bypass `Param.of`.

## Test coverage summary

| Component | Test file | Count |
|---|---|---|
| BindValue | BindValueTest | 8 |
| Param | ParamTest | 9 |
| PreparedSql | PreparedSqlTest | 7 |
| ExecutedStatement | ExecutedStatementTest | 4 |
| LikeEscape | LikeEscapeTest | 4 |
| ValueRules | ValueRulesTest | 15 |
| BindSqlBuilder | BindSqlBuilderTest | 19 |
| MockPreparedStatement | MockPreparedStatementTest | 7 |
| MockConnection | MockConnectionTest | 5 |
| PreparedStatementBinder | PreparedStatementBinderTest | 7 |
| DBAccessManager (PreparedSql path) | DBAccessManagerTest | 9 (incl. 1 pre-existing + 1 integration stub) |
| **Total new** | | **94** |
| SQLParser (unchanged, Gate 1) | SQLParserTest | 10 (0 modified) |
| **Full suite** | | **231/231 green** |

## Deviations from the plan

None that change scope or behavior. The only departures are the two counting-related judgment calls above (#1 shared-helper class instead of duplicated private statics — a structural choice, not a behavior change since both call sites still get an identical scan; and the test counts exceeding the 5-8 "standard" guidance where the design's own decision tables demanded it for BR-n traceability).

## Gate confirmation

- **Gate 1** (`business-logic-model.md` §9, step 4): `SQLParserTest` executed after the `SQLParser`→`ValueRules` delegation, **10/10 green, zero assertion changes**, before any Step 5+ work began.
- **Gate 2** (§9, step 7 depends on step 6): `PreparedStatementBinder` was implemented and tested only after `MockConnection`/`MockPreparedStatement`/`MockDriver` extensions were complete and passing (`MockPreparedStatementTest`/`MockConnectionTest` green first).

---

## Review

NOT-READY

**Reviewer:** aidlc-architecture-reviewer-agent — iteration 1

### 検証の方法（独立実行を含む）

ビルドは**独立に実行した**。`mvn -o -B test` → `Tests run: 230, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`（4.7s）。`SQLParserTest` は `Tests run: 10, Failures: 0`。Gate 1 の「1 行も変更なし」も `git diff HEAD -- src/test/java/org/tamacat/sql/SQLParserTest.java` が空であることで独立確認した（サマリの自己申告に依存していない）。`git status --short` で既存テストの変更は `DBAccessManagerTest.java` の 1 件のみ（judgment call #4 と一致、追記のみで `testGetConnection` は無変更）。

`SQLParser` の委譲は `git show HEAD:src/main/java/org/tamacat/sql/SQLParser.java` と現行版を突き合わせ、§ 3 の対応表 6 行すべてを手で等価性検証した。走査アルゴリズムは § 5.2 の 6 行を手でトレースした。

### 反証を試みて**破れなかった**主張（＝確認済み）

- **tokenFor の判定順序**（`BindSqlBuilder.java:50-66`）は OBJECT → FUNCTION → `isNullValue` → `isSqlFunction` → `validate` で § 4.1 と完全一致。misordering なし。
- **`SQLParser` の挙動保存**: NUMERIC/FLOAT 分岐（`SQLParser.java:89-95`）は `isNullValue` → `validate` の組で旧 `isEmpty` → `isNumeric` → throw と論理等価。DATE/TIME 分岐（`:96-103`）、`parseLikeStringValue`（`:111-122`、noEscape 側の `boundValue` が旧 `:138` の `replaceHolder` 適用後文字列と一致）、必須チェック（`:41`）、`isNumeric`（`:128-130`）いずれも等価。
- **`PlaceholderScanner`**（`PlaceholderScanner.java:26-42`）は § 5.2 の 6 行すべてで期待どおり。`''` は `i++; continue;` で読み飛ばし引用符を閉じない、未終端は `-1`。`PreparedSqlTest.java:37-45` が 6 行すべてを実際にアサートしている。
- **3 つの保存すべき欠陥**は同じ壊れ方を再現している。BETWEEN 3 値 → `SQLParser.VALUES[i]` で `ArrayIndexOutOfBoundsException`（`BindSqlBuilder.java:108`）、BETWEEN 1 値 → 単一値経路に落ち `#{value2}` 未置換のまま `Param.of` の個数検査を通過、IS_NULL + 値 → `append` 内の `template.replace` が `NullPointerException`（`:69`）。回帰テスト 3 件（`BindSqlBuilderTest.java:168-194`）は例外型と壊れた出力を正しく期待しており、誤って別物を assert してはいない。
- **BR-34 後勝ち**は `TreeMap.put`（`MockPreparedStatement.java:78-80`）で実装され、`setNull` → `setBinaryStream` の上書きを `MockPreparedStatementTest.java:57-63` が `assertSame(in, ...)` で実際に検証している。
- **deviation #5（`sqlFunction` の `?` 拒否）は実在する**。`sqlFunction`（`BindSqlBuilder.java:151-154`）は値ゼロで `Param.of` に入り、`Param.java:55-58` の個数検査が `1 != 0` で `InvalidParameterException` を投げる。テスト（`BindSqlBuilderTest.java:158-165`）も実際に throw を要求している。
- **deviation #2（lazy `placeholderCount` が非 volatile）は無害**と判定した。`int` の書き込みは tear しない、走査は決定的、`getPlaceholderCount()`（`PreparedSql.java:106-111`）はフィールドを 2 回読むが `UNCOUNTED` に戻す書き込みが存在しないため、競合下でも戻り値は常に正しい。最悪ケースは再走査のみ。判断は妥当。
- **パッケージ配置**は `domain-entities.md` の型一覧表どおり（`Param`/`PreparedSql`/`BindValue` → `org.tamacat.dao`、`ExecutedStatement`/`LikeEscape`/`ResultSetHandler` → `org.tamacat.sql`）。`ValueRules`・`PreparedStatementBinder`・`PlaceholderScanner` はいずれも package-private なので、§ 10 の「新規 public メンバ 12 件」の申告は追加クラスがあっても正確なまま。deviation #1 は public 表面を増やしていない。
- **既存 public メソッドの無変更**: `DBAccessManager` の diff は純粋な追加のみ（削除行なし）。`executeQuery(String)` / `executeUpdate(String)` / `preparedStatement(String)` / `getStatement()` / `createStatement()` / `getExecutedQuery()` / `commit` / `rollback` / `release` / `getInstance` / `close` は 1 行も変わっていない。`shutdown()` への `dba.executedStatements.remove();` 追加は BR-30 どおり、`release()` は不変。
- BR-27（記録が検査より前）は `DBAccessManager.java:125-126, 142-143` で実装されている。

### Blocking

**B-1. AC-6 を判定するテストが 1 件も存在しないのに、plan の完了チェックボックスが「判定できる」として `[x]` になっている。**

`code-generation-plan.md:90` は `- [x] AC-6（FR-2.3、LIKE のエスケープ）が BindSqlBuilderTest で判定できる` とチェック済みだが、これは 2 つの点で成立していない。

1. AC-6 は LIKE ではない。`business-rules.md:396` の対応表は **AC-6 = FR-2.2（バインド経路でシングルクォートが `''` に二重化されない）＝ BR-18** と定めている（FR-2.3/LIKE は AC-2 で、判定は U2 完了時）。plan の FR ラベルが誤っている。
2. `BindSqlBuilderTest.java` 全 18 テストのうち、**シングルクォートを含む値を 1 つも通していない**。grep したところ、値にアポストロフィを含むケースは 0 件で、`'` の出現は `escape '$'` の期待文字列とコメントのみ。他のテストファイルにもバインド経路の二重化非適用を assert したものはない。

BR-18 自体は構造的には満たされている（`BindSqlBuilder` は `ValueConvertFilter` フィールドを持たず、`org.tamacat.sql` 配下で `ValueConvertFilter` を参照するのは `SQLParser` だけ）。しかし `business-rules.md:407` が「**U1 が単独で判定できる AC は AC-5 / AC-6 / AC-8 の 3 件**」と定めており、そのうち本取り組みの中心にある**セキュリティ上の受け入れ条件だけが未検証のまま完了扱いになっている**。将来 `BindSqlBuilder` に `ValueConvertFilter` を通す変更が入っても、現在のテストスイートは緑のままである。

修正は 1 テストで足りる。例:

```java
@Test
public void testQuoteIsNotDoubledOnBindPath() { // AC-6 / BR-18
    Param p = builder.value(stringColumn, Condition.EQUAL, "O'Brien");
    assertEquals("test1.name=?", p.getSql());
    assertEquals("O'Brien", p.getValues().get(0).getValue());  // NOT O''Brien
    // 対比: literal 経路は二重化する
    assertEquals("test1.name='O''Brien'",
        new SQLParser().value(stringColumn, Condition.EQUAL, "O'Brien"));
}
```

あわせて `code-generation-plan.md:90` の FR ラベルを FR-2.3 → FR-2.2 に訂正すること。

### Non-blocking

**N-1. `MockDriver.getInstance()` が「登録済みインスタンス」を返す保証がない。** `instance = this;` はコンストラクタ内（`MockDriver.java:33`）にあり、static 初期化子の中ではない。したがって `new MockDriver()` を 1 回でも呼ぶと、static フィールドは **`DriverManager` に登録されていない別インスタンス（別の `MockConnection` を保持）** に差し替わる。Javadoc（`:37-38`）の「the instance registered with `DriverManager` by the static initializer」は現状の実装が保証していない主張である。BR-36 が `getInstance()` を導入した目的（`DriverManager` の登録順に依存しない確実な到達経路）が、`setConnection(Connection)` という公開 setter の存在も相まって静かに崩れうる。現リポジトリ内に `new MockDriver()` の呼び出しはないため実害は今はない。`if (instance == null) { instance = this; }` あるいは static 初期化子側での代入に変えれば閉じる。

**N-2. 設計 § 8.2 が示した「テストからの使い方」そのものを検証したテストがない。** `PreparedStatementBinderTest` は自前で `new MockConnection()` を作っており（`:31-32`）、`MockDriver.getInstance().connect(...)` → `DBAccessManager` 実行 → `getLastPreparedStatement().getBoundValue(1)` という § 8.2 の経路は 1 本も通っていない。`DBAccessManagerTest` は記録（`getExecutedStatements()`）は assert するが、**実際に JDBC に適用された位置と値は一度も assert していない**。BR-25 / 「失敗様式」表 #1 は、値と `?` の順序ずれを検出できるのは FR-8.1 の位置・値アサートだけだと明言している。U1 の範囲では順序を決めるのは U2/U3 だが、`DBAccessManager` 経路が本当に mock に到達することを 1 本の統合テストで固定しておくと、U2 が乗る土台が確かになる（現状は「到達するはず」が未検証）。

**N-3. `ExecutedStatement` が渡されたリストを防御的コピーしていない。** `ExecutedStatement.java:24-28` は `Collections.unmodifiableList(values)` でラップするだけで、呼び出し側が保持する可変リストを後から変更すると記録が変わる。ES-1（生成後に変化しない）が構造的に成立しているのは、`DBAccessManager.record`（`:169`）がたまたま既に不変な `sql.getValues()` を渡しているからにすぎない。`Param.of` / `PreparedSql.of` は `new ArrayList<>(values)` でコピーしており（`Param.java:49-50`、`PreparedSql.java:64-65`）、本型だけ扱いが揃っていない。

**N-4. `parseValue` が overridable な `isNumeric` を呼ばなくなった。** 旧実装は `parseValue` 内で **protected インスタンスメソッド** `isNumeric(value)`（1.4-20180217 以降の API）を呼んでいたため、サブクラスが `isNumeric` を override すると数値検証の挙動を変えられた。現行は `ValueRules.validate(column, value)`（`SQLParser.java:93`）が static に判定するため、override は `parseValue` に効かなくなっている。これは § 3 の対応表が指示したとおりの実装であり設計違反ではないが、`SQLParserTest` では捕まらない**外部サブクラス向けの挙動互換の変化**であり、FR-3.1 / NFR-3 / AC-11 の監査対象として Build and Test（3.6）に申し送るべき事項である（現リポジトリ内に `SQLParser` のサブクラスはない）。

**N-5. 設計自身の検算表に対するカバレッジの欠け。** § 5.2 の 6 行は全件テスト済みだが、(a) BR-13 の検算表 5 行のうち `"%_%_"` → `%$%$_$%$_%` と `""` → `%%` の 2 行が `ValueRulesTest` / `BindSqlBuilderTest` のどちらにもない（3/5 のみ）、(b) BR-10 の型ゲート（NUMERIC / DATE カラムに `LIKE_*` を渡すと汎用分岐に落ちエスケープされない）を固定するテストがない、(c) BR-27 の「記録は検査より前」（弾かれた SQL も `getExecutedQuery()` に残る）が実装されているのに assert されていない。いずれも各 1〜2 行で足せる。

**N-6. 保存欠陥 #2 の回帰テストが弱い。** `BindSqlBuilderTest.java:179-183` は `p.getSql().indexOf("#{value2}") >= 0` しか見ていない。§ 4.3 の 2 行目は「`? and #{value2}` になり、値は 1 個。`Param.of` の個数検査は**通る**」と具体的に予測しているので、完成テキストの `assertEquals` と `assertEquals(1, p.size())` まで書けば、この欠陥の「静かに壊れた SQL を作る」形そのものが固定される。

### 判定

B-1 のみが blocking。実装コード自体に設計との乖離は見つからなかった（tokenFor の順序、走査、保存欠陥 3 件、後勝ち、パッケージ配置、既存 API 無変更、Gate 1 / Gate 2 はいずれも独立に確認済み）。**欠けているのは U1 が単独で判定すべき 3 つの AC のうち AC-6 の検証手段であり、それが完了済みとして申告されている点**である。B-1 を解消すれば READY。

---

### 適用記録（オーケストレータ、iteration 1 是正）

B-1 の是正をレビュアーが提示したとおりに適用した——`BindSqlBuilderTest.java` に `testQuoteIsNotDoubledOnBindPath`（AC-6 / BR-18）を追加し、`code-generation-plan.md` の AC-6 チェックボックスの FR ラベルを `FR-2.3` から正しい `FR-2.2、BR-18` に訂正した。`mvn -o test -Dtest=BindSqlBuilderTest,SQLParserTest` で新規テストを含め全て green であることを確認した。N-1〜N-6 の非ブロッキング所見は Build and Test（3.6）への申し送りとして記録し、本 iteration では未対応。レビュアーへ iteration 2 の再検証を依頼する。

---

READY

## Review — iteration 2（architecture-reviewer, 最終 iteration）

**Reviewer:** aidlc-architecture-reviewer-agent

### B-1 の是正検証（独立に確認）

| 検証項目 | 手段 | 結果 |
|---|---|---|
| テストが実在し、主張どおりのアサートをしている | `BindSqlBuilderTest.java:51-59` を直接読解 | ✅ `testQuoteIsNotDoubledOnBindPath` は `@Before` の `stringColumn` / `builder` / `Condition.EQUAL` を他のテストと同じ形で使用。構文・import とも整合（`SQLParser` は同一パッケージ `org.tamacat.sql` のため追加 import 不要） |
| バインド値が二重化されないことを assert している | 同上 `:55` | ✅ `assertEquals("O'Brien", p.getValues().get(0).getValue())` |
| リテラル経路が依然として二重化することを対比で固定している | 同上 `:57-58` | ✅ `assertEquals("test1.name='O''Brien'", new SQLParser().value(...))`。`SQLParser.value(Column, Conditions, String...)`（`SQLParser.java:35`）は `public String` を返し `Condition` は `Conditions` を実装（`Condition.java:9`）— 呼び出しは型的に成立 |
| ビルドが緑（レビューアーが自分で実行） | `mvn -o -B test` | ✅ **Tests run: 231, Failures: 0, Errors: 0** / BUILD SUCCESS。iteration 1 の 230 に対し **+1**（追加は 1 本のみ）。`BindSqlBuilderTest` 単独で 19/19（18 → 19） |
| 回帰検出力（バグを入れたら落ちるか） | `BindSqlBuilder.java:50-66` のコード経路を追跡 | ✅ 想定される回帰は「`tokenFor` / `BindValue.of` へ `SQLParser` 側のクォートエスケープを持ち込む」こと。その場合バインド値は `O''Brien` となり `:55` の `assertEquals` が落ちる。**assert が空振りしない**ことを確認 |
| BR-18 の規則本文と一致するか | `business-rules.md:216-222` | ✅ BR-18 は「`BindSqlBuilder` は `ValueConvertFilter` を保持せず呼ばない」「これが **AC-6** を満たす規則」と明記。実装側も `BindSqlBuilder.java` に `ValueConvertFilter` のフィールド・参照は存在せず、**構造的規則と振る舞いテストの両方**が揃った |
| プランのラベル訂正が対応表と一致するか | `business-rules.md:396` / `code-generation-plan.md:90` | ✅ 対応表 396 行は「規則 = BR-18 / 満たす要件 = FR-2.2（バインド経路でクォートエスケープを適用しない）/ 判定する AC = **AC-6**」。訂正後のプラン行はこの引用と字句レベルで一致。iteration 1 で指摘した誤ラベル `FR-2.3 / LIKE` は解消 |
| 編集が隔離されているか（他を壊していないか） | ファイル全読 + `git status` | ✅ `BindSqlBuilderTest.java` の既存 18 テストは順序・内容とも不変（`testSingleValueBindsPlaceholder` 直後への挿入のみ）。`src/` の変更集合も iteration 1 から不変（modified 6 / untracked 19、`BindSqlBuilder.java` 本体は無変更） |

**B-1 は closed。** U1 が単独で判定すべき 3 つの AC（AC-5 / AC-6 / AC-8）すべてに、実行される判定手段が対応した。

### 新規の非ブロッキング所見

**N-7. `code-generation-plan.md:93` のテスト総数が陳腐化した。** 「`mvn -o test` = 230/230 green」とあるが、B-1 の是正で 1 本増えて実測は **231/231** である。同じ行の「新規 compile 依存 0 件、Java 8 のみ」は引き続き正しい。次に同ファイルへ触れるとき（Build and Test 3.6）に `231/231` へ更新すれば足りる。プランの結論を変えるものではないため非ブロッキング。

### 判定

**READY。** iteration 1 の唯一の blocking（B-1）は、レビューアーが提示した是正がそのまま適用され、テストの実在・構文・アサート内容・回帰検出力・引用の正しさ・ビルド緑をすべて独立に再確認した。iteration 1 で clean と判定した項目（`SQLParser` の委譲、`tokenFor` の判定木、プレースホルダ走査の 6 行、保存欠陥 3 件、BR-34 の後勝ち、逸脱申告、パッケージ配置、既存 `DBAccessManager` メソッドのバイト同一性）は、この隔離されたテスト追加によって一切影響を受けていない。

N-1〜N-6（および新規 N-7）は Build and Test（3.6）への申し送りとする。**この繰り越しは妥当**と判断する — いずれも生成コードの正しさではなくテスト網羅・javadoc の正確さ・防御的コピーに関する所見であり、U1 のコード生成の受け入れを妨げる根拠にはならない。ただし **N-2（`DBAccessManager` 経路が実際に mock のバインド位置・値へ到達することを 1 本も統合テストで固定していない）は 3.6 で最優先**とすべきである。「失敗様式」表 #1（個数が合ったまま値が全てずれる）を捕まえる唯一の手段が FR-8.1 の位置・値アサートであり、そこが未検証のまま U2 がこの土台の上に乗るためである。

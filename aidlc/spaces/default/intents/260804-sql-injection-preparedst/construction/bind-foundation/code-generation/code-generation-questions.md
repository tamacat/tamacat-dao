# Code Generation Plan — U1 `bind-foundation`

Test Strategy: **Standard**（コンポーネントあたり 5〜8 テスト + 主要境界の統合テストスタブ）。すべての新規クラスは `src/main/java/`、テストは `src/test/java/`（JUnit 4、`org.tamacat.mock.sql` のモックスタック、既存 `*Test.java` 命名規約）に置く。Java 8 のみ、新規 compile 依存なし（`tech-stack-decisions.md` U1 TSD-1/TSD-2）。

`business-logic-model.md` § 9「実装順序」の 8 段をそのままステップ順に採用する（設計自身が「4 段目が本 Unit の要」「6 を 7 より先に置く」という依存順序を明示しているため、独自の並べ替えをしない）。

## Story ↔ 実装ステップ対応表

| ステップ | FR / AC | 由来 |
|---|---|---|
| 1, 2 | FR-1.5, FR-3.2 | `domain-entities.md` C-1〜C-3, C-7、`LikeEscape`、`ResultSetHandler<R>` |
| 3, 4 | FR-2.1〜FR-2.4 | `business-logic-model.md` § 2, § 3 |
| 5 | FR-2.3 | § 4 |
| 6 | FR-8.1 | § 8 |
| 7, 8 | FR-4.1, FR-4.2, AC-8 | § 6, § 7 |

## Steps

- [ ] **Step 1: `BindValue`（C-3）の実装**
  - `src/main/java/org/tamacat/dao/BindValue.java` — public final class、不変（`InputStream` を除く）
  - ファクトリ: `of(DataType, String)` / `ofStream(InputStream)` / `ofNull(DataType)`
  - `domain-entities.md` C-3 の不変条件（BV-1〜BV-5）をコンストラクタ／ファクトリで強制する
  - Test: `BindValueTest.java`（3 形のファクトリ、`isNull()`、OBJECT カラムの扱い — 6〜8 テスト）

- [ ] **Step 2: `Param` / `PreparedSql`（C-1, C-2）とプレースホルダ計数**
  - `src/main/java/org/tamacat/dao/Param.java` — `of(String, List<BindValue>)`、`and`/`or`、`size()`
  - `src/main/java/org/tamacat/dao/PreparedSql.java` — `of` / `ofLiteral`、`getPlaceholderCount()`、`getBindIndexOf(DataType, int)`、`hasUnboundPlaceholders()`
  - `countPlaceholders(String)`（`business-logic-model.md` § 5.1 の引用符スキャナ）を private static ヘルパとして実装
  - `Param.of` / `PreparedSql.of` の個数検査（BR-21/BR-23/BR-24 相当）
  - Test: `ParamTest.java`、`PreparedSqlTest.java`（§ 5.2 の検算表 6 件を単体テスト化、`getBindIndexOf` の occurrence 検索、`hasUnboundPlaceholders` の `ofLiteral` 経路 — 各 6〜8 テスト）

- [ ] **Step 2b: `ExecutedStatement`（C-7）、`LikeEscape`、`ResultSetHandler<R>`**
  - `src/main/java/org/tamacat/sql/ExecutedStatement.java`
  - `src/main/java/org/tamacat/sql/LikeEscape.java` — `of(String, char)` / `noEscape(String)`、`getBoundValue()`/`getEscapeChar()`/`hasEscape()`
  - `src/main/java/org/tamacat/sql/ResultSetHandler.java` — public interface、`handle(ResultSet) throws SQLException`
  - Test: `ExecutedStatementTest.java`、`LikeEscapeTest.java`（各 4〜5 テスト。`ResultSetHandler` はインターフェースのため単体テストなし、Step 7/8 の統合テストで検証）

- [ ] **Step 3: `ValueRules`（C-4）の実装**
  - `src/main/java/org/tamacat/sql/ValueRules.java`（package-private でよければそのまま、`component-methods.md` C-4 のシグネチャに従う）
  - `isNumeric` / `validate` / `isRequiredButEmpty` / `isNullValue` / `isSqlFunction` / `escapeLike`
  - **`SQLParser.java:39-150` から 1 文字も変えず移す**（BR-31 の唯一の成功条件）
  - Test: `ValueRulesTest.java`（§ 2.1〜§ 2.6 の各メソッド、§ 2.7 の決定木の全分岐 — 8 テスト）

- [ ] **Step 4: `SQLParser`（M-6）の委譲化**
  - `src/main/java/org/tamacat/sql/SQLParser.java` を編集: `isNumeric` / NUMERIC・FLOAT 検証 / 空値・NULL 判定 / `current_timestamp` 判定 / `parseLikeStringValue` / 必須チェックを `ValueRules` への委譲に置き換える（`business-logic-model.md` § 3 の対応表どおり）
  - シグネチャ・戻り値・`ValueConvertFilter` の保持位置・`parseMultiValue` は変更しない（BR-32）
  - **完了条件（ゲート）**: `SQLParserTest` を実行し、既存アサートが 1 行も変更なしで全て緑であることを確認する。落ちたら Step 3/4 に戻って修正する — 以降のステップはこの緑を前提にする

- [ ] **Step 5: `BindSqlBuilder`（C-5）の実装**
  - `src/main/java/org/tamacat/sql/BindSqlBuilder.java`
  - private `Token` 内部クラス、`tokenFor(Column, String)`（§ 4.1 の決定木）
  - `value(Column, Conditions, String...)`（§ 4.2）、`placeholder(Column, String)`（§ 4.4）、`sqlFunction(Column, String)`（§ 4.5）
  - § 4.3 の 3 つの既知の欠陥（BETWEEN 3値以上、BETWEEN 1値、IS_NULL/NOT_NULL＋値）は**そのまま維持**（現行と同じ例外/壊れた SQL を再現する）
  - Test: `BindSqlBuilderTest.java`（§ 2.7 決定木の全分岐 × `value`/`placeholder`/`sqlFunction`、LIKE のエスケープ経路、IN/BETWEEN の複数値、既知の欠陥 3 件の回帰確認 — 8 テスト）

- [ ] **Step 6: mock スタック拡張（C-8）**
  - `MockConnection.prepareStatement(String)` を編集: `sql` を保持して `MockPreparedStatement` に渡す
  - `MockConnection` に `getPreparedStatements()` / `getLastPreparedStatement()` / `clearPreparedStatements()` を追加
  - `MockPreparedStatement` の各 `setXxx(int, ...)` で位置ごとに値を記録（同位置は後勝ち）。`getPreparedSql()` / `getBoundValue(int)` / `getBoundValues()` を追加。`executeUpdate()` の `0` を返す既存挙動は変更しない
  - `MockPreparedStatement(Connection, String)` コンストラクタを追加（既存の 1 引数形は残す）
  - `MockDriver` に登録済みインスタンスを static 保持する `getInstance()` を追加
  - Test: `MockPreparedStatementTest.java`（位置ごとの記録、後勝ち規則、`executeUpdate()` 不変 — 5〜6 テスト）、`MockConnectionTest.java`（新規 3 アクセサ — 4 テスト）

- [ ] **Step 7: `PreparedStatementBinder`（C-6）の実装**
  - `src/main/java/org/tamacat/sql/PreparedStatementBinder.java`（package-private、static `bind(PreparedStatement, List<BindValue>)`）
  - `sqlTypeOf(DataType)` ヘルパ（§ 6 の switch）
  - **Step 6 完了後に実装する**（mock の記録機能がないと正しさを確認できない、設計の明示的な依存順序）
  - Test: `PreparedStatementBinderTest.java`（Step 6 の mock 拡張を使い、1 始まり位置、`setNull`/`setBinaryStream`/`setString` の型別振り分け、`SQLException` → `DaoException` の変換 — 6〜8 テスト）

- [ ] **Step 8: `ExecutedStatement` 記録と `DBAccessManager`（M-5）の拡張**
  - `DBAccessManager` に `executedStatements`（`ThreadLocal<List<ExecutedStatement>>`）、`getExecutedStatements()` を追加
  - `executeQuery(PreparedSql, ResultSetHandler<R>)` と `executeUpdate(PreparedSql)` を追加（§ 7.1 のコード、try-with-resources、record → checkBindable → prepareStatement → bind → 実行）
  - `checkBindable(PreparedSql)`（§ 7.2、走査不能／個数不一致の 2 分岐、BLOB 向けの案内文言を含む）
  - `shutdown()` に `dba.executedStatements.remove();` を追加。`release()` は変更しない
  - 既存の `executeQuery(String)` / `executeUpdate(String)` / `preparedStatement(String)` / `getStatement()` / `createStatement()` / `getExecutedQuery()` / `commit`/`rollback`/`release`/`getInstance`/`close` は変更しない
  - Test: `DBAccessManagerTest.java`（AC-8 — `getExecutedStatements()` の記録内容、実行前検査の 2 分岐、try-with-resources の資源解放、既存 `executeQuery(String)` 等の無変更確認 — 8 テスト）。**統合テストスタブ**として `ResultSetHandler` を使った `executeQuery` の呼び出し例を 1 本追加する

- [ ] **Step 9: テスト設定の確認**
  - `pom.xml` の変更なし（surefire・JUnit 4・EasyMock 宣言のみ、依存追加なし — TSD-1/TSD-4）
  - 全 U1 テストが `**/*Test.java` の既存 surefire include に一致することを確認

- [ ] **Step 10: ドキュメント**
  - 新規 public メンバ（§ 10 A-1〜A-12、C-1）に Javadoc を付す。とくに `PreparedSql.ofLiteral`（旧経路の互換シム）、`sqlFunction(Column, String)`（`function` に `?` を含めてはならない制約、§ 4.5）に注記を入れる
  - `MIGRATION.md` はまだ作らない（U2 が新設する。U1 単体では新旧 API の並存のみで移行ガイドは不要）

## 完了の確認（`business-logic-model.md` § 9 と対応）

- [ ] `SQLParserTest` が 1 行も変更なしで緑（Step 4 のゲート）
- [ ] AC-5（FR-2.1、NUMERIC/FLOAT の検証）が `ValueRulesTest` で判定できる
- [ ] AC-6（FR-2.3、LIKE のエスケープ）が `BindSqlBuilderTest` で判定できる
- [ ] AC-8（FR-4.1/4.2、実行記録）が `DBAccessManagerTest` で判定できる
- [ ] FR-8.1（mock からバインド位置・値を取得できる）が `MockPreparedStatementTest` で判定できる
- [ ] 新規 compile 依存 0 件、Java 8 のみ（`mvn -q compile` 成功）

---

## Plan Approval

- [ ] Approve Plan — proceed to code generation
- [ ] Request Changes — revise the plan

[Answer]: Approve Plan — 2026-08-10

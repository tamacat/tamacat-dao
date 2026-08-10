# Code Generation Plan — U4 `dialects`

Test Strategy: **Standard**（コンポーネントあたり 5〜8 テスト）。U4 は新規クラスを 0 個作り、既存の方言クラス（`MySQLDao`/`OracleDao`/`OracleSearch`）だけを編集する brownfield 変更。**新規 public/protected メンバはゼロ**（`compose`/`wrapRownum` は private static）。Java 8 のみ、新規 compile 依存なし。U1/U2 の成果物（`PreparedSql`/`Dao.executeQuery(PreparedSql,ResultSetHandler)`）に依存する。

`business-logic-model.md` § 7「実装順序」の 4 段をそのまま採用する（段 3 は段 1・2 が安定してから、が設計自身の理由）。

## Story ↔ 実装ステップ対応表

| ステップ | FR / AC | 由来 |
|---|---|---|
| 1 | FR-6.4, AC-10 | § 5（`OracleValueConvertFilter`） |
| 2 | FR-5.1, FR-6.3, AC-9(MySQL側) | § 1〜§ 3（MySQL） |
| 3 | FR-6.1, AC-9 | § 1, § 2, § 4（Oracle） |
| 4 | FR-8.2 | 移行（BR-18） |

## Steps

- [x] **Step 1: `OracleValueConvertFilter` の null ガード（FR-6.4）**
  - `src/main/java/org/tamacat/dao/OracleSearch.java` を編集: `OracleValueConvertFilter.convertValue(String)` に null チェックを追加（§ 5 のコードどおり。`Search.DefaultValueConvertFilter` / `MySQLSearch.MySQLValueConvertFilter` と同形）
  - 他の変更なし（クラス階層・コンストラクタ・パッケージいずれも不変）
  - Test: `OracleSearchTest.java` に null ケースを追加（AC-10 — 汎用実装・MySQL 実装と同じ結果になること） — 3〜4 テスト（純増）

- [x] **Step 2: `MySQLDao.searchList` の新経路化（FR-5.1 / FR-6.3）**
  - `src/main/java/org/tamacat/dao/MySQLDao.java` を編集
  - private static `compose(PreparedSql base, String sql)`（§ 2.2）を追加 — `hasUnboundPlaceholders()` で分岐し、由来（checked/unchecked）を保つ
  - `searchList(Query<T>,int,int)` を § 3.2 のアルゴリズムに書き換える: `Dao.executeQuery(PreparedSql, ResultSetHandler)` を経由（現在は `dbm.createStatement()` で生の `Statement` を使っている）。LIMIT は `" limit " + (start-1) + "," + max` の **int リテラル連結のまま維持**（バインドしない — MySQL の LIMIT パラメータは整数必須のため。§ 8 D-1、FR-6.3 は「int 型経由で SQL 構文注入不可」という手段で充足し、文面どおりの「連結の解消」ではない旨を code-summary.md に明記すること）
  - `SQL_CALC_FOUND_ROWS` の `replaceFirst` は現行のまま変更しない
  - `FOUND_ROWS()` を 2 本目の `executeQuery` として実行（値ゼロの `PreparedSql`）
  - **完了条件（ゲート）**: `MySQLDaoTest` を実行し、既存アサートが 1 行も変更なしで全て緑であることを確認する（`searchList` を呼ぶ既存テストが現在存在しないため自明に成立するはずだが、必ず実行して確認する）
  - Test: なし（この段では mock アサートは Step 4 の移行で追加する）

- [x] **Step 3: `OracleDao.searchListForOracle` の修正・有効化（FR-6.1）**
  - `src/main/java/org/tamacat/dao/OracleDao.java` を編集
  - private static `compose(PreparedSql, String)` を追加（Step 2 と同じ形。`Dao` に共通化しない — § 2.3、汎用経路の境界を守るため）
  - private static `wrapRownum(String base, int start, int max)`（§ 4.3 のコードどおり）: `for update` を `core` から剥がしてラップし最外側に 1 回だけ付け直す（**現行は 2 か所に現れる欠陥を持っていた**、剥がさず内側に append していたため）。W-1〜W-4 の 4 状態を § 4.2 の表どおりに生成
  - `searchListForOracle(Query<T>,int,int)` を新アルゴリズムに書き換える。行の読み飛ばし（`Dao.searchList` 相当）は**行わない**（rownum が既に読み飛ばしている — 現行のコメントアウトを削除し積極的に不要と記述する）
  - `searchList(Query<T>,int,int)`（`Dao` の override）を追加し `searchListForOracle` に委譲する（現状は `searchListForOracle` がどこからも呼ばれていない死んだコード。§ 8 D-3、`component-methods.md` M-7 が既に規定）
  - **完了条件（ゲート）**: AC-9 — W-1〜W-4 の 4 状態それぞれで生成 SQL を確認し、未バインドの `?` が 0 個であることを確認する
  - Test: `OracleDaoTest.java` を新規作成（現在 `OracleDao` のテストは 1 本もない） — W-1〜W-4 の 4 状態の生成 SQL、`for update` の最外側 1 回付与（付く場合／付かない場合）、`searchList` から `searchListForOracle` への委譲を確認 — 8 テスト

- [x] **Step 4: 移行（Q7 = B、BR-18）**
  - `MySQLDaoTest` にページング系アサートを追加: mock の `getLastPreparedStatement().getPreparedSql()` に `limit 0,5` がリテラルで現れ、バインド値は WHERE 由来のものだけであることを確認
  - FR-8.2 対応表の U4 該当行（`MySQLDaoTest` のページング系アサート、新設 `OracleDaoTest` の rownum 系アサート）

- [x] **Step 5: FR-5.2 / FR-5.3 の不作為確認**
  - PostgreSQL 専用クラスを追加しないことを確認する（何もしない。`components.md`/OOS-4）
  - `Condition` / `MySQLCondition` / `PostgreSQLCondition` を変更しないことを確認する（`BindSqlBuilder.value` が `SQLParser.value` と同じ `Conditions` メソッドを使うため、変更なしで FR-5.3 が成立する — § 6）

- [x] **Step 6: テスト設定・ドキュメントの確認**
  - `pom.xml` の変更なし
  - `MIGRATION.md`（U2 が新設）に U4 の変更点を追記: Oracle のページングが全行スキャンから rownum 方式に変わる観測可能な挙動変更（性能特性が変わる。AC-9 の対象そのもの）を明記

## 完了の確認（`business-logic-model.md` § 7 と対応）

- [x] `OracleSearchTest` に null ケースが追加され緑（Step 1、AC-10）— 1 本 → 4 本（純増 3 本）
- [x] `MySQLDaoTest` が 1 行も変更なしで緑（Step 2 のゲート）— 既存 6 本は無変更のまま緑（`mvn test` で確認）
- [x] AC-9（W-1〜W-4 の 4 状態、未バインド `?` 0 個）が `OracleDaoTest` で判定できる（Step 3 のゲート）— 新設 `OracleDaoTest` 10 本、全緑
- [x] 新規 public/protected メンバ 0 件（`compose`/`wrapRownum` は private static）
- [x] 新規 compile 依存 0 件、Java 8 のみ

## 完了報告

`mvn -o compile` / `mvn -o test` とも成功（`BUILD SUCCESS`、274 tests / 0 failures / 0 errors）。
詳細は `code-generation/code-summary.md` を参照。

---

## Plan Approval

- [ ] Approve Plan — proceed to code generation
- [ ] Request Changes — revise the plan

[Answer]:

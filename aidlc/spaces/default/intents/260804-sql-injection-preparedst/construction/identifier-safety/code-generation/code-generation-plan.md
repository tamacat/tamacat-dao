# Code Generation Plan — U5 `identifier-safety`

Test Strategy: **Standard**（コンポーネントあたり 5〜8 テスト）。U5 は 1 個の新規クラス（`IdentifierRules`）を作り、既存 2 クラス（`Sort`/`QueryImpl`）にそれぞれ 1 行の変更を加える brownfield 変更。**唯一の新規 public 型が `IdentifierRules`**（U1/U2 と同じ理由——クロスパッケージ呼び出しには package-private が届かない）。Java 8 のみ、新規 compile 依存なし。

`business-logic-model.md` § 5「実装順序」の 4 段のうち、段 1（機構決定）は Functional Design で完了済み。段 2〜4 を実装対象とする。

## Story ↔ 実装ステップ対応表

| ステップ | FR / AC | 由来 |
|---|---|---|
| 1 | — | § 1（`IdentifierRules` の新設） |
| 2 | FR-1.7(Should), AC-10b(一部) | § 2（`Sort.sort`） |
| 3 | FR-1.7(Should), AC-10b(一部) | § 3（`QueryImpl` SELECT 句） |
| — | — | § 4（`DataType.FUNCTION` は対象外、不作為の確認） |

## Steps

- [x] **Step 1: `IdentifierRules` の新設**
  - `src/main/java/org/tamacat/sql/IdentifierRules.java` を新規作成
  - `public final class IdentifierRules`、private コンストラクタ（インスタンス化不可）
  - `public static String validate(String raw)`（§ 1 のコードどおり）: `raw == null` は**例外を投げず素通しする**（BR-14 — `Column.isFunction()==true` かつ `getFunctionName()==null` という現行の正当な状態を壊さないため）。`'` / `"` / `;` / `--` / `/*` / `*/` のいずれかを含む場合 `InvalidParameterException("Unsafe identifier: [" + raw + "]")` を投げる。単純な `indexOf` の連鎖（正規表現を使わない、BR-1）。戻り値は入力をそのまま返す（IR-2、意味変換なし）
  - Test: `IdentifierRulesTest.java` — null 素通し、5 種の危険な字面それぞれで例外、危険な字面を含まない正当な複合式（`"RAND()"`、`"COUNT(*)"`、`"TO_CHAR(d,'YYYY')"` は `'` を含むため拒否されることの確認も含む）— 7〜8 テスト

- [x] **Step 2: `Sort.sort` への適用**
  - `src/main/java/org/tamacat/dao/Sort.java` を編集
  - `import org.tamacat.sql.IdentifierRules;` を追加
  - `sort(Object, Object)` の非 `Column` 枝（else 節）のみ変更: `k.toString()` を `IdentifierRules.validate(k.toString())` に置き換える（§ 2.2、変更は本体 1 行 + import）
  - `Column` の枝（`isFunction()` の真偽どちらも）は 1 文字も変えない。`o`（`Order`）も検証しない（BR-6、範囲外と明示済み）
  - **完了条件（ゲート）**: `SortTest` を実行し、既存アサートが 1 行も変更なしで全て緑であることを確認する（既存テストは全て `Column` を渡すため自明に成立するはずだが、必ず実行して確認する）
  - Test: `SortTest.java` に追記 — 非 `Column` キーに危険な字面（ID-1〜ID-5）を渡すと `InvalidParameterException`、正当な複合式（危険な字面を含まないもの）は通ることを確認 — 5〜6 テスト

- [x] **Step 3: `QueryImpl` の SELECT 句への適用**
  - `src/main/java/org/tamacat/dao/impl/QueryImpl.java` を編集
  - `import org.tamacat.sql.IdentifierRules;` を追加
  - SELECT 句組み立てループの `isFunction()` 枝のみ変更: `col.getFunctionName()` を `IdentifierRules.validate(col.getFunctionName())` に置き換える（§ 3.2、変更は本体 1 行 + import）
  - `blobIndex++` の計数（`isFunction()` 分岐の外側・前）、`tables.add(col.getTable())`、`getColumnName(col)` の枝はいずれも 1 文字も変えない
  - `getFunctionName()` が `null` の場合（`isFunction()==true` かつ未設定）は `validate` が素通しするため、現行どおり `"null " + col.getColumnName()` が SELECT 句に出力される（BR-14 の回帰、§ 6 引き継ぎ 4）
  - **注意**: `getSelectSQL()`（リテラル版）と `getSelectPreparedSql()`（U2 が追加）はいずれも同じ `buildSelectClause` を経由するため、この 1 行の変更は自動的に両方の経路に効く（§ 3.2「変更は自動的に両方の経路に効く」、§ 6 引き継ぎ 3）
  - **完了条件（ゲート）**: `QueryImplTest` を実行し、既存アサートが 1 行も変更なしで全て緑であることを確認する（既存テストに危険な字面を持つ `functionName` はないため自明に成立するはずだが、必ず実行して確認する）
  - Test: `QueryImplTest.java` に追記 — 危険な字面を含む `functionName` で `getSelectSQL()` / `getSelectPreparedSql()` の両方が `InvalidParameterException` を投げること、`getFunctionName()==null` の `Column`（`Column.FUNCTION` 定義のみ、`setFunctionName` 未呼び出し）で例外にならず現行どおり `"null"` が出力されること（BR-14 の回帰）、正当な `functionName`（`"COUNT"` 等）は通ること — 6〜7 テスト

- [x] **Step 4: `DataType.FUNCTION` は対象外であることの確認（不作為）**
  - コード変更なし。`DataType.FUNCTION` は U1 の `BindSqlBuilder.tokenFor` が既に扱う値の分類であり、`Column.getFunctionName()`（識別子位置）とは別概念であることを code-summary.md に記録する（§ 4）

- [x] **Step 5: テスト設定・ドキュメントの確認**
  - `pom.xml` の変更なし
  - `MIGRATION.md`（U2 が新設）に U5 の変更点を追記: **これは破壊的変更である**（U2/U4 の追記とは異なり非破壊的でない）——正当な非 `Column` キー・`functionName` のうち危険な字面（`'`/`"`/`;`/`--`/`/*`/`*/`）を含むものは、変更後は生成時に `InvalidParameterException` で拒否される。AC-10b が「部分的」にしか判定できないこと（テーブル名・カラム名そのものは対象外、R-5）も明記する

## 完了の確認（`business-logic-model.md` § 5 と対応）

- [x] `SortTest` が 1 行も変更なしで緑（Step 2 のゲート） — 既存 2 テスト（`testAsc`/`testDesc`）は無変更、実行結果 8/8 green（新規 6 件追加）
- [x] `QueryImplTest` が 1 行も変更なしで緑（Step 3 のゲート） — 既存 34 テストは無変更、実行結果 40/40 green（新規 6 件追加）
- [x] `IdentifierRules.validate(null)` が例外を投げず素通しすることを確認するテストがある（BR-14） — `IdentifierRulesTest#testValidate_Null_PassesThrough`
- [x] `getFunctionName()==null` の `Column` で例外にならない回帰テストがある（BR-14、§ 6 引き継ぎ 4） — `QueryImplTest#testGetSelectSQL_FunctionColumnWithNullFunctionName_DoesNotThrow_RegressionBR14`
- [x] `getSelectSQL()` と `getSelectPreparedSql()` の両方が同じ検証を受けることを確認するテストがある（§ 6 引き継ぎ 3） — `QueryImplTest#testGetSelectSQL_FunctionNameWithSingleQuote_Throws` / `testGetSelectPreparedSql_FunctionNameWithSingleQuote_Throws`
- [x] 新規 public 型は `IdentifierRules` の 1 件のみ、新規 compile 依存 0 件、Java 8 のみ — 確認済み（`String.indexOf` のみ使用）

---

## Plan Approval

- [ ] Approve Plan — proceed to code generation
- [ ] Request Changes — revise the plan

[Answer]:

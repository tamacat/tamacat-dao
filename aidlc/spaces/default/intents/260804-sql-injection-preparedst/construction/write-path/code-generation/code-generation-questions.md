# Code Generation Plan — U3 `write-path`

Test Strategy: **Standard**（コンポーネントあたり 5〜8 テスト）。U3 は新規クラスを 0 個作り、既存 4 クラス（`QueryImpl`/`Dao`/`DaoAdapter`/`DBAccessManager`）を編集する brownfield 変更。`QueryImpl` の INSERT/UPDATE/DELETE バインド版メソッドは `Query` インターフェース（U2 が宣言済みの `default`）の override。Java 8 のみ、新規 compile 依存なし。U1/U2 の成果物（`PreparedSql`/`BindValue`/`BindSqlBuilder`/`Param`/`DBAccessManager.executeUpdate(PreparedSql)`）に依存する。

`business-logic-model.md` § 9「実装順序」の 8 段をそのまま採用する。§ 4.3 `.replaceFirst` の追加（FR-6.2）はリテラル版への唯一の変更であり最初に片付ける。

## Story ↔ 実装ステップ対応表

| ステップ | FR / AC | 由来 |
|---|---|---|
| 1 | FR-6.2 | § 9 段 3（リテラル版修正） |
| 2 | FR-1.4, AC-3b | § 2, § 3, § 3.1 |
| 3 | — | § 4（DELETE） |
| 4 | FR-3.2, AC-7 | § 7 |
| 5 | FR-1.5, FR-4.1 | § 5, § 5.1, § 5.2（BLOB） |
| 6 | NFR-3 | § 6（`Dao` の既定） |

## Steps

- [ ] **Step 1: FR-6.2 のリテラル版修正**
  - `src/main/java/org/tamacat/dao/impl/QueryImpl.java` を編集: `getUpdateSQL` の `:268` OBJECT 分岐に `.replaceFirst(tableName + ".", "")` を追加し、`:257`/`:262` と同じ形に揃える（BR-12）。**U3 が触れる唯一のリテラル版コード変更**
  - 他のリテラル版メソッド（`getInsertSQL`/`getDeleteSQL`/`getDeleteAllSQL`）は 1 文字も変えない
  - Test: 既存の `QueryImplTest` にテーブル修飾の一貫性を確認する回帰テストを追加 — 2〜3 テスト

- [ ] **Step 2: `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` の実装**
  - `QueryImpl` の `getInsertPreparedSql(T)`（`Query` の override）を § 2 のコードどおり実装: `BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();` をメソッドローカル変数として宣言（BR-15。現行の `SQLParser parser` ローカル変数パターンと同形）、`insertValues`（メソッドローカルの `List<BindValue>`）に値を蓄積、`bindSqlBuilder.placeholder(col, value)` が返すトークンをそのまま使う（`"?"` を固定書きしない、BR-3）、`PreparedSql.of(sql, insertValues)` 組み立て後に `blobIndex = prepared.getBindIndexOf(DataType.OBJECT, 1)`（BR-6、独立走査。手計算のカウンタを使わない）
  - `getUpdatePreparedSql(T)` を § 3 のコードどおり実装: `setValues`（メソッドローカル）と `bindWhereValues`（`buildBindWhere` から収集、Step 2 の私有ヘルパを利用）を**分離**したまま `PreparedSql.of` 直前の 1 箇所でのみ結合（`combined = new ArrayList<>(setValues); combined.addAll(bindWhereValues);`、BR-7、失敗様式 1 を構造的に防ぐ）。主キー述語は 3 引数 `addWhere(String, String, Param)`（U2 が追加）を使う
  - private `buildBindWhere(List<BindValue> collected)`（§ 3.1、`getUpdatePreparedSql`/`getDeletePreparedSql`/`getDeleteAllPreparedSql` で共有。U2 `getSelectPreparedSql()` の WHERE 描画ループと同じパターンだが U2 の成果物には手を入れず U3 が新設する 3 メソッド間でのみ共有）
  - Test: `QueryImplTest.java` に追記 — **AC-3b**（INSERT VALUES / UPDATE SET のカラム並び順どおりのバインド値）を mock の位置・値アサートで判定。自動タイムスタンプ列（SQL 関数値、値ゼロの `Param`）を含む UPDATE のケースを必ず含める（BR-3 の失敗様式 1 是正の回帰） — 8 テスト

- [ ] **Step 3: `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)` の実装**
  - § 4 のコードどおり実装: `buildBindWhere`（Step 2 で新設）を使い、主キー述語は 3 引数 `addWhere` 経由。例外送出（`updateColumns == null` 等）とテーブル名解決ロジックは現行のまま変えない（BR-13）
  - Test: `QueryImplTest.java` に追記 — DELETE の WHERE バインド値、例外ケースの現状維持確認 — 5〜6 テスト

- [ ] **Step 4: `getBlobIndex()` のバインド版契約**
  - `blobIndex` フィールド自体は既存のまま（二重の意味を持つ、Step 2/3 で `getBindIndexOf` の戻り値としてのみ設定）
  - **完了条件（ゲート）**: **AC-7**（OBJECT ＋ STRING 2 列の UPDATE で `getBlobIndex()` がバインド位置を返す）を判定できることを確認する。Build and Test（3.6）が申し送り事項どおり「列の並びから手で導いた絶対位置」との突き合わせで検証する（`getBindIndexOf` との比較では恒真になるため使わない——R-28 の申し送り）
  - Test: Step 2 の INSERT/UPDATE テストに含める（独立ステップではない）

- [ ] **Step 5: BLOB 実行の追加（`DBAccessManager` / `Dao` / `DaoAdapter`）**
  - `src/main/java/org/tamacat/sql/DBAccessManager.java` を編集: `executeUpdate(PreparedSql sql, int blobIndex, InputStream in)`（§ 5 のコードどおり）を追加。`record` → `checkBindable` → `prepareStatement` → `PreparedStatementBinder.bind` → `setBinaryStream(blobIndex, in)`（バインダーが適用した `setNull` を上書き）→ `executeUpdate()`
  - `src/main/java/org/tamacat/dao/Dao.java` を編集: `executeUpdate(PreparedSql sql)`（1 引数、protected）と `executeUpdate(PreparedSql sql, int index, InputStream in)`（3 引数、protected）を追加（§ 5.1 のコードどおり。両方とも `createDaoEvent` → `handleBeforeExecuteUpdate` → dbm 呼び出し → `TransactionStateManager.getInstance().executed()` → `event.setResult` → `handleAfterExecuteUpdate` の完全な手順。BR-16、1 引数版の本体を空にしない）。`getInsertPreparedSql(T)`/`getUpdatePreparedSql(T)`/`getDeletePreparedSql(T)`（protected、既定 `PreparedSql.ofLiteral(getInsertSQL(data))` 等、ADR-004）を追加。`create(T)`/`update(T)`/`delete(T)` を `executeUpdate(getInsertPreparedSql(data))` 等に切り替える（BLOB は自動判定しない、BR-11）
  - `src/main/java/org/tamacat/dao/DaoAdapter.java` を編集: `Dao` を継承しないため独立に同じ 5 メンバ（`executeUpdate(PreparedSql)`、`executeUpdate(PreparedSql,int,InputStream)`、`getInsertPreparedSql(T)`/`getUpdatePreparedSql(T)`/`getDeletePreparedSql(T)`）を追加（BR-17）し、すべて `delegate` に転送する（§ 5.2）。`create`/`update`/`delete` を `delegate.executeUpdate(getInsertPreparedSql(data))` 等に切り替える
  - Test: `DaoTest.java` / `DBAccessManagerTest.java` に追記 — BLOB 実行の統合テスト（`DaoAdapter` を継承し `create`/`update` を override した BLOB サブクラスが `getBindIndexOf(DataType.OBJECT,1)` で位置を得て `executeUpdate(PreparedSql,int,InputStream)` を呼べること。override 例では `QueryImpl` 由来のバインド版 `PreparedSql` を明示的に使うこと——`DaoAdapter.getInsertPreparedSql(T)` の既定 `ofLiteral` のままだと `getBindIndexOf` が `-1` を返す、R-27）、`Dao.executeUpdate(PreparedSql)` の完全な手順（`DaoEvent`/`TransactionStateManager`）の確認 — 8 テスト

- [ ] **Step 6: `UserDao` / `FileDataDao` の `getXxxPreparedSql` 移行（前提条件付き、任意）**
  - **判断が必要**: U2 の R-29 が「既定フォールバック（`ofLiteral`）のもとでは、`UserDao`/`FileDataDao` が `getInsertPreparedSql(T)` 等を override しない限り `create`/`update`/`delete` はリテラル SQL のまま実行される」ことを記録している。3.6 の必須項目として申し送られているが、U3 のスコープに含めるかは判断が要る——含める場合は `UserDao.java`/`FileDataDao.java` を `getInsertSQL` 委譲パターンと同形で `getInsertPreparedSql(data)` 等へ委譲する形に書き換え、`UserDaoTest` の INSERT/UPDATE/DELETE アサートを `?` 入りに更新する。含めない場合は R-29 の申し送りをそのまま Build and Test（3.6）に残す
  - developer agent の判断に委ねる。含めない場合は code-summary.md に理由を明記すること（U2 の select-path での類似判断——`param()`→`prepare()` の一部見送り——と同じ形式で）

- [ ] **Step 7: テスト設定・ドキュメントの確認**
  - `pom.xml` の変更なし
  - 新規 protected メンバ（Pr-1〜Pr-10）に Javadoc を付す。とくに BLOB override の手順（`create`/`update` を override し `getBindIndexOf(DataType.OBJECT,1)` で位置を得て `executeUpdate(PreparedSql,int,InputStream)` を呼ぶ）を明記
  - `MIGRATION.md`（既存）に U3 の BLOB override セクションを追加（U3 nfr-requirements Q2=B、TSD-24 の 4 項目構成: 何が変わったか／BLOB を扱うサブクラスが必要な変更／注意点／呼び出し元 0 件であることの明記）。override 先は `DaoAdapter`（実利用サブクラスはすべて `DaoAdapter` を継承）であることを明記する

## 完了の確認（`business-logic-model.md` § 9 と対応）

- [ ] FR-6.2 のリテラル版修正がテーブル修飾の一貫性を回復する（Step 1）
- [ ] AC-3b（INSERT VALUES / UPDATE SET のバインド値がカラム並び順どおり）が `QueryImplTest` の mock 位置・値アサートで判定できる
- [ ] AC-7（OBJECT + STRING 2 列の UPDATE で `getBlobIndex()` がバインド位置を返す）が判定できる（`getBindIndexOf` との突き合わせではなく絶対位置の期待値と比較する）
- [ ] `Dao.executeUpdate(PreparedSql)`（1 引数）が完全な手順（`DaoEvent`/`ExecuteHandler`/`TransactionStateManager`）を持つ
- [ ] `DaoAdapter` が `Dao` と独立に 5 メンバを持ち、すべて `delegate` に転送する
- [ ] 新規 compile 依存 0 件、Java 8 のみ

---

## Plan Approval

- [ ] Approve Plan — proceed to code generation
- [ ] Request Changes — revise the plan

[Answer]: Approve Plan — 2026-08-10

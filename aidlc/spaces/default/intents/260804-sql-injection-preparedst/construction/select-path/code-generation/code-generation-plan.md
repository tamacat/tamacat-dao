# Code Generation Plan — U2 `select-path`

Test Strategy: **Standard**（コンポーネントあたり 5〜8 テスト + 主要境界の統合テストスタブ）。U2 は新規クラスを 0 個作り、既存 5 クラス（`Query`/`QueryImpl`/`Search`/`Dao`/`DaoAdapter`）を書き換える brownfield 変更である。**全ての編集は既存ファイルへのインプレース変更**（新規クラスの追加ではない）。Java 8 のみ、新規 compile 依存なし。

`business-logic-model.md` § 9「実装順序」の 8 段（1 → 2 → 3 → 4 → 5 → 6 → 6b → 7 → 8）をそのまま採用する。**段 1（`Query` の `default` 宣言を先に一括で行う）と段 2 の直後に `SearchTest` 無変更ゲート、段 4 の直後に `QueryImplTest` 無変更ゲートがある** — これらは設計自身が明示した依存順序であり、飛ばさない。

## Story ↔ 実装ステップ対応表

| ステップ | FR / AC | 由来 |
|---|---|---|
| 1 | FR-3.1, FR-7.3 | § 6（`Query` インターフェース） |
| 2, 3 | FR-1.1〜1.3 | § 2（`Search`） |
| 4, 5 | FR-1.1〜1.3, AC-1, AC-2, AC-3 | § 3, § 4（`QueryImpl` WHERE / SELECT） |
| 6 | FR-1.6, AC-4 | § 5（サブクエリ） |
| 6b | — | § 7（FROM/JOIN） |
| 7 | FR-3.3, NFR-1 | § 8（`Dao`/`DaoAdapter`） |
| 8 | FR-8.2 | 移行（BR-20） |

## Steps

- [x] **Step 1: `Query`（M-1）インターフェースの変更**
  - `src/main/java/org/tamacat/dao/Query.java` を編集
  - `default` メソッド 9 個を追加（§ 6.1 の表どおり）: `getSelectPreparedSql()` / `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)` / `where(Param)` / `and(Param)` / `or(Param)` / `andOuterJoin(Table, Param)`
  - `getSelectSQL()` / `getInsertSQL(T)` / `getUpdateSQL(T)` / `getDeleteSQL(T)` / `getDeleteAllSQL(Table)` / `andOuterJoin(Table, Search)` の 6 個に `@Deprecated` を付ける
  - `andOuterJoin(Table, Param)` の既定は `return this;`（何もしない、委譲先がないため）。他 8 個は対応する旧メソッドへの委譲
  - **完了条件（ゲート）**: このステップだけでコンパイルが通ることを確認する（`QueryImpl` はまだ override していないため、既定実装だけで通るはず）
  - Test: なし（インターフェースの既定実装は Step 4/5/6b の `QueryImpl` override テストで間接検証）

- [x] **Step 2: `Search`（M-3）の 3 状態同期**
  - `src/main/java/org/tamacat/dao/Search.java` を編集
  - フィールド追加: `bindSearch`（`StringBuilder`）、`bindValues`（`List<BindValue>`）、`builder`（`BindSqlBuilder`、`ValueConvertFilter` を受け取らない）
  - private `append(String connector, String literalSql, Param bindParam)`（§ 2.2）— 3 状態を同時更新する唯一の入口
  - `and(Column,Conditions,String...)` / `or(Column,Conditions,String...)` / `and(Search)` / `or(Search)` の 4 メソッドを `append` 経由に書き換える（§ 2.3）。**例外時の原子性**（`parser.value` と `builder.value` を `append` の引数として評価し、どちらかが投げたら 3 状態とも変更しない）を維持する
  - **完了条件（ゲート）**: `SearchTest` を実行し、既存アサートが 1 行も変更なしで全て緑であることを確認する（BR-10）。落ちたら Step 2 に戻って修正する

- [x] **Step 3: `Search.getSearchParam()`（public）の追加**
  - `Search.getSearchParam()` — `Param.of(bindSearch.toString(), bindValues)`
  - `getSearchString()` に `@Deprecated` を付ける（戻り値は不変）
  - Test: `SearchTest.java` に追記（既存アサートは変更せず新規テストのみ追加）— 空の `Search`、1 述語、2 述語（and/or 混在）で `getSearchParam()` が個数検査を通り正しい値を返すこと、`getSearchString()` との述語数一致（BR-1 の構造的保証の確認）— 6〜8 テスト

- [x] **Step 4: `QueryImpl`（M-2）の WHERE 2 状態**
  - `src/main/java/org/tamacat/dao/impl/QueryImpl.java` を編集
  - フィールド追加: `bindFragments`（`List<WhereFragment>`）、private static 内部クラス `WhereFragment`（connector + `Param`、public にしない）
  - private `appendWhere(String connector, String literalSql, Param bindParam)`（§ 3.2）
  - `addWhere` を 3 形にする: 既存の `addWhere(String, String)`（リテラル専用）、`addWhere(String, Param)`（2.6 の宣言、3 引数形への薄いラッパ）、新規 private `addWhere(String, String, Param)`（§ 3.3）
  - `join(Column, Column)` を `appendWhere` 経由に書き換える（値ゼロの `Param`、`useAutoPrimaryKeyUpdate` は設定しない現行の非対称を維持、§ 3.4）
  - `addSearch(String, Search, Sort)` の②を `search.getSearchString()` / `search.getSearchParam()` を渡す形に書き換える。①（DISTINCT 伝播）と③（ORDER BY 付与）は 1 文字も変えない（§ 3.5）
  - **完了条件（ゲート）**: `QueryImplTest` を実行し、既存アサートが 1 行も変更なしで全て緑であることを確認する

- [x] **Step 5: `QueryImpl.getSelectPreparedSql()` の実装**
  - private ヘルパ `buildSelectClause()` / `buildFromClause(boolean bind, List<BindValue> collected)` に既存の SELECT/FROM 句組み立てロジックを切り出し、`getSelectSQL()` と `getSelectPreparedSql()` の両方から呼ぶ（§ 4.1）
  - `getSelectPreparedSql()`（`@Override`）— § 4.2 のコードどおり。値の結合順は **FROM 句の値 ++ 各 WhereFragment の値**（BR-6）
  - `blobIndex` の計数は SELECT 経由の既存の意味（OBJECT カラム数）のまま変えない
  - Test: `QueryImplTest.java` に追記 — **AC-1**（STRING の EQUAL）、**AC-2**（LIKE）、**AC-3**（IN の `(?,?,?)` と値順序）を mock の位置・値アサートで判定。`getSelectSQL()` と `getSelectPreparedSql()` の述語数一致の同期テストも含める — 8 テスト

- [x] **Step 6: サブクエリ（FR-1.6）**
  - `andIn` / `andNotIn` / `andExists` / `andNotExists` を編集: 子の `getSelectPreparedSql()` を呼び、子のテキスト＋値を単一の `WhereFragment` として親の WHERE に差し込む（§ 5）
  - **Step 5 完了後に実装する**（子の `getSelectPreparedSql()` に依存）
  - Test: **AC-4**（子の値が親のバインドパラメータ列の正しい位置に入ること）を mock の位置アサートで判定 — 6 テスト

- [x] **Step 6b: `andOuterJoin` の非推奨化とバインド版追加（Q2 = C）**
  - フィールド追加: `bindOuterJoinTables`（`Map<Table, Param>`）
  - private `putOuterJoin(Table, String literalExpr, Param bindExpr)`（§ 7.2）
  - `outerJoin(Column, Column)` の**両方の分岐**（キー存在／不在）を `putOuterJoin` 経由に書き換える（片方だけ直すと FROM 句から outer join が消える、§ 7.3 の注記）
  - `andOuterJoin(Table, Search)` に `@Deprecated` を付け、バインド面にもリテラルテキストを値ゼロで積む（BR-12。この経路は SM-1 対象外）
  - `andOuterJoin(Table, Param)`（新規、`@Override`）を追加（§ 7.3）
  - FROM 句の値収集は `buildFromClause` の同一ループ内で行う（テキスト組み立てと値収集の順序が構造的にずれないようにする、§ 7.4）
  - Test: `QueryImplTest.java` に追記 — outer join の両分岐、非推奨版のバインド面（値ゼロ、FROM 句に値がリテラルのまま残ること）、新規 `andOuterJoin(Table,Param)` の値結合順 — 6 テスト

- [x] **Step 7: `Dao` / `DaoAdapter`（M-4）の実行経路切り替え**
  - `src/main/java/org/tamacat/dao/Dao.java` を編集
  - フィールド追加: `protected BindSqlBuilder bindSqlBuilder`（既存の `protected SQLParser parser` と対称。iteration 2 レビューで確定、Pr-7）
  - `prepare(Column, Conditions, String...)`（public、`bindSqlBuilder.value(...)` に委譲、§ 8.1）を追加。`param(...)` は無変更で残す
  - `executeQuery(PreparedSql, ResultSetHandler<R>)`（protected）を追加（§ 8.2 のコード。`createDaoEvent` → `handleBeforeExecuteQuery` → `dbm.executeQuery` → `handleAfterExecuteQuery`）
  - `search(Query<T>)` / `searchList(Query<T>,int,int)` をコールバック形に書き換える（ラムダで `ResultSetHandler` を実装。`ResultSet` の close は `DBAccessManager` 側に移る）。`handleException` の funnel が広がることを認識した上で実装する（BR-16、通知を失う方向の変化はない）
  - `src/main/java/org/tamacat/dao/DaoAdapter.java` を編集: `prepare(...)`（public）と `executeQuery(PreparedSql, ResultSetHandler<R>)`（protected）を追加し、両方とも `delegate` に転送する（§ 8.3）。`search`/`searchList`/`searchList(Query)` は既に転送済みのため追加不要
  - Test: `DaoTest.java` / `UserDaoTest.java` 等に統合テストを追加 — `prepare(...)` の呼び出し、コールバック経路での SELECT 実行、例外 funnel の拡大が既存の通知経路を壊さないこと — 6〜8 テスト

- [x] **Step 8: 移行（Q7 = B、BR-20）**（deviation: `UserDao.getUpdateSQL`/`FileDataDao.getUpdateSQL`/`MySQLDaoTest.testGetUpdateSQL` の `param()`→`prepare()` は行わなかった — see code-summary.md）
  - `src/test/java/org/tamacat/dao/test/UserDao.java` / `FileDataDao.java` の `param(...)` 呼び出しを `prepare(...)` に切り替える
  - `src/test/java/org/tamacat/dao/impl/MySQLDaoTest.java` の同様の切り替え
  - `UserDaoTest.testSearchUser` / `testSearchListSearchSort` 等、SELECT 経路のアサート文字列を `?` 入りに書き換える（FR-8.2 対応表）
  - **これらはすべて `src/test` にあり production jar には同梱されない**（テストフィクスチャの移行であり、利用者に見える変更ではない）

- [x] **Step 9: テスト設定・ドキュメントの確認**
  - `pom.xml` の変更なし（新規依存追加なし）
  - 新規 public/protected メンバ（§ 10 P-1〜P-12, Pr-1〜Pr-7）に Javadoc を付す。とくに非推奨 6 メソッドの `@deprecated` タグに代替経路を明記する
  - `MIGRATION.md` を新規作成し、Javadoc と対になる移行ガイドとする（U2 nfr-requirements TSD-9 の 4 節構成: 1. 何が変わったか、2. 旧→新の対応表、3. SM-1 対象外に残る経路、4. 注意点）

## 完了の確認（`business-logic-model.md` § 9 と対応）

- [x] `SearchTest` が 1 行も変更なしで緑（Step 2 のゲート）
- [x] `QueryImplTest` が 1 行も変更なしで緑（Step 4 のゲート）
- [x] AC-1 / AC-2 / AC-3（Step 5）、AC-4（Step 6）が mock の位置・値アサートで判定できる
- [x] `getSelectSQL()` と `getSelectPreparedSql()` の述語数が一致する同期テストがある（BR-1/BR-3 の失敗様式を検出する唯一の手段）
- [x] 既存 public メソッドの戻り値が不変（BR-10。`getSelectSQL()` 等の出力が変更前と一致）
- [x] 新規 compile 依存 0 件、Java 8 のみ

---

## Plan Approval

- [ ] Approve Plan — proceed to code generation
- [ ] Request Changes — revise the plan

[Answer]:

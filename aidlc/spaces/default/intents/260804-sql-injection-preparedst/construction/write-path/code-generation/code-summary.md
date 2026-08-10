# Code Summary — U3 `write-path`

## Files modified

Production code (brownfield edits, 0 new classes):

- `src/main/java/org/tamacat/dao/impl/QueryImpl.java` — FR-6.2 literal fix
  (`getUpdateSQL`'s OBJECT branch); added `getInsertPreparedSql(T)`,
  `getUpdatePreparedSql(T)`, `getDeletePreparedSql(T)`,
  `getDeleteAllPreparedSql(Table)` overrides and the private `buildBindWhere`
  helper.
- `src/main/java/org/tamacat/sql/DBAccessManager.java` — added
  `executeUpdate(PreparedSql, int, InputStream)`.
- `src/main/java/org/tamacat/dao/Dao.java` — added `executeUpdate(PreparedSql)`,
  `executeUpdate(PreparedSql, int, InputStream)`, `getInsertPreparedSql(T)` /
  `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)`; rewrote
  `create`/`update`/`delete` to route through the new `executeUpdate(PreparedSql)`.
- `src/main/java/org/tamacat/dao/DaoAdapter.java` — added the same 5 members
  independently (`DaoAdapter` does not extend `Dao`). Forwarding is
  asymmetric: the 2 `executeUpdate` overloads forward to `delegate`; the 3
  `getXxxPreparedSql` methods wrap `DaoAdapter`'s own `getXxxSQL` (mirroring
  the existing `create()` pattern). Rewrote `create`/`update`/`delete` to
  route through `delegate.executeUpdate(getXxxPreparedSql(data))`.

Test fixtures / tests:

- `src/test/java/org/tamacat/dao/test/UserDao.java` — added
  `getInsertPreparedSql`/`getUpdatePreparedSql`/`getDeletePreparedSql` overrides
  (Step 6 migration).
- `src/test/java/org/tamacat/dao/test/FileDataDao.java` — same (Step 6
  migration).
- `src/test/java/org/tamacat/dao/test/UserDaoTest.java` — `testCreate` /
  `testUpdate` / `testDelete` assertions updated to the `?`-bearing SQL form
  (FR-8.2) and the mock-artifact result count (see Deviations).
- `src/test/java/org/tamacat/dao/impl/QueryImplTest.java` — 15 new tests (FR-6.2
  regression, AC-3b, AC-7, DELETE).
- `src/test/java/org/tamacat/sql/DBAccessManagerTest.java` — 3 new tests
  (BLOB `executeUpdate` overload).
- `src/test/java/org/tamacat/dao/DaoTest.java` — 3 new tests
  (`executeUpdate(PreparedSql)` / `executeUpdate(PreparedSql,int,InputStream)`
  full-procedure, and a `DaoAdapter`-subclass BLOB override integration test via
  a package-private nested `BlobFileDataDao` class).

Documentation:

- `aidlc/.../construction/write-path/code-generation/code-generation-plan.md` —
  all steps and the completion checklist marked `[x]`.
- `MIGRATION.md` — new §7 "U3 `write-path` (this update, final Unit of this
  initiative)" per TSD-24's 4-part structure (what changed / BLOB-subclass
  changes / caveat / zero-callers), plus updates to the intro Unit list, the
  old→new mapping table, and the §3 "paths remaining outside SM-1" INSERT/
  UPDATE/DELETE bullet (now struck through and resolved) — the forward
  references to "pending U3" left by U1/U2/U4/U5 were the ones flagged in the
  handoff instructions.

## Key implementation decisions (BR-n rationale)

- **Value-accumulator separation (AC-3b, WV-1–WV-5).** `insertValues` (INSERT)
  and `setValues` (UPDATE SET) are method-local `List<BindValue>`s, never
  `QueryImpl` fields (BR-1). WHERE-side values are collected by the new private
  `buildBindWhere(List<BindValue>)` helper, which walks `bindFragments` in a
  single pass identical in shape to `getSelectPreparedSql()`'s WHERE-rendering
  loop (BR-4a, BR-9 — U2's reviewed `getSelectPreparedSql()` itself is
  untouched; the loop shape is duplicated only across U3's own 3 new methods).
  In `getUpdatePreparedSql`, `setValues` and `bindWhereValues` are kept
  separate until exactly one combination point right before `PreparedSql.of`
  (`combined = new ArrayList<>(setValues); combined.addAll(bindWhereValues);`,
  BR-7) — this is the structural fix for "failure mode 1" (all values shifting
  by one), confirmed by `testGetUpdatePreparedSql_AC3b_SetAndWhereValueOrder`
  and `testGetUpdatePreparedSql_AC7_ObjectAndAutoTimestamp_BlobIndex`, both of
  which assert bound values at their mock `PreparedStatement` positions.
- **Text tokens are never hardcoded `"?"` (BR-3).** Every INSERT/UPDATE value
  uses `bindSqlBuilder.placeholder(col, value).getSql()` as-is; for
  `current_timestamp` (auto-timestamp columns) this is a bare literal token
  with zero bind values, not a placeholder — verified by
  `testGetInsertPreparedSql_AutoGenerateId_SetsGeneratedIdOnData` (5
  placeholders + 1 literal `current_timestamp` token, 6 columns) and the
  `AC-7` test above (SET clause's auto-timestamp branch).
- **`blobIndex` via independent scan (AC-7, BR-6).** Both
  `getInsertPreparedSql` and `getUpdatePreparedSql` set `blobIndex` by calling
  `prepared.getBindIndexOf(DataType.OBJECT, 1)` *after* the `PreparedSql` is
  built — never a hand-maintained counter. Per `security-requirements.md`
  R-28 (a known, accepted, unresolved non-blocking item from the functional
  design review), the AC-7 test does **not** cross-check `getBlobIndex()`
  against `getBindIndexOf` (that comparison is tautological after this fix) —
  instead it asserts the SQL text/value list directly and then a
  hand-derived absolute position (`assertEquals(2, q.getBlobIndex())`,
  independently derived from the SET clause's 2-entry column order), per
  `security-requirements.md`'s Build-and-Test item #15.
- **`DaoAdapter`'s independent 5-member addition (BR-17).** `DaoAdapter` does
  not extend `Dao` (`DaoAdapter.java:27`, `implements AutoCloseable` only, a
  `delegate: Dao<T>` field). All 5 members (`executeUpdate(PreparedSql)`,
  `executeUpdate(PreparedSql,int,InputStream)`, `getInsertPreparedSql(T)`,
  `getUpdatePreparedSql(T)`, `getDeletePreparedSql(T)`) were added to
  `DaoAdapter` independently of the identically-named `Dao` members, mirroring
  the existing asymmetric pattern where `create()` reads SQL from
  `DaoAdapter`'s own `getInsertSQL` but executes via `delegate`.
  `getInsertPreparedSql`/etc. on `DaoAdapter` likewise wrap `DaoAdapter`'s own
  `getInsertSQL`/etc. (not `delegate`'s), and `executeUpdate(...)` forwards to
  `delegate.executeUpdate(...)`. Verified indirectly by
  `UserDao`/`FileDataDao` (both `DaoAdapter` subclasses) exercising the bind
  path end-to-end through `UserDaoTest`, and directly by
  `DaoTest#testBlobOverride_CreateUsesExecuteUpdatePreparedSqlIntInputStream`'s
  `BlobFileDataDao extends DaoAdapter<FileData>`.
- **`BindSqlBuilder` as a method-local variable (BR-15).** Each of
  `getInsertPreparedSql`/`getUpdatePreparedSql`/`getDeletePreparedSql`
  constructs its own `BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();`
  at the top of the method — mirroring the existing `SQLParser parser = new
  SQLParser(valueConvertFilter);` local-variable pattern in the literal-path
  methods (`getInsertSQL`/`getUpdateSQL`/`getDeleteSQL`). No new `QueryImpl`
  field was added.
- **`Dao.executeUpdate(PreparedSql)` (1-arg) has a real body (BR-16).** Goes
  through the same 4 steps as every other `executeUpdate` variant —
  `createDaoEvent` → `handleBeforeExecuteUpdate` → `dbm.executeUpdate(sql)` →
  `TransactionStateManager.getInstance().executed()` → `event.setResult` →
  `handleAfterExecuteUpdate` — none skipped for the BLOB variant either
  (BR-9a). `Dao#create`/`update`/`delete` now call this method instead of
  `executeUpdate(String)`.
- **BLOB never auto-detected (BR-11, BI-3).** `Dao#create`/`update`'s default
  implementation has no code path that inspects `blobIndex` or branches to the
  3-arg `executeUpdate`; a BLOB-writing subclass must override `create(T)`/
  `update(T)` itself, exactly as the pre-existing (never-executed-by-default)
  `executeUpdate(String,int,InputStream)` already required. Demonstrated by
  `DaoTest.BlobFileDataDao`.
- **FR-6.2's fix (BR-12) applies structurally, not just textually, on the bind
  path.** The literal fix (`.replaceFirst(tableName + ".", "")` added to the
  OBJECT branch) is the one and only literal-path code change (verified by
  `testGetUpdateSQL_ObjectColumn_NoTableQualification` and
  `testGetUpdateSQL_AllSetBranches_ConsistentlyUnqualified`). The bind path
  (`getUpdatePreparedSql`) builds its SET clause directly from
  `col.getColumnName()` (BR-5) — table qualification is never introduced in
  the first place, so there is nothing to strip and no equivalent defect class
  can occur there.

## Test coverage summary

`mvn test`: **316 tests, 0 failures, 0 errors** (baseline before this Unit was
295 — net +21, all additions, satisfying NFR-6). Breakdown of new tests:

| File | New tests | Covers |
|---|---|---|
| `QueryImplTest.java` | 15 | FR-6.2 regression (2), INSERT/UPDATE AC-3b (8), AC-7 (embedded in the UPDATE tests), DELETE/DELETE ALL (5) — 2 + 8 + 5 = 15 (corrected from iteration-1 review B-1, which caught a double-count against the same 8/5 cases) |
| `DBAccessManagerTest.java` | 3 | `executeUpdate(PreparedSql,int,InputStream)` — overwrites `setNull` with the stream (BR-10), records the executed statement, rejects an unbound placeholder (BR-9a parity with the non-BLOB overload) |
| `DaoTest.java` | 3 | `Dao.executeUpdate(PreparedSql)` and `executeUpdate(PreparedSql,int,InputStream)` full-procedure (event/handler/transaction hooks), plus the `DaoAdapter`-subclass BLOB override integration test |
| `UserDaoTest.java` | 0 new, 3 updated | `testCreate`/`testUpdate`/`testDelete` — SQL text updated to `?`-bearing bind form and result count updated (see Deviations) |

15 + 3 + 3 = 21, matching the net-new count above.

**AC-3b is judgeable**: `testGetInsertPreparedSql_AC3b_ValuesInColumnOrder` and
`testGetUpdatePreparedSql_AC3b_SetAndWhereValueOrder` both bind the returned
`PreparedSql` onto a `MockPreparedStatement` and assert each value at its
1-based position, in column order — directly exercising the "failure mode 1"
(all-values-shift-by-one) detection path.

**AC-7 is judgeable**: `testGetUpdatePreparedSql_AC7_ObjectAndAutoTimestamp_BlobIndex`
builds an UPDATE with an OBJECT column and a STRING column (plus an
auto-timestamp column, `FileData`), asserts the full SQL text and value list,
then asserts `getBlobIndex()` against a hand-derived absolute position (2) —
per R-28's guidance, not a self-referential comparison against
`getBindIndexOf`.

## Deviations, with rationale

1. **Step 6 decision: migrated, not deferred.** `UserDao` and `FileDataDao`
   (this repository's `DaoAdapter` subclasses referenced by R-29) were migrated
   to override `getInsertPreparedSql`/`getUpdatePreparedSql`/
   `getDeletePreparedSql`, delegating to `query.getInsertPreparedSql(data)`
   etc. — mirroring the existing `getInsertSQL` delegation pattern. This is
   safe because, unlike U2's select-path exclusion (where sending an unbound
   `?` down a literal-execution path with no placeholder check was a genuine
   correctness regression), the very thing that was missing before — a
   bind-aware `getXxxPreparedSql` on `QueryImpl` — now exists as of this
   session's Steps 2–4, and `Dao`/`DaoAdapter#create/update/delete` (Step 5)
   already route through the checked `executeUpdate(PreparedSql)` path
   (`PreparedSql.of`'s construction-time placeholder-count check, plus
   `checkBindable`) regardless of whether the migration in this step happens.
   `UserStatDao` was **not** migrated — per R-29's own guidance ("`UserStatDao`
   の移行要否は 3.6 が判断する"), its write-path test coverage is thin (no
   dedicated write test exists), so the call is left to a later stage.
   `TestDao` was left untouched (it doesn't override any `getXxxSQL` method,
   so the compatibility-shim default already applies uniformly to both the
   literal and bind builders).
2. **`UserDaoTest`'s `testCreate`/`testUpdate`/`testDelete` result-count
   assertions changed from `1` to `0`.** This is a byproduct of Step 5 alone
   (not Step 6) — once `Dao#create`/`update`/`delete` route through
   `executeUpdate(PreparedSql)`, execution goes through
   `MockConnection#prepareStatement(String)` → `MockPreparedStatement`
   instead of `MockStatement#executeUpdate(String)`, and
   `MockPreparedStatement#executeUpdate()` is **hard-coded to always return
   `0`** — a pre-existing, deliberately-tested U1 mock limitation
   (`MockPreparedStatementTest#testExecuteUpdateAlwaysReturnsZero`, and
   `DBAccessManagerTest#testExecuteUpdateRecordsExecutedStatement`'s own
   `assertEquals(0, result); // ... (BR-35)` comment already documents this
   for the non-DAO-level `executeUpdate(PreparedSql)` path). This is not a
   production-code behavior change — a real JDBC driver's
   `PreparedStatement#executeUpdate()` correctly returns the affected-row
   count; it is purely an artifact of this test-only mock's fixed return
   value. Verified this is unavoidable (not specific to migrating
   `UserDao`'s SQL builders) by confirming the failure appeared even before
   Step 6's migration, immediately after Step 5's `Dao#create`/`update`/
   `delete` switch, while `UserDao` still only had its pre-existing literal
   `getInsertSQL`/etc. overrides (the SQL text stayed literal at that point;
   only the returned count changed, from `1` to `0`).
3. **FR-6.2 regression tests use `FileData` (from `src/test/java/org/tamacat/dao/test/`), not `User`.**
   `User` has no `DataType.OBJECT` column; `FileData` (already an existing
   fixture with `FILE_ID`/`FILE_NAME`/`SIZE`/`CONTENT_TYPE`/`DATA`(OBJECT)/
   `UPDATE_DATE`(auto-timestamp)) was the natural existing fixture for both the
   FR-6.2 OBJECT-branch regression and the AC-7 OBJECT+STRING(+auto-timestamp)
   UPDATE test — no new fixture class was introduced.
4. **`DaoTest.BlobFileDataDao` is a package-private nested test class**, not a
   new top-level class under `src/main`, keeping "zero new [production]
   classes" intact per the Unit's constraint while still giving Step 5's BLOB
   integration test (per the plan and `security-requirements.md`'s
   Build-and-Test item #17) a concrete `DaoAdapter`-extending override to
   exercise, following R-27's guidance that the override must obtain its
   `PreparedSql` from a `QueryImpl` built directly (not from `DaoAdapter`'s
   own default `getInsertPreparedSql`, which would still be
   `PreparedSql.ofLiteral(...)` and make `getBindIndexOf` return `-1`).

## Confirmations

- `mvn -q compile`: succeeds.
- `mvn test`: 316/316 tests pass (0 failures, 0 errors).
- Zero new compile-scope dependencies; Java 8 only (verified: no post-8 syntax
  or APIs used — `try-with-resources` (already Java 7+), diamond operator, and
  `ArrayList`/`List` usage only, consistent with the rest of the codebase).
- No breaking changes to existing public/protected signatures — every new
  member listed in `domain-entities.md`'s Pr-1–Pr-10 / P-1 table is additive.

---

## Review

NOT-READY

**Reviewer:** aidlc-architecture-reviewer-agent（Code Generation 3.5 / U3 `write-path`、iteration 1、2026-08-10）

### 検証の方法

`business-logic-model.md`（適用記録後の本文）、`business-rules.md` BR-1〜BR-17、`domain-entities.md`、`security-requirements.md` SEC-27〜32 / R-24〜R-29、`tech-stack-decisions.md` TSD-24、`code-generation-plan.md` を読んだうえで、生成コードを全文読んで突き合わせた: `QueryImpl.java`、`Dao.java`、`DaoAdapter.java`、`DBAccessManager.java`、`UserDao.java`、`FileDataDao.java`、`UserDaoTest.java`、`QueryImplTest.java`（U3 節 `:570-859`）、`DaoTest.java`、`DBAccessManagerTest.java`、`MIGRATION.md`、および裏取りのため `PlaceholderScanner.java`、`SQLParser.java:95-108`、`MockPreparedStatement.java:88-90`、`MockStatement.java:71-74`。

**ビルドは独立に実行した**（`mvn -B test`、exit 0）。`target/surefire-reports/*.xml` を集計して **tests=316 / failures=0 / errors=0 / skipped=0** を確認——316 件・0 失敗という主張は実測どおりである。

### 設計との突き合わせ（12 点すべて可、コードの欠陥は 0 件）

1. **AC-3b / 値の 1 つずれ（失敗様式 1）— 発生しない。** `QueryImpl.java:455` の `setValues` と `:486` の `bindWhereValues` は別リストで、結合は `:489-490` の 1 箇所のみ。ループ内で `setValues` に触れるのは `:484` だけ、WHERE 値の収集は `buildBindWhere`（`:435`）だけで、早期マージも二重収集もない。テキスト連結順（`:488` SET → `bindWhere`）と結合順が一致する。
2. **AC-7 / `blobIndex`— 独立走査。** `:360` と `:492` のいずれも `PreparedSql` 構築**後**に `prepared.getBindIndexOf(DataType.OBJECT, 1)` の戻り値を代入する。`blobIndex` への他の代入はリテラル版（`:284`、`:308`、`:383`、`:412`）と `buildSelectClause`（`:188`、`:200`）のみで、新経路に手計算カウンタは無い。
3. **AC-7 テストの恒真回避（R-28）— 満たしている。** `QueryImplTest.java:743` は `assertEquals(2, q.getBlobIndex())` と**手で導いた絶対位置**を書き、`getBindIndexOf` との突き合わせをしていない。期待値 2 は SET 句 2 要素（`file_name=?`,`data=?`）から独立に導け、`:733` の SQL テキスト全文アサートが導出根拠を固定している。
4. **BR-3 / `"?"` 直書き — 無い。** 新規 3 メソッドの本文にリテラル `"?"` は 1 件も無い（`QueryImpl.java` 全体の `"?"` は `:411` のリテラル版 OBJECT 分岐のみ＝現行維持）。`current_timestamp` が値ゼロのテキストトークンになることは `QueryImplTest.java:662-666`（6 列で `?` 5 個）と `:733`（`update_date=current_timestamp`）が実証する。
5. **`buildBindWhere` の適用範囲（BR-9）— 境界どおり。** 呼び出しは `:487` / `:558` / `:576` の 3 箇所のみ。`getSelectPreparedSql()`（`:264-277`）は自前のループのままで、U2 の成果物に変更が及んでいない。
6. **`DaoAdapter` の独立 5 メンバ（BR-17）— 正しい。** `DaoAdapter.java:181/190/199` の `getXxxPreparedSql` は**自身の** `getXxxSQL`（`:160-170`）を `ofLiteral` で包み、`:240/251` の `executeUpdate` は `delegate` に転送する。`:204-212` の `create`/`update`/`delete` は `delegate.executeUpdate(getXxxPreparedSql(data))` という現行と同じ非対称形。`Dao` 側（`Dao.java:256-290`）とは別実装であり、取り違えは起きていない。
7. **`Dao.executeUpdate(PreparedSql)` 1 引数版の本体（BR-16）— 実体がある。** `Dao.java:357-364` が `createDaoEvent` → `handleBeforeExecuteUpdate` → `dbm.executeUpdate(sql)` → `TransactionStateManager.getInstance().executed()` → `event.setResult` → `handleAfterExecuteUpdate` の 4 手順をすべて踏む（既存 `:328-335` と行単位で対応）。3 引数版（`:378-385`）も同じ。
8. **BR-11 / BLOB 自動判定 — 無い。** `Dao.java:280-290` と `DaoAdapter.java:203-213` に `blobIndex` / `DataType.OBJECT` / `InputStream` を参照する分岐は 1 つも無い。
9. **Step 6 の安全性の主張 — コードで裏が取れた。** `Dao.create`（`:281`）/ `DaoAdapter.create`（`:204`）はいずれも `executeUpdate(PreparedSql)` → `DBAccessManager.executeUpdate(PreparedSql)`（`:142-151`、`record` + `checkBindable` 付き）に入る。override の有無で経路は変わらず、`PreparedSql.of` の生成時検査と `checkBindable` が常に効く。U2 が移行を見送った先（無検査のリテラル実行）とは条件が異なるという説明は正しい。
10. **結果件数 1→0 の帰属 — 正しい。** `MockPreparedStatement.java:88-90` が `return 0;`、`MockStatement.java:71-74` が `return 1;`。Step 5 で実行が `Statement` から `PreparedStatement` に移った時点で 0 になり、Step 6（SQL テキストの `?` 化）とは独立である。引用されている `MockPreparedStatementTest#testExecuteUpdateAlwaysReturnsZero`（`:66`）と `DBAccessManagerTest#testExecuteUpdateRecordsExecutedStatement`（`:74`）はいずれも実在する。
11. **ビルド — 上記のとおり独立実行・実測。**
12. **`MIGRATION.md`— §7 は TSD-24 の 4 節構成（何が変わったか / BLOB サブクラスの変更 / 注意点 / 呼び出し元 0 件）を満たし、§3 の INSERT/UPDATE/DELETE 行は取り消し線＋「Resolved by U3」に更新済み。**ただし下記 B-2 の誤記がある。

コード側に設計との乖離・欠陥は検出できなかった。以下の 2 件は**いずれも成果物の記述が事実と食い違う点**であり、修正はテキスト 1〜2 行で足りる（コード変更は不要）。

### ブロッキング

**B-1. 「テスト内訳」表の件数が実測と合わず、同じ文書の「+21」と自己矛盾している**

`code-summary.md:138-143` の表は `QueryImplTest.java` **21**、`DBAccessManagerTest.java` **3**、`DaoTest.java` **4** と書き、合計 **28** になる。しかし同 `:135-136` は「316 − 295 = 純増 **21**」と書く。実測は次のとおりで、内訳側が誤っている。

- `QueryImplTest.java` の U3 節（`:571` の FR-6.2 コメント以降）の `@Test` は **15 件**（`:577, 594, 614, 637, 653, 674, 694, 721, 754, 769, 786, 796, 811, 818, 829`）。
- `DaoTest.java` の U3 節（`:134` 以降）は **3 件**（`:142, 155, 189`）。表の「4」は 1 件多い。
- `DBAccessManagerTest.java` の U3 節は **3 件**（`:120, 135, 151`）で表と一致。

15 + 3 + 3 = **21** で純増の実測と一致する。`:140` の内訳文（「FR-6.2 (2)、AC-3b (8)、DELETE (5)、**さらに 6 件の補助ケース**」）も同じ誤りで、その「6 件」は既に 8 + 5 の中に含まれており二重計上である。NFR-6 の判定と 3.6 の受け取りはこの表を ID ではなく件数で参照するため、存在しない 7 件を探す形になる。**是正**: 表を `QueryImplTest.java` = 15 / `DaoTest.java` = 3 に、`:140` の内訳を「FR-6.2 回帰 2 + INSERT/UPDATE 8 + DELETE/DELETE ALL 5 = 15」に直す（二重計上の一文を削る）。

**B-2. `MIGRATION.md:347-350` が `FileDataDao` を「移行していないサブクラスの例」として名指ししているが、同じ Unit で移行済みであり、しかも OBJECT 列を持つ DAO である**

当該段落は「**BLOB-free subclasses need no changes.** ... only overrides `getInsertSQL`/`getUpdateSQL`/`getDeleteSQL`（the existing pattern, **e.g. `FileDataDao`**）keeps generating the exact same literal SQL text - unchanged behavior」と書く。実物は 2 点で食い違う。

- `FileDataDao.java:44-69` は本 Unit で `getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` を override 済みであり、6 行下（`MIGRATION.md:355-356`）が自ら「`UserDao` and `FileDataDao` were migrated this way as part of this Unit」と述べている。同一段落内の自己矛盾である。
- `FileDataDao` は `FileData.DATA`（`DataType.OBJECT`）を含む DAO であり、「BLOB-free subclasses」の例として最も不適切な選択である。`SQLParser.java:104-105` が OBJECT 列に対してリテラル `"?"` を返すため、OBJECT 列を持つ**未移行**サブクラスの `create`/`update` は既定フォールバックのもとで `checkBindable`（`DBAccessManager.java:179-192`）に弾かれて `DaoException` になる——「unchanged behavior」ではない（ADR-012 が意図した拒否であり、それ自体は正しい挙動）。

`MIGRATION.md` は利用者向けの出荷物であり、R-29 / SEC-30 が「呼び出し元 0 件の機能に対する唯一の到達手段」と位置づけた文書である。**是正**: (i) `e.g. FileDataDao` を、移行前の形のまま残る例（`UserStatDao`）に差し替えるか例示自体を削る、(ii) 同じ節に「OBJECT 列を持つ DAO を移行しない場合、既定経路の `create`/`update` は `checkBindable` により `DaoException` で fail-fast する（ADR-012 の意図した拒否）」を 1 行加える。

### 非ブロッキング

- **N-1（`MIGRATION.md` の "this update" が 2 箇所にある）**: `:22` と §7（`:326`）が U3 を "this update" と呼ぶ一方、§3（`:113`）と §6 見出し周辺は U5 を "this update" と呼んだままである。U3 が最後の追記なので、U5 側を "U5（前回の更新）" に直すと読者が混乱しない。
- **N-2（`code-generation-plan.md:61` の完了確認の文言）**: 「`DaoAdapter` が `Dao` と独立に 5 メンバを持ち、**すべて `delegate` に転送する**」は不正確。`delegate` に転送するのは `executeUpdate` 2 件のみで、`getXxxPreparedSql` 3 件は `DaoAdapter` 自身の `getXxxSQL` を包む（`DaoAdapter.java:181-201`）——これは BR-17 / § 5.2 のとおりの**正しい**実装なので、直すべきはチェックリストの文言のほうである。
- **N-3（`QueryImpl.java:442-443` の Javadoc が別メソッドを指す）**: 「The SET clause is built directly with `{@link #getColumnName(Column)}`（unqualified column name）」と書くが、実装（`:483`）が呼ぶのは `col.getColumnName()` であり、`QueryImpl.getColumnName(Column)`（`:856-858`）は `MappingUtils.getColumnName` ＝**テーブル修飾付き**である。Javadoc が名指ししているのは、使えば FR-6.2 の欠陥を再導入するほうのメソッドなので、`col.getColumnName()` に直したい。
- **N-4（`code-generation-plan.md:68-69`）**: 全ステップと完了確認が `[x]` である一方、Plan Approval の 2 つのチェックボックスは未チェックのまま。ゲートの記録として整合させたい。
- **N-5（`code-summary.md:99-102` の「Verified indirectly by `UserDao`/`FileDataDao` ...」）**: `UserDaoTest` は `DaoAdapter` 経路を実際に通すので主張は成立するが、`FileDataDao` の書き込み経路を通すテストはリポジトリに存在しない（`FileDataDao` を参照するテストは `DaoTest`（`FileData` 型のみ）だけ）。`FileDataDao` は「コンパイル・移行済み」であって「テストで実証済み」ではないので、文言を分けると正確になる。

### 再提出時に判定する点

B-1（`code-summary.md:138-143` と `:140` の件数）と B-2（`MIGRATION.md:347-350`）の 2 箇所のみ。**コードの変更は不要**——12 の検証観点すべてで設計との乖離は検出されず、`mvn test` の 316 件・0 失敗もレビュアーが独立に実測して一致した。非ブロッキング N-1〜N-5 は同じ改訂の機会に閉じられる。

---

### 適用記録（オーケストレータ、iteration 1 是正）

両 blocking はドキュメントのみの誤りであり、コード変更は不要という判定どおり、テキスト修正のみを適用した。

| # | 対応した指摘 | 適用内容 |
|---|---|---|
| 1 | B-1（テスト内訳表の自己矛盾） | `code-summary.md` のテスト内訳表を `QueryImplTest.java` = 15 / `DaoTest.java` = 3 に訂正し、二重計上していた「6 件の補助ケース」の一文を削除。15+3+3=21 が純増と一致することを明記 |
| 2 | B-2（`MIGRATION.md` の `FileDataDao` 誤記） | 「BLOB-free subclasses need no changes」の段落を書き直し、`FileDataDao` を「移行していない例」から外した。OBJECT 列を持つ未移行サブクラスが `checkBindable` により fail-fast することを明記し、`FileDataDao` が実際には本 Unit で移行済み（かつ OBJECT 列を持つ）ことを正しく記述 |
| 3 | N-1（`MIGRATION.md` の "this update" が U3/U5 で重複） | U5 の記述を「a prior update」に訂正 |
| 4 | N-2（plan の完了確認の文言） | `DaoAdapter` の 5 メンバの転送先を正確に記述（`executeUpdate` 2 件は `delegate` 転送、`getXxxPreparedSql` 3 件は自身の `getXxxSQL` を包む非対称形） |
| 5 | N-3（`QueryImpl.java` の Javadoc が誤ったメソッドを指す） | `{@link #getColumnName(Column)}`（テーブル修飾付き、FR-6.2 の欠陥を再導入する側）を `{@code col.getColumnName()}`（実装が実際に使う無修飾版）に訂正。`mvn -q compile` で再確認 |
| 6 | N-4（Plan Approval のチェックボックス未チェック） | `code-generation-plan.md` の Approve Plan チェックボックスにチェックを入れた |

**未解消の指摘**: N-5（`FileDataDao` の書き込み経路を直接通すテストが無いこと）は Build and Test（3.6）への申し送りとし、本 Unit では対応しない。

---

## Review（iteration 2、アーキテクチャレビュアー）

NOT-READY

### iteration 1 の blocking 2 件の判定

- **B-1 — 部分的にしか閉じていない（下記 B-3 参照）。** 指摘した表そのもの（`:138-143`）は正しく直った。実測を再カウントし、`QueryImplTest.java` の U3 節の `@Test` は **15 件**（`@Test` 行 `:574, 591, 612, 635, 650, 671, 690, 716, 751, 765, 784, 795, 810, 817, 826`）、`DaoTest.java` の U3 節は **3 件**（`@Test` 行 `:137, 152, 188`）、`DBAccessManagerTest.java` は **3 件**。表の 15 / 3 / 3 と `:145` の「15 + 3 + 3 = 21」は実測どおりで、`:136` の純増 +21 とも一致する。ただし同じ文書の別箇所が未修正のまま残った。
- **B-2 — 閉じた。** `MIGRATION.md:347-364` は `FileDataDao` を「未移行の例」から外し、その位置には `UserStatDao` を置いた上で、`FileDataDao`（`FileData.DATA` という `OBJECT` 列を持つ）が本 Unit で移行済みであることを正しく述べている。新設の fail-fast 注記も実装どおり検証した: `PreparedSql.ofLiteral`（`PreparedSql.java:85-90`）は values を空リスト・`checked=false` で生成し、`hasUnboundPlaceholders()`（`:139-148`）は `count > values.size()` で `true` を返す。したがって `OBJECT` 列のリテラル SQL（`?` 1 個・値 0 件）は `DBAccessManager.checkBindable`（`:179-186`、`executeUpdate` の `:144`/`:169` から呼ばれる）で `DaoException` になる。文書の記述と実装は一致する。

### 非ブロッキング指摘（N-1〜N-4）の確認

- N-1: `MIGRATION.md:120` が「a prior update」、`:22`/`:326` の U3 が「this update」。整合した。
- N-2: `code-generation-plan.md:61` が非対称形（`executeUpdate` 2 件は `delegate` 転送、`getXxxPreparedSql` 3 件は自身の `getXxxSQL` を `ofLiteral` で包む）を正確に記述。
- N-3: `QueryImpl.java:442` が `{@code col.getColumnName()}` に訂正済み。実装（`:483`）が実際に呼ぶメソッドと一致し、テーブル修飾版 `getColumnName(Column)`（`:856`）は SET 句では使われていない。Javadoc のみの変更で、コード意味論に影響なし。
- N-4: `code-generation-plan.md:68` の Approve Plan にチェック、`:69` の Request Changes は未チェック（正しい）。

### ブロッキング

**B-3. `code-summary.md` の「Files modified」節（`:33`, `:37`）が、修正済みの内訳表（`:140`, `:142`）と同一文書内で矛盾している——B-1 と同じ欠陥の残存**

`:33-34` は `QueryImplTest.java` を「**21** new tests」、`:37-40` は `DaoTest.java` を「**4** new tests」と書いたままである。この 2 箇所と `:35` の 3 件を足すと 28 になり、`:136` の純増 21・`:140-145` の 15/3/3=21 と食い違う。実測は上記のとおり 15 / 3 / 3 で、`:33` と `:37` の数字が誤りである。iteration 1 の是正は表だけを直し、同じ数字を持つ上流の一覧を直さなかった。

**適用すべき修正（テキストのみ、コード変更不要）**:

- `code-summary.md:33` の `— 21 new tests` を `— 15 new tests` にする。
- `code-summary.md:37` の `— 4 new tests` を `— 3 new tests` にする。あわせて `:37-40` の列挙が 3 件（`executeUpdate(PreparedSql)` の full-procedure、`executeUpdate(PreparedSql,int,InputStream)` の full-procedure、`BlobFileDataDao` による `DaoAdapter` サブクラス BLOB override 統合テスト）であることを確認する——現行の文面はすでに 3 件を列挙しており、数字だけが 4 で残っている。

### 非ブロッキング

- **N-6（`code-generation-plan.md:43`）**: Step 5 の本文が `DaoAdapter` の 5 メンバを「すべて `delegate` に転送する」と書いており、N-2 で訂正した同ファイル `:61` の非対称形の記述と矛盾する。`:43` は計画時点の意図を記録した行なので実害は小さいが、同一ファイル内で読み手が取り違えうる。`:43` に「（実装では `getXxxPreparedSql` 3 件は自身の `getXxxSQL` を包む非対称形になった——`:61` 参照）」の一文を足すのが最小の整合策。
- N-5（`FileDataDao` の書き込み経路を直接通すテストが無い）は Build and Test（3.6）への申し送りという処置に同意する。本 Unit のコード欠陥ではない。

### 総評

コード側は iteration 1 の 12 検証点に加え、今回の Javadoc 訂正（N-3）を含めて**欠陥ゼロのままである**。B-3 は `code-summary.md` の 2 つの数字のみで、他成果物・実装・テストには波及しない。上記 2 行を直せば本 Unit は完了条件を満たす。

---

### 適用記録（オーケストレータ、iteration 2 上限到達後の直接適用）

`reviewer_max_iterations: 2` に到達したため、レビュアーの再検証を受けずにオーケストレータが以下を適用した。

| # | 対応した指摘 | 適用内容 |
|---|---|---|
| 1 | B-3（`code-summary.md`「Files modified」節の件数が訂正後のテスト内訳表と食い違う） | `QueryImplTest.java` の記述を「21 new tests」→「15 new tests」、`DaoTest.java` の記述を「4 new tests」→「3 new tests」に訂正。テスト内訳表（15+3+3=21）と一致させた |
| 2 | N-6（`code-generation-plan.md` Step 5 が `DaoAdapter` の 5 メンバ全てを `delegate` 転送と記述、N-2 是正後の記述と矛盾） | Step 5 の該当行を「転送先は非対称——`executeUpdate` の 2 件は `delegate` に転送、`getXxxPreparedSql` の 3 件は `DaoAdapter` 自身の `getXxxSQL` を包む」に訂正 |

**未解消の指摘**: N-5（`FileDataDao` の書き込み経路を直接通すテストが無い）は Build and Test（3.6）への申し送りとし、本 Unit では対応しない（コード欠陥ではなくカバレッジの拡張項目）。

これで U3 `write-path` を含む全 5 Unit（bind-foundation / select-path / dialects / identifier-safety / write-path）の Code Generation が完了した。

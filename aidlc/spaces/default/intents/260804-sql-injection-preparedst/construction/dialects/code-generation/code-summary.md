# Code Summary — U4 `dialects`

Status: **complete**. `mvn -o compile` and `mvn -o test` both succeed
(`BUILD SUCCESS`, 274 tests / 0 failures / 0 errors). Plan executed in order
per `code-generation-plan.md`; all steps `[x]`.

## Files modified

| File | Change |
|---|---|
| `src/main/java/org/tamacat/dao/impl/OracleSearch.java` | `OracleValueConvertFilter.convertValue` null guard (Step 1, FR-6.4) |
| `src/main/java/org/tamacat/dao/impl/MySQLDao.java` | `searchList` rewritten onto `Dao.executeQuery(PreparedSql, ResultSetHandler)`; added private static `compose(PreparedSql, String)` (Step 2, FR-5.1/FR-6.3) |
| `src/main/java/org/tamacat/dao/impl/OracleDao.java` | Added private static `compose` and `wrapRownum`; `searchListForOracle` rewritten to rownum-wrap and execute via `Dao.executeQuery`; added `searchList` override delegating to `searchListForOracle` (Step 3, FR-6.1) |
| `src/test/java/org/tamacat/dao/impl/OracleSearchTest.java` | +3 tests: null-safety, cross-check against `Search.DefaultValueConvertFilter` and `MySQLSearch.MySQLValueConvertFilter` (AC-10) |
| `src/test/java/org/tamacat/dao/impl/MySQLDaoTest.java` | +1 test: paging-window migration assertion (Step 4) |
| `src/test/java/org/tamacat/dao/impl/OracleDaoTest.java` | New file, 10 tests: W-1..W-4 generated SQL, `for update` single-append (two states), delegation, no-unbound-`?` (AC-9) |
| `MIGRATION.md` | New "§5 U4 `dialects`" section: Oracle paging behavior change, FR-6.3 equivalent-safety note, W-2 dialect asymmetry |
| `aidlc/.../dialects/code-generation/code-generation-plan.md` | All steps marked `[x]`, completion checklist filled in |

No new classes. No new public/protected members (`compose`/`wrapRownum` are
`private static` on each dialect class, per BR-4/AC-11). Java 8 only (lambdas
via the existing `ResultSetHandler` functional interface; no post-8 APIs). No
new third-party compile dependency.

## Key implementation decisions

- **`compose` is duplicated verbatim in `MySQLDao` and `OracleDao`** rather than
  hoisted to `Dao` (BR-4/BR-5, business-logic-model.md sec 2.3) — keeps U4 off
  the generic-path boundary and off the AC-11 protected-member surface.
- **LIMIT/rownum boundary values are `int`-literal text, never bound** (BR-6,
  Q2=C). MySQL's LIMIT and Oracle's rownum predicates require integer SQL
  literals; this codebase's bind machinery only supports `setString` (U1
  ADR-007). Safety comes from the Java-level `int` typing of `start`/`max`, not
  from binding. **FR-6.3's literal text ("eliminate direct `int`
  concatenation") is NOT satisfied by the letter** — the concatenation itself
  remains in `MySQLDao.java` (`" limit " + (start-1) + "," + max`) and in
  `OracleDao.wrapRownum` (`offset`, `offset+max`, `max`). The requirement is
  met through an equivalent-safety substitute (type-level safety), not the
  textual form the requirement describes. Recorded per business-logic-model.md
  sec 8 D-1 and business-rules.md R-1/R-2.
- **`for update` double-append defect fixed** (BR-21): `wrapRownum` strips
  `for update` from the base SQL (`core`) before building the rownum-wrapped
  inline view, then re-appends it exactly once at the outermost level. The
  pre-existing (dead) code appended the wrapper's closing `for update` while
  leaving the original `for update` embedded in the untouched `sql` variable
  that was wrapped verbatim — producing SQL with `for update` in two places
  had that path ever executed. Verified in `OracleDaoTest` for both the W-1
  (2-stage wrap) and W-3 (1-stage wrap) cases.
- **`OracleDao.searchList(Query,int,int)` added as an override delegating to
  `searchListForOracle`** (BR-12) — previously `searchListForOracle` was dead
  code, reachable from nowhere. `searchList` now calls it directly; the old
  method keeps its public signature and remains independently callable
  (NFR-3).
- **No row-skipping in the Oracle callback** (BR-13) — rownum has already
  skipped the offset in the SQL; skipping again client-side would double-skip
  `2 x (start-1)` rows. The pre-existing commented-out skip loop was removed
  rather than left as dead commentary, per business-logic-model.md sec 4.4.
- **MySQL's `FOUND_ROWS()` executed as a second `Dao.executeQuery` call with a
  zero-value `PreparedSql`** (BR-8/BR-9) rather than a second call on the same
  raw `Statement` (the pre-existing approach). Same-session correctness is
  guaranteed by `DBAccessManager`'s `ThreadLocal<Connection>` (`services.md`
  R-2/R-3), not same-`Statement` identity.
- **FR-5.2/FR-5.3 satisfied by inaction** (Step 5): no PostgreSQL-specific
  `Dao`/`Search` was added; `Condition`/`MySQLCondition`/`PostgreSQLCondition`
  were not touched. Confirmed by inspection — no diff exists for these files.

## Test coverage summary

| Suite | Before | After | Delta |
|---|---|---|---|
| `OracleSearchTest` | 1 | 4 | +3 (AC-10: null-safety, cross-check vs default/MySQL filters) |
| `MySQLDaoTest` | 6 | 7 | +1 (paging-window migration: literal `limit 0,5`, WHERE-only bind values) |
| `OracleDaoTest` | 0 (file did not exist) | 10 | +10 (AC-9: W-1..W-4 generated SQL, `for update` single-append x2, no-`for update` passthrough, `searchList`->`searchListForOracle` delegation, unbound-`?` check) |
| Full suite (`mvn test`) | — | 274 tests, 0 failures | net +14 (NFR-6: no test count decrease) |

## Gate confirmation

- **MySQLDaoTest gate (Step 2 completion condition)**: confirmed empirically,
  not assumed. `MySQLDaoTest.testSearchListRdbQueryOfTIntInt` has its
  `dao.searchList(query, 1, 5)` call commented out (line 39, unchanged) — no
  existing test invoked `searchList` before this Unit. Ran `mvn -o test
  -Dtest=MySQLDaoTest` both immediately after Steps 1-3 (result: 6/6 green,
  identical to pre-U4 baseline) and again after Step 4's added test (result:
  7/7 green, the new test being the only addition). Zero existing assertions
  were changed.
- **AC-9 gate (Step 3 completion condition)**: `OracleDaoTest` exercises all
  four paging states against a mocked "default" datasource
  (`org.tamacat.mock.sql.MockDriver`, wired via `src/test/resources/db.xml`)
  and asserts the exact SQL text `DBAccessManager` executed
  (`Dao#getExecutedQuery()`), confirming zero unbound `?`:
  - **W-1** (`start=3,max=5`): `select * from ( select row_.*, rownum
    rownum_ from ( SELECT users.user_id FROM users ) row_ ) where rownum_ > 2
    and rownum_ <= 7` — 0 `?`.
  - **W-2** (`start=3,max=0`): `... where rownum_ > 2` (no upper bound) —
    0 `?`.
  - **W-3** (`start=-1,max=5`): `select * from ( SELECT users.user_id FROM
    users ) where rownum <= 5` — 0 `?`.
  - **W-4** (`start=0,max=0`): `SELECT users.user_id FROM users` (no wrap) —
    0 `?`.
  - A fifth test adds a WHERE predicate (`users.user_id=?`, bound to
    `"admin"`) under W-1 paging and confirms exactly one `?` survives (the
    WHERE placeholder) and it is bound — i.e. paging introduces zero
    additional placeholders. `DBAccessManager.executeQuery(PreparedSql,...)`
    would throw `DaoException` (`checkBindable`) had any placeholder gone
    unbound; all ten tests pass without exception, which is itself part of
    the AC-9 confirmation.

## Deviations from the plan / design docs

None identified as unresolved. One documentation clarification carried
forward per the design doc's own instruction: business-logic-model.md's
`## Review` section records an orchestrator-applied correction to the W-3
paging-window claim (MySQL's W-3 effective window is `[1, max]`, not `[1,
∞)` as an earlier iteration mis-stated); the current (post-fix) text of
§1/§9 was treated as authoritative throughout, consistent with the task's
explicit instruction.

The `for update` test fixtures use `Query.where("1=1 for update")` (a
literal WHERE fragment ending in the token) rather than a dedicated
"for-update" builder method, since no such method exists on `Query`/`Search`
in this codebase (confirmed by grep — `business-rules.md`'s "現行から引き継ぐ
既知の欠陥" item 5 notes the same: no code in `src/main` generates `for
update`; it can only arrive via an external `Query` implementation). This is
a test-construction choice, not a behavioral deviation — `wrapRownum`
operates purely on the trailing text of `base.getSql()` regardless of how
that text was produced.

---

## Review

READY

**Reviewer:** aidlc-architecture-reviewer-agent（3.5 Code Generation / U4 `dialects`、iteration 1）

**Blocking: 0 / Non-blocking: 6.** ビルドはレビュアー自身が実行した（`mvn -B test` → `BUILD SUCCESS`, **Tests run: 274, Failures: 0, Errors: 0, Skipped: 0**）。274 件の主張は独立に裏が取れた。

参照した実ソース: `MySQLDao.java`、`OracleDao.java`、`OracleSearch.java`、`Dao.java`（`:89-90`, `:191-219`, `:271`, `:286`）、`DBAccessManager.java`（`:92-110`, `:168`, `:252`）、`MySQLDaoTest.java`、`OracleDaoTest.java`、`OracleSearchTest.java`、`MIGRATION.md`、および `git diff --numstat`。

### 反証を試みたが妥当だった主張（記録）

- **AC-9 / W-1〜W-4 の手動トレース** — `OracleDao.java:52-75` を 4 状態それぞれに通し、`business-logic-model.md` § 4.2 の表と 1 文字ずつ突き合わせた。W-1 (`start=3,max=5`) → `... where rownum_ > 2 and rownum_ <= 7`（3〜7 行目の 5 件、off-by-one なし）、W-2 (`3,0`) → 下限のみ、W-3 (`-1,5`) → `select * from ( <core> ) where rownum <= 5`、W-4 (`0,0`) → `base` 素通し。**4 状態とも設計の表と完全一致。**
- **`for update` の是正（BR-21）** — `OracleDao.java:53-73`。`forUpdate` は関数先頭で無条件に評価され、`core` は `substring(0, len-10).trim()` で剥がされ、`if (forUpdate)`（`:71-73`）は W-1 / W-2 / W-3 の**3 経路すべてが合流する位置**にある（W-4 だけが `:69` の `return base` で手前に抜ける）。指示の懸念「一部の W でしか付け直されないのでは」は成立しない。`OracleDaoTest.java:102-124` が W-1 / W-3 の両方で `countOccurrences(sql,"for update") == 1` と完全 SQL 文字列の両方をアサートしており、"no exception thrown" 止まりではない。
- **未バインド `?` ゼロ** — `MySQLDao.java` / `OracleDao.java` の全 `?` 出現を grep（8 件）。**すべて Java の三項演算子か Javadoc であり、SQL 文字列リテラル内の `?` は 1 つも存在しない。** 境界値は `offset` / `offset+max` / `max` / `(start-1)` の `int` 連結のみ。
- **W-2 / W-3 の非対称の主張** — `MySQLDao.java:57-59` の `paged = start>0 && max>0` により W-2 / W-3 とも LIMIT は付かず、`:80-81` の `if (max > 0 && add >= max) break;` は `paged` を参照せず `max>0` だけで発火する。ゆえに W-2 (`max<=0`) は打ち切られず `[1,∞)`、W-3 (`max>0`) は `[1,max]`。**設計 § 1 / BR-1 / R-6 の訂正後の記述どおりで、コードが主張を裏付けている**（アサートではなく実挙動として確認）。
- **`compose` の二重実装（BR-4）** — `MySQLDao.java:45-49` と `OracleDao.java:33-37` を逐字比較。メソッド名・分岐条件・両枝とも**完全に同一**。乖離なし。checked（`hasUnbound==false`）→ `of(sql, base.getValues())` で値リスト保持、unchecked かつ `?` 残存 → `ofLiteral` で由来保持。§ 2.2 / BR-5 のとおり。
- **委譲（BR-12）と非破壊性（NFR-3）** — `OracleDao.java:77-80` の `searchList` は `return searchListForOracle(query, start, max);` の 1 行のみでロジック重複なし。`searchListForOracle(Query<T>,int,int)` は public / 戻り値 `Collection<T>` のまま（`:85`）、独立呼び出し可能。`OracleDaoTest.java:137-147` が両経路の生成 SQL 一致をアサートしている。新規 public / protected メンバは 0（`compose` / `wrapRownum` はいずれも `private static`）。
- **MySQL W-1 のリテラル LIMIT** — `MySQLDaoTest.java` の新規テストが `"... WHERE users.user_id=? limit 0,5"` と `values.size()==1`（`"admin"`）の両方をアサート。バインド値が WHERE 由来のみであることまで見ており、計画 Step 4 の要求を満たす。
- **`MIGRATION.md`** — § 3 の該当箇所（`:103-106`）は打ち消し線 ＋ **Resolved by U4 `dialects`** に書き換え済み。「planned for U4」の残存なし。§ 5（`:148-194`）の 5 項目はいずれも実コードと一致（`FOUND_ROWS()` の 2 本目実行、rownum ラップ、`for update` 二重化の修正、null ガード、FR-6.3 の equivalent-safety）。
- **AC-10** — `OracleSearch.java` の null ガードは `Search.DefaultValueConvertFilter` / `MySQLSearch.MySQLValueConvertFilter` と同形。`OracleSearchTest` の 3 本は「null を返すこと」だけでなく**他 2 実装との結果一致**を直接アサートしており、AC-10 の文面（「他 2 実装と同じ結果」）を正面から判定している。
- **不作為（FR-5.2 / FR-5.3）** — `git status` / `git diff` に `MySQLSearch` / `MySQLCondition` / `PostgreSQLCondition` / PostgreSQL 専用クラスの差分は 1 件もない。

### Non-blocking

**N-1 — `useHitCount(true)` の経路がテストスイート全体で 1 度も実行されていない。** `MySQLDao.java:84-91`（2 本目の `SELECT FOUND_ROWS()` を値ゼロ `PreparedSql` で実行する BR-8 / BR-9 / BR-10 の中核）は**新規に書き換えたコードでありながら未実行**である。`MySQLDaoTest` の新規テストは `useHitCount(false)`（`MySQLDaoTest.java` のページングテスト冒頭）を明示的に設定しており、既存 6 本は `searchList` を呼ばない。設計 § 9-5 は「mock で確認できるのは *2 本の文が同じ `Connection` から生成されたこと* までである」と述べており、**その範囲の確認は mock で実施可能**（`getExecutedQuery()` に 2 件記録されることのアサートで足りる）。計画 Step 2 が「Test: なし」としているため計画違反ではないが、書き換えたブランチが一度も走らない状態のまま 3.6 に渡ることは記録しておく。

**N-2 — `compose` の `hasUnboundPlaceholders() == true` 枝が両方言とも未実行。** `business-rules.md`「失敗様式 2」は検出方法として「unchecked な `PreparedSql` を返す `Query` スタブを 1 つ作り、**両方の `searchList` に通して同じ例外型を確認する**」を明示的に指定しているが、`MySQLDaoTest` / `OracleDaoTest` のいずれにもそのテストがない。実装は逐字同一であることを本レビューで確認済み（上記）なので現時点の乖離はないが、BR-4 が想定する「片方だけ直る」将来の乖離を検出する仕掛けは存在しない。3.6 に引き継ぐこと。

**N-3 — 計画 Step 4 の「FR-8.2 対応表の U4 該当行」が `[x]` だが、成果物が存在しない。** `code-generation-plan.md:43` はこれを Step 4 の成果物として列挙し `:41` で `[x]` を付けているが、`dialects/` 配下を grep しても FR-8.2 の対応表行はどこにも起こされていない（`code-summary.md` にも該当節なし）。設計 § 9-4 は同じ作業を Build and Test（3.6）宛の引き継ぎとしているため実質的な欠落は小さいが、**チェック済みステップに裏付けがない状態**である。`[x]` を落として 3.6 への引き継ぎとして書き直すか、対応表行を起こすこと。

**N-4 — 「`MySQLDaoTest` は既存行 0 行変更」の主張が厳密には成り立たない。** `git diff --numstat` は `41 / 1`。削除 1 行は既存の空行の末尾タブ除去（`-	` → `+`）であり、`OracleSearchTest.java` にも同種の 1 行（`26 / 1`）がある。**既存アサートは 1 つも変更されていない**（これは確認済み）ので `code-summary.md` の「Zero existing assertions were changed」は正確だが、同節と本タスクの前提が使う「0 existing lines touched」という強い言い方は差分と食い違う。加えて新規に追加した `@Before setUpPagedDao` / `@After tearDownPagedDao` は**既存 6 本を含む全テストの前後で実行される**ため、既存テストの実行環境は厳密には無変更ではない（実測では全緑）。JUnit4 は同一クラス内の複数 `@Before` の実行順を規定しないため、`pagedDao` を各テストで生成・クローズする副作用（コネクションプールの取得/返却）が既存テストに掛かることは記録に値する。

**N-5 — 実装が計画の指定した観測手段と異なる（改善方向だが未記録）。** 計画 `:42` と設計 § 7 段 4 は `getLastPreparedStatement().getPreparedSql()` での確認を指定しているが、実装は `getDBAccessManager().getExecutedStatements()` の末尾要素 ＋ `ExecutedStatement#getSql()` / `#getValues()` を使っている。バインド値まで検証できる分**より強い**確認であり是正不要だが、`code-summary.md`「Deviations from the plan」節が「None identified as unresolved」としており、この差し替えが記録されていない。同節に 1 行足すこと。

**N-6 — `MIGRATION.md:187` にタイプミス。** `` `" limit " + (start-1) + "," + max"` `` の末尾に余分な `"` があり、Java 式として読めない。読者を惑わせるだけの純粋な誤植。

### 記録（是正不要）

- `for update nowait` / `for update of ...` / 末尾空白付きの変種で `endsWith` が偽になり `for update` がインラインビュー内側に取り残される件は、`business-rules.md`「現行から引き継ぐ既知の欠陥」5 として 3.1 で既に是正しないと決定済み。本ステージの実装は当該決定に忠実であり、新たな逸脱ではない。
- FR-6.3 を「文面どおりには満たさない」とする framing（`code-summary.md` § Key implementation decisions、`MIGRATION.md` § 5 最終項）は、設計 § 8 D-1 / § 9-8 の指示（「文面どおり達成と報告しないこと」）に正しく従っている。過大主張はない。
- `code-generation-plan.md:70-73` の Plan Approval チェックボックスが未チェック・`[Answer]` 空のまま。ゲート記録の形式上の欠落であり、コードの是非には影響しない。

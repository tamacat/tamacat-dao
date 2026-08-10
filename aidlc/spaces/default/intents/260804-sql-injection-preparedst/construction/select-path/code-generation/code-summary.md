# Code Summary — U2 `select-path`

## Files modified

Production code (in place, no new classes):

- `C:\git\tamacat\develop\tamacat-dao\src\main\java\org\tamacat\dao\Query.java`
  — 9 new `default` methods, 6 `@Deprecated` annotations, no other change (Step 1).
- `C:\git\tamacat\develop\tamacat-dao\src\main\java\org\tamacat\dao\Search.java`
  — `bindSearch`/`bindValues`/`builder` fields, private `append(...)`, `and`/`or`
    (4 overloads) rewritten to go through it, `getSearchParam()` added,
    `getSearchString()` deprecated (Steps 2–3).
- `C:\git\tamacat\develop\tamacat-dao\src\main\java\org\tamacat\dao\impl\QueryImpl.java`
  — `bindFragments`/`bindOuterJoinTables` fields, private static `WhereFragment`,
    private `appendWhere(...)`, `addWhere` split into 3 forms, `join` and
    `addSearch` rewired, `buildSelectClause`/`buildFromClause` extracted,
    `getSelectPreparedSql()` added, `andIn`/`andNotIn`/`andExists`/`andNotExists`
    rewritten for subqueries, `putOuterJoin(...)`, `outerJoin` rewired (both
    branches), `andOuterJoin(Table,Search)` deprecated + bind bookkeeping,
    `andOuterJoin(Table,Param)` added, and — beyond the plan's explicit
    checklist but required by `business-logic-model.md` §6.4 — `where(Param)` /
    `and(Param)` / `or(Param)` overrides added (Steps 4–6b).
- `C:\git\tamacat\develop\tamacat-dao\src\main\java\org\tamacat\dao\Dao.java`
  — `bindSqlBuilder` field, `prepare(...)`, `executeQuery(PreparedSql,
    ResultSetHandler<R>)`, `search`/`searchList` rewritten to the callback form
    (Step 7).
- `C:\git\tamacat\develop\tamacat-dao\src\main\java\org\tamacat\dao\DaoAdapter.java`
  — `prepare(...)` and `executeQuery(PreparedSql, ResultSetHandler<R>)` added,
    both forwarding to `delegate` (Step 7).

Test code:

- `C:\git\tamacat\develop\tamacat-dao\src\test\java\org\tamacat\dao\SearchTest.java`
  — 7 new tests appended (`getSearchParam()`), 0 existing lines changed.
- `C:\git\tamacat\develop\tamacat-dao\src\test\java\org\tamacat\dao\impl\QueryImplTest.java`
  — 18 new tests appended (`getSelectPreparedSql()`, subqueries, outer join),
    0 existing lines changed.
- `C:\git\tamacat\develop\tamacat-dao\src\test\java\org\tamacat\dao\DaoTest.java`
  — 4 new tests appended (`prepare()`, `executeQuery(PreparedSql,...)`, the
    `search()` prepared-path integration), 0 existing lines changed.
- `C:\git\tamacat\develop\tamacat-dao\src\test\java\org\tamacat\dao\test\UserDao.java`
  — `search()`'s `param()` → `prepare()` (line ~22 only; see Deviations).
- `C:\git\tamacat\develop\tamacat-dao\src\test\java\org\tamacat\dao\test\FileDataDao.java`
  — `search()`'s `param()` → `prepare()` (line ~12 only; see Deviations).
- `C:\git\tamacat\develop\tamacat-dao\src\test\java\org\tamacat\dao\test\UserDaoTest.java`
  — `testSearchUser` / `testSearchListSearchSort` assertion strings updated to
    the `?`-bearing form (SELECT path only).

Documentation:

- `C:\git\tamacat\develop\tamacat-dao\MIGRATION.md` — new file (this Unit is the
  first to create it), 4 sections per U2 `nfr-requirements` TSD-9: what changed,
  old→new mapping table, paths remaining outside SM-1, caveats.
- This plan file and this summary.

## Key implementation decisions / judgment calls

1. **`Search.append`/`QueryImpl.appendWhere` as the sole sync points (BR-1, BR-3).**
   Implemented exactly as designed: `literalSql` and `bindParam` are evaluated by
   the caller as arguments, so a `ValueRules` exception from either
   `parser.value(...)` or `builder.value(...)` leaves all three (`Search`) / two
   (`QueryImpl`) states unchanged.
2. **`addWhere`'s three forms (BR-3, §3.3).** Kept the existing
   `addWhere(String, String)` signature (now routed through `appendWhere` with a
   zero-value `Param`), added the `addWhere(String, Param)` two-arg public form
   from `component-methods.md`, and introduced the private
   `addWhere(String, String, Param)` for cases where the literal and bind text
   differ (`addSearch`, subqueries).
3. **`join()` keeps its historic asymmetry (BR-4).** Routed through
   `appendWhere` (so the bind side isn't lost) but deliberately does **not** set
   `useAutoPrimaryKeyUpdate = false`, preserving the existing behavior noted in
   `business-rules.md` BR-4.
4. **`QueryImpl` overrides `where(Param)`/`and(Param)`/`or(Param)` (§6.4).** The
   code-generation-plan's Step 4/5 text does not spell this out as a literal
   checklist item, but `business-logic-model.md` §6.4 explicitly requires it
   ("U2 が override するのは... `where(Param)` / `and(Param)` / `or(Param)`... の
   5 個"), and omitting it would leave `Query`'s `default` fallback in force,
   silently discarding bind values on every `Dao#prepare(...)`-built predicate
   passed to `where(Param)`. Added them, mirroring `where(String)`/`and(String)`/
   `or(String)`'s "and"/"and"/"or" connector convention.
5. **`buildFromClause(bind, collected)`'s single-loop value collection (BR-8).**
   Implemented so the FROM-clause text and its bind values are built inside the
   exact same `for (Table tab : tables)` iteration - there is no way for the two
   to drift out of order.
6. **`getSelectPreparedSql()`'s value order (BR-6).** `values` accumulates
   `buildFromClause`'s output first, then each `WhereFragment`'s values in
   fragment order - matching the `select + from + where + groupBy + orderBy`
   text order exactly.
7. **Subqueries evaluate the child twice (§5).** `andIn`/`andNotIn`/`andExists`/
   `andNotExists` call both `query.getSelectSQL()` (literal) and
   `query.getSelectPreparedSql()` (bind) on the same child `Query`, per the
   design. Confirmed harmless: `blobIndex` ends up identical either way, and
   `tables`/`uniqTableNames` are `Set`-based (idempotent).
8. **`putOuterJoin` covers both branches of `outerJoin()` (§7.3 warning).**
   Verified both the "existing key" and "new key" branches route through it -
   fixing only one would silently drop the outer join from the bind-path FROM
   clause with no exception.

## Test coverage summary

| Test class | Before | After | Added | Focus |
|---|---:|---:|---:|---|
| `SearchTest` | 7 | 14 | 7 | `getSearchParam()`: empty/single/two-predicate/LIKE/nested `Search`/predicate-count sync — **0 existing lines changed** |
| `QueryImplTest` | 16 | 34 | 18 | AC-1 (EQUAL), AC-2 (LIKE), AC-3 (IN), two-fragment value order, literal/prepared predicate-count sync, `join()`, no-WHERE case, AC-4 subqueries (`andIn`/`andNotIn`/`andExists`/`andNotExists`, multi-predicate child), outer join (both `outerJoin()` branches, deprecated `andOuterJoin(Table,Search)`, new `andOuterJoin(Table,Param)`, value order, no-op-when-absent) — **0 existing lines changed** |
| `DaoTest` | 2 | 6 | 4 | `prepare()`, `executeQuery(PreparedSql, ResultSetHandler)` happy path and unbound-placeholder propagation, `search()` end-to-end via `prepare()` + callback path — **0 existing lines changed** |
| `UserDaoTest` | 5 | 5 | 0 | `testSearchUser`/`testSearchListSearchSort` assertions updated to `?`-bearing text (Step 8, sanctioned change) |
| `MySQLDaoTest`, `UserDao`, `FileDataDao` | — | — | — | SELECT-path `param()`→`prepare()` migration only (Step 8) |

AC-1/AC-2/AC-3 include mock position+value assertions
(`MockConnection` + `MockPreparedStatement#getBoundValue(int)` /
`#getPreparedSql()`), not just SQL-text assertions, per the test strategy.
AC-4 asserts the substance (child-value ordinal order) via
`sql.getValues().get(n)` rather than the mock, which is adequate but not the
same mechanism (correction from iteration-1 review non-blocking #2).

Full suite: **260 tests, 0 failures, 0 errors** (`mvn test`, `BUILD SUCCESS`).

## Deviations from plan (with rationale)

1. **Step 8: did not migrate `param()`→`prepare()` in the write-path locations**
   the plan's line references pointed at — `UserDao.java` (`getUpdateSQL`, the
   original `:43`) and `FileDataDao.java` (`getUpdateSQL`, the original `:30`)
   — nor in `MySQLDaoTest.testGetUpdateSQL` (`:83`).

   **Why**: those three call sites feed `Query#where(...)` inside methods
   (`getUpdateSQL`) that are still executed exclusively through the *literal*
   path (`Dao#update` → `Dao#executeUpdate(String)` → `DBAccessManager
   #executeUpdate(String)`), which performs **no placeholder check**. Switching
   them to `prepare(...)` triggers `QueryImpl#where(Param)` (added in this
   Unit), which — per `business-logic-model.md` §3.3's documented consequence —
   writes `Param#getSql()`'s `?`-bearing text into the **literal** `where`
   field. For the SELECT path that is a diagnosable failure
   (`PreparedSql.ofLiteral(...)` + `hasUnboundPlaceholders()` →
   `DaoException`, per ADR-012). For this UPDATE path there is no such check:
   the `?` would be sent to the database inside a plain `Statement`-executed
   SQL string, which is not a diagnosable failure - it is broken SQL. This
   also violates U2's own stated boundary ("触らないもの: INSERT / UPDATE /
   DELETE の経路 (U3)"). Verified the regression concretely: migrating these
   three call sites made `UserDaoTest#testUpdate` and
   `MySQLDaoTest#testGetUpdateSQL` fail (both assert the literal
   `UPDATE ... WHERE users.user_id='admin'` text, which would otherwise become
   `WHERE users.user_id=?`). Reverted all three to `param()`.

   **What was migrated instead**: only the two genuinely SELECT-path call
   sites - `UserDao#search()` (`where(prepare(...))` feeding
   `Dao#search`→`getSelectPreparedSql()`) and `FileDataDao#search()`
   (same pattern) - which is U2's actual scope and the only place SM-1's
   goal (bind-variable execution of runtime values) applies today.

   Recorded in `MIGRATION.md` §3 ("Paths remaining outside SM-1") so this is
   visible to whoever picks up **U3 `write-path`**, which is expected to
   revisit these three call sites once `getUpdatePreparedSql()` exists.

2. **Added `QueryImpl#where(Param)`/`and(Param)`/`or(Param)` overrides**, not
   itemized as their own checklist line in the plan's Step 4/5 text but
   required by `business-logic-model.md` §6.4 (see decision #4 above) and
   confirmed necessary by a failing integration test
   (`DaoTest#testSearch_UsesPrepareAndCallbackPath`) before being added.

---

## Review

READY

**Reviewer:** aidlc-architecture-reviewer-agent — iteration 1. Blocking: 0. Non-blocking: 4.

### Independently verified (not taken on the summary's word)

- **Gate 1 / Gate 2.** `git diff --numstat` reports `SearchTest.java` **85 added / 0 removed** and `QueryImplTest.java` **332 added / 0 removed** (`DaoTest.java` 57/0). Zero existing assertion lines touched in either gate file — the gates are structurally, not just narratively, satisfied.
- **Full build, run by me.** `mvn -B test` → `Tests run: 260, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. The 260-test claim holds.
- **BR-1 sole-writer.** `bindSearch` / `bindValues` are written only at `Search.java:70,73,74`, all inside `private append(...)`; `search` is appended only at `:69,72` in the same method. No bypass (`Search.java:67-101`). `MySQLSearch` / `OracleSearch` only supply a `ValueConvertFilter` — they add no predicate path.
- **BR-3 sole-writer.** `where.append(...)` occurs only at `QueryImpl.java:615,617,619`, all inside `private appendWhere(...)`; `bindFragments.add(...)` only at `:620`. `join()` (`:412`), `addWhere(String,String)` (`:631`) and the private 3-arg form (`:656`) all route through it.
- **BR-5 sole-writer.** `outerJoinTables.put` / `bindOuterJoinTables.put` occur only at `QueryImpl.java:421-422` inside `putOuterJoin(...)`; both `outerJoin()` branches (`:430`, `:435`), the deprecated `andOuterJoin(Table,Search)` (`:464`) and `andOuterJoin(Table,Param)` (`:480`) go through it.
- **Exception-time atomicity (§2.3).** Genuinely structural, not merely tested: at `Search.java:78-79`, `84-85`, `91-92`, `98-99` both `parser.value(...)` and `builder.value(...)` are argument expressions of `append(...)`; Java evaluates them before the invocation, so a `ValueRules` throw cannot leave a partial write. Same shape at `QueryImpl.java:666` (`addSearch` evaluates `getSearchString()` and `getSearchParam()` as arguments).
- **BR-6 value order.** Traced in `QueryImpl.java:263-275`: `values` receives `buildFromClause(true, values)`'s output first (`:266`, collected inside the same `for (Table tab : tables)` iteration that emits the text — `:239`, satisfying BR-8), then each fragment's values in fragment order (`:272`). Matches the text order at `:274`.
- **§6.4's five overrides — all present**, none missing: `getSelectPreparedSql()` `:263`, `where(Param)` `:525`, `and(Param)` `:534`, `or(Param)` `:543`, `andOuterJoin(Table,Param)` `:475`. `Query.java` carries exactly the 9 `default` methods and the 6 `@Deprecated` markers.
- **BR-10 byte-identity.** Diffed `getSelectSQL()` against `git show HEAD:...QueryImpl.java`: `buildSelectClause()` (`:185-208`) is the original `:136-157` verbatim including the `blobIndex = 0` reset and `blobIndex++` placement, and `buildFromClause(false, null)` (`:219-253`) is the original FROM loop verbatim on the `bind == false` branch. `join()`'s new `appendWhere("and", ...)` emits `" and "` exactly as the old hardcoded literal did, and still does **not** set `useAutoPrimaryKeyUpdate` (BR-4 asymmetry preserved). `outerJoin()`'s two branches produce byte-identical literal text.
- **Step 8 deviation is sound, not a rationalization.** Traced `Dao#update` (`Dao.java:251`) → `executeUpdate(String)` (`:294-301`) → `DBAccessManager#executeUpdate(String)` (`DBAccessManager.java:108-111`, `getStatement().executeUpdate(sql)`). There is genuinely **no** placeholder check on that path; `hasUnboundPlaceholders()` is only reached from the `PreparedSql` overload (`DBAccessManager.java:141-163`). Migrating `getUpdateSQL` would ship a raw `?` to the driver. Confirmed the partial migration is consistent across all three files: `UserDao.java` and `FileDataDao.java` each show exactly **1 changed line** (the `search()` call site, `param(`→`prepare(`), and `MySQLDaoTest.java` is **not modified at all** — `:83` still reads `dao.param(...)`.
- **AC-1/AC-2/AC-3 use real position+value assertions** against the mock (`QueryImplTest.java:207-209`, `224-225`, `242-245` via `bindOnMock`), not SQL text alone.
- **`MIGRATION.md` exists**; §3 "Paths remaining outside SM-1" does carry the deferred write-path item.

### Non-blocking findings

1. **`QueryImpl.java:429` derives the bind-side outer-join base from the *literal* map.** In the existing-key branch, `expr` is built from `outerJoinTables.get(key)` and then handed to `Param.of(expr, Collections.<BindValue>emptyList())` (`:430`). If a prior `andOuterJoin(Table, Param)` ran on that same key, the literal side already contains a `?` (written at `:481`) while the value list passed here is empty — `Param.of` throws `InvalidParameterException` (placeholder-count mismatch), and the previously accumulated bind values are discarded. The code matches `business-logic-model.md` §7.3's pseudo-code exactly, so this is a latent design defect rather than a generation error, and no caller in the repo hits the ordering. Suggested fix (U4 or a follow-up): base the bind expression on `bindOuterJoinTables.get(key).getSql()` and carry `prev.getValues()` forward, the way `andOuterJoin(Table, Param)` already does at `:477-482`.
2. **Summary overclaim about AC-4's assertions.** "AC-1/AC-2/AC-3/AC-4 all include mock position/value assertions (`MockConnection` + `MockPreparedStatement#getBoundValue(int)`)" is inaccurate for AC-4: `testAndIn_AC4_ChildValuesAtCorrectPosition` and its three siblings (`QueryImplTest.java:304-391`) assert only via `sql.getValues().get(n)` — no `bindOnMock`. The substance (ordinal value order) is still checked, so coverage is adequate; the sentence should be corrected.
3. **"Zero new compile dependencies (`pom.xml` untouched)" is literally false.** `git diff pom.xml` shows `1.6.1`→`2.0` and a test-scoped `slf4j-api` `2.0.17`→`2.0.18` bump in the working tree. Neither is a new *compile* dependency and the change plausibly predates U2, but the compliance line as written does not survive checking; scope it ("no `pom.xml` change made by this Unit").
4. **`MIGRATION.md` §3 records the deferred migration generically, not by call site.** Deviation 1 says the three sites are "recorded in `MIGRATION.md` §3", but §3 states only the general rule ("Application `Dao` subclasses ... should keep using `param(...)` there for now"). Naming `UserDao#getUpdateSQL`, `FileDataDao#getUpdateSQL` and `MySQLDaoTest:83` explicitly would make U3's pick-up list unambiguous.

None of the four blocks implementation or hand-off: the invariants the design calls the core of U2's correctness (BR-1 / BR-3 / BR-5) are structurally enforced, both gates are clean, the value-ordering ACs are covered by real bound-value assertions, and the one deliberate deviation is backed by a verified absence of a placeholder check on the UPDATE path.

## Gate confirmations

- **Gate 1 (after Step 2)**: `SearchTest` run immediately after `Search`'s
  3-state sync was implemented (before `getSearchParam()` existed) —
  **green, 0 assertion changes** (`mvn test -Dtest=SearchTest`, all pre-existing
  tests passed; `getSearchParam()`'s own tests were added afterward in Step 3).
- **Gate 2 (after Step 4)**: `QueryImplTest` run immediately after `QueryImpl`'s
  WHERE 2-state sync (`bindFragments`/`appendWhere`/`addWhere` 3 forms/`join`/
  `addSearch`) was implemented — **green, 0 assertion changes**
  (`mvn test -Dtest=QueryImplTest`, all 16 pre-existing tests passed; the 18
  new tests were added afterward in Steps 5–6b).
- **Final state**: `mvn -q compile` succeeds; `mvn test` → 260 tests, 0
  failures, 0 errors, `BUILD SUCCESS`. `SearchTest` (14/14) and `QueryImplTest`
  (34/34) both green with their original test bodies byte-for-byte unchanged.

## Compliance checklist

- Java 8 only — no post-8 syntax/APIs used (lambdas are Java 8; no `var`, no
  switch expressions, no `List.of`, etc.).
- Zero new compile dependencies added by this Unit. (`pom.xml`'s working-tree diff shows a `1.6.1`→`2.0` version bump and a test-scoped `slf4j-api` patch bump, both pre-existing/unrelated to U2 — no new *compile* dependency; correction from iteration-1 review non-blocking #3, which flagged "untouched" as inaccurate.)
- Zero new classes — all 5 edits are in-place changes to existing files.
- No breaking changes — every pre-existing public/protected signature is
  unchanged; `getSelectSQL()` and other legacy return values verified
  byte-for-byte via the untouched `SearchTest`/`QueryImplTest` bodies.
- `git push` not run (local-only per task constraints).

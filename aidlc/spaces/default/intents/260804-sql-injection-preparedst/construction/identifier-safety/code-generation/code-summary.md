# Code Summary — U5 `identifier-safety`

## Files created

- `src/main/java/org/tamacat/sql/IdentifierRules.java` — new `public final class`, private constructor, `public static String validate(String raw)`. Rejects input containing `'`, `"`, `;`, `--`, `/*`, or `*/` (ID-1..ID-5) by throwing `InvalidParameterException`; returns `null` unchanged for `null` input (no exception); otherwise returns the input unchanged. Implemented as a chain of `String.indexOf` calls — no regex, Java 8 only, no new compile dependency (`business-logic-model.md` §1, `business-rules.md` BR-1/BR-10/BR-14).
- `src/test/java/org/tamacat/sql/IdentifierRulesTest.java` — 9 tests: null passthrough, one test per dangerous literal (ID-1..ID-5, with ID-5 split into open/close forms), legitimate compound expressions passing through unchanged, and a string-literal-bearing expression (`TO_CHAR(d,'YYYY')`) correctly rejected under ID-1.
- `src/test/java/org/tamacat/dao/meta/FunctionOnlyColumnFixture.java` — test-only factory living in `org.tamacat.dao.meta` (not `org.tamacat.dao.impl`, where the regression test needed it) because `ColumnDefine` is package-private: even an implicit empty-varargs `DefaultColumn(...)` call from outside the package fails to compile if `ColumnDefine` is unreachable. Exposes `create(table, columnName)` (isFunction()==true, getFunctionName()==null, via the `Column.FUNCTION` define) and `createPlain(table, columnName)` (DataType.FUNCTION column, isFunction()==false until `functionName(...)` is called).

## Files modified

- `src/main/java/org/tamacat/dao/Sort.java` — added `import org.tamacat.sql.IdentifierRules;` and changed the non-`Column` branch of `sort(Object, Object)` from `sort.append(k.toString() + " " + o.toString())` to `sort.append(IdentifierRules.validate(k.toString()) + " " + o.toString())`. The `Column` branch (both `isFunction()` outcomes) and `Order` handling are untouched (BR-3/BR-4/BR-6).
- `src/main/java/org/tamacat/dao/impl/QueryImpl.java` — added `import org.tamacat.sql.IdentifierRules;` and changed the `isFunction()` branch of `buildSelectClause()` (the shared SELECT-clause builder used by both `getSelectSQL()` and `getSelectPreparedSql()`) from `select.append(col.getFunctionName() + " " + col.getColumnName())` to `select.append(IdentifierRules.validate(col.getFunctionName()) + " " + col.getColumnName())`. `blobIndex` counting, `tables.add(...)`, and the `getColumnName(col)` else-branch are untouched (BR-5/BR-7/BR-13).
- `src/test/java/org/tamacat/dao/SortTest.java` — appended 6 tests for the non-`Column` key path (ID-1..ID-5 rejection, one legitimate expression passing through). Existing `testAsc`/`testDesc` untouched; also added the `InvalidParameterException` import.
- `src/test/java/org/tamacat/dao/impl/QueryImplTest.java` — appended 6 tests for the `getFunctionName()` path: single-quote rejection via both `getSelectSQL()` and `getSelectPreparedSql()` (confirming both share validation), semicolon rejection, line-comment rejection, a legitimate `functionName` passing unchanged, and the BR-14 regression (isFunction()==true / getFunctionName()==null does not throw, still emits `"null "` prefix). All 34 pre-existing tests untouched.
- `MIGRATION.md` — appended a section documenting this as a breaking behavioral change.

## Key implementation decisions

1. **Null passthrough (BR-14) confirmed against real source.** `DefaultColumn.isFunction` is set `true` in exactly two places: the `Column.FUNCTION`-define branch of the `DefaultColumn(Table, String, DataType, String, ColumnDefine...)` constructor (`functionName` left `null`), and `setFunctionName(String)` (which may itself be called with `null`). `ColumnFunctionTest.java` fixes `getFunctionName()==null` as an asserted, legitimate outcome (its `COL3`). `IdentifierRules.validate(null)` therefore returns `null` without throwing, preserving the current `"null " + columnName` SELECT-clause output for that state. This was verified directly against `DefaultColumn.java` and `ColumnFunctionTest.java`, not just taken from the design doc.
2. **`Sort`'s non-`Column` branch never sees `null` reach `validate`.** `k.toString()` throws `NullPointerException` first if `k` is `null` — identical to pre-existing behavior — so the null-passthrough path is only exercised via `QueryImpl`.
3. **`IdentifierRules` is `public`, not package-private,** because its callers (`org.tamacat.dao.Sort`, `org.tamacat.dao.impl.QueryImpl`) are in different packages from `org.tamacat.sql`. This is the same cross-package constraint that made U1's `PreparedSql.getPlaceholderCount()` and U2's `Search.getSearchParam()` public. It is the only new public type/member introduced by this unit.
4. **Scope boundary respected (BR-5/R-5):** table names and column names themselves (`Column.getColumnName()` / `MappingUtils.getColumnName(col)`) are deliberately not validated — a documented scope decision (not a technical limitation; the design records that wrapping `MappingUtils.getColumnName(...)` call sites would technically work but was intentionally not pursued in this unit). `Order`/`o.toString()` is likewise out of scope (BR-6). `DataType.FUNCTION` (a value-position concern owned by U1's `BindSqlBuilder.tokenFor`) is untouched — confirmed distinct from `Column.getFunctionName()` (an identifier-position concern).
5. **Test fixture workaround:** `ColumnDefine` (the varargs element type of `DefaultColumn`'s full constructor) is package-private in `org.tamacat.dao.meta`. Even an implicit zero-length varargs array from a different package fails Java's accessibility check at the variable-arity applicability phase. `FunctionOnlyColumnFixture` (test-only, in `org.tamacat.dao.meta`) works around this so the BR-14 regression test and the plain-functionName test can live in `QueryImplTest` as the plan directs.

## Test coverage summary

| Test class | Before | After | New | Gate |
|---|---|---|---|---|
| `SortTest` | 2 | 8 | 6 | PASS — 0 existing assertions changed |
| `QueryImplTest` | 34 | 40 | 6 | PASS — 0 existing assertions changed |
| `IdentifierRulesTest` (new) | 0 | 9 | 9 | PASS |
| Full suite (`mvn test`) | — | 295 tests, 0 failures, 0 errors | — | BUILD SUCCESS |

## Deviations from the plan (with rationale)

- The plan's `business-logic-model.md` §3.1 cited `QueryImpl.java:139-157`/`getSelectSQL` loop directly; the actual current source (already refactored by U2) has the loop inside a private `buildSelectClause()` method shared by `getSelectSQL()` and `getSelectPreparedSql()`. The single-line change was applied inside `buildSelectClause()` — functionally identical to the plan's intent (and explicitly what BR-8/§3.2 "変更は自動的に両方の経路に効く" describes), just at the actual current line location rather than the design doc's line-number citation.
- Added a test-only fixture class (`FunctionOnlyColumnFixture`) not explicitly named in the plan, required purely to work around `ColumnDefine`'s package-private visibility when constructing the BR-14 regression fixture from `org.tamacat.dao.impl`'s test package. No production code is affected.
- Test counts landed at 6 per `Sort`/`QueryImpl` (plan estimated 5-6/6-7) and 9 for `IdentifierRules` (plan estimated 7-8) — within or one above the estimated range, covering all listed cases plus the ID-5 open/close split.

## Gate confirmation

- `SortTest`: before 2 tests green, after 8 tests green, zero existing assertions modified (diff confirms only additions after the existing `testSort()` commented-out method).
- `QueryImplTest`: before 34 tests green, after 40 tests green, zero existing assertions modified (diff confirms only additions before `countConnectors`).
- Full `mvn test`: 295 tests, 0 failures, 0 errors, BUILD SUCCESS.

---

## Review

READY

**Reviewer:** aidlc-architecture-reviewer-agent — iteration 1 of 2. Blocking: 0. Non-blocking: 4. Build independently run.

### Verification method

Read the 3 design artifacts (`business-logic-model.md`, `business-rules.md`, `domain-entities.md`, post-fix text), the plan, and every generated/modified source and test file. Independently ran `mvn test`. Cross-checked each claim in this summary against the real source rather than trusting it.

### Claims I tried and failed to refute

- **BR-14 null passthrough lands correctly.** `IdentifierRules.java:49-51` returns `null` **before** the `indexOf` chain at `:52-53`. No NPE path. IR-1/IR-2/IR-3 all hold as written; the character set at `:52-53` is exactly ID-1..ID-5, no more, no less (BR-10). Message includes `raw` (`:54`), consistent with BR-2, not SEC-4.
- **The `isFunction()==true / getFunctionName()==null` state is real and preserved.** `DefaultColumn.java:61-62` (`FUNCTION.equals(def) → isFunction = true`, `functionName` never touched) and `DefaultColumn.java:193-197` (`setFunctionName`, may be passed `null`) are the only two assignments to `isFunction`. `ColumnFunctionTest.java:30` genuinely asserts `assertNull(COL3.getFunctionName())` — not invented by the summary. `QueryImplTest.java:560-567` pins the end-to-end outcome `"SELECT null c FROM users"`, and it passes.
- **`QueryImpl`'s location choice is correct and equivalent, not a rationalization.** The single change sits at `QueryImpl.java:202` inside `private String buildSelectClause()` (`:186`), which is called by both `getSelectSQL()` (`:174`) and `getSelectPreparedSql()` (`:266`). §3.2 / BR-8 ("両経路に自動的に効く") is satisfied structurally, and `QueryImplTest.java:492-516` proves it behaviourally through both entry points. `blobIndex++` (`:199`), `tables.add(...)` (`:206`) and the `getColumnName(col)` else-branch (`:204`) are byte-for-byte unchanged from the design's §3.2 listing.
- **`Sort`'s null case is moot as claimed.** `Sort.java:58` evaluates `k.toString()` as the argument expression; `null instanceof Column` is false so control reaches the else branch and NPEs before `validate` is entered — identical to pre-change behaviour (§1). No contradiction with BR-14.
- **Both gates hold.** `git diff --numstat`: `SortTest.java` 65 added / **0 deleted**; `QueryImplTest.java` 413 added / **0 deleted**; `Sort.java` 2 added / 1 deleted (the import + the single body line, BR-12). Zero existing test lines touched, verified against HEAD for the whole intent, not just this unit.
- **Scope boundary held exactly — neither over- nor under-implemented.** `grep -rn IdentifierRules src/main` returns exactly two call sites (`Sort.java:58`, `QueryImpl.java:202`) plus imports. `Sort.java:53` (`col.getColumnName()`), `Sort.java:55` (`MappingUtils.getColumnName(col)`), `QueryImpl.java:204` (`getColumnName(col)`) and `o.toString()` (`Sort.java:53/55/58`) are all unwrapped — BR-5, BR-6, BR-7 respected. `Query`'s only ORDER BY entry point is `orderBy(Sort)` (`Query.java:198`, `QueryImpl.java:596`), so no third raw identifier path was missed. `DataType.FUNCTION` untouched (BR-9).
- **The fixture is necessary and genuinely test-only.** `ColumnDefine.java:9` is `class ColumnDefine` — package-private — and `Column.java:16` exposes `FUNCTION` with that inaccessible type, so `Column.FUNCTION` cannot be named from `org.tamacat.dao.impl`'s test package. `FunctionOnlyColumnFixture.java` lives at `src/test/java/org/tamacat/dao/meta/` (confirmed: no such file under `src/main`), so production public surface is unaffected. `IdentifierRules` remains the only new public production type.
- **Test quality is real.** `IdentifierRulesTest` (9) covers null, all five literals with ID-5 split open/close, `TO_CHAR(d,'YYYY')` rejection (R-6), and a passing set `RAND()` / `COUNT(*)` / `t1.col1, t2.col2`; ID-1 asserts the exact message. `SortTest` adds 5 rejection tests (including `"col'; DROP TABLE users; --"`) plus `testSort_NonColumnKey_LegitimateExpression_Passes` asserting `"RAND() asc"`. `QueryImplTest` adds 4 rejection tests, a legitimate `COUNT` pass asserting the full SQL, and the BR-14 regression. No `assert true`-style filler.
- **Build reproduced independently.** `mvn test` → `Tests run: 295, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`; `SortTest` 8, `QueryImplTest` 40, `IdentifierRulesTest` 9 — every number in the coverage table matches.
- **MIGRATION.md §6 is accurate.** Present at `MIGRATION.md:219`, framed as **BREAKING BEHAVIORAL CHANGE** in bold and explicitly contrasted with U1/U2/U4's additive framing (satisfying the project rule that breaking notes be visually distinguished). It names both call sites, states both SELECT entry points are affected identically, lists the exact character set, documents the null exemption, and closes with "Known partial coverage" recording AC-10b's 2-of-3 gap and that U5 is the final unit. The claim "`InvalidParameterException extends IllegalArgumentException` (unchecked)" is verified against `InvalidParameterException.java:7`.

### Non-blocking findings

1. **`FunctionOnlyColumnFixture.createPlain` is not actually required** (`FunctionOnlyColumnFixture.java:33-35`; used at `QueryImplTest.java:550`). The accessibility argument in decision 5 holds only for `create` (which must name `Column.FUNCTION`). For `createPlain` the existing public API suffices: `DefaultTable.registerColumn` sets the table on the *original* instance (`DefaultTable.java:107` `column.setTable(this)` before cloning), so `Tables.create("users").registerColumn(col)` with `Columns.create("c").type(DataType.FUNCTION).functionName("COUNT")` reaches the same state — the pattern `ColumnFunctionTest` itself uses. Harmless (test-only), but decision 5's "required purely to work around" is overbroad for half the fixture.
2. **MIGRATION.md's §6 "A narrowed corner case" workaround is imprecise and can mislead.** It says to "build the equivalent expression via a `Column` whose `isFunction()` branch is used instead". In `Sort`, that branch emits `col.getColumnName()` (`Sort.java:53`), *not* the function name — a reader who follows the hint by calling `functionName("TO_CHAR(d,'YYYY')")` gets `isFunction()==true` but the plain column name silently emitted into ORDER BY. The correct instruction is: put the expression in `columnName(...)` **and** mark the column as a function. Same class of imprecision the project rule about verifying user-facing workarounds against real source targets.
3. **`Sort.sort` mutates before it validates.** `Sort.java:49` appends the separator comma, then `:58` may throw. A caller that catches `InvalidParameterException` — which MIGRATION.md §6 explicitly recommends ("catch `InvalidParameterException` and handle it as a rejected/invalid sort request") — and reuses the same `Sort` instance gets a dangling comma and, on the next successful call, `"a asc,,b asc"`. The generated code is faithful to §2.2's exact listing, so this is not a code-generation defect; the same shape pre-exists for the `k == null` NPE. Worth recording for Build and Test / any future revision, and worth softening the MIGRATION.md catch-and-continue advice to "catch and discard the `Sort`".
4. **Minor test gaps.** `SortTest` exercises only `sort(k, Order)` directly, never the `asc(Object)` / `desc(Object)` wrappers that MIGRATION.md tells users to audit, and no test asserts that `getSortString()` is left unchanged after a rejection (which would have surfaced finding 3).

### Verdict

Every substantive claim in this summary was checked against the real source or a reproduced build, including the three claims most at risk of being rationalizations (the `buildSelectClause` relocation, the fixture's necessity, and the null-passthrough ordering). All hold. The design's §1-§6 text, BR-1..BR-14 and IR-1..IR-3 are implemented exactly, with no over-implementation past the BR-5/BR-6 scope line. The four non-blocking findings are documentation precision and test-surface hygiene; none requires architectural guidance to resolve and none blocks a developer. **READY.**

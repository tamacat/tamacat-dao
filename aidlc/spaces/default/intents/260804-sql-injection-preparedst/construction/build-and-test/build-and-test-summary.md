# Build and Test Summary — SQL Injection Remediation (`sql-injection-preparedst`)

## Overall build status

**BUILD SUCCESS.** `mvn -o -B compile` and `mvn -o -B test` both succeed with zero code changes needed. All 5 units (U1 `bind-foundation`, U2 `select-path`, U3 `write-path`, U4 `dialects`, U5 `identifier-safety`) are integrated in a single working tree with no unresolved conflicts.

## Prerequisites

JDK (build JVM: JDK 25 Corretto, compiling to Java 8 target), Apache Maven. No new dependencies, no environment variables, no local services. See `build-instructions.md`.

## Test type inventory

| Type | Generated | Rationale |
|---|---|---|
| Unit tests | ✅ `unit-test-instructions.md` | Standard strategy default |
| Integration tests | ✅ `integration-test-instructions.md` | Standard strategy default — cross-unit AC verification |
| Security tests | ✅ `security-test-instructions.md` | Beyond the Standard default — warranted because the intent itself is a security remediation; SM-1's AC-1 through AC-10b are inherently security acceptance criteria |
| Performance tests | ❌ Not generated | No NFR performance targets were established at any unit's `nfr-requirements` stage (NFR-7 explicitly scoped performance out) |

## Coverage expectations per unit

| Unit | New tests | Suite status |
|---|---|---|
| U1 `bind-foundation` | 94 (across 10 test classes) | 231/231 green at unit completion (later folded into the full 316) |
| U2 `select-path` | 29 (`SearchTest` +7, `QueryImplTest` +18, `DaoTest` +4) | 260/260 green |
| U4 `dialects` | 17 (`OracleSearchTest` +3, `OracleDaoTest` +10 new, `MySQLDaoTest` +1, `+3` migration-related) | 274/274 green |
| U5 `identifier-safety` | 15 (`IdentifierRulesTest` 9 new, `SortTest` +6) + `QueryImplTest` +6 | 295/295 green |
| U3 `write-path` | 21 (`QueryImplTest` +15, `DBAccessManagerTest` +3, `DaoTest` +3) | 316/316 green |

**Current full-suite result: 316 tests, 0 failures, 0 errors, 0 skipped.**

## Readiness assessment

| Dimension | Status | Notes |
|---|---|---|
| Build-ready | ✅ Yes | `mvn -o -B compile` succeeds offline |
| Test-ready | ✅ Yes | `mvn -o -B test` succeeds, 316/316 |
| AC-1 through AC-8, AC-3b, AC-10 | ✅ Judged and closed | Each by its owning unit's tests, independently confirmed by that unit's adversarial code review |
| AC-9 (dialect paging) | ⚠️ Partially judged | SQL-text generation and placeholder-count verified for all 4 paging states; real-database session behavior (`FOUND_ROWS()`, `for update` variants) unverified — no DB available in this environment |
| AC-10b (identifier safety) | ⚠️ Permanently partial by design | 2 of 3 Given positions covered (ORDER BY key, SELECT function name); table/column names are an accepted, documented scope boundary (R-5/R-20) — not a defect |
| AC-11 (no breaking changes) | ✅ Judged and closed at this stage | Consolidated audit performed in `integration-test-instructions.md`; every new member across all 5 units is additive |
| Deployment-ready | ⚠️ Conditional | Code and tests are ready. Recommend: (1) a CodeQL scan once this branch is in CodeQL's trigger scope (currently `master`-only), (2) the real-DB manual verification checklist in `security-test-instructions.md`, before treating this as production-ready |

## Known limitations / outstanding items (prioritized)

**High priority** (security-relevant, currently untested):
1. `compose`'s `hasUnboundPlaceholders()==true` branch (U4, both dialects) — the exact scenario `business-rules.md`'s "failure mode 2" was written to guard against has no test.
2. `FileDataDao`'s migrated write path has no dedicated test (only indirectly exercised).
3. Real-database verification of AC-9's session-scoped behaviors (`FOUND_ROWS()`, `for update` variants) — requires live MySQL/Oracle, out of reach in this environment.

**Medium priority** (defensive/regression value, not currently at risk):
4. SEC-13's cross-cutting sync test (U2) — 16 entry points into `Search`/`QueryImpl`'s state pairs, currently covered only indirectly by each entry point's own unit tests.
5. `MySQLDao.searchList`'s `useHitCount(true)` branch — implemented, never executed by any test.
6. `UserStatDao` — never migrated off the literal SQL fallback; migration safety/value undecided (thin test coverage to judge against).

**Low priority** (documented, accepted, or cosmetic):
7. `ValueRules.isNumeric`'s regex backtracking risk (R-6) — theoretical, no length guard added.
8. Literal-side `where` field pollution after calling `getUpdateSQL()` following `getUpdatePreparedSql()` on the same `Query` instance (U3) — pre-existing-shape, doesn't affect the bind path.
9. Schema-qualified table name residue in the FR-6.2 literal-version fix (U3) — bind version is correct by construction.
10. `Sort`'s dangling-comma-after-caught-exception behavior (U5) — pre-existing shape, `MIGRATION.md` advice should be softened.

See `integration-test-instructions.md`'s "Consolidated backlog" section for the full, categorized list with rationale for each item.

## What this stage verified vs. deferred

This stage (Build and Test, 3.6) is the terminal stage of the Construction phase for this intent (`next_stage: null`). It performed:
- A full build + test execution (not just instruction generation).
- The AC-11 consolidated cross-unit compatibility audit (previously self-certified per-unit, never aggregated).
- Consolidation of every unit's "送る検証項目" (handoff) list into one prioritized backlog.

It deliberately did **not** implement the ~10 backlog items above — Standard test strategy targets key-boundary coverage, and these are enumerated as next-step candidates (with rationale) rather than executed, since several require infrastructure (a live database) not available in this session and others are judgment calls for a follow-up scoping decision (e.g., whether to extend identifier-safety to table/column names).

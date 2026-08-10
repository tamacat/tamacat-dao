# Security Test Instructions — SQL Injection Remediation

Generated beyond the Standard-strategy default because the intent itself is a security remediation (`sql-injection-preparedst`) — SM-1 (bind-variable execution of runtime values) is the security goal the whole initiative exists to satisfy, and its acceptance criteria (AC-1 through AC-10b) are inherently security tests, not general functional tests.

## Threat model recap (STRIDE, consolidated across units)

The central concern across all 5 units is **Tampering (T)** — attacker-controlled input becoming SQL syntax rather than a bound value:
1. **Value positions** (WHERE predicates, INSERT VALUES, UPDATE SET) — addressed by U1–U3's bind-variable machinery.
2. **Identifier positions** (ORDER BY keys, SELECT function names) — addressed by U5's validate-and-reject mechanism (bind parameterization is impossible here; JDBC has no `?` for identifiers).
3. **Silent predicate loss** — a distinct failure mode where a WHERE clause silently drops a condition (widening the result set) rather than injecting syntax; the sync-point mechanisms (`Search.append`, `QueryImpl.appendWhere`) exist specifically to prevent this.

No Spoofing/Repudiation/Elevation-of-Privilege concerns apply (library has no identity or authorization model). Denial-of-Service is out of scope (NFR-7 — no performance targets set) except where a regex backtracking risk was flagged (see below).

## How to run

Security-relevant tests are not separated into a distinct Maven profile — they are the AC-1 through AC-10b tests already embedded in the unit suite, run via:

```bash
mvn -o -B test
```

The commands below isolate the security-critical subset for focused review:

```bash
mvn -o -B test -Dtest=BindSqlBuilderTest,ValueRulesTest,IdentifierRulesTest,SortTest#testNonColumnKeyRejects*
```

## Injection-class test coverage (what's verified today)

| Injection vector | Mechanism | Test evidence |
|---|---|---|
| Single-value predicate (`col = 'value'`) | Bind variable | U2 `QueryImplTest` AC-1 |
| LIKE pattern injection via `%`/`_` | Escape + bind variable | U1 `BindSqlBuilderTest`, U2 `QueryImplTest` AC-2 |
| Quote-based injection (`'; DROP TABLE...`) via a bound value | Bind variable (never string-concatenated) | U1 `BindSqlBuilderTest#testQuoteIsNotDoubledOnBindPath` — confirms the bind path carries the raw value without SQL-level escaping being necessary |
| IN-list injection | Bind variable per element, `(?,?,?)` | U2 `QueryImplTest` AC-3 |
| INSERT/UPDATE value injection | Bind variable, column-order preserved | U3 `QueryImplTest` AC-3b |
| Subquery-carried value injection | Child `PreparedSql`'s values spliced at the correct parent position | U2 `QueryImplTest` AC-4 |
| BLOB/binary value | Bind variable (never embedded as text) | U3 `QueryImplTest` AC-7 |
| ORDER BY key injection (non-`Column` raw string) | Reject on `'`/`"`/`;`/`--`/`/*`/`*/` | U5 `SortTest`, `IdentifierRulesTest` |
| SELECT function-name injection (`Column.getFunctionName()`) | Reject on same character set | U5 `QueryImplTest` |
| Paging-boundary injection (`start`/`max`) | Never string-concatenated as untrusted text — always `int`-typed at the Java level before any concatenation (MySQL LIMIT / Oracle rownum can't be bound as `?` due to driver-level integer-literal requirements, so the safety argument is type-level, not bind-level) | U4 `MySQLDaoTest`/`OracleDaoTest` |

## Injection-class gaps (explicitly out of scope, not silently missed)

| Vector | Why it's not covered | Disposition |
|---|---|---|
| Table name / column name injection | No owning unit — U5 (the identifier-safety unit) covered only ORDER BY keys and SELECT function names, not `MappingUtils.getColumnName`'s call sites (R-5/R-20, a documented scope decision, not a technical limitation) | **Accepted residual risk.** Would require wrapping `MappingUtils.getColumnName` call sites in `Sort.java`/`QueryImpl.java` with `IdentifierRules.validate` — technically straightforward (the same pattern U5 already used twice) but was deliberately not pursued within this intent's scope. Recorded for a future scoping decision, not a defect. |
| Raw SQL fragments via `where(String)`/`and(String)`/`or(String)`/`Dao#param(...)` | Deliberately out of SM-1's scope (FR-7.1) — these methods exist specifically so developers can supply hand-written SQL, and the string they pass is treated as developer-authored code, not runtime user input | **By design, not a gap.** `MIGRATION.md` §3 documents this explicitly. |
| `Query#andOuterJoin(Table, Search)` (deprecated) | Bind-side value is embedded as literal text (BR-12) — deliberately excluded from SM-1 since it's the deprecated form | **By design.** Callers should migrate to `andOuterJoin(Table, Param)`. |
| Regex backtracking DoS in `ValueRules.isNumeric` (`^\-?[0-9]*\.?[0-9]+$`) | Potential O(n²) worst case on adversarial input; flagged during U1's NFR review (R-6) | **Unresolved, low severity.** No length guard was added (would be a behavior change). Recommend revisiting if this library is ever exposed to untrusted-length input without an upstream size limit. |

## Static analysis (CodeQL)

- U1's `nfr-requirements` flagged that the repository's CodeQL workflow runs only on `master` push/PR + a weekly cron — **not** on the `v2.0` branch this work happened on (OQ-7, CON-8). CodeQL has not scanned this initiative's changes as part of this session.
- **Recommendation**: before merging `v2.0` back to `master` (or whenever this branch is included in CodeQL's trigger scope), run a CodeQL scan and specifically review any findings against:
  - The residual literal-concatenation paths (`SQLParser`'s literal system, `where(String)`/`and(String)`/`or(String)`) — these are intentional, pre-existing, and by-design (see gaps table above); CodeQL findings here should be triaged as accepted/suppressed, not treated as regressions.
  - `MySQLDao`'s LIMIT and `OracleDao`'s rownum boundary-value concatenation (`" limit " + (start-1) + "," + max"`) — these are `int`-typed at the Java level (type-safe), which CodeQL's taint analysis may or may not recognize as safe; manual triage may be needed (U4 nfr-requirements item 18).

## Manual verification checklist (cannot be automated in this test suite)

- [ ] Confirm a real MySQL connection's `FOUND_ROWS()` returns the correct count in the same session as the preceding `SQL_CALC_FOUND_ROWS` query (U4, requires live DB).
- [ ] Confirm Oracle's `for update nowait` / `for update of t.c` variants behave correctly (or fail loudly, not silently) when passed through an external `Query` implementation (U4, requires live DB — no code path in this repository currently generates these variants).
- [ ] Run a CodeQL scan against this branch once it's in scope (see above) and triage findings per the guidance in this file.

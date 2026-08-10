# Unit Test Instructions — SQL Injection Remediation

Test Strategy: **Standard** (`aidlc-state.md`). Framework: JUnit 4, no new test frameworks introduced. Mock stack: `org.tamacat.mock.sql` (`MockConnection`/`MockPreparedStatement`/`MockDriver`/`MockStatement`), extended by U1 to record bound position/value pairs — this is the load-bearing test double for every new class; no real JDBC driver is used in unit tests.

## How to run

```bash
mvn -o -B test
```

Filter to one unit's tests:

```bash
mvn -o -B test -Dtest=BindValueTest,ParamTest,PreparedSqlTest,ValueRulesTest,BindSqlBuilderTest,PreparedStatementBinderTest,MockPreparedStatementTest,MockConnectionTest,DBAccessManagerTest,SQLParserTest   # U1
mvn -o -B test -Dtest=SearchTest,QueryImplTest,DaoTest   # U2 (+ overlaps with U3/U5 additions to the same files)
mvn -o -B test -Dtest=MySQLDaoTest,OracleDaoTest,OracleSearchTest   # U4
mvn -o -B test -Dtest=IdentifierRulesTest,SortTest   # U5 (+ QueryImplTest overlap)
```

`QueryImplTest`, `DaoTest`, `DBAccessManagerTest` each carry contributions from multiple units (U2/U3/U5 all added to `QueryImplTest`; U2/U3 to `DaoTest`) — there is no clean per-unit file split for those three.

## Existing test suite status (unit-level, per current build)

| Component | Test class | Count | Contributing unit(s) |
|---|---|---|---|
| `BindValue` | `BindValueTest` | 8 | U1 |
| `Param` | `ParamTest` | 9 | U1 |
| `PreparedSql` | `PreparedSqlTest` | 7 | U1 |
| `ExecutedStatement` | `ExecutedStatementTest` | 4 | U1 |
| `LikeEscape` | `LikeEscapeTest` | 4 | U1 |
| `ValueRules` | `ValueRulesTest` | 15 | U1 |
| `BindSqlBuilder` | `BindSqlBuilderTest` | 19 | U1 |
| `MockPreparedStatement` | `MockPreparedStatementTest` | 7 | U1 |
| `MockConnection` | `MockConnectionTest` | 5 | U1 |
| `PreparedStatementBinder` | `PreparedStatementBinderTest` | 7 | U1 |
| `SQLParser` (unchanged, delegation gate) | `SQLParserTest` | 10 | U1 (0 modified) |
| `IdentifierRules` | `IdentifierRulesTest` | 9 | U5 |
| `Search` | `SearchTest` | 8 | U2 (0 pre-existing modified) |
| `QueryImpl` | `QueryImplTest` | 40+15 U3 additions | U2, U3, U5 (0 pre-existing modified per unit) |
| `Dao` | `DaoTest` | 6+3 U3 additions | U2, U3 |
| `DBAccessManager` | `DBAccessManagerTest` | 12 | U1, U3 |
| `MySQLDao` | `MySQLDaoTest` | 7 | U4 (0 pre-existing modified) |
| `OracleDao` | `OracleDaoTest` (new) | 10 | U4 |
| `OracleSearch` | `OracleSearchTest` | 4 | U4 |
| `Sort` | `SortTest` | 8 | U5 (0 pre-existing modified) |

**Total (current `mvn test` run): 316 tests, 0 failures, 0 errors, 0 skipped.**

## Coverage expectations (Standard strategy)

5–8 tests per new component was the floor; several components exceeded it because the design's own decision tables (e.g. `ValueRules` §2.7's branches, `BindSqlBuilder`'s 3 preserved defects, the paging-window state table) each warranted a dedicated assertion for BR-n traceability — this is expected and correct, not scope creep.

## Test data management

No external test data files or fixtures. All test data is constructed inline (`Columns.create(...)`, `DefaultTable`, in-memory `User`/`FileData` domain objects already present in `src/test/java/org/tamacat/dao/test/`). No database is required — the entire suite runs against the mock JDBC stack.

## Known gaps to close at this stage (see `integration-test-instructions.md` and `security-test-instructions.md` for the concrete additions)

- AC-9 (MySQL/Oracle paging correctness) is unit-tested for SQL-text generation (`OracleDaoTest`'s W-1..W-4) but not against a real database.
- AC-10b (identifier-position rejection) is unit-tested for 2 of 3 Given positions (ORDER BY key, `getFunctionName()`) — table/column names themselves have no owning unit's tests (R-5/R-20, permanently deferred, see summary).
- Several `hasUnboundPlaceholders()==true` / `useHitCount(true)` branches in the dialect `compose` helpers are implemented but never exercised by any existing test (U4 code-review N-1/N-2).

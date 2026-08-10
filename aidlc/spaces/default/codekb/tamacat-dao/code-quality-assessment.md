# Code Quality Assessment — tamacat-dao

> Reverse-engineered from commit `e51c32739506565c572fed458c1fbba51bbab846`, branch
> `v2.0`. This is an **observation record**, not a remediation plan. Every item below
> states what is in the code today; none of it prescribes a fix.

## Verified Build Baseline

| Check | Result |
|---|---|
| `mvn test` | `Tests run: 138, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` |
| Offline compile | succeeds |
| Offline `mvn test` | fails only because `com.mysql:mysql-connector-j:9.6.0` is not in the local repository |
| Working tree | uncommitted `pom.xml` edit (version `1.6.1` to `2.0`, slf4j `2.0.17` to `2.0.18`); untracked `.claude/` and `aidlc/` |

## Test Coverage

### What exists

- 48 files under `src/test/java`; 3 resource files under `src/test/resources`
  (`db.xml`, `logback.xml`, `tomcat_datasource.txt`).
- 138 tests execute and pass.
- The SQL-generation contract is densely pinned. Tests that assert on generated SQL
  strings and **do execute**:

| File | Lines | What is pinned |
|---|---|---|
| `src/test/java/org/tamacat/sql/SQLParserTest.java` | 50-140 | The complete literal-formatting contract: quoting (51-62), numeric validation plus 3 rejection cases (65-90), date (93-97), time (100-104), BETWEEN (107-110), LIKE escape behaviour including escape-char selection (113-122), IN for string and numeric (125-140), `isNumeric` regex (143-157). **Highest-density SQL-string test in the repo** |
| `src/test/java/org/tamacat/dao/SearchTest.java` | 43-103 | `Search` composition output: backslash escaping (47-48), `and` chaining (52-58), IN single/multi across string/numeric (60-77), MySQL `regexp` / `rlike` (79-89), PostgreSQL `~` (91-95), EQUAL (97-103) |
| `src/test/java/org/tamacat/dao/impl/QueryImplTest.java` | 64-169 | SELECT explicit columns (64-70), all columns (72-79), DISTINCT x3 (81-108), INSERT (110-121), UPDATE (123-135), DELETE plus exception path (137-153), inner-join WHERE fragment (155-159), `getTimestampString` (161-164), `getColumnName` (166-169) |
| `src/test/java/org/tamacat/dao/impl/MySQLDaoTest.java` | 31-86 | MySQL filter wiring (43-52), backslash param escaping (31-34), INSERT with `'` and `\'` and `"` payloads (54-72), UPDATE (74-86) |
| `src/test/java/org/tamacat/dao/test/UserDaoTest.java` | 34-102 | End-to-end through the mock driver, asserting on `dao.getExecutedQuery().get(0)`: INSERT (41-44), SELECT plus WHERE (54-58), SELECT plus LIKE_PART (67-71), UPDATE (85-88), DELETE (97-100) |
| `src/test/java/org/tamacat/dao/test/TestDaoTest.java` | 18 | SELECT column list |
| `src/test/java/org/tamacat/dao/SortTest.java` | — | ORDER BY fragment output |
| `src/test/java/org/tamacat/dao/ConditionTest.java` | 22 | Condition enum operator strings |
| `src/test/java/org/tamacat/dao/DaoTest.java` | 39-41, 59-61 | Raw SQL through `executeQuery` / `executeUpdate` inside a transaction |
| `src/test/java/org/tamacat/sql/DBAccessManagerTest.java` | 34 | `executeQuery("select * from users")` |
| `MySQLSearchTest.java`, `OracleSearchTest.java` | 1 test each | Dialect filter behaviour |

### Coverage gaps and measurement

- **No coverage tooling at all.** No JaCoCo, no Cobertura, no coverage plugin in
  `pom.xml`, no thresholds. The 138-test figure is a count, not a coverage measurement;
  the actual line/branch coverage of this repository is **unmeasured**.
- **Three test classes compile but never execute** because surefire includes only
  `**/*Test.java` and their filenames end in `Test02` / `Test03` / `Test2`:
  - `org/tamacat/dao/impl/QueryImplTest02.java` (assertions at 27-38) — the **only** test
    asserting the BLOB `?` placeholder in INSERT
    (`INSERT INTO file (file_id,data) VALUES ('123',?)` at 35-37)
  - `org/tamacat/dao/impl/QueryImplTest03.java` (28-43) — function-column and GROUP BY
    SELECT assertions
  - `org/tamacat/dao/test/UserDaoTest2.java` — the Derby (`javadb`) integration path
- **Four files are `main()` harnesses, not JUnit at all**: `dao/test/User_test.java`,
  `pool/impl/Pooling_test.java`, `sql/MultiThreadJDBC_test.java`,
  `sql/TransactionStateManager_test.java`. Nothing in the build runs them.
- **No executing test validates generated SQL against a real database engine.** The
  entire executing suite runs against `MockDriver` / `MockStatement`, which log SQL and
  return canned results without parsing it.

### Specific behaviours with no test coverage

| Behaviour | Location |
|---|---|
| `OracleDao.searchListForOracle` in any form | `OracleDao.java:30-73` |
| A LIKE value containing all five escape candidates `$ # ~ ! ^` | `SQLParser.java:124-137` |
| Passing a value alongside `Condition.IS_NULL` / `NOT_NULL` (would NPE at `SQLParser.java:57`) | `Condition.java:21-22` |
| `Query.getBlobIndex()` semantics | `QueryImpl.java:469-471` |
| The JNDI `DataSourceJdbcConfig` dialect path with a null driver class | `DataSourceJdbcConfig.java:92-94` into `DaoAdapter.java:86` |
| Bound parameter values on any prepared statement | see the mock limitation below |

### Test-infrastructure limitation: the mock captures nothing

`MockPreparedStatement` **records nothing**:

- All `setXxx(...)` methods have **empty bodies**.
- `executeUpdate()` returns `0` (`MockPreparedStatement.java:41-44`) rather than a row
  count.
- The three `setBinaryStream` overloads (lines 119, 274, 292) do nothing.
- `MockConnection.prepareStatement(String sql)` (`MockConnection.java:168-170`)
  **discards the `sql` argument** and returns a bare `new MockPreparedStatement(this)`.

Consequence, recorded as a fact: **assertions on bound parameters or on prepared SQL text
are impossible without extending the mock stack.** The de-facto assertion channel used by
the existing end-to-end tests is `DBAccessManager.getExecutedQuery()`
(`DBAccessManager.java:181-188`), a `ThreadLocal<List<String>>` populated at `:90`
(prepared), `:99` (query), and `:109` (update). That list records **only SQL text**; there
is no companion structure for parameter values, and the prepared entry at `:90` is added
before any parameter is set.

`MockStatement` logs SQL via `LOG.info` (lines 40-80) rather than recording it assertably,
and its `executeUpdate` returns `1` (71-74) — inconsistent with `MockPreparedStatement`'s
`0`.

## Framework Consistency

Two test files still extend JUnit 3 `junit.framework.TestCase` — `SearchTest.java:19` and
`SQLParserTest.java:19`. In those files `@Before protected void setUp()` works only
because of the JUnit 3 lifecycle, not because of the annotation. These are the two
highest-value SQL-contract test files in the repository.

## Linting and Static Analysis

| Tool | Present |
|---|---|
| Checkstyle | no |
| SpotBugs | no |
| PMD | no |
| ErrorProne | no |
| `.editorconfig` | no |
| Formatter configuration | no |

Formatting is mixed as a result: tabs in `org.tamacat.dao` and `org.tamacat.sql`;
4-space indentation in `OracleDao`, `OracleSearch`, `Sort`, `Transaction`,
`MockDataSourceRegister`, and several test classes.

## CI/CD

| Aspect | State |
|---|---|
| Workflows | exactly one: `.github/workflows/codeql-analysis.yml` |
| What it does | CodeQL analysis for `java` |
| Triggers | push and PR **to `master` only**, plus a weekly cron |
| Effect on this branch | the current branch is `v2.0`, so **CI does not run on this branch's pushes** |
| Build/test workflow | **none** — there is no `mvn test` anywhere in CI |
| Action versions | `actions/checkout@v2` and `github/codeql-action/*@v1`, both long-deprecated |

## Documentation

| Artifact | Present |
|---|---|
| README | **no** |
| LICENSE file | **no** — the license is declared only in `pom.xml` |
| CONTRIBUTING | no |
| `docs/` directory | no |
| Javadoc plugin | not configured |
| Javadoc comments | present but sparse, often only `@since` tags. `Query.java` and the `pool` SPIs are the best-documented |

## Technical Debt Register

Recorded as observed. Numbering follows the code scan.

| # | Item | Location | Observation |
|---|---|---|---|
| 1 | **SQL built by string concatenation end-to-end** | `SQLParser.java:39-117`, `QueryImpl.java:136-309`, `DBAccessManager.java:100`, `:109` | Every caller-supplied value is rendered into the SQL text as a literal and executed through `java.sql.Statement`. This is the **central design fact** of the codebase, not an incidental defect. The only bind path is BLOB via `BlobUtils.java:18`. See `architecture.md` |
| 2 | Open version range | `pom.xml:39` — `junit:junit` `[4.13.2,)` | The build is not reproducible; the resolved JUnit version can change without a source change |
| 3 | Manifest version drift | `src/main/resources/META-INF/MANIFEST.MF` vs `pom.xml` | Manifest says `1.6.1` / `1.6.1-20260324`; pom says `2.0`. The jar plugin bakes the stale manifest into the artifact |
| 4 | Three test classes never run | `QueryImplTest02.java`, `QueryImplTest03.java`, `UserDaoTest2.java` | Surefire includes only `**/*Test.java`; these filenames end in `Test02` / `Test03` / `Test2`. This silently excludes the sole BLOB-placeholder assertion |
| 5 | **`OracleDao.searchListForOracle` is broken and dead** | `OracleDao.java:30-73` | Not an override of `Dao.searchList` (different name), never called anywhere. Carries a `TODO: bugfix` Javadoc at 27-29 and commented-out blocks at 54-56 and 64-68. Builds SQL containing `?` placeholders (`rownum_ <= ? and rownum_ > ?` at line 44, `rownum <= ?` at line 46) which are executed through `Dao.executeQuery(sql)` into plain `Statement.executeQuery` (`DBAccessManager.java:100`) at `OracleDao.java:51`. **The placeholders are never bound**, so this would fail at runtime against a real Oracle instance. Also, `max` is unused in the rownum construction — only `start` gates the wrapping. No test covers it |
| 6 | **Null-guard asymmetry across dialect filters** | `OracleSearch.java:12-14` | `OracleValueConvertFilter.convertValue` calls `value.replace("'", "''")` with no null guard, so it NPEs on a null value. `Search.DefaultValueConvertFilter` (`Search.java:100-106`) and `MySQLValueConvertFilter` (`MySQLSearch.java:13-19`) are both null-safe. The three implementations of the same one-method SPI therefore disagree on null handling |
| 7 | **`MockPreparedStatement` records nothing** | `MockPreparedStatement.java:41-44`, `MockConnection.java:168-170` | All `setXxx` are empty bodies; `executeUpdate()` returns `0`; `prepareStatement(String sql)` discards the `sql` argument. Assertions on bound parameters or on prepared SQL text are impossible without extending the mock stack |
| 8 | Static state mutated from an instance method | `DBAccessManager.java:175` | `release()` calls `MANAGER.remove(name)` while holding only the instance lock; `getInstance` is `synchronized` on the class but `release` on the instance. `executedQuery`, `running`, `con`, and `stmt` are all `ThreadLocal` fields on a shared singleton |
| 9 | Unguarded unboxing | `TransactionStateManager.java:68-71` | `isNotCommited()` unboxes `this.state.get()` without a null check — NPE if called on a thread that never began a transaction |
| 10 | Empty / silent catch blocks | `DBUtils.java:22-23`, `:29-30`, `:35-36`; `ConnectionManager.ConnectionFactory.destroy` `:100-101`; `DefaultTable.registerColumn` `:113-115` (catches `Exception` and calls `e.printStackTrace()`) | Failures at these points are neither surfaced to the caller nor logged through the `Log` abstraction |
| 11 | `e.printStackTrace()` instead of logging | `LoggingDaoExecuterHandler.java:29`, `DefaultTable.java:114`, `MockDriver.java:25`, `MockDataSourceRegister.java:32` | Bypasses `tamacat-core`'s `Log` / `LogFactory` |
| 12 | Full row-scan pagination | `Dao.java:177-197`, offset loop at `:181-184` | `searchList(query, start, max)` implements the offset by calling `rs.next()` in a loop, for every dialect except MySQL |
| 13 | **Mock JDBC classes ship in the production artifact** | `src/main/java/org/tamacat/mock/sql/` | Includes `MockDriver`, whose static initializer auto-registers with `DriverManager` (`:21-27`) and whose `acceptsURL` returns `true` for **every** URL (`:37-39`) |
| 14 | Over-broad driver deregistration | `ConnectionManager.java:50-58` | `closeAll()` deregisters **every** driver in the JVM, not just its own |
| 15 | Unused dependency and stub-only fixture | `pom.xml`; `TestDao.java` | EasyMock 5.1.0 is declared at test scope and referenced nowhere. `TestDao.java` is 88 lines of `// TODO Auto-generated method stub` |
| 16 | Deprecated-but-present API | `Query.java:166-172` (`setUseAutoPrimaryKeyUpdate`), `MappingUtils.java:99-102` (`parse`), `MapBasedORMappingBean.java:181-185` (`BigDecimal.ROUND_HALF_UP`) | Deprecated members retained in the public surface |
| 17 | Uncommitted version bump in the working tree | `pom.xml` on branch `v2.0` | `git status` shows the edit is not committed |

### Additional correctness observations recorded during the spine analysis

| Item | Location | Observation |
|---|---|---|
| `replaceFirst` used with an unescaped regex metacharacter | `QueryImpl.java:257`, `:263` | `.replaceFirst(tableName + ".", "")` treats its argument as a **regex**; the `.` is an any-char metacharacter, not a literal dot |
| Table-prefix stripping omitted on the OBJECT branch | `QueryImpl.java:268` | The BLOB branch omits the `.replaceFirst` applied at 257 and 263, so a BLOB column emits `file.data=?` (table-qualified) inside a SET list where every other column is unqualified |
| `QueryImpl` is single-use | `QueryImpl.java:48`, `:442-453` | The `where` `StringBuilder` is never reset; calling `getUpdateSQL` twice appends the primary-key predicate twice |
| Hidden builder side effect | `QueryImpl.java:450` | Any `addWhere` call sets `useAutoPrimaryKeyUpdate = false`, silently changing what `getUpdateSQL` / `getDeleteSQL` emit |
| Exact-string routing in the parser | `SQLParser.java:51` | IN routing uses `condition.getCondition().equals(" in ")` — a custom `Conditions` implementation with different spacing bypasses the IN path entirely |
| LIKE escaping is type-gated | `SQLParser.java:48-50` | A `LIKE_*` condition on a NUMERIC / DATE / TIME column falls through to the generic branch at line 57 and gets no wildcard escaping |
| LIKE escape-candidate exhaustion | `SQLParser.java:124-137` | If the value contains all five candidates `$ # ~ ! ^`, the loop completes without returning and control falls to line 138: `%` and `_` are emitted **unescaped** as active wildcards with no `escape` clause. No test covers this |
| `//TODO` on the timestamp special case | `SQLParser.java:107` | The exact literal `current_timestamp` is emitted unquoted via a string comparison, marked `//TODO` in the source |
| Dialect asymmetry on `createQuery` | `OracleDao` | `OracleDao` overrides `createSearch` (22-25) but not `createQuery`, so its INSERT/UPDATE literals use the **default** filter while its WHERE predicates use the Oracle one |
| Null driver class on the JNDI path | `DataSourceJdbcConfig.java:92-94` into `DaoAdapter.java:86` | `getDriverClass()` returns `null`; the dialect check dereferences it. No guard and no covering test were found |
| Existing manual injection probe | `src/test/java/org/tamacat/dao/test/User_test.java:14` | Builds `search.and(User.AGE, Condition.EQUAL, "';select * from dual --'")` against a NUMERIC column. Never run by the build. With current code this input is rejected by `SQLParser.isNumeric` and throws `InvalidParameterException` |

## Strengths Observed

Recorded for balance, from the same evidence:

- The SQL-generation contract is **densely and specifically pinned** by
  `SQLParserTest`, `SearchTest`, `QueryImplTest`, `MySQLDaoTest`, and `UserDaoTest` —
  quoting, numeric rejection, BETWEEN, LIKE escape-character selection, and IN arity are
  all asserted at the string level. Any change to SQL text is loudly detected.
- The value-rendering logic is funnelled through **two methods in one class**
  (`SQLParser.parseValue` and `SQLParser.value`), and execution through **one class**
  (`DBAccessManager`). The system has narrow choke points rather than diffuse SQL
  construction.
- NUMERIC and FLOAT columns are **regex-validated and rejected** on non-numeric input
  (`SQLParser.java:97-101`), which is why the existing injection probe in
  `User_test.java:14` fails closed.
- Dependency reporting (`versions-maven-plugin`) and SBOM generation (CycloneDX) are
  wired into the build, and a source jar is attached on every build.
- The library has **no compile-scope framework dependencies** beyond `tamacat-core` and
  JSON-P, keeping the consumer's classpath obligations small.

## Stated Unknowns

Carried forward from the code scan; **not** resolved by inference:

- **The contract of `Query.getBlobIndex()`** — 1-based JDBC parameter position or a count
  of OBJECT columns — is **not determined**. No caller exists in this repository, no
  Javadoc documents it, no executing test asserts it, and external tamacat projects are
  outside this repository.
- **`org.tamacat:tamacat-core:1.5` internals** were read only at usage sites, not from
  source. `StringUtils.isEmpty` / `isNotEmpty` / `parse`, `UniqueCodeGenerator.generate()`,
  `DI.configure`, and `ClassUtils.newInstance` behaviour is taken from call context.
- **Whether any dialect other than MySQL / Oracle / generic is used in production** is
  **not determined**. `PostgreSQLCondition` exists with no matching `Dao` / `Search`, and
  `DaoAdapter.setDatabase` has no `postgres` branch.
- **Whether `DataSourceJdbcConfig` (JNDI) is exercised anywhere** is **not determined**.
  `DataSourceConnectionManagerTest` runs 1 test; the scan did not trace whether the
  null-driver-class path is reachable within it.
- **The intended fix for the `TODO: bugfix` on `OracleDao.searchListForOracle`** is **not
  determined** — the comment carries no detail and there is no issue reference in the
  repository.
- **Real-database behaviour of any generated SQL** is **not determined**. The entire
  executing suite runs against `MockDriver` / `MockStatement`, which log SQL and return
  canned results without parsing it. No executing test validates that generated SQL is
  accepted by a real engine.

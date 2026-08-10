# Code Structure — tamacat-dao

> Reverse-engineered from commit `e51c32739506565c572fed458c1fbba51bbab846`, branch
> `v2.0`. Describes the organisation **as it is**.

## Repository Layout

```
C:\git\tamacat\develop\tamacat-dao\
  pom.xml                                  Maven build, org.tamacat:tamacat-dao:2.0 (uncommitted edit)
  .github/workflows/codeql-analysis.yml    the only CI workflow
  src/main/java/                           production sources (includes the mock JDBC stack)
    org/tamacat/dao/                       public API
      impl/                                dialect + query implementation, event handlers
      meta/                                metadata model
      orm/                                 O/R mapping
      util/                                helpers
      event/                               event SPI
      exception/                           exception types
      validation/                          validation SPI
      tx/                                  transaction facade
    org/tamacat/sql/                       JDBC layer
    org/tamacat/pool/                      generic object pool
      impl/                                StackObjectPool
    org/tamacat/mock/sql/                  JDBC test doubles — SHIPPED IN THE MAIN JAR
  src/main/resources/META-INF/MANIFEST.MF  OSGi headers, Implementation-Version 1.6.1-20260324
  src/test/java/                           48 test files
  src/test/resources/                      db.xml, logback.xml, tomcat_datasource.txt
```

There is **exactly one Maven module**. The "packages" below are Java packages, not modules.

## Package Organisation

| Package | Type | Purpose |
|---|---|---|
| `org.tamacat.dao` | public API | Core DAO facade: `Dao`, `DaoAdapter`, `DaoFactory`, `Query` (interface), `Search`, `Sort`, `Condition` |
| `org.tamacat.dao.impl` | implementation | `QueryImpl` (SQL builder), dialect subclasses `MySQLDao` / `OracleDao` / `MySQLSearch` / `OracleSearch`, dialect condition enums `MySQLCondition` / `PostgreSQLCondition`, event handlers (`Logging*`, `None*`), `DaoEventImpl` |
| `org.tamacat.dao.meta` | metadata model | `Column` / `DefaultColumn`, `Table` / `DefaultTable`, factories `Columns` / `Tables`, `DataType` enum, package-private `ColumnDefine` |
| `org.tamacat.dao.orm` | O/R mapping | `ORMapper` (ResultSet to bean), `ORMappingSupport` (interface), `MapBasedORMappingBean` (LinkedHashMap-backed bean) |
| `org.tamacat.dao.util` | helpers | `MappingUtils` (type mapping + `getColumnName`), `BlobUtils` (binary stream bind), `JSONUtils` (javax.json serialize/deserialize) |
| `org.tamacat.dao.event` | SPI | `DaoEvent`, `DaoExecuteHandler`, `DaoTransactionHandler` extension points |
| `org.tamacat.dao.exception` | errors | `DaoException` (RuntimeException), `InvalidParameterException` (IllegalArgumentException), `InvalidValueLengthException` |
| `org.tamacat.dao.validation` | SPI | `Validator` single-method interface |
| `org.tamacat.dao.tx` | transactions | `Transaction` — multi-datasource begin / commit / rollback / release |
| `org.tamacat.sql` | JDBC layer | `SQLParser` (value to SQL literal), `DBAccessManager` (ThreadLocal conn/stmt), `ConnectionManager` (pool), `JdbcConfig` + `DriverManagerJdbcConfig` + `DataSourceJdbcConfig`, `DriverManagerDataSource`, `TransactionStateManager`, `DBUtils`, `ResourceManager`, `LifecycleSupport`, `IllegalTransactionStateException` |
| `org.tamacat.pool` / `.impl` | generic pool | `ObjectPool`, `PoolableObject`, `PoolableObjectFactory`, `StackObjectPool`, `PoolLimitException`, `ObjectActivateException` |
| `org.tamacat.mock.sql` | test doubles **in main** | `MockDriver`, `MockConnection`, `MockStatement`, `MockPreparedStatement`, `MockResultSet`, `MockDataSourceImpl`, `MockDataSourceRegister` |

Two root namespaces coexist: `org.tamacat.dao.*` (the DAO product) and `org.tamacat.sql`
/ `org.tamacat.pool` / `org.tamacat.mock.sql` (infrastructure the DAO sits on). The `sql`
and `pool` namespaces have no compile dependency on `org.tamacat.dao`; the dependency runs
one way, `dao` to `sql` to `pool`.

## File Classification

### Files that build SQL text

| File | Role |
|---|---|
| `org/tamacat/sql/SQLParser.java` | The two value-interpolation primitives: `parseValue` (85-117) and `value` (39-73), plus `parseLikeStringValue` (119-139) and `parseMultiValue` (75-83) |
| `org/tamacat/dao/impl/QueryImpl.java` | Statement assembly: `getSelectSQL` (136-182), `getInsertSQL` (185-219), `getUpdateSQL` (236-274), `getDeleteSQL` (277-302), `getDeleteAllSQL` (305-309), `addWhere` (442-453) |
| `org/tamacat/dao/Search.java` | WHERE-predicate accumulation into a `StringBuilder` (`:23`), exposed by `getSearchString` (88-90) |
| `org/tamacat/dao/Sort.java` | ORDER BY accumulation; raw path at `Sort.java:47-60` |
| `org/tamacat/dao/Condition.java` | Operator strings and value templates (11-24) |
| `org/tamacat/dao/impl/MySQLCondition.java`, `PostgreSQLCondition.java` | Dialect-specific operators |
| `org/tamacat/dao/impl/MySQLDao.java` | LIMIT concatenation (`:46`), `SQL_CALC_FOUND_ROWS` rewrite (`:44`), `SELECT FOUND_ROWS()` (`:64-70`) |
| `org/tamacat/dao/impl/OracleDao.java` | `searchListForOracle` rownum wrapper (30-73) — dead code, see quality assessment |

### Files that execute SQL

| File | Role |
|---|---|
| `org/tamacat/sql/DBAccessManager.java` | The single execution funnel: `executeQuery` (97-104), `executeUpdate` (106-113), `preparedStatement` (88-95), `getStatement` (65-78), `createStatement` (80-86), `getExecutedQuery` (181-188) |
| `org/tamacat/dao/Dao.java` | `executeQuery(String)` (249-255), `executeUpdate(String)` (257-264), `executeUpdate(String,int,InputStream)` (266-274) |
| `org/tamacat/dao/util/BlobUtils.java` | The only bind-parameter call site: `setBinaryStream` (`:18`), `executeUpdate` (`:19`) |
| `org/tamacat/sql/DriverManagerJdbcConfig.java`, `DataSourceJdbcConfig.java` | Connection-activation probe SQL (54-55 / 50-51) |

### Files that hold configuration

| File | Role |
|---|---|
| `pom.xml` | Build, dependencies, plugins, repositories |
| `src/main/resources/META-INF/MANIFEST.MF` | OSGi headers; `Implementation-Version: 1.6.1-20260324`, `Bundle-Version: 1.6.1` |
| `src/test/resources/db.xml` | DI wiring for `JdbcConfig` beans: `default`, `javadb`, `ds`, `db1`, `db2` |
| `src/test/resources/logback.xml` | Test logging |
| `src/test/resources/tomcat_datasource.txt` | Reference snippet for container datasource setup |
| `.github/workflows/codeql-analysis.yml` | CodeQL scan on `master` only, plus a weekly cron |

### Test files

48 files under `src/test/java`. Three shapes coexist:

1. **JUnit 4 tests** — the majority, using `@Test` / `@Before`.
2. **JUnit 3 tests** — `SearchTest.java:19` and `SQLParserTest.java:19` still extend
   `junit.framework.TestCase`; their `@Before protected void setUp()` works only because
   of the JUnit 3 lifecycle.
3. **`main()` harnesses, not JUnit at all** — `dao/test/User_test.java`,
   `pool/impl/Pooling_test.java`, `sql/MultiThreadJDBC_test.java`,
   `sql/TransactionStateManager_test.java`.

Surefire includes only `**/*Test.java`, so `QueryImplTest02.java`, `QueryImplTest03.java`,
and `UserDaoTest2.java` compile but never execute.

## Recurring Code Patterns

### 1. Template SQL with `${...}` token replacement

`QueryImpl` holds string templates (`QueryImpl.java:36-37` for INSERT and UPDATE) and
fills them with `String.replace`:

```
INSERT INTO ${TABLE} (${COLUMNS}) VALUES (${VALUES})     QueryImpl:36, filled at 216-217
UPDATE ${TABLE} SET ${VALUES}                            QueryImpl:37, filled at 272-273
DELETE template with ${TABLE}                            filled at 300-301
```

### 2. Condition template tokens `#{...}`

`Conditions` enum constants carry a `replaceHolder` (`Condition.java:11-24`) containing
`#{value1}`, `#{value2}`, or `#{values}` (`SQLParser.java:21-24`). The parser substitutes
the caller's value into the holder, then renders the result as a literal.

### 3. `StringBuilder` / `StringBuffer` accumulation with no reset

`Search.search` (`Search.java:23`), `QueryImpl.where` (`QueryImpl.java:48`),
`QueryImpl.groupBy`, `QueryImpl.orderBy`, and `SQLParser.value`'s local `StringBuffer`
(`SQLParser.java:41`) all accumulate by append. `QueryImpl.where` is never reset, making a
`QueryImpl` single-use.

### 4. Regex-as-string-surgery

Two sites use regex methods where a literal was intended:

- `QueryImpl.java:257` and `:263` — `.replaceFirst(tableName + ".", "")` strips the table
  prefix from a rendered predicate (turning `users.password='x'` into `password='x'`).
  The argument is a **regex**, so the `.` is an unescaped any-char metacharacter.
  The OBJECT branch at `:268` **omits** this call, so a BLOB column emits
  `file.data=?` (table-qualified) inside a SET list where every other column is
  unqualified.
- `MySQLDao.java:44` — `replaceFirst("SELECT ", "SELECT SQL_CALC_FOUND_ROWS ")`.

### 5. Flag-carrying builder side effects

`addWhere` (`QueryImpl.java:442-453`) has the side effect of setting
`useAutoPrimaryKeyUpdate = false` at line 450, which changes what `getUpdateSQL` and
`getDeleteSQL` later emit. The flag is not exposed in the builder's method names.

### 6. Dialect specialization by subclass + injected filter

Each dialect subclass overrides a small, fixed set of hooks. `MySQLDao` overrides the
parser field (23-25), `createSearch` (28-30), `createQuery` (34-36), and pagination.
`OracleDao` overrides `createSearch` (22-25) only — it does **not** override
`createQuery`, so its INSERT/UPDATE literals use the default filter. This asymmetry is a
structural fact of the code, not an accident of reading.

### 7. ThreadLocal-on-singleton session state

`DBAccessManager` keeps `executedQuery`, `running`, `con`, and `stmt` as `ThreadLocal`
fields on an instance that is itself shared via a static `MANAGER` map. `getInstance` is
class-synchronized; `release` is instance-synchronized yet calls `MANAGER.remove(name)`
at `:175`.

### 8. Hand-rolled JDBC test doubles instead of a mocking library

EasyMock is declared at test scope but referenced nowhere. All mocking is the
`org.tamacat.mock.sql` stack, and the de-facto SQL assertion channel is
`DBAccessManager.getExecutedQuery()` rather than the mock objects.

### 9. Mixed formatting conventions

Tabs in `org.tamacat.dao` and `org.tamacat.sql`; 4-space indentation in `OracleDao`,
`OracleSearch`, `Sort`, `Transaction`, `MockDataSourceRegister`, and several test classes.
No `.editorconfig`, no formatter configuration.

## Naming Conventions Observed

- Interfaces are unadorned nouns (`Query`, `Column`, `Table`, `ObjectPool`,
  `ORMappingSupport`); implementations carry `Impl` or `Default` (`QueryImpl`,
  `DefaultColumn`, `DefaultTable`, `MockDataSourceImpl`, `StackObjectPool`).
- Dialect classes are prefixed with the vendor (`MySQLDao`, `OracleSearch`,
  `PostgreSQLCondition`).
- Static factories are the plural of the type they build (`Columns` builds `Column`,
  `Tables` builds `Table`).
- Test classes end in `Test`; the `_test` suffix marks a `main()` harness rather than a
  JUnit class.

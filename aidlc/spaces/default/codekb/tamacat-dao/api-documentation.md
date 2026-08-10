# API Documentation — tamacat-dao

> Reverse-engineered from commit `e51c32739506565c572fed458c1fbba51bbab846`, branch
> `v2.0`. Describes the API surface **as it is**.

## Scope of "API" for This Artifact

`tamacat-dao` is a **Java library**. It exposes **no HTTP endpoints, no RPC surface, no
message contracts, and no network protocol**. Its API is the Java type surface published
by the jar plus the XML configuration schema it consumes. This document covers:

1. The public consumption surface (classes an application calls or extends).
2. The SPI / extension points (interfaces an application implements).
3. The configuration surface (`db.xml` bean properties).

## Public Consumption Surface

| API type | Location | Surface |
|---|---|---|
| Primary DAO facade | `org/tamacat/dao/DaoAdapter.java` | ~30 public methods: `create` / `update` / `delete` / `search` / `searchList`, `createQuery` / `createSearch` / `createSort`, `param(...)`, transaction control, `AutoCloseable` |
| DAO base class | `org/tamacat/dao/Dao.java` | Public: `setDatabase`, `setORMapper`, `search`, `searchList`, `param`, `getExecutedQuery`, `getDBAccessManager`, `handleException`. Protected: `executeQuery(String)`, `executeUpdate(String)`, `executeUpdate(String,int,InputStream)`, `getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` |
| Query builder contract | `org/tamacat/dao/Query.java` | 30 methods (listed below) |
| Condition builder | `org/tamacat/dao/Search.java` | `and` / `or(Column, Conditions, String...)`, `and` / `or(Search)`, `unique`, `start` / `max`, `getSearchString`; nested `Conditions` and `ValueConvertFilter` SPIs plus `DefaultValueConvertFilter` |
| Sort builder | `org/tamacat/dao/Sort.java` | ORDER BY accumulation; `sort(Object k, Object o)` at `:47-60` |
| SQL literal formatter | `org/tamacat/sql/SQLParser.java` | Public `value(Column, Conditions, String...)`, `parseValue(Column, String)`; protected `parseLikeStringValue`, `isNumeric`; package-private `parseMultiValue` |
| JDBC session | `org/tamacat/sql/DBAccessManager.java` | `getInstance(String)`, `createStatement`, `preparedStatement(String)`, `executeQuery(String)`, `executeUpdate(String)`, `commit` / `rollback` / `release`, `getExecutedQuery`, `setAutoCommit`, static `shutdown` |
| Transaction facade | `org/tamacat/dao/tx/Transaction.java` | Multi-datasource `begin` / `commit` / `rollback` / `release` |
| Metadata factories | `org/tamacat/dao/meta/Columns.java`, `Tables.java` | Construct `Column` and `Table` declarations |
| Mapped bean | `org/tamacat/dao/orm/MapBasedORMappingBean.java` | `LinkedHashMap`-backed generic bean with `GetFilter` / `SetFilter` hooks |

### `Query` — the full method list

`org/tamacat/dao/Query.java`, 30 methods:

`select`, `distinct`, `addUpdateColumn`, `addUpdateColumns`, `removeUpdateColumns`,
`addTable`, `removeFromTables`, `join`, `outerJoin`, `andOuterJoin`,
`where(Search, Sort)`, `and(Search, Sort)`, `or(Search, Sort)`, `where(String)`,
`and(String)`, `or(String)`, `andIn`, `andNotIn`, `andExists`, `andNotExists`, `groupBy`,
`orderBy`, `getSelectSQL`, `getInsertSQL`, `getUpdateSQL`, `getDeleteSQL`,
`getDeleteAllSQL`, `getBlobIndex`, `getTimestampString`, `autoPrimaryKeyUpdate`.

Deprecated but still present: `Query.setUseAutoPrimaryKeyUpdate` (`Query.java:166-172`).

### Contracts a subclass MUST fulfil

`Dao.getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` all throw
`RuntimeException(NoSuchMethodException)` by default
(`Dao.java:212-222`, mirrored at `DaoAdapter.java:152-162`). **Subclasses must override
them.** This is the library's primary extension contract: the framework owns assembly,
escaping, execution, and transactions; the subclass owns the bean-to-SQL binding.

### Value-rendering contract (`SQLParser.parseValue`)

Callers depend on this behaviour, which is pinned by `SQLParserTest.java:50-157`:

| Input `DataType` | Contract | Location |
|---|---|---|
| STRING, BOOLEAN | escape filter applied, then quote-wrapped; `null` returns unquoted | `SQLParser.java:87-91` |
| NUMERIC, FLOAT | empty returns `null`; regex-validated then emitted bare; **non-numeric throws `InvalidParameterException("value is not numeric.")`** | `:93-101` |
| TIME, DATE | empty or `"NULL"` returns `null`; the exact literal `current_timestamp` emitted bare; otherwise quote-wrapped | `:104-110` |
| OBJECT | returns the literal `"?"` | `:112-113` |
| FUNCTION and fallback | emitted bare, unquoted, unfiltered | `:115` |

`SQLParser.value` predicate contract:

- Single value: empty value on a `isNotNull()` column throws `InvalidParameterException`
  (`:45`); LIKE routing at `:48-50`; IN routing at `:51-52` gated on the **exact** string
  `" in "`; null value at `:55`; otherwise the raw value is substituted into the
  `replaceHolder` and the combined string is passed to `parseValue` (`:57`).
- Two or more values: BETWEEN expansion at `:62-65`, IN expansion at `:66-68`.

### BLOB / binary contract

The one bind-parameter path. Caller responsibilities:

1. Declare the column as `DataType.OBJECT`.
2. Build the SQL via `Query.getInsertSQL` or `getUpdateSQL` — the `?` is emitted for you.
3. Read the position via `Query.getBlobIndex()` (`Query.java:158`, impl
   `QueryImpl.java:469-471`).
4. Call `Dao.executeUpdate(sql, index, inputStream)` (`Dao.java:266-274`).

**Stated unknown:** the exact semantics of `getBlobIndex()` — 1-based JDBC parameter
position, or a count of OBJECT columns — is **not determined**. No caller exists anywhere
in this repository, no Javadoc documents it, and no executing test asserts it. Its
increment sites (`QueryImpl.java:212-214` and `:269`) make it inferable but the scan did
not resolve it, and external tamacat projects are outside this repository.

## Extension Points (SPI)

| Interface | Location | Methods | Purpose |
|---|---|---|---|
| `DaoExecuteHandler` | `dao/event/DaoExecuteHandler.java` | 4 | Observe query/update execution. Shipped impls: `LoggingDaoExecuterHandler`, `NoneDaoExecuteHandler` |
| `DaoTransactionHandler` | `dao/event/DaoTransactionHandler.java` | 8 | Observe transaction lifecycle. Shipped impls: `Logging*`, `None*` |
| `DaoEvent` | `dao/event/DaoEvent.java` | — | Event payload; `impl/DaoEventImpl` |
| `Validator` | `dao/validation/Validator.java` | 1 | Value validation hook |
| `Search.ValueConvertFilter` | nested in `dao/Search.java` | 1 (`convertValue`) | Escaping strategy. Impls: `DefaultValueConvertFilter` (`Search.java:99-107`), `MySQLValueConvertFilter` (`MySQLSearch.java:11-20`), `OracleValueConvertFilter` (`OracleSearch.java:11-15`) |
| `Search.Conditions` | nested in `dao/Search.java` | 2 (`getCondition`, `getReplaceHolder`) | Operator vocabulary. Impls: `Condition`, `MySQLCondition`, `PostgreSQLCondition` |
| `ORMappingSupport` | `dao/orm/ORMappingSupport.java` | 8 | The bean contract `ORMapper` populates |
| `MapBasedORMappingBean.GetFilter` / `SetFilter` | nested | 1 each | Per-property read/write interception |
| `Column` | `dao/meta/Column.java` | ~30 | Column metadata contract |
| `Table` | `dao/meta/Table.java` | 11 | Table metadata contract |
| `PoolableObjectFactory` | `pool/PoolableObjectFactory.java` | 6 | Pooled-object lifecycle |
| `JdbcConfig` | `sql/JdbcConfig.java` | 6 | Connection-source configuration |
| `LifecycleSupport` | `sql/LifecycleSupport.java` | 3 | Component start/stop |

### SPI contract note — `Conditions` implementations

`SQLParser` routes on the **literal text** returned by `getCondition()`:

- LIKE routing uses `indexOf(" like ") >= 0` (`SQLParser.java:48`).
- IN routing uses `equals(" in ")` — **exact string equality** (`SQLParser.java:51`).
- BETWEEN routing uses `indexOf(" between ") >= 0` (`SQLParser.java:62`).

A custom `Conditions` implementation whose operator string differs in spacing therefore
bypasses the corresponding special handling.

`Condition.IS_NULL` and `NOT_NULL` (`Condition.java:21-22`) carry a `null`
`replaceHolder`; if a non-null single value is supplied alongside them,
`SQLParser.java:57` calls `.replace` on `null` and throws NPE. No test covers this.

## Exception Contract

| Type | Extends | Raised by |
|---|---|---|
| `DaoException` | `RuntimeException` | General DAO failures, funnelled through `Dao.handleException` |
| `InvalidParameterException` | `IllegalArgumentException` | `SQLParser.parseValue` on non-numeric input for NUMERIC/FLOAT (`:101`); `SQLParser.value` on empty value for a not-null column (`:45`); `QueryImpl.getDeleteSQL` when no table resolves (`:289`, `:297`) |
| `InvalidValueLengthException` | — | Value-length violations |
| `IllegalTransactionStateException` | — | `org.tamacat.sql` transaction-state violations |
| `PoolLimitException`, `ObjectActivateException` | — | `org.tamacat.pool` |

## Configuration Surface

Configuration is XML consumed by `tamacat-core`'s `DI` container. The test wiring lives at
`src/test/resources/db.xml`.

### `DriverManagerJdbcConfig` bean properties

| Property | Meaning |
|---|---|
| `driverClass` | JDBC driver class name — **also the dialect selector** (`DaoAdapter.java:86-92`) |
| `url` | JDBC URL |
| `user` | Database user |
| `password` | Database password |
| `maxPools` | Pool ceiling |
| `minPools` / `initPools` | Pool floor / initial size |
| `activateSQL` | Probe SQL run on connection activation (`DriverManagerJdbcConfig.java:54-55`) |
| `activateResult` | Expected probe result |

### `DataSourceJdbcConfig` bean properties

| Property | Meaning |
|---|---|
| `dataSourceName` | JNDI name, e.g. `java:comp/env/jdbc/test` |
| `maxPools` | Pool ceiling |
| `initPools` | Initial size |
| `activateResult` | Expected probe result (`DataSourceJdbcConfig.java:50-51`) |

`DataSourceJdbcConfig.getDriverClass()` returns `null` (`DataSourceJdbcConfig.java:92-94`).
Since `DaoAdapter.setDatabase` calls that method at `:86` to pick a dialect, a
JNDI-datasource configuration reaches a null driver class there. **Stated unknown:**
whether that path is actually reachable was **not determined** — the scan found no guard
and no test covering it, and did not trace whether the one passing
`DataSourceConnectionManagerTest` exercises it.

### Beans defined in the test wiring (`src/test/resources/db.xml`)

| Bean | Configuration |
|---|---|
| `default` | `MockDriver`, `jdbc:mock://localhost/test`, `maxPools` 10, `activateSQL` `SELECT 1`, `activateResult` `1` |
| `javadb` | Derby `ClientDriver` — used only by the non-executing `UserDaoTest2` |
| `ds` | `DataSourceJdbcConfig` on `java:comp/env/jdbc/test` |
| `db1`, `db2` | Two `MockDriver` databases, used by `TransactionTest` |

## Packaging Surface

The published jar also exports `org.tamacat.mock.sql`. Consequences visible in the code:

- `MockDriver` self-registers with `DriverManager` in a static initializer
  (`MockDriver.java:21-27`) and its `acceptsURL` returns `true` for **every** URL
  (`:37-39`).
- `ConnectionManager.closeAll()` deregisters **every** driver in the JVM
  (`ConnectionManager.java:50-58`), not only its own.

These are packaging and lifecycle facts of the current artifact, recorded here because
they are part of what a consumer receives.

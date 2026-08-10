# Business Overview — tamacat-dao

> Reverse-engineered description of the system **as it is** at commit
> `e51c32739506565c572fed458c1fbba51bbab846` (branch `v2.0`). This document records
> observed behaviour and design facts. It proposes no changes.

## What This Product Is

`org.tamacat:tamacat-dao` is a **standalone Java data-access library** (Maven `jar`
packaging, Java 8 source/target). It is not an application, not a service, and has no
network surface. Its consumers are other Java programs that link the jar and extend its
classes.

The product's job is to let an application developer talk to a relational database
**without writing SQL text by hand**, by declaring table and column metadata in Java and
then composing queries from that metadata. The library assembles the SQL, executes it
through JDBC, and maps result rows back onto Java objects.

It is part of the wider `tamacat` family of libraries — it depends on
`org.tamacat:tamacat-core:1.5` for dependency injection (`DI`/`DIContainer`, XML-driven),
logging (`Log`/`LogFactory`), and general utilities.

## Business Domain

The domain is **generic relational persistence infrastructure**. There is no vertical
business domain (no orders, no accounts, no customers) baked into the library. The only
business-like entities in the repository are test fixtures — `User`, `TestDao`,
`UserDao` under `src/test/java/org/tamacat/dao/test/` — which exist to exercise the
framework, not to model a real business.

Domain concepts the library itself owns:

| Concept | Type in code | Meaning |
|---|---|---|
| Table | `meta/Table`, `meta/DefaultTable`, factory `meta/Tables` | A declared database table with its column set |
| Column | `meta/Column`, `meta/DefaultColumn`, factory `meta/Columns` | A declared column: name, `DataType`, primary-key flag, not-null flag, auto-generate-id flag, auto-timestamp flag, optional function name |
| Data type | `meta/DataType` enum | STRING, BOOLEAN, NUMERIC, FLOAT, DATE, TIME, OBJECT, FUNCTION — drives how a value is rendered into SQL |
| Query | `Query` interface, `impl/QueryImpl` | An accumulating SQL builder for SELECT / INSERT / UPDATE / DELETE |
| Search | `Search`, dialect subclasses `impl/MySQLSearch`, `impl/OracleSearch` | A WHERE-predicate accumulator |
| Condition | `Condition` enum, `impl/MySQLCondition`, `impl/PostgreSQLCondition` | The comparison operators and their value templates |
| Sort | `Sort` | An ORDER BY accumulator |
| DAO | `Dao`, `DaoAdapter<T>`, `impl/MySQLDao`, `impl/OracleDao` | The execute-and-map facade |
| Mapped bean | `orm/ORMappingSupport`, `orm/MapBasedORMappingBean` | The application object a row maps to |
| Transaction | `tx/Transaction`, `sql/TransactionStateManager` | Multi-datasource begin / commit / rollback / release |

## Key Functionality

1. **Metadata-declared schema.** The application declares tables and columns once
   (`Tables`/`Columns` factories, `DataType` per column) and every query is expressed
   against those declarations rather than against SQL text.
2. **Fluent query composition.** `Query` offers 30 methods — `select`, `distinct`,
   `addTable`, `join`, `outerJoin`, `andOuterJoin`, `where/and/or`, `andIn`, `andNotIn`,
   `andExists`, `andNotExists`, `groupBy`, `orderBy` — which accumulate into four SQL
   emitters: `getSelectSQL`, `getInsertSQL`, `getUpdateSQL`, `getDeleteSQL` (plus
   `getDeleteAllSQL`).
3. **SQL text generation by string assembly.** This is the library's **central design
   fact**. Every value that reaches the database is rendered into the SQL text as a
   literal by `org.tamacat.sql.SQLParser`, and the finished string is executed through
   `java.sql.Statement`. The single exception is binary/BLOB data (`DataType.OBJECT`),
   which is emitted as a `?` and bound via `PreparedStatement.setBinaryStream`.
   See `architecture.md` for the full spine.
4. **Value rendering with a type contract.** `SQLParser.parseValue` quote-wraps STRING
   and BOOLEAN values, validates NUMERIC/FLOAT values against a numeric regex and
   **rejects non-numeric input** with `InvalidParameterException`, renders empty
   DATE/TIME as `null`, passes the literal `current_timestamp` through unquoted, and
   emits `?` for OBJECT.
5. **Quote escaping via a pluggable filter.** `Search.ValueConvertFilter` implementations
   escape `'` (and, on MySQL, `\`) before interpolation.
6. **LIKE wildcard handling.** When a LIKE value itself contains `%` or `_`, the parser
   escapes them and appends a dynamically chosen `escape 'X'` clause.
7. **O/R mapping.** `orm/ORMapper` walks a `ResultSet` and populates
   `ORMappingSupport` beans; `MapBasedORMappingBean` provides a `LinkedHashMap`-backed
   generic bean with get/set filters and JSON serialization via `util/JSONUtils`.
8. **Connection pooling.** `org.tamacat.pool` provides a generic `ObjectPool` /
   `StackObjectPool`; `org.tamacat.sql.ConnectionManager` pools JDBC connections, with
   an optional activation SQL probe (`activateSQL` / `activateResult`) run on borrow.
9. **Two configuration modes.** `DriverManagerJdbcConfig` (driver class + URL + user +
   password) and `DataSourceJdbcConfig` (JNDI datasource lookup), both wired from an
   XML DI file (`db.xml`) through `tamacat-core`'s `DI` container.
10. **Dialect specialization.** MySQL, Oracle, and a generic fallback are selected at
    runtime by substring match on the configured driver class name. PostgreSQL has only
    a condition enum, no DAO or Search subclass.
11. **Transaction control across multiple datasources.** `tx/Transaction` and
    `sql/TransactionStateManager` coordinate begin/commit/rollback/release over more than
    one named database.
12. **Extension points (SPI).** `event/DaoExecuteHandler` and `event/DaoTransactionHandler`
    let a consumer observe execution and transaction lifecycle;
    `validation/Validator` and `Search.ValueConvertFilter` let a consumer inject
    validation and escaping.

## Who Uses It and How

The only consumption pattern visible in the repository is **subclassing**:

```
class UserDao extends DaoAdapter<User> { ... }
```

`DaoAdapter<T>` deliberately leaves `getInsertSQL` / `getUpdateSQL` / `getDeleteSQL`
throwing `RuntimeException(NoSuchMethodException)` by default
(`Dao.java:212-222`, `DaoAdapter.java:152-162`) — **subclasses must override them**. The
subclass therefore owns the mapping between its bean and the generated SQL, while the
framework owns the assembly, escaping, execution, transaction, and pooling.

## Deployment and Distribution

- Published as a Maven artifact `org.tamacat:tamacat-dao` from a custom repository
  (`https://raw.github.com/tamacat/tamacat.github.io/mvn-repo/`), with a source jar
  attached and a CycloneDX SBOM generated at `package`.
- Ships an OSGi-flavoured `MANIFEST.MF` under `src/main/resources/META-INF/`.
- The **mock JDBC stack (`org.tamacat.mock.sql`) lives in `src/main/java`**, so the test
  doubles — including a `MockDriver` that self-registers with `DriverManager` and accepts
  every URL — are inside the distributed production jar.

## Business-Relevant Constraints Observed

- **Java 8** is the compile target; the source uses no post-8 language features.
- **No JDBC driver at compile scope.** The library codes against `java.sql`/`javax.sql`
  only; the consuming application supplies the driver.
- **No ORM/framework dependency** — no Spring, no Hibernate, no MyBatis. The only
  compile-scope third-party artifacts are `tamacat-core` and the two `javax.json`
  artifacts.
- A `QueryImpl` instance is **single-use**: its `where` `StringBuilder`
  (`QueryImpl.java:48`) is never reset, so calling `getUpdateSQL` twice appends the
  primary-key predicate twice.

## Stated Unknowns

Carried forward verbatim from the code scan; these were **not** resolved by inference:

- **Downstream consumers of `Query.getBlobIndex()`** are unknown — no caller exists
  anywhere in this repository, and its contract (1-based JDBC parameter position vs. a
  count of OBJECT columns) is neither documented nor asserted. External tamacat projects
  are outside this repository.
- **Whether any dialect other than MySQL / Oracle / generic is used in production** is
  unknown. `PostgreSQLCondition` exists with no matching `Dao`/`Search`, and
  `DaoAdapter.setDatabase` has no `postgres` branch.
- **Whether the JNDI `DataSourceJdbcConfig` path is exercised anywhere** is unknown.
- **`org.tamacat:tamacat-core:1.5` internals** were read only at usage sites, not from
  source.

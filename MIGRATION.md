# Migration Guide: SQL Injection Remediation (bind-variable path)

This guide tracks the introduction of a bind-variable (`PreparedStatement`) execution
path alongside tamacat-dao's existing literal-SQL-embedding API. It is maintained
incrementally, one Unit of Work at a time, as part of the `260804-sql-injection-preparedst`
initiative:

- **U1 `bind-foundation`** - the bind-carrying types (`Param`, `PreparedSql`,
  `BindValue`, `ResultSetHandler`) and the value-classification/binding machinery
  (`BindSqlBuilder`, `ValueRules`, `PreparedStatementBinder`,
  `DBAccessManager#executeQuery(PreparedSql, ResultSetHandler)` /
  `#executeUpdate(PreparedSql)`).
- **U2 `select-path`** - switches the WHERE-predicate-through-SELECT-execution path
  (`Search`, `QueryImpl`, `Query`, `Dao`, `DaoAdapter`) over to the bind path.
- **U4 `dialects`** - switches `MySQLDao#searchList` and
  `OracleDao#searchListForOracle` over to the bind path built by U2, and fixes the
  pre-existing defects that lived on the (previously unused) Oracle paging route.
- **U5 `identifier-safety`** - validates identifier-position text (non-`Column`
  `Sort` keys, `Column#getFunctionName()`) and rejects dangerous literal characters
  at SQL-generation time. **Unlike U1-U4, this is a breaking behavioral change** -
  see §6 below.
- **U3 `write-path`** (this update, final Unit of this initiative) - switches
  INSERT/UPDATE/DELETE (`QueryImpl`, `Dao`, `DaoAdapter`) over to the bind path,
  including BLOB columns. See §7 below.

No existing public or protected method's signature or return value has changed.
Everything through U4 is purely **additive**; U5 (§6) introduces one narrow,
intentional exception to that at two identifier positions.

---

## 1. What changed

The SELECT path now has two parallel forms everywhere a predicate or a SQL statement
is built:

- **Literal form** (existing, unchanged): builds SQL text with values embedded
  directly (quoted and escaped) into the string. Still fully supported -
  `getSearchString()`, `getSelectSQL()`, `where(String)` / `and(String)` /
  `or(String)`, `Dao#param(...)`, `Query#andOuterJoin(Table, Search)` all work
  exactly as before, byte-for-byte.
- **Bind form** (new): builds `?`-bearing SQL text paired with an ordered list of
  values, which `Dao#search` / `Dao#searchList` now execute via
  `PreparedStatement` - `getSearchParam()`, `getSelectPreparedSql()`,
  `where(Param)` / `and(Param)` / `or(Param)`, `Dao#prepare(...)`,
  `Query#andOuterJoin(Table, Param)`.

Internally, `Search` keeps three synchronized states (literal text, bind text, bind
values) and `QueryImpl` keeps two (literal WHERE text, a list of bind-value
fragments), each guarded by a single private "sync point" method
(`Search#append`, `QueryImpl#appendWhere`, and an outer-join equivalent) so the two
forms cannot silently drift apart.

`Dao#search` / `Dao#searchList` now call `query.getSelectPreparedSql()` and execute
it through a callback (`ResultSetHandler`) instead of returning a raw `ResultSet` -
the `ResultSet` is closed by `DBAccessManager` before the call returns.

---

## 2. Old -> new mapping table

| Old (literal, still supported) | New (bind path) | Notes |
|---|---|---|
| `Search#getSearchString()` | `Search#getSearchParam()` | Both always report the same predicate count |
| `Query#getSelectSQL()` | `Query#getSelectPreparedSql()` | `default` on the interface; `QueryImpl` overrides both |
| `Query#where(String)` / `and(String)` / `or(String)` | `Query#where(Param)` / `and(Param)` / `or(Param)` | `QueryImpl` overrides route bind values through; the interface `default` falls back to the literal form and **discards** values |
| `Query#andOuterJoin(Table, Search)` | `Query#andOuterJoin(Table, Param)` | Deprecated old form keeps working (see sec 3) |
| `Dao#param(Column, Conditions, String...)` | `Dao#prepare(Column, Conditions, String...)` | Different method name - Java does not allow overloads that differ only by return type |
| `Dao#executeQuery(String)` (returns `ResultSet`, caller closes) | `Dao#executeQuery(PreparedSql, ResultSetHandler<R>)` (protected, callback-based) | New method does not return the `ResultSet` - it is closed before the call returns |
| `Query#getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` / `getDeleteAllSQL` | `Query#getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` / `getDeleteAllPreparedSql` | `QueryImpl` now overrides all four (U3); see §7 |
| `Dao#executeUpdate(String)` (via `create`/`update`/`delete`) | `Dao#executeUpdate(PreparedSql)` (via `create`/`update`/`delete`) | `Dao`/`DaoAdapter`'s default `create`/`update`/`delete` now route through the bind path (U3); see §7 |

**`Query`'s new `default` methods and their fallback behavior** (used automatically
by any `Query` implementation outside this codebase that has not been updated):

| Method | Default behavior |
|---|---|
| `getSelectPreparedSql()` | Wraps `getSelectSQL()` via `PreparedSql.ofLiteral(...)` - no values |
| `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)` | Same pattern, wrapping the corresponding literal method |
| `where(Param)` / `and(Param)` / `or(Param)` | Delegates to the `String` form with `param.getSql()` - **values are discarded** |
| `andOuterJoin(Table, Param)` | No-op (`return this;`) - there is no way to derive a `Search` from a `Param`'s text to delegate to the legacy method |

---

## 3. Paths remaining outside SM-1 (the "use bind variables" security goal)

These paths are deliberately left on the literal (string-embedding) form. Using
them still produces working, correct SQL - they are simply not part of the
bind-variable hardening this initiative delivers:

- **`where(String)` / `and(String)` / `or(String)` / `Dao#param(...)`** - kept for
  developers who need to supply a hand-written SQL fragment (FR-7.1). Use
  `where(Param)` / `and(Param)` / `or(Param)` / `Dao#prepare(...)` instead when the
  fragment carries a runtime value.
- **`Query#andOuterJoin(Table, Search)`** (deprecated) - the outer join condition it
  adds is embedded as literal text in the FROM clause even when
  `getSelectPreparedSql()` is used. Use `andOuterJoin(Table, Param)` instead.
- ~~INSERT / UPDATE / DELETE~~ **Resolved by U3 `write-path`** (this update, see §7
  below) - `getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` / `getDeleteAllSQL`
  (the literal path) remain fully supported, unchanged; `Dao#create` / `#update` /
  `#delete` now execute through the bind path by default. The three call sites
  previously noted here as staying on `Dao#param(...)` (`UserDao#getUpdateSQL`,
  `FileDataDao#getUpdateSQL`, `MySQLDaoTest#testGetUpdateSQL`'s fixture) remain on
  `param(...)` and are unaffected by U3 - the caution was about not mixing
  `prepare(...)`/`where(Param)` into the *same* `Query` instance a literal
  `getUpdateSQL()` builder reads from (`where(Param)` also writes `?`-bearing text
  into the legacy `where` field, per the caveat in §4). U3 does not touch these
  `getUpdateSQL`/`getInsertSQL`/`getDeleteSQL` overrides at all; `UserDao` and
  `FileDataDao` (this repository's own `DaoAdapter` subclasses) instead gained
  separate, additional overrides -
  `getInsertPreparedSql`/`getUpdatePreparedSql`/`getDeletePreparedSql` - each
  building its own fresh `Query` instance, so the two builder families never share
  state. See §7.
- ~~Dialect subclasses that bypass `Dao#executeQuery`~~ **Resolved by U4
  `dialects`** (see §5 below) - `MySQLDao#searchList` and
  `OracleDao#searchList`/`searchListForOracle` now execute through
  `Dao#executeQuery(PreparedSql, ResultSetHandler)`.
- **`orderBy(Sort)` and `select`-clause `Column#getFunctionName()`** - these are
  identifier/expression positions, not value positions; caller-supplied text is
  inserted as SQL syntax either way. **U5 `identifier-safety`** (a prior update) adds
  validate-and-reject at these two positions - see §6 below. Bind parameterization
  is not possible here (JDBC has no `?` for identifiers), so the safety mechanism
  is rejection of dangerous literal characters, not binding; these positions
  therefore remain deliberately outside SM-1 in the sense that no `?` is ever used
  here, even after U5.

---

## 4. Caveats

- **`Query#andOuterJoin(Table, Param)`'s default implementation does nothing.**
  External `Query` implementations that have not been recompiled against this
  version silently ignore calls to this method (no exception) - the same as the
  legacy method's own behavior when the outer join table hasn't been registered
  yet via `outerJoin(...)`.
- **`where(Param)` / `and(Param)` / `or(Param)`'s `Query` interface default
  discards the supplied values.** Only `QueryImpl` (and any other implementation
  updated to override these methods) actually binds them. Calling these methods on
  an un-updated `Query` implementation compiles and runs, but the values never
  reach the database - the resulting SQL text still contains the literal
  `?`-bearing string from `Param#getSql()`.
- **`where(Param)` / `and(Param)` / `or(Param)` also update the legacy `where`
  field with `Param#getSql()`'s `?`-bearing text.** Calling `getSelectSQL()` (the
  legacy literal method) afterward returns SQL containing unbound `?` characters.
  Executing that text through a literal path that performs no placeholder check
  (e.g. `Dao#executeUpdate(String)`, or `Dao#executeQuery(String)`) sends invalid
  SQL to the database. Executing it via `PreparedSql.ofLiteral(...)` fails fast
  with a `DaoException` (`hasUnboundPlaceholders()`) instead. Do not mix
  `where(Param)`/`and(Param)`/`or(Param)` into a `Query` whose SQL you intend to
  read via `getSelectSQL()`/`getUpdateSQL()`/etc. and execute literally.
- **`handleException`'s notification funnel is now wider.** `Dao#search` /
  `Dao#searchList` route failures from the query execution itself (not just from
  `ResultSet` iteration/mapping) through `handleException`. Any custom
  `DaoTransactionHandler` will now also observe execution failures it previously
  did not see. No existing notification is lost.
- **Subquery methods (`andIn` / `andNotIn` / `andExists` / `andNotExists`) evaluate
  the child query twice** - once via `getSelectSQL()` (for the literal path) and
  once via `getSelectPreparedSql()` (for the bind path). Both produce the same
  `blobIndex` value on the child (harmless), and the child's `tables` /
  `uniqTableNames` accumulation is idempotent (`Set`-based).

---

## 5. U4 `dialects` - MySQL / Oracle paging now execute through the bind path

- **`MySQLDao#searchList`** no longer bypasses `Dao#executeQuery` - it now goes
  through `Dao#executeQuery(PreparedSql, ResultSetHandler)` (U2), the same as the
  generic path. `FOUND_ROWS()` executes as a second, separate call to that method
  (with zero bind values) rather than a second `executeQuery(String)` call on the
  same raw `Statement`. Both statements now show up in `getExecutedQuery()` /
  `getExecutedStatements()` (previously neither did, since `createStatement()`
  bypassed `DBAccessManager`'s recording). The LIMIT clause's boundary values
  (`start-1`, `max`) remain `int`-literal text, not bound placeholders - see the
  note on FR-6.3 below.

- **Oracle's paging behavior changes observably: full-scan to rownum-based.**
  `OracleDao#searchListForOracle` existed but was never called by anything
  (`searchList` did not override it) - callers went through `Dao#searchList`'s
  generic full-result-fetch-then-skip implementation. `OracleDao#searchList` is now
  overridden and delegates to `searchListForOracle`, which wraps the SELECT in a
  `rownum`-restricting inline view (`(offset, offset+max]` and single/double-stage
  variants depending on whether `start`/`max` are supplied) so paging is now
  performed by the database rather than by fetching every row and discarding most
  of them client-side. This is a performance-characteristic change, not an API
  break: `searchListForOracle`'s signature and return type are unchanged, and it
  remains callable directly.

- **Two defects fixed on that (previously dead) Oracle route**: unbound `?`
  placeholders in the executed SQL, and a `for update` clause that could appear
  twice in the generated SQL (once inside the rownum inline view, once appended
  again outside) when the base query ended in `for update`. `for update` is now
  stripped from the inner query before wrapping and re-appended exactly once at
  the outermost level.

- **`OracleSearch.OracleValueConvertFilter#convertValue(null)`** no longer throws
  `NullPointerException` - it now returns `null`, matching
  `Search.DefaultValueConvertFilter` and `MySQLSearch.MySQLValueConvertFilter`.
  This only affects the legacy literal path (`SQLParser`); the bind path never
  invokes `ValueConvertFilter`.

- **FR-6.3 is satisfied by an equivalent-safety substitute, not literally.** The
  MySQL LIMIT clause and the Oracle rownum boundary values remain `int`-literal
  text concatenation (`" limit " + (start-1) + "," + max"`, and similarly for
  rownum) rather than bound `?` placeholders. This is deliberate: both clauses
  require integer SQL literals, which is incompatible with this codebase's
  bind-value machinery (`ADR-007`, all bind values use `setString`). Because
  `start`/`max` are `int` parameters at the Java type level, no string
  concatenation of untrusted *text* is possible here - the security goal is met -
  but the literal text of FR-6.3 ("eliminate direct `int` concatenation") is not:
  the concatenation itself remains, just proven safe by type rather than removed.

- **MySQL and Oracle paging windows diverge only when `start > 0 && max <= 0`**
  (offset supplied, no limit). MySQL ignores `offset` in this case (unchanged from
  before this Unit - `MySQLDao#searchList` has no row-skip step); Oracle applies
  it. This asymmetry is pre-existing (previously masked by Oracle's paging route
  being dead code, which meant Oracle went through `Dao#searchList`'s generic
  row-skip implementation instead - that path *did* honor `offset`). Widening
  `MySQLDao#searchList` to also skip in this case was judged out of scope for this
  Unit (see `business-rules.md` R-6 in the `260804-sql-injection-preparedst`
  intent record). All other `(start, max)` combinations return the same row set on
  both dialects.

---

## 6. U5 `identifier-safety` - **BREAKING BEHAVIORAL CHANGE**: identifier positions now reject dangerous literal characters

**Unlike every other change tracked in this guide (U1/U2/U4, all additive), this
one can make code that previously ran successfully throw a new exception at SQL
generation time.** Read this section if your application passes non-`Column`
sort keys to `Sort#sort`/`asc`/`desc`, or sets a `Column#functionName(String)`
that isn't a literal your own code controls.

### What changed

Identifier positions (table names, column names, ORDER BY expressions) cannot be
bind-parameterized - JDBC has no `?` placeholder for identifiers. U5 secures these
positions with **validation-and-rejection of dangerous literal characters**
(a new `org.tamacat.sql.IdentifierRules.validate(String)`), not binding. Two call
sites now run every identifier-position string through this check before it is
concatenated into generated SQL:

- **`Sort#sort(Object k, Object o)`'s non-`Column` branch** - when `k` is not a
  `Column` (e.g. `sort.asc("some_raw_expression")`), the raw string is now
  validated. The `Column` branch is untouched.
- **`QueryImpl`'s SELECT-clause builder, when `Column#isFunction()` is `true`** -
  `Column#getFunctionName()`'s value is now validated. This is the single method
  shared by both `getSelectSQL()` (literal) and `getSelectPreparedSql()` (bind) -
  **both entry points are affected identically.** `Column#getColumnName()` in the
  same branch is not validated (out of scope, see below).

**The rejected characters/sequences** are `'` (single quote), `"` (double quote),
`;` (semicolon), `--` (line comment), `/*` and `*/` (block comment delimiters).
Any of these present in a non-`Column` `Sort` key or in `Column#getFunctionName()`
now throws `org.tamacat.dao.exception.InvalidParameterException`
(`RuntimeException`, unchecked - no `throws` clause change) at the point the SQL
is generated (`Sort#sort(...)`, or `QueryImpl#getSelectSQL()` /
`#getSelectPreparedSql()`).

> **Before this change**, a raw sort key or function name containing one of these
> characters was concatenated into the generated SQL text as-is - it either
> produced a SQL syntax error at the database, or (in the worst case) was
> interpreted as SQL syntax rather than an inert identifier. After this change,
> the same input is rejected earlier and more clearly, before any SQL is sent
> to the database.

### What is *not* affected

- **Everything routed through a `Column` object is unaffected** -
  `Column#getColumnName()`, `MappingUtils#getColumnName(Column)`, and the
  `Column`-typed branch of `Sort#sort` are metadata your own code defines at
  compile time and are not validated by this Unit (a deliberate scope decision,
  not a technical limitation - see "Known partial coverage" below).
- **`Sort.Order`** (`o.toString()` in `Sort#sort`) is not validated - out of scope
  (identifiers only, not sort direction).
- **A `Column` with `isFunction()==true` and `getFunctionName()==null`** (a
  `Column` defined only with the `Column.FUNCTION` marker, never given a
  `functionName(...)`) is a real, pre-existing, legitimate state (see
  `ColumnFunctionTest`). This continues to work exactly as before -
  `IdentifierRules.validate(null)` returns `null` without throwing, so the
  existing `"null " + columnName` SELECT-clause output is unchanged. `null` was
  a deliberate exemption, not an oversight: it cannot carry any of the rejected
  characters.
- Legitimate compound expressions and function calls that don't contain any of
  the rejected characters keep working exactly as before, e.g. `"RAND()"`,
  `"COUNT(*)"`, `"t1.col1, t2.col2"`.

### A narrowed corner case

Expressions that legitimately need a **string literal** embedded (e.g.
`"TO_CHAR(d,'YYYY')"`, `"CASE WHEN x='A' THEN 1 ELSE 0 END"`) contain a `'` and
are now rejected via the non-`Column` `Sort` path, even though they were valid
SQL before. If you need this, build the equivalent expression via a `Column`
whose `isFunction()` branch is used instead (that branch is not validated) -
or restructure the query to avoid embedding a literal in the ORDER BY key.

### Migration guidance

- **Audit calls to `Sort#sort(Object, Object)` / `Sort#asc(Object)` /
  `Sort#desc(Object)`** where the key is not a `Column`. If the key is built from
  any value that isn't a fixed string literal in your own code, confirm it cannot
  contain `'`, `"`, `;`, `--`, `/*`, or `*/` - or catch
  `InvalidParameterException` and handle it as a rejected/invalid sort request.
- **Audit calls to `Column#functionName(String)`.** Same character set applies.
- No signature changed and no exception's checked-vs-unchecked status changed -
  `InvalidParameterException extends IllegalArgumentException` (unchecked), so
  existing `catch` blocks around SQL generation that already catch
  `RuntimeException` or `IllegalArgumentException` will continue to catch this.

### Known partial coverage (deliberately out of scope, not a defect)

**This Unit's identifier-safety coverage is deliberately partial.** Of the three
identifier positions conceptually in scope (ORDER BY keys, table names, column
names), only ORDER BY keys (via `Sort`) and SELECT function names (via
`Column#getFunctionName()`) are validated. **Table names and column names
themselves - i.e. the values passed to `Column#columnName(String)` or a
`Table`'s name setters - are not validated by this Unit.** This is a scope
decision, not a technical impossibility: the same call-site-wrapping technique
used here could be applied to `MappingUtils#getColumnName(Column)`'s call sites,
but doing so would touch every WHERE/FROM/SELECT/ORDER BY code path built across
U1-U4, which was judged to be a larger change than this Unit's intended scope.
This gap is not picked up by U3 (§7) - it is recorded here as the closing status
of identifier-position coverage for this initiative.

---

## 7. U3 `write-path` (this update, final Unit of this initiative) - INSERT/UPDATE/DELETE now execute through the bind path

### What changed

`Dao`/`DaoAdapter`'s default `create(T)` / `update(T)` / `delete(T)` now route
through `executeUpdate(PreparedSql)` instead of `executeUpdate(String)`. Concretely:

- `QueryImpl` now overrides `Query#getInsertPreparedSql(T)` /
  `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)`
  (previously literal-fallback-only `default`s on the interface, per §2's mapping
  table). INSERT VALUES, UPDATE SET, and DELETE/UPDATE WHERE clauses are all bound.
- `Dao` and `DaoAdapter` each gained (independently - `DaoAdapter` does not extend
  `Dao`, see below) a `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` /
  `getDeletePreparedSql(T)` extension point (default: wraps the existing
  `getInsertSQL`/`getUpdateSQL`/`getDeleteSQL` override via `PreparedSql.ofLiteral(...)`,
  same compatibility-shim pattern as `Query`'s `default`s), and an
  `executeUpdate(PreparedSql)` method that `create`/`update`/`delete` now call.
- `DBAccessManager` gained `executeUpdate(PreparedSql, int, InputStream)`, and
  `Dao`/`DaoAdapter` gained a matching `executeUpdate(PreparedSql, int, InputStream)`,
  for BLOB (`DataType.OBJECT`) columns.

**Subclasses that only override `getInsertSQL`/`getUpdateSQL`/`getDeleteSQL` need
no changes to compile or keep working on non-`OBJECT` columns** - they keep
generating the exact same literal SQL text via the `PreparedSql.ofLiteral(...)`
default, unchanged behavior, no recompilation required (NFR-3). This
repository's own `UserStatDao` is currently in this state (deferred - see
"Paths remaining outside SM-1" above). **A subclass with a `DataType.OBJECT`
column left in this state will fail fast**: the default `getInsertPreparedSql`/
etc. wrap the literal SQL text as-is, which for an `OBJECT` column contains a
literal `?` with zero bound values, so `create`/`update` reaches
`DBAccessManager`'s `checkBindable` and throws `DaoException` on an unbound
placeholder (the intentional ADR-012 rejection, not a defect). To *use* the
bind path (recommended - see FR-1.4/NFR-1, and required for any subclass with
an `OBJECT` column), a subclass additionally overrides
`getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)`,
delegating to `query.getInsertPreparedSql(data)` etc. - the same pattern as the
existing `getInsertSQL` delegation. This repository's own `UserDao` and
`FileDataDao` (the latter has an `OBJECT` column, `FileData.DATA`) were both
migrated this way as part of this Unit.

### BLOB-handling subclasses need a `create`/`update` override

**BLOB is never auto-detected.** `Dao`/`DaoAdapter`'s default `create`/`update`
have no way to know where in `T data` an `InputStream` for a `DataType.OBJECT`
column lives - this was true before U3 too (the pre-existing `create`/`update`
defaults never handled BLOB; `executeUpdate(String, int, InputStream)` has always
required a subclass override to call it). The same responsibility split continues
unchanged under the bind path.

**This repository's real DAOs all extend `DaoAdapter`** (the direct `Dao`
extenders, `MySQLDao`/`OracleDao`, are dialect base classes only) - so the
override point in practice is `DaoAdapter`, not `Dao`. A BLOB-writing subclass
overrides `create(T)`/`update(T)` and:

```java
public class BlobFileDataDao extends DaoAdapter<FileData> {
    @Override
    public int create(FileData data) {
        Query<FileData> query = createQuery().addUpdateColumns(FileData.TABLE.getColumns());
        PreparedSql sql = query.getInsertPreparedSql(data);     // (1) bind-path PreparedSql
        int blobIndex = sql.getBindIndexOf(DataType.OBJECT, 1); // (2) bind position (or use getBlobIndex())
        InputStream in = /* extracted from data */;
        return executeUpdate(sql, blobIndex, in);                // (3) BLOB-aware execute
    }
}
```

1. Get a bind-path `PreparedSql` from a `QueryImpl` built directly - **not** from
   `DaoAdapter#getInsertPreparedSql(T)`'s own default, and not from
   `super.getInsertPreparedSql(data)`.
2. Get the OBJECT column's 1-based bind position via
   `PreparedSql#getBindIndexOf(DataType.OBJECT, 1)` (or the query's
   `getBlobIndex()`, equivalent after the query built the same `PreparedSql`).
3. Call `executeUpdate(PreparedSql, int, InputStream)` (protected, on `DaoAdapter`)
   with the extracted `InputStream`.

### Caveat: the default `getInsertPreparedSql`/`getUpdatePreparedSql` return `-1` from `getBindIndexOf`

**`Dao#getInsertPreparedSql(T)` and, independently, `DaoAdapter#getInsertPreparedSql(T)`
default to `PreparedSql.ofLiteral(...)`** (a compatibility shim carrying zero
values - same as `Query`'s `default`s, §2). If a BLOB override calls
`getBindIndexOf` on a `PreparedSql` obtained from the *default* implementation
(`super.getInsertPreparedSql(data)` or `this.getInsertPreparedSql(data)` without
overriding it), it always gets back `-1` (no values to scan), and
`executeUpdate(PreparedSql, -1, in)` fails fast with a `DaoException` from
`checkBindable` (unbound-placeholder rejection) rather than silently writing to
the wrong column. Always obtain the `PreparedSql` from a `QueryImpl` built
directly with the BLOB column in `updateColumns`, as in the example above - not
from the DAO's own (default) `getInsertPreparedSql`/`getUpdatePreparedSql`.

### Zero existing callers

There is no BLOB override anywhere in this repository's product or test code as
of this Unit - `getBlobIndex()` and `executeUpdate(String/PreparedSql, int,
InputStream)` have no callers outside the example above and its test
(`DaoTest#testBlobOverride_CreateUsesExecuteUpdatePreparedSqlIntInputStream`).
This section and the referenced Javadoc
(`Dao#executeUpdate(PreparedSql,int,InputStream)`,
`DaoAdapter#executeUpdate(PreparedSql,int,InputStream)`) are therefore the only
places this information reaches an implementer before they write the first one.

### Other observable changes

- **The literal `getUpdateSQL()`'s OBJECT-column SET branch no longer leaves a
  table-qualified column name.** Previously `SET ..., file.data=?` (inconsistent
  with the other SET-clause branches, which already stripped the qualification);
  now `SET ..., data=?`, matching every other branch. This is the only change to
  the literal (non-bind) SQL text produced by this Unit.
- **`Dao#create`/`update`/`delete`'s executed-SQL text changes shape once a
  subclass's `getInsertSQL`/`getUpdateSQL`/`getDeleteSQL` override is *also*
  migrated to `getInsertPreparedSql`/etc.** (as `UserDao`/`FileDataDao` were in
  this Unit) - the SQL recorded via `Dao#getExecutedQuery()` changes from
  fully-literal (e.g. `INSERT INTO users (...) VALUES ('admin',...)`) to
  `?`-bearing bind form (`INSERT INTO users (...) VALUES (?,?,...)`). Any test or
  logging code that pattern-matches on the literal executed-SQL text of a migrated
  DAO's writes needs updating - see `UserDaoTest` in this repository for the
  before/after shape.

# Reverse Engineering Timestamp — tamacat-dao

> Freshness / staleness marker for the `tamacat-dao` code knowledge base. The
> `reverse-engineering` stage declares "Always rerun for freshness"; compare the commit
> below against the repository's current HEAD to decide whether this codekb is stale.

## Run Record

| Field | Value |
|---|---|
| **Date performed** | 2026-08-05 |
| **Stage** | `reverse-engineering` (2.1), Inception phase |
| **Mode** | `pipeline` — link 1 `aidlc-developer-agent` (code scan), link 2 `aidlc-architect-agent` (synthesis) |
| **Repository** | `tamacat-dao` |
| **Repository root** | `C:\git\tamacat\develop\tamacat-dao` |
| **Repo set** | single-repo intent |
| **Branch** | `v2.0` |
| **Commit hash** | `e51c32739506565c572fed458c1fbba51bbab846` |
| **Commit date** | 2026-03-24T20:25:21+09:00 |
| **Commit subject** | Merge pull request #7 from tamacat/v1.6 |
| **Default branch** | `master` |
| **Declared artifact version** | `org.tamacat:tamacat-dao:2.0` (pom, uncommitted) |
| **Active intent at scan time** | `260804-sql-injection-preparedst` |
| **Codekb location** | `aidlc/spaces/default/codekb/tamacat-dao/` |

### Working-tree state at scan time

The scan was performed against a **dirty working tree**. The artifacts describe the
working tree, not the clean commit:

| Path | State |
|---|---|
| `pom.xml` | **modified, uncommitted** — version `1.6.1` to `2.0`, slf4j `2.0.17` to `2.0.18` |
| `.claude/` | untracked |
| `aidlc/` | untracked |

Nothing else was modified. No source file under `src/` differs from the commit.

## Scope of Analysis

### Covered

| Area | Depth |
|---|---|
| Package and module inventory | complete — all 12 Java packages under `src/main/java`, classified by purpose |
| Build system | complete — `pom.xml`, all 8 plugins, repositories, `distributionManagement`, `MANIFEST.MF` |
| Dependency tree | complete — verified by executing `mvn dependency:tree` |
| Java API surface | complete — public facade, all 13 SPI interfaces, configuration bean properties |
| Frameworks and library versions | complete — direct and transitive, with scopes |
| Test inventory | complete — 48 test files classified by framework generation and by execution status under the surefire filter |
| Code quality indicators | complete — linting (none), CI (one CodeQL workflow), documentation (none), formatting |
| Technical debt signals | 17 numbered items plus 12 additional correctness observations, all with file:line anchors |
| **SQL construction and execution spine (depth section)** | **deep** — every value-interpolation site, every JDBC execution site in `src/main`, existing `PreparedStatement` usage, LIKE / IN / BETWEEN handling, INSERT / UPDATE / DELETE value construction, per-dialect differences, the co-mutation test set, and the shipped mock JDBC infrastructure |

### Verified by execution, not inference

| Command | Result |
|---|---|
| `mvn test` | `Tests run: 138, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` |
| `mvn dependency:tree` | full resolved tree, recorded in `dependencies.md` |
| offline compile | succeeds |
| offline `mvn test` | fails only on the missing `com.mysql:mysql-connector-j:9.6.0` artifact |

### Not covered

| Area | Reason |
|---|---|
| `org.tamacat:tamacat-core:1.5` source | external artifact; read only at usage sites within this repository |
| Downstream consumers of this library | outside this repository (no caller of `Query.getBlobIndex()` exists here) |
| Real-database behaviour of generated SQL | the entire executing test suite runs against `MockDriver` / `MockStatement`; no executing test reaches a real engine |
| Runtime performance, load, or concurrency measurement | no benchmarks exist in the repository and none were run |
| Reachability of the JNDI `DataSourceJdbcConfig` dialect path | not traced through `DataSourceConnectionManagerTest` |
| Git history beyond HEAD metadata | only HEAD, branch, and `git status` were consulted |

## Artifacts Produced by This Run

All under `aidlc/spaces/default/codekb/tamacat-dao/`:

| # | File | Content |
|---|---|---|
| 1 | `business-overview.md` | Business domain, purpose, key functionality |
| 2 | `architecture.md` | Architectural style, the SQL-string-concatenation design, layer and component diagrams, **Interaction Diagrams** (6 sequence/flow diagrams), dialect architecture, predicate patterns, execution surface, cross-cutting concerns |
| 3 | `code-structure.md` | Repository layout, package organisation, file classification, 9 recurring code patterns, naming conventions |
| 4 | `api-documentation.md` | Java type surface, `Query` method list, subclass contracts, value-rendering contract, BLOB contract, 13 SPI extension points, exception contract, `db.xml` configuration surface |
| 5 | `component-inventory.md` | Every shipped component with responsibility, dependencies, and SQL-bearing flag; dependency-direction diagram |
| 6 | `technology-stack.md` | Language, build, plugins, frameworks and library versions, dialect support, testing stack, CI/CD tooling |
| 7 | `dependencies.md` | Resolved external tree by scope, JDK-provided packages, internal package graph, coupling table and hotspots, hygiene observations |
| 8 | `code-quality-assessment.md` | Verified build baseline, test coverage and gaps, framework consistency, linting, CI/CD, documentation, 17-item technical debt register plus 12 correctness observations, strengths |
| 9 | `reverse-engineering-timestamp.md` | This file |

## Staleness Triggers

Rerun `reverse-engineering` for this repository when any of the following is true:

1. `git rev-parse HEAD` no longer returns `e51c32739506565c572fed458c1fbba51bbab846`.
2. The working tree's `pom.xml` change has been committed, reverted, or extended (the
   scan captured it uncommitted).
3. Any file under `src/main/java/org/tamacat/sql/` or
   `src/main/java/org/tamacat/dao/impl/` changes — these carry the SQL construction and
   execution spine that the depth section documents line by line.
4. `pom.xml` dependency versions change. The `junit:junit` `[4.13.2,)` open range means
   the resolved tree can change **without** a source change, so a fresh
   `mvn dependency:tree` is worth checking independently.
5. The surefire `<includes>` pattern changes — it currently determines which of the 48
   test files execute.
6. A dialect class is added or removed under `org.tamacat.dao.impl`.
7. Any of the stated unknowns listed below is resolved outside the codekb.

## Unknowns Carried Into the Codekb

These were explicitly **not determined** by this run and are recorded as open in the
relevant artifacts. They must not be treated as settled by later stages:

1. The contract of `Query.getBlobIndex()` — 1-based JDBC parameter position, or a count
   of OBJECT columns.
2. `org.tamacat:tamacat-core:1.5` internal behaviour (`StringUtils`,
   `UniqueCodeGenerator`, `DI`, `ClassUtils`).
3. Whether any dialect beyond MySQL / Oracle / generic is used in production.
4. Whether the JNDI `DataSourceJdbcConfig` path is exercised anywhere.
5. The intended fix behind the `TODO: bugfix` on `OracleDao.searchListForOracle`.
6. Real-database behaviour of any generated SQL.

# Technology Stack — tamacat-dao

> Reverse-engineered from commit `e51c32739506565c572fed458c1fbba51bbab846`, branch
> `v2.0`. Versions are as declared in `pom.xml` and as resolved by
> `mvn dependency:tree` at scan time.

## Language and Runtime

| Item | Value | Evidence |
|---|---|---|
| Language | Java | all sources under `src/main/java` |
| Source/target level | **1.8** | set both via `maven.compiler.*` properties and an explicit `maven-compiler-plugin` 3.8.1 configuration |
| Build-machine JDK at scan time | JDK 25 (Amazon Corretto) | environment, not a project constraint |
| Language features used | plain Java 8; no records, no `var`, no modules | source inspection |

## Build

| Item | Value |
|---|---|
| Build tool | Apache Maven, `jar` packaging |
| Coordinates | `org.tamacat:tamacat-dao:2.0` |
| Maven version at scan time | 3.9.11 |
| Verified baseline | `mvn test` produced `Tests run: 138, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` |
| Offline behaviour | offline compile succeeds; offline `test` fails only because `com.mysql:mysql-connector-j:9.6.0` is absent from the local repository |

### Maven plugins

| Plugin | Version | Role |
|---|---|---|
| `maven-compiler-plugin` | 3.8.1 | source/target 1.8 |
| `maven-source-plugin` | 3.2.1 | attaches a source jar |
| `maven-install-plugin` | 2.5.2 | `createChecksum` |
| `maven-surefire-plugin` | 2.22.2 | `<includes>**/*Test.java</includes>` |
| `maven-jar-plugin` | 3.2.0 | uses `src/main/resources/META-INF/MANIFEST.MF` |
| `versions-maven-plugin` | 2.16.0 | `display-dependency-updates` bound to `compile` (advisory only) |
| `cyclonedx-maven-plugin` | 2.7.9 | `makeAggregateBom` at `package` — SBOM generation |
| `site-maven-plugin` | 0.12 | publishes to the `mvn-repo` branch at deploy |

### Repositories

- Custom repository `tamacat.org` at
  `https://raw.github.com/tamacat/tamacat.github.io/mvn-repo/`
- `distributionManagement` points to `file://${project.build.directory}/mvn-repo`

### Packaging metadata

`src/main/resources/META-INF/MANIFEST.MF` carries OSGi headers with
`Implementation-Version: 1.6.1-20260324` and `Bundle-Version: 1.6.1` — **stale relative to
the pom's `2.0`**. The jar plugin bakes this manifest into the artifact.

## Frameworks and Libraries

| Name | Version | Scope | Purpose |
|---|---|---|---|
| Java SE | 8 (source/target 1.8) | — | Language level |
| `org.tamacat:tamacat-core` | 1.5 | compile | Foundation: `DI` / `DIContainer` (XML DI from `db.xml` / `orm.xml`), `Log` / `LogFactory`, `StringUtils`, `DateUtils`, `ClassUtils`, `CollectionUtils`, `UniqueCodeGenerator`, `ResourceNotFoundException` |
| `javax.json:javax.json-api` | 1.1.4 | compile | JSON-P API used by `JSONUtils` / `MapBasedORMappingBean` |
| `org.glassfish:javax.json` | 1.1.4 | compile | JSON-P implementation |
| JDBC (`java.sql`, `javax.sql`) | JDK | — | The only DB access mechanism. **No ORM framework, no Spring, no MyBatis, no Hibernate** |
| JNDI (`javax.naming`) | JDK | — | `DataSourceJdbcConfig` container-datasource lookup |
| JUnit | 4.13.2, declared as the **open range `[4.13.2,)`** (`pom.xml:39`) | test | Tests |
| EasyMock | 5.1.0 | test | **Declared but referenced nowhere in `src/`** |
| Objenesis | 3.3 | test | Transitive via EasyMock |
| MySQL Connector/J | 9.6.0 | test | Test-scope driver only |
| protobuf-java | 4.31.1 | test | Transitive via Connector/J |
| SLF4J API | 2.0.18 | test | Backing for `tamacat-core`'s `Log`; **no direct slf4j import in `src/main`** |
| Logback core / classic | 1.3.16 | test | Test-scope logging backend |
| Hamcrest core | 1.3 | test | Transitive via JUnit |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 2.3.3 | compile (transitive) | Via `tamacat-core` |
| `jakarta.activation:jakarta.activation-api` | 1.2.2 | compile (transitive) | Via `jakarta.xml.bind-api` |
| `com.sun.xml.bind:jaxb-impl` | 2.3.9 | runtime (transitive) | Via `tamacat-core` |
| `com.sun.activation:jakarta.activation` | 1.2.2 | runtime (transitive) | Via `jaxb-impl` |

Only `tamacat-core` and the two `javax.json` artifacts are compile-scope direct
dependencies. **There is no JDBC driver at compile scope** — the library codes against
`java.sql` only and the consuming application supplies the driver.

## Database Dialects

| Dialect | Support in code | Selection |
|---|---|---|
| MySQL | `MySQLDao`, `MySQLSearch`, `MySQLCondition` | driver class name contains `mysql` (`DaoAdapter.java:86-87`) |
| Oracle | `OracleDao`, `OracleSearch` | driver class name contains `oracle` (`:88-89`) |
| PostgreSQL | `PostgreSQLCondition` **only** — no `PostgreSQLDao`, no `PostgreSQLSearch` | falls to the generic branch |
| Generic fallback | `Dao`, `Search` | anything else (`:90-91`) |
| Derby (test only) | `javadb` bean in `db.xml`, used by the non-executing `UserDaoTest2` | — |
| Mock (test) | `org.tamacat.mock.sql.MockDriver` | `jdbc:mock://localhost/test` |

`DataSourceJdbcConfig.getDriverClass()` returns `null` (`DataSourceJdbcConfig.java:92-94`),
so a JNDI-datasource configuration reaches the dialect check at `DaoAdapter.java:86` with a
null driver class. The scan found no guard and no test covering that path.

## Testing Stack

| Item | Value |
|---|---|
| Primary framework | JUnit 4 annotations |
| Legacy | `SearchTest.java:19` and `SQLParserTest.java:19` still extend JUnit 3 `junit.framework.TestCase`; their `@Before protected void setUp()` works only because of the JUnit 3 lifecycle |
| Mocking | Hand-rolled `org.tamacat.mock.sql` JDBC stack. EasyMock is on the classpath and unused |
| SQL-assertion channel | `DBAccessManager.getExecutedQuery()` (`:181-188`), a `ThreadLocal<List<String>>` |
| Coverage tooling | **absent** — no JaCoCo, no Cobertura, no coverage plugin, no thresholds |
| Test selection | Surefire `**/*Test.java` only |
| Test resources | `db.xml` (five beans), `logback.xml`, `tomcat_datasource.txt` |

## CI/CD and Tooling

| Item | Value |
|---|---|
| CI workflows | one: `.github/workflows/codeql-analysis.yml` |
| CodeQL trigger | push/PR to **`master` only** plus a weekly cron. The current branch is `v2.0`, so CI does not run on this branch's pushes |
| Build/test workflow | **none** — no `mvn test` in CI |
| Action pinning | `actions/checkout@v2` and `github/codeql-action/*@v1`, both long-deprecated |
| Linting | **none** — no Checkstyle, SpotBugs, PMD, ErrorProne, or `.editorconfig` |
| SBOM | CycloneDX generated at `package` |
| Dependency-update reporting | `versions-maven-plugin` `display-dependency-updates` on every `compile`, advisory only |

## Working-Tree State at Scan Time

- Branch `v2.0`, HEAD `e51c32739506565c572fed458c1fbba51bbab846`
  (2026-03-24T20:25:21+09:00).
- Uncommitted `pom.xml` edit: version `1.6.1` to `2.0`, slf4j `2.0.17` to `2.0.18`.
- Untracked `.claude/` and `aidlc/` directories.

## Stated Unknowns

- `org.tamacat:tamacat-core:1.5` internals were **not** read from source — only at usage
  sites. The exact behaviour of `StringUtils.isEmpty` / `isNotEmpty` / `parse`,
  `UniqueCodeGenerator.generate()`, `DI.configure`, and `ClassUtils.newInstance` is taken
  from call context.
- Whether any dialect other than MySQL / Oracle / generic is used in production was **not
  determined**.
- Whether `DataSourceJdbcConfig` (JNDI) is exercised anywhere was **not determined**.

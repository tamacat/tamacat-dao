# Build Instructions — SQL Injection Remediation (`sql-injection-preparedst`)

## Prerequisites

- **JDK**: build JVM is JDK 25 Corretto (`technology-stack.md`). `pom.xml` targets Java 8 (`maven.compiler.source`/`target = 1.8`) — a newer JDK compiling to the 1.8 target is expected and supported.
- **Maven**: Apache Maven, `jar` packaging. No wrapper script; use the `mvn` on `PATH`.
- No new environment variables, config files, or local services are required by this initiative — all 5 units (U1–U5) added zero new third-party compile dependencies and zero new runtime configuration.

## Dependency installation

```bash
mvn -o dependency:resolve
```

Compile-scope dependencies are unchanged from before this initiative: `org.tamacat:tamacat-core:1.5`, `javax.json:javax.json-api:1.1.4`, `org.glassfish:javax.json:1.1.4`. Test-scope: JUnit 4, EasyMock (declared, unused by the new tests — all new tests use the `org.tamacat.mock.sql` mock stack instead), MySQL Connector/J, SLF4J, Logback, Hamcrest.

## Build commands

```bash
mvn -o -B compile
```

Expected: `BUILD SUCCESS`, no new dependency resolution (fully offline-capable — `-o` works because nothing new was added).

## Build verification

```bash
mvn -o -B compile 2>&1 | grep -E "WARNING|ERROR"
```

Two pre-existing `sun.misc.Unsafe`-deprecation warnings from a build-plugin transitive (`com.google.inject`) are expected and harmless — not related to this initiative's code.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `mvn -o` fails to resolve a plugin | Local repository cache is missing a plugin version | Drop `-o` for one run to let Maven fetch online, then resume offline |
| Compile fails on a `PreparedSql`/`BindValue`/`Param` import | Working tree missing U1's `org.tamacat.dao`/`org.tamacat.sql` additions | Confirm `git status` shows the 5 units' files as tracked/modified, not reverted |
| JaCoCo agent fails to attach | Some JaCoCo plugin versions don't support JDK 25 out of the box (U1 `tech-stack-decisions.md` 未解決事項) | Not currently wired into this build (`pom.xml` has no `jacoco` plugin as of this stage — see Known Limitations in `build-and-test-summary.md`); no action needed until it's added |

## Notes for CI

- `mvn test` (not `verify`) is what this stage ran — there is no coverage gate wired into the `test` phase today (U1's `tech-stack-decisions.md` flagged that only `mvn verify` would trigger a JaCoCo gate, and no JaCoCo plugin is present in `pom.xml` at all yet).
- `mvn -o` (offline) succeeds because no new dependencies were introduced by any of the 5 units — this is a deliberate constraint (CON-6) verified at every unit's NFR-requirements stage.

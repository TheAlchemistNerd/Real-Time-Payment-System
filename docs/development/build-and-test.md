# Build and test

Updated 6 November 2026. This guide covers the reproducible build, unit and integration tests, packaged service startup, and the additional Compose checks. [Build and bootstrap plan](../reviews/2026-10-19/remediation-plan.md#ordered-work-packages-and-acceptance-gates)

Run the full suite locally with JDK 21, the Maven wrapper, and a working Linux container engine. B1 passed on 5 November. Full Maven run 29 passed all 51 tests with exit 0 on 6 November. B2's assembled stack acceptance remains open. [B1 startup evidence](2026-11-05-b1-service-startup.md) · [B2 evidence](2026-11-06-b2-local-stack.md)

## Toolchain

| Component | Repository baseline | Purpose |
| --- | --- | --- |
| Java | JDK 21 | Compile and run every Maven module with the same Java release. |
| Maven | 3.9.9 through `mvnw` / `mvnw.cmd` | Use the distribution selected in `.mvn/wrapper/maven-wrapper.properties`. |
| Maven Wrapper | 3.3.2, `only-script` | Bootstrap Maven with committed scripts and a pinned distribution SHA-256. |
| Compiler | 3.13.0 | Compile with `maven.compiler.release=21`. |
| Enforcer | 3.5.0 | Require Java `[21,22)` and Maven `[3.9.9,4)` at build validation. |
| Surefire | 3.5.2 | Execute unit tests in the `test` phase. |
| Failsafe | 3.5.2 | Execute integration tests in `integration-test` and check their result in `verify`. |
| Testcontainers | 1.21.4 | Starts the PostgreSQL and Kafka test containers. Import its BOM before Spring Boot's to select the version tested with Docker Engine 29.3.1. |
| Docker | Working engine with Linux containers | Run PostgreSQL and Kafka dependencies required by the full integration suite. |

Use a JDK installation containing both `java` and `javac`. Point `JAVA_HOME` to its root and select its `bin` directory on `PATH`. A global Maven installation is optional because the wrapper selects Maven 3.9.9. The first wrapper invocation needs network access to download its configured distribution; Maven also downloads missing dependencies and plugins into its local cache.

[Wrapper properties](../../.mvn/wrapper/maven-wrapper.properties) record the Maven Central distribution URL and its SHA-256. That digest was derived from the Maven 3.9.9 archive previously checked against the published SHA-512 during baseline setup. The Windows wrapper quotes environment assignments and the Maven executable path to handle `&` in paths. Its `MVNW_REPOURL` handling preserves the correct Maven distribution suffix when a mirror base URL is supplied. The 4 November mirror check reused cached Maven; it verifies invocation and URL construction without establishing a fresh-download result.

Open a visible PowerShell session in the repository root before implementation test runs, as required by [AGENTS.md](../../AGENTS.md). Check the current environment after a restart:

```powershell
java -version
javac -version
.\mvnw.cmd --version
```

Confirm that Maven reports version 3.9.9 and Java 21. Its reported Java home should match the intended JDK. Record the actual vendor and patch version in run evidence. The earlier machine checks in the review are historical observations.

On Linux/macOS, invoke `./mvnw` with the Maven arguments shown below. On Windows, use the PowerShell helper for test and packaging commands.

## Windows test entry point

[scripts/Test-Backend.ps1](../../scripts/Test-Backend.ps1) runs Maven in the current visible terminal. It selects the repository root, forwards `MavenArguments` to `mvnw.cmd`, reports and returns Maven's exit code, and restores the caller's working directory and `MAVEN_OPTS` afterward. Calling the helper without arguments selects `test`.

Surefire's Windows fork launcher can misquote a temporary path containing `&`. When the current system temporary path contains that character, the helper creates `payment-backend-test-temp` beneath the Windows public-user directory and sets `java.io.tmpdir` through process-scoped `MAVEN_OPTS` for that run. Existing Maven options are preserved, and the original value is restored afterward. The helper keeps the workaround local to the command; machine-wide environment settings remain unchanged.

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', 'clean', 'test')
```

The array form passes each Maven option explicitly, including properties beginning with `-D`. Direct `.\mvnw.cmd` commands use the same Maven lifecycle; the helper also applies the Windows temporary-path handling.

## Lifecycle and test selection

Run commands from the repository root. The full reactor builds the parent, the shared `common` module, the four application modules, and `service-startup-tests`.

| Maven arguments passed to the Windows helper | Work performed | Infrastructure |
| --- | --- | --- |
| `@('-B', '-ntp', 'clean', 'test')` | Clean outputs, compile production and test sources, then run Surefire unit tests. | Unit tests use local code and test doubles. |
| `@('-B', '-ntp', 'package')` | Compile, run unit tests, and produce module artifacts. | Same unit-test requirements. |
| `@('-B', '-ntp', 'verify')` | Run preceding lifecycle phases, including unit tests and packaging, then run and verify Failsafe integration tests. | Docker for PostgreSQL and Kafka; an ephemeral loopback port for SMTP. |
| `@('-B', '-ntp', 'clean', 'verify')` | Repeat full verification from clean build outputs. | Same integration-test requirements. |

For milestone verification, clean all outputs and let independent modules finish if a module fails.

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', '-fae', 'clean', 'verify')
```

The corresponding Linux/macOS commands are `./mvnw -B -ntp clean test`, `./mvnw -B -ntp package`, and `./mvnw -B -ntp -fae clean verify`.

`-B` selects batch output and `-ntp` hides dependency transfer progress. Commands remain visible and stream their normal build and test output.

Unit classes end in `Test`; integration classes end in `IT`. Surefire and Failsafe are both pinned to 3.5.2. Keep the suffix consistent with the dependency and behaviour being exercised. Maven compiles all test sources during `test-compile`, including integration sources, even when the selected lifecycle stops before executing them.

Failsafe uses `${project.build.outputDirectory}` for application classes. This keeps Spring Boot integration-test discovery on compiled classes after packaging has produced a nested executable JAR. The Spring Boot repackage goal is bound explicitly for application modules; packaging and service startup remain distinct checks.

Use the full `verify` lifecycle for integration results so Failsafe checks failures after the integration-test phase. A successful `package` command establishes packaging and its preceding checks; application startup requires a separate run with the application's dependencies and configuration.

For a focused unit-test repair, include dependent reactor modules with `-am`:

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', '-pl', 'payment-service', '-am', '-Dtest=TransactionAggregateTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
```

The Surefire property permits upstream modules without that named test to complete. Check the payment-service report to confirm that the requested class actually executed. Follow a focused repair with the milestone's required reactor checks.

To exercise just the packaged transaction service and its broker, start Docker and run the following in the repository root.

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', '-pl', 'transaction-service', '-am', '-Dit.test=TransactionKafkaIT', '-Dfailsafe.failIfNoSpecifiedTests=false', 'clean', 'verify')
```

On Linux/macOS, use `./mvnw -B -ntp -pl transaction-service -am -Dit.test=TransactionKafkaIT -Dfailsafe.failIfNoSpecifiedTests=false clean verify`. This builds the transaction service and its dependencies, runs their unit tests, packages the JAR, then selects its Kafka integration test. Failsafe passes the path of that JAR to the test. Inspect the `TransactionKafkaIT` report in `transaction-service/target/failsafe-reports/` for its result and the adjacent `packaged-transaction.log` for child-process output.

For the packaged payment, fraud-detection, and notification checks, use the startup test module and build its dependencies in the same reactor.

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', '-pl', 'service-startup-tests', '-am', '-Dit.test=PackagedServicesIT', '-Dfailsafe.failIfNoSpecifiedTests=false', 'verify')
```

On Linux/macOS, use `./mvnw -B -ntp -pl service-startup-tests -am -Dit.test=PackagedServicesIT -Dfailsafe.failIfNoSpecifiedTests=false verify`. Each JAR starts in its own JVM with loopback HTTP and a real Kafka broker. Payment also uses fresh PostgreSQL, fraud detection uses a local provider address, and notification uses local SMTP. The tests check readiness, an application endpoint, and assigned Kafka topics for each service. A fourth case inspects the default notification port inside its JAR. Each child process writes a named log in `service-startup-tests/target/failsafe-reports/`. Transaction startup remains in `TransactionKafkaIT`. A full reactor `clean verify` includes both classes. [Packaged startup evidence](2026-11-05-b1-service-startup.md)

### Implemented integration-test scope

The current integration classes exercise these boundaries. Consult the dated evidence record for completed results and commands that select only part of the suite.

| Class | Dependency and intended coverage | Evidence boundary |
| --- | --- | --- |
| [TransactionHttpIT](../../transaction-service/src/test/java/com/paymentprocessor/transaction/TransactionHttpIT.java) | Spring Boot context and MockMvc HTTP requests, with a mocked Kafka template; request validation, accepted status, and command dispatch interaction. | HTTP/context behaviour with simulated producer acknowledgement. Real Kafka delivery and consumer processing require separate tests. |
| [TransactionKafkaIT](../../transaction-service/src/test/java/com/paymentprocessor/transaction/TransactionKafkaIT.java) | Launches the executable transaction JAR, waits for HTTP readiness, and submits a creation request. A consumer checks the returned transaction ID against the Kafka record key and JSON command on a real broker (`confluentinc/cp-kafka:7.6.1`). | Covers the public intake API through broker publication. Payment execution, durable intake, and restart recovery have separate acceptance criteria. |
| [PaymentEventStoreIT](../../payment-service/src/test/java/com/paymentprocessor/payment/PaymentEventStoreIT.java) | Boots the payment persistence configuration on fresh PostgreSQL 16.6. Exercises the production migration, Hibernate mapping, JDBC event persistence and replay, rollback, version conflicts, restart preservation and protection of pre-existing payment records. | Covers the database boundary. The assembled payment service and Kafka consumers have separate startup checks. |
| [SmtpEmailServiceIT](../../notification-service/src/test/java/com/paymentprocessor/notification/service/SmtpEmailServiceIT.java) | GreenMail on an ephemeral loopback port; SMTP submission and received sender, recipient, subject, and body. | Actual local SMTP transport; external provider delivery, Spring notification-service startup, and durable retry behaviour require separate checks. |
| [PackagedServicesIT](../../service-startup-tests/src/test/java/com/paymentprocessor/startup/PackagedServicesIT.java) | Starts the payment, fraud-detection, and notification JARs separately with local fixtures. Checks their HTTP responses and Kafka assignments. | Covers each application's startup, HTTP surface, and listener wiring. Cross-service payment processing belongs to journey tests. |

The event-store cases cover creation and replay, database rollback, and expected-version conflicts on missing and existing histories. Concurrent writers, reusing the same aggregate after rollback, and publishing committed events to Kafka need separate tests. The Kafka integration test gives its admin and consumer clients a 10-second request timeout within a 20-second operation timeout. These limits apply only to the test.

## Docker and integration dependencies

Before the full verification lifecycle, confirm the Docker server is reachable:

```powershell
docker version
docker info
```

For Docker Desktop installations using WSL 2, also inspect `wsl --status` after restarts or backend repairs. `PaymentEventStoreIT`, `TransactionKafkaIT`, and `PackagedServicesIT` require a working container runtime and access to their pinned images. Testcontainers starts and waits for those dependencies. `TransactionHttpIT` uses mocked Kafka, and `SmtpEmailServiceIT` starts a local mail server. These two focused checks can run independently of Docker. Full `verify` includes all five integration classes.

Testcontainers-managed dependencies are provisioned by the tests through Docker. Local Compose and these test-owned containers have separate configuration and lifecycles. Record container images, relevant configuration, and dependency failures with integration-run evidence. If infrastructure is unavailable, record that result and continue the independent unit checks; keep integration acceptance pending.

## Compose scope

The eight Compose services are the transaction, payment, fraud-detection, and notification applications, plus PostgreSQL, Kafka, Mailpit, and a local fraud-provider fixture. Build the JARs through Maven before building the application images. The local stack guide covers first setup with `.env.example`, image versions, ports, and local data persistence. [Local stack guide](local-stack.md)

The stack checks run separately from Maven in a visible PowerShell session.

```powershell
.\scripts\Test-LocalStack.ps1 -Run local
```

The helper creates an isolated PostgreSQL 16 Compose project with local credentials and dynamically allocated ports. It checks service health, broker assignments, real intake persistence, a separate payment-query fixture, and data preservation after restart and container recreation. Evidence is saved under `logs/validation/rtps-test-.../`.

Maven run 29 and stack run 32 passed. B2 is complete for the PostgreSQL 16 development stack. On 7 November the owner confirmed that legacy upgrades should be deferred because the project has no production deployment. The uncompleted PostgreSQL 15 rehearsal was cancelled. [B2 evidence](2026-11-06-b2-local-stack.md)

## Visible output and evidence

Explain the test purpose before the run, execute the actual command in the visible PowerShell session, and interpret the completed result afterward. The following example retains output while streaming it to the terminal:

```powershell
$buildEvidenceDir = Join-Path (Get-Location) 'logs\validation'
New-Item -ItemType Directory -Force -Path $buildEvidenceDir | Out-Null
$buildLogPath = Join-Path $buildEvidenceDir ('unit-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
& .\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', 'clean', 'test') *>&1 | Tee-Object -FilePath $buildLogPath
$buildExitCode = $LASTEXITCODE
"Maven exit code: $buildExitCode" | Tee-Object -FilePath $buildLogPath -Append
```

Capture `$LASTEXITCODE` immediately after the helper returns Maven's result. An active process or a partial reactor summary is progress evidence. Completion requires the command's exit code and final test reports.

Surefire writes reports beneath each module's `target/surefire-reports/`; Failsafe uses `target/failsafe-reports/`. Inspect executed, failed, errored, and skipped counts per module and test class. Class naming alone establishes no coverage: a Spring context test with a mocked Kafka template proves only its exercised context/HTTP boundary, while a provider double establishes simulated outcomes.

Local `logs/`, `target/`, and `*.log` files are ignored by Git. Preserve selected, reviewed output in the dated milestone evidence as tracked `.txt` files, with credentials and machine-specific sensitive details removed. Include:

- revision and branch, run date, operating system, JDK vendor/patch, and Maven version;
- exact commands, relevant environment configuration, dependency images, and exit codes;
- per-module test counts and any skipped or excluded scope;
- reproduced failure, implemented repair, completed verification, and remaining acceptance criteria.

B1 and the agreed B2 development scope are complete. B3 will establish controlled payment journeys. Durable workflows, tenant isolation, and wallet and ledger correctness have subsequent acceptance criteria. [B1 startup evidence](2026-11-05-b1-service-startup.md) · [Payment database evidence](2026-11-05-payment-database.md)

## Local CI policy

Run the repository's configured verification before each milestone commit and push, preserving exact commands, exit codes, and missing prerequisites. Keep GitHub Actions CI disabled unless explicitly requested by the owner. Maven checks compilation, toolchain enforcement, unit tests, packaging, and integration boundaries including packaged startup. The separate stack script checks assembled services and database preservation. Lint, dependency-security, and complete journey gates remain implementation work. Follow the current milestone's acceptance criteria.

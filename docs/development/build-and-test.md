# Build and test

Updated: 5 November 2026. Scope: B1.1, the reproducible build, unit tests, and selected integration boundaries within [B1: Build and bootstrap](../reviews/2026-10-19/remediation-plan.md#ordered-work-packages-and-acceptance-gates).

Run the full suite locally with JDK 21, the Maven wrapper, and a working Linux container engine. On 5 November, `clean verify` passed all 37 unit tests and eight integration tests with zero skips. See the [B1.1 evidence record](2026-11-04-b1-1-evidence.md) and the [19 October compilation baseline](../reviews/2026-10-19/README.md#executed-checks).

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

Run commands from the repository root so Maven can build the parent and all five child modules: `common`, `transaction-service`, `payment-service`, `fraud-detection-service`, and `notification-service`.

| Maven arguments passed to the Windows helper | Work performed | Infrastructure |
| --- | --- | --- |
| `@('-B', '-ntp', 'clean', 'test')` | Clean outputs, compile production and test sources, then run Surefire unit tests. | Unit tests use local code and test doubles. |
| `@('-B', '-ntp', 'package')` | Compile, run unit tests, and produce module artifacts. | Same unit-test requirements. |
| `@('-B', '-ntp', 'verify')` | Run preceding lifecycle phases, including unit tests and packaging, then run and verify Failsafe integration tests. | Docker for PostgreSQL and Kafka; an ephemeral loopback port for SMTP. |
| `@('-B', '-ntp', 'clean', 'verify')` | Repeat full verification from clean build outputs. | Same integration-test requirements. |

For example, run the full verification lifecycle on Windows with:

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', 'verify')
```

The corresponding Linux/macOS commands are `./mvnw -B -ntp clean test`, `./mvnw -B -ntp package`, and `./mvnw -B -ntp verify`.

`-B` selects batch output and `-ntp` hides dependency transfer progress. Commands remain visible and stream their normal build and test output.

Unit classes end in `Test`; integration classes end in `IT`. Surefire and Failsafe are both pinned to 3.5.2. Keep the suffix consistent with the dependency and behaviour being exercised. Maven compiles all test sources during `test-compile`, including integration sources, even when the selected lifecycle stops before executing them.

Failsafe uses `${project.build.outputDirectory}` for application classes. This keeps Spring Boot integration-test discovery on compiled classes after packaging has produced a nested executable JAR. The Spring Boot repackage goal is bound explicitly for application modules; packaging and service startup remain distinct checks.

Use the full `verify` lifecycle for integration results so Failsafe checks failures after the integration-test phase. A successful `package` command establishes packaging and its preceding checks; application startup requires a separate run with the application's dependencies and configuration.

For a focused unit-test repair, include dependent reactor modules with `-am`:

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', '-pl', 'payment-service', '-am', '-Dtest=TransactionAggregateTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
```

The Surefire property permits upstream modules without that named test to complete. Check the payment-service report to confirm that the requested class actually executed. Follow a focused repair with the milestone's required reactor checks.

### Implemented integration-test scope

The current integration classes exercise these boundaries. Consult the dated evidence record for completed results and commands that select only part of the suite.

| Class | Dependency and intended coverage | Evidence boundary |
| --- | --- | --- |
| [TransactionHttpIT](../../transaction-service/src/test/java/com/paymentprocessor/transaction/TransactionHttpIT.java) | Spring Boot context and MockMvc HTTP requests, with a mocked Kafka template; request validation, accepted status, and command dispatch interaction. | HTTP/context behaviour with simulated producer acknowledgement. Real Kafka delivery and consumer processing require separate tests. |
| [TransactionKafkaIT](../../transaction-service/src/test/java/com/paymentprocessor/transaction/TransactionKafkaIT.java) | Testcontainers `confluentinc/cp-kafka:7.6.1`; actual producer serialization and consumption of the keyed creation command. | Intake-to-broker boundary only; payment processing, broker recovery, and durable intake require separate scenarios. |
| [PaymentEventStoreIT](../../payment-service/src/test/java/com/paymentprocessor/payment/PaymentEventStoreIT.java) | Testcontainers `postgres:16.6-alpine`; JDBC event persistence and replay, rollback, and stale expected-version rejection. | The test creates its own `domain_events` schema. Production migrations and payment-service bootstrap remain B2/B1 work respectively. |
| [SmtpEmailServiceIT](../../notification-service/src/test/java/com/paymentprocessor/notification/service/SmtpEmailServiceIT.java) | GreenMail on an ephemeral loopback port; SMTP submission and received sender, recipient, subject, and body. | Actual local SMTP transport; external provider delivery, Spring notification-service startup, and durable retry behaviour require separate checks. |

The event-store cases cover creation and replay, database rollback, and expected-version conflicts on missing and existing histories. Concurrent writers, reusing the same aggregate after rollback, and publishing committed events to Kafka need separate tests. The Kafka integration test gives its admin and consumer clients a 10-second request timeout within a 20-second operation timeout. These limits apply only to the test.

## Docker and integration dependencies

Before the full verification lifecycle, confirm the Docker server is reachable:

```powershell
docker version
docker info
```

For Docker Desktop installations using WSL 2, also inspect `wsl --status` after restarts or backend repairs. `PaymentEventStoreIT` and `TransactionKafkaIT` require a working container runtime and access to their pinned images. Testcontainers starts and waits for those dependencies. `TransactionHttpIT` uses mocked Kafka, and `SmtpEmailServiceIT` starts a local mail server; these focused checks can execute independently of Docker. Full `verify` includes all four integration classes.

Testcontainers-managed dependencies are provisioned by the tests through Docker. Local Compose and these test-owned containers have separate configuration and lifecycles. Record container images, relevant configuration, and dependency failures with integration-run evidence. If infrastructure is unavailable, record that result and continue the independent unit checks; keep integration acceptance pending.

## Compose scope

The current `docker-compose.yml` defines infrastructure only:

- Kafka and ZooKeeper;
- PostgreSQL and Redis;
- Prometheus and Grafana.

Inspect the list with:

```powershell
docker compose config --services
```

The file references `DB_USER`, `DB_PASSWORD`, and `GRAFANA_ADMIN_PASSWORD`, plus local database initialization and Prometheus configuration paths. Supply local values through the environment or the ignored `.env` file when working on that stack. B2 owns the complete configuration, readiness checks, and reproducible fresh startup. Application services have their own startup and configuration work; a Compose configuration parse establishes only that the file can be interpreted.

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

B1.1 evidence is one part of B1. The remaining bootstrap gates include all service contexts and executable JAR startup with required dependencies. Database migrations, durable workflows, tenant isolation, provider sandbox journeys, and wallet/ledger correctness retain their own milestones and evidence.

## Local CI policy

Run the repository's configured verification before each milestone commit and push, preserving exact commands, exit codes, and missing prerequisites. Keep GitHub Actions CI disabled unless explicitly requested by the owner. A disabled hosted workflow provides no test result. This repository currently configures compilation, toolchain enforcement, unit tests, packaging, and integration tests in Maven; separate lint, dependency-security, system, and end-to-end journey gates remain implementation work. Follow the current milestone's acceptance criteria rather than inferring those checks from a successful Maven command.

# B1 packaged service startup

**B1 passed on 5 November 2026.** Run 25 built all seven reactor entries from a clean checkout of the staged files. All four packaged services started separately with their required local dependencies. Eleven fresh XML reports recorded 37 unit and 14 integration tests, with zero failures, errors, or skips. [B1 acceptance criteria](../reviews/2026-10-19/remediation-plan.md#ordered-work-packages-and-acceptance-gates)

## Why B1 stayed open

| Run | What the local run established |
| --- | --- |
| 18 | A clean reactor built all four executable JARs and passed 37 unit and eight integration tests. Manifest and archive inspection established that each JAR contained its launcher, entry point, and dependencies. [Build evidence](2026-11-04-b1-1-evidence.md) |
| 19–20 | The transaction JAR ran in its own JVM, reported readiness, accepted an HTTP request, and published the corresponding keyed command to Kafka. Run 19 exposed a Windows teardown error. After the repair, run 20 passed all 45 tests and preserved the inherited port-file fixture. This established packaged startup for one of the four services. [Transaction startup evidence](2026-11-05-transaction-startup.md) |
| 21–22 | Payment startup depends on a database schema that matches its mappings. Run 21 exposed the missing read-model table. Run 22 passed 37 unit and ten integration tests, including migration and replay against PostgreSQL. This B2 prerequisite exercised the payment persistence configuration. Starting the entire payment JAR also loads its web, messaging, and application components. [Payment database evidence](2026-11-05-payment-database.md) |

## First packaged startup run

Run 23 ran from 18:33:46 to 18:38:12 EAT on 5 November. Maven exited 1. Four cases ran in `PackagedServicesIT`, with one pass, three failures, zero errors, and zero skips. The focused command was `scripts/Test-Backend.ps1 -B -ntp -pl service-startup-tests -am -Dit.test=PackagedServicesIT -Dfailsafe.failIfNoSpecifiedTests=false verify`.

| Case | Result |
| --- | --- |
| Fraud startup | Passed. With a local provider address, the JAR reached readiness, returned its service identity over HTTP, and acquired the expected `fraud-check-requested` Kafka assignment. |
| Payment startup | The payment context exited before readiness because Spring could not find a `TransactionQueryService` bean. |
| Notification startup | The notification channel factory rejected a Spring-proxied channel during startup. The JAR exited before readiness. |
| Notification default port | The packaged configuration returned no value for `server.port`. The expected default was `8083`. |

## Full verification from a fresh checkout

Run 24 ran `scripts/Test-Backend.ps1 -B -ntp -fae clean verify` from a fresh checkout of the staged files, 18:52:17–18:57:50 EAT. Fifty tests passed, one failed, and none errored or skipped. Maven exited 1. Fraud startup passed. Notification passed its HTTP, local SMTP health and Kafka checks, and its packaged default port was 8083.

Registering the payment query service allowed Hibernate to validate the repository queries. It then rejected `findByDateRange` because the query selected `T` but declared `t`. The repair uses `t` in both places.

Run 25 repeated the same full command from a clean checkout, 23:45:28–23:51:26 EAT on 5 November. Maven exited 0. The ports below were allocated for this run. [Local verification commands](build-and-test.md#lifecycle-and-test-selection)

| Packaged service | Run 25 observation |
| --- | --- |
| Transaction (51281) | The service accepted a real HTTP POST and published a command to Kafka. The response ID matched the command ID and record key. |
| Payment (51411) | The service became ready against fresh PostgreSQL, returned a database-backed summary with zero transactions, and acquired the expected Kafka topics across three consumer groups. |
| Fraud detection (51389) | The service became ready with a local provider address, returned its identity over HTTP, and acquired the expected Kafka topic. |
| Notification (51376) | Readiness, service identity, and aggregate health passed with local SMTP. Three consumer groups acquired their expected topics. A separate check confirmed the packaged default port of 8083. |

B2 has its versioned payment schema. Its remaining local-stack work includes starting the services together through Compose. Provider confirmation, financial posting, and merchant-visible completion belong to the subsequent payment journey.

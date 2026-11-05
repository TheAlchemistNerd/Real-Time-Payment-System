# Packaged transaction-service startup

5 November 2026, Africa/Nairobi. Source under test is the working tree on `fix/backend-build-baseline` following B1.1 commit `075c12c`. The previous milestone verified JAR structure for all four services. This check follows one accepted HTTP request through the executable transaction JAR to a real Kafka broker. [B1.1 evidence](2026-11-04-b1-1-evidence.md)

## Acceptance criteria

1. Start a real Kafka broker using Testcontainers and `confluentinc/cp-kafka:7.6.1`. Create the `create-transaction` topic and start the freshly built transaction service in a separate JVM with `java -jar`.
2. Bind the service to an ephemeral loopback port. Read the allocated port from the test's temporary directory and wait for `GET /actuator/health/readiness` to return HTTP 200 with status `UP`.
3. Submit a valid `POST /api/v1/transactions`. Require HTTP 202, status `PENDING`, and a nonblank transaction ID.
4. Read a record from the topic. Require the response ID to match both the Kafka key and the ID in the decoded JSON command. Compare the user ID, amount, currency, payment method, description, and webhook URL with the original request.

The test starts its own broker, stops the child JVM after execution, and writes application output to `transaction-service/target/failsafe-reports/packaged-transaction.log`. It requires JDK 21, the Maven wrapper, a Linux container engine with access to the Kafka image, and local loopback connections. The focused and full commands are in the [build and test guide](build-and-test.md#lifecycle-and-test-selection).

## Local verification

| Run | Command and purpose | Result |
| --- | --- | --- |
| 19 | `-B -ntp -fae clean verify` across the reactor. | 44 passed, one teardown error, Maven exit 1. The JAR reached readiness, accepted the HTTP request, and published to Kafka. JUnit then reported an `IOException` while deleting the test directory that Windows still held open as the child process's working directory. |
| 20 | `scripts/Test-Backend.ps1 -B -ntp -fae clean verify` with an inherited `PORTFILE` pointing to a test fixture, 18:00–18:07 EAT. | 37 unit and eight integration tests passed, zero failures, errors or skips, exit 0. The fixture retained its original bytes. |

For the rerun, the child JVM used the JAR's build directory as its working directory. The test supplied an absolute `-DPORTFILE` path in JUnit's temporary directory. The service bound loopback port 62423 and passed the readiness probe. The accepted HTTP response, Kafka key, and decoded command shared the same transaction ID, and the child stopped successfully. An earlier launch stopped before Maven when the console lacked a hashing command. The completed run compared the guard file's bytes directly.

This gate ends when Kafka delivers the creation command to the test consumer. Payment processing, ledger posting, merchant-visible completion, and notification delivery belong to the subsequent multi-service journey.

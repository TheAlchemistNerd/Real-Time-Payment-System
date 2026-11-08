# B2 local stack evidence

**B2 development scope passed.** This milestone assembles the four application services and their local dependencies through Docker Compose using PostgreSQL 16. B1 established that each packaged service starts separately. B2's payment schema migration validates fresh PostgreSQL, replay, and persistence across configuration restarts. [B1 startup results](2026-11-05-b1-service-startup.md) · [Payment migration results](2026-11-05-payment-database.md)

Verified acceptance covers image builds, service readiness, internal broker and database connections, and preservation of schema `001-payment-schema` and its records through restart and container recreation. On 7 November the owner confirmed that this is a development project with no production deployment and deferred legacy database upgrades. Setup and restart commands are in the [local stack guide](local-stack.md). [Sanitized execution evidence](2026-11-06-b2-verification.txt)

| Check | Evidence |
| --- | --- |
| Local build and configured tests | Run 29 passed 37 unit and 14 integration tests. All 51 tests had zero failures, errors, or skips. Maven exited 0. |
| Compose build and startup from an empty environment | Run 32 built all five local images and started all eight services. |
| Four services ready with their local dependencies | Run 32 passed application health, all nine topic/group assignments, and real HTTP intake persistence. |
| Legacy database upgrades | Deferred by the owner on 7 November; outside this development milestone. |
| Stop, restart, and recovery procedure | Run 32 exited 0 after application restart, full container recreation with retained volumes, persistence checks, and cleanup. |

Run `28-b2-red` exited 1 against the earlier Compose configuration. The required-service check reported missing transaction, payment, fraud-detection, notification, Mailpit, and fraud-fixture services. This establishes the failure the assembled stack must repair.

## Executed checks

Run 29's full Maven verification ran on 6 November from 11:21:36 to 11:28:40 UTC (14:21:36–14:28:40 EAT). Its result is recorded locally in `logs/validation/29-b2-backend.json`. The subsequent stack check ran from 11:28:42 to 11:41:40 UTC, built the images, and stopped at the unhealthy fraud fixture. Its isolated containers, network, and volumes were removed.

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', '-fae', 'clean', 'verify')
.\scripts\Test-LocalStack.ps1 -Run 29-b2-stack
```

Run 30 started the same fraud image alone with its 64 MiB memory limit. Docker reported a healthy running container, zero health-check failures, and `OOMKilled=false`. A direct request to `/health` returned HTTP 200 and status `UP`. This isolated result leaves assembled startup acceptance open. The diagnostic transcript is `logs/validation/30-b2-fixture-debug.log`.

```powershell
docker run --detach --name rtps-b2-fixture-debug --memory 64m rtps-test-29-b2-stack-035009b9ac-fraud-stub
Start-Sleep -Seconds 15
docker inspect --format '{{json .State}}' rtps-b2-fixture-debug
docker exec rtps-b2-fixture-debug python -c "from urllib.request import urlopen; print(urlopen('http://127.0.0.1:8080/health', timeout=5).read())"
docker logs rtps-b2-fixture-debug
docker rm --force rtps-b2-fixture-debug
```

Run 31 (`scripts/Test-LocalStack.ps1 -Run 31-b2-stack`) exited 1. Its saved container states show seven healthy services and payment-service still starting when its probe failure threshold was reached. Payment had connected to PostgreSQL and successfully applied `001-payment-schema`, then entered Hibernate initialization. No container was OOM-killed. Several dependency probes timed out; the host had approximately 390 MiB of free physical memory during startup. Cleanup exited 0. The isolated fixture had already passed, so this failure is distinct from run 29's missing probe diagnostics.

The Kafka probe previously inherited the broker's `-Xms256m -Xmx512m` heap settings in a separate JVM every ten seconds. The repair uses `-Xms16m -Xmx64m` for that probe every thirty seconds, and a bounded 128 MiB heap for subscription diagnostics. Application probes allow a four-minute startup grace period; the Compose command waits up to ten minutes. These are local startup settings, not performance acceptance.

Run 32 (`scripts/Test-LocalStack.ps1 -Run 32-b2-stack`) passed from 20:32:57 to 20:57:38 UTC on 6 November (23:32:57–23:57:38 EAT). All eight services became healthy. The empty event store and read model each had zero rows before intake. A real request produced event `TX-a6076c8a-d2a4-45a5-86de-23ae5f50ac17`; its identity, user, amount `12.34`, currency `USD`, and method `CREDIT_CARD` remained unchanged. The separate `b2-query-fixture` row returned `12.34:USD:COMPLETED`, and the HTTP summary reported one completed fixture. All checks passed again after restarting the four applications and recreating all containers with retained volumes. Migration history stayed `001-payment-schema:9:f1efc124a515ff0dd1105acd4c0c0d95`. Cleanup exited 0 and the overall result was 0.

Run 33 was an exploratory PostgreSQL 15 upgrade rehearsal. It was cancelled during the image download after the owner's scope clarification. Its disposable-project cleanup exited 0. It produced no upgrade pass. The provisional upgrade switch was removed; the shipped helper retains the PostgreSQL 16 command sequence verified in run 32. Legacy upgrade support can be assessed when an actual migration requirement exists.

The configured local CI gate is Maven `clean verify`; no Java source or Maven configuration changed after run 29. The Compose acceptance gate is run 32. The final simplification removes the unused upgrade path and updates documentation; it preserves the tested PostgreSQL 16 behavior. GitHub Actions settings were not changed. B3 is the next milestone.

## B3 carry-forward

Pair review identified that the existing external fraud client constructs its authorization value as `"Bearer" + apiKey`, omitting the separating space. The local fixture validates the JSON request and accepts this header. B3 must repair the header and test the provider contract against an adapter that checks authentication. [External fraud client](../../fraud-detection-service/src/main/java/com/paymentprocessor/fraud/client/ExternalFraudApiClient.java)

The verification script checks a real HTTP request through Kafka into the event store. A separate SQL fixture checks query access. Complete payment success and decline flows, external provider confirmation, ledger posting, tenant isolation, and durable publication remain subsequent milestones.

Final run 34 passed PowerShell and Python syntax checks, `git diff --check`, and the updated documentation file links (exit 0). [Execution evidence](2026-11-06-b2-verification.txt)

# Local development stack

This Compose stack starts the transaction, payment, fraud-detection, and notification services with PostgreSQL, Kafka, Mailpit, and a local fraud-provider fixture. The PostgreSQL 16 development stack passed its B2 checks. [Dated evidence](2026-11-06-b2-local-stack.md)

## Build and start

Use JDK 21, the Maven 3.9.9 wrapper, and a working Docker engine with Linux containers. Docker Compose must support `up --wait`. The application Dockerfiles copy the JARs produced by Maven, so build and verify the backend before building images. Run the commands from the repository root in the visible PowerShell session.

```powershell
java -version
.\mvnw.cmd --version
docker info
docker compose version
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', '-fae', 'clean', 'verify')
```

After Maven exits 0, create `.env` from `.env.example` on first setup. Choose a local database password before starting the stack. Keep an existing `.env` and update its settings as needed. The repository ignores this file.

```powershell
Copy-Item -LiteralPath .env.example -Destination .env
docker compose config --services
docker compose up --build --detach --wait --wait-timeout 600
docker compose ps
```

`DB_NAME` defaults to `payment_db` and `DB_USERNAME` to `payment_user`. `DB_PASSWORD` is required. These settings supply both PostgreSQL and the payment service. Keep the Compose project name consistent across starts so it reuses its named volumes.

| Component | Pinned image |
| --- | --- |
| Application runtime | `eclipse-temurin:21.0.13_9-jre-jammy` |
| PostgreSQL | `postgres:16.6-alpine` |
| Kafka | `confluentinc/cp-kafka:7.6.1` |
| Mailpit | `axllent/mailpit:v1.20.3` |
| Fraud fixture runtime | `python:3.12.10-alpine3.21` |

Kafka uses a single KRaft broker. Applications connect to `kafka:29092` inside the Compose network. PostgreSQL uses `postgres:5432` internally. Published ports bind to the local loopback address.

The former ZooKeeper service is replaced by KRaft. The stack includes the dependencies used by these applications; optional Redis caching and the former Prometheus/Grafana declarations are outside this startup configuration. Telemetry and deployment infrastructure are part of B10. Existing volumes from an older project remain available for an explicitly planned migration or recovery.

| Interface | Default local address | Environment setting |
| --- | --- | --- |
| Transaction API | `http://127.0.0.1:8080` | `TRANSACTION_PORT` |
| Payment queries | `http://127.0.0.1:8081` | `PAYMENT_PORT` |
| Fraud API | `http://127.0.0.1:8082` | `FRAUD_PORT` |
| Notification API | `http://127.0.0.1:8083` | `NOTIFICATION_PORT` |
| Kafka host listener | `localhost:9092` | `KAFKA_PORT` and `KAFKA_ADVERTISED_PORT` |
| Mailpit inbox | `http://127.0.0.1:8025` | `MAILPIT_PORT` |

Set both Kafka port settings to the same value when changing the host listener. Each application exposes `/actuator/health` and `/actuator/health/readiness`. Docker health checks wait for readiness, allowing a four-minute application startup grace period. Compose waits up to ten minutes. The payment service applies Liquibase before Hibernate validates the schema. Kafka's health probe runs in a separate JVM with a small heap to limit probe overhead.

Allow capacity for Docker and the host applications together. This test host has 8 GiB of RAM and exposes about 4 GiB to Docker. An earlier run with only about 390 MiB free on the host encountered slow startup and probe timeouts. Close unused applications before assembling the stack on a constrained machine. The image build also needs free disk space for image layers and database volumes.

Mailpit captures local email. Its permissive SMTP authentication and the example credentials serve this development environment. The fraud fixture returns an approval with risk score `0.1` for a valid `transaction_id` request. Provider sandbox verification and operational telemetry belong to later milestones.

## Verify the assembled stack

After the full Maven run, use the automated checks in a visible PowerShell window.

```powershell
.\scripts\Test-LocalStack.ps1 -Run local
```

Each invocation creates a unique `rtps-test-...` project, temporary local credentials, and dynamically allocated host ports. It builds and starts all eight services, reads application health, and checks Kafka group assignments. A real HTTP request must reach the payment event store through Kafka. A separate SQL fixture exercises the payment query endpoint. The normal check then restarts applications and recreates containers, verifying that the event, query fixture, and Liquibase checksum survive.

Evidence is written beneath `logs/validation/rtps-test-.../`, including commands, service logs, image information, and `result.json`. Check the result's exit code and observations. The script checks ownership before creating resources and removes its own test containers and volumes afterward. A cleanup failure makes the run fail.

For failed cleanup, identify the exact project in `result.json` and inspect its containers and volumes using their `com.docker.compose.project` label. Use the generated environment when repeating cleanup. Restrict volume removal to the script-created project recorded in that run. Retain the regular development project's database and the evidence directory.

## Stop and restart

`docker compose stop` stops the development containers. `docker compose down` removes the containers and network while retaining the named PostgreSQL and Kafka volumes. A subsequent start reuses their data:

```powershell
docker compose up --detach --wait --wait-timeout 600
```

The automated check verifies this retained-data path. Its isolated test project is cleaned up afterward; the normal development project keeps its volumes.

For a fresh development database, `docker compose down --volumes` removes that project's database and broker data. Use it when intentionally resetting local development state, then start the stack again.

This project uses PostgreSQL 16. Legacy database upgrade support is deferred by the owner's 7 November scope decision. Provider payment journeys follow in B3; staging deployment and operational recovery have later acceptance criteria.

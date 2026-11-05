# Payment database setup and migration evidence

This stage 2 milestone gives the payment service a versioned PostgreSQL schema. The transaction intake service remains a separate bounded context. The payment service owns its event history and transaction read model. The wallet ledger has its own later milestone.

## How startup works

Point `SPRING_DATASOURCE_URL` at an existing PostgreSQL database and set `DB_USERNAME` and `DB_PASSWORD` for a role with permission to create tables and indexes in the target schema. The service's local database default is `payment_db`. The existing Compose file provisions `payment_system`, so set the URL explicitly when using that database.

Spring Boot runs Liquibase before Hibernate validates the entity mapping. The first change creates `domain_events` and `transaction_read_model` in one transaction. The event table stores JSONB payloads and enforces uniqueness for each aggregate and sequence number. The read model stores the amount, currency, payment method, status, timestamps and optimistic version. Liquibase records the applied changeset and checks its identity and checksum on subsequent starts. Append a new changeset for each schema change. [Production changelog](../../payment-service/src/main/resources/db/changelog/db.changelog-master.xml) · [Baseline SQL](../../payment-service/src/main/resources/db/changelog/001-payment-schema.sql)

If either payment table already exists without this migration's history, the baseline stops before applying its SQL. Inventory the existing tables and rows and rehearse an explicit upgrade against a restored copy before adopting that database. In particular, establish the source of currency for any historical rows missing that field. For a failed rollout on a managed database, preserve its migration history and restore a verified backup or apply a reviewed forward migration.

## Reproduce locally

With JDK 21 and Docker available, run the following in a visible PowerShell window from the repository root.

```powershell
.\scripts\Test-Backend.ps1 -MavenArguments @('-B', '-ntp', '-pl', 'payment-service', '-am', '-Dit.test=PaymentEventStoreIT', '-Dfailsafe.failIfNoSpecifiedTests=false', 'clean', 'verify')
```

The test starts PostgreSQL from `postgres:16.6-alpine` and runs the application's persistence configuration on it. It writes an event and a payment projection using the JDBC store and Hibernate mapping, restarts that configuration, and reads both back. A separate case seeds an unversioned schema and checks that its payment row survives a refused baseline. Kafka and provider adapters belong to the assembled-service checks. [Database integration test](../../payment-service/src/test/java/com/paymentprocessor/payment/PaymentEventStoreIT.java)

## Local results

On 5 November 2026, run 21 used the focused command above with `-Dit.test=PaymentEventStoreIT#restartPreservesEventHistoryAndMappedPaymentDetails` and `verify` on the existing build. PostgreSQL started successfully. Hibernate reported the missing `transaction_read_model` table and Maven exited 1 before the restart case could write data.

Run 22 executed `scripts/Test-Backend.ps1 -B -ntp -fae clean verify` in visible PowerShell on Windows from 18:17 to 18:21 EAT. The source was based on commit `5f04955`. The build used Temurin 21.0.10, Maven 3.9.9, Docker Desktop's Linux engine 29.3.1, and Liquibase 4.24.0. Maven exited 0. Ten fresh XML reports recorded **37 unit tests and 10 integration tests**, with zero failures, errors or skips. The six PostgreSQL cases passed. The other integration checks exercised transaction HTTP validation, packaged transaction intake through Kafka, and local SMTP delivery.

The fresh schema accepted the baseline once. Two further starts reported one previously applied changeset. Hibernate read the stored USD 100.00 credit-card projection, and the event store replayed its first version. In the unversioned-schema case, the migration stopped, the original payment row survived, and the event table remained absent. Inspection of the payment JAR confirmed that both changelog files and the managed Liquibase library were packaged.

The remaining stage 2 work is to align the Compose database, broker and application configuration, start the other packaged services together, and verify an upgrade between released schema versions before introducing the next migration.

package com.paymentprocessor.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessor.common.command.CreateTransactionCommand;
import com.paymentprocessor.common.event.TransactionCreatedEvent;
import com.paymentprocessor.common.exception.TransactionNotFoundException;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.payment.aggregate.TransactionAggregate;
import com.paymentprocessor.payment.query.TransactionReadModel;
import com.paymentprocessor.payment.repository.JdbcEventStore;
import com.paymentprocessor.payment.repository.TransactionAggregateRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** Exercises the payment service's real schema, Hibernate mappings and JDBC event store. */
@Testcontainers
class PaymentEventStoreIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private TransactionAggregateRepository repository;
    private JdbcEventStore store;
    private TransactionTemplate transactions;

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = TransactionReadModel.class)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, LiquibaseAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class})
    static class PaymentPersistenceConfiguration {}

    private static ConfigurableApplicationContext startPersistence(String jdbcUrl) {
        return new SpringApplicationBuilder(PaymentPersistenceConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
    }

    @BeforeAll
    static void initializeDatabase() {
        // Use the production changelog and ddl-auto setting before any test writes data.
        startPersistence(POSTGRES.getJdbcUrl()).close();
    }

    @BeforeEach
    void setUp() {
        var datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("DELETE FROM domain_events");
        store = new JdbcEventStore(jdbc, new ObjectMapper().findAndRegisterModules());
        repository = new TransactionAggregateRepository(store);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(datasource));
    }

    private TransactionAggregate newTransaction() {
        var aggregate = new TransactionAggregate("tx-123");
        aggregate.handle(new CreateTransactionCommand("tx-123", "user-1", new BigDecimal("100.00"),
                Currency.USD, PaymentMethod.CREDIT_CARD, "Order", "https://merchant.example/events"));
        return aggregate;
    }

    @Test
    void restartPreservesEventHistoryAndMappedPaymentDetails() {
        transactions.executeWithoutResult(status -> repository.save(newTransaction()));
        var projection = TransactionReadModel.fromTransactionCreated("tx-123", "user-1",
                new BigDecimal("100.00"), Currency.USD, PaymentMethod.CREDIT_CARD, "Order",
                LocalDateTime.of(2026, 1, 1, 12, 0));
        try (var context = startPersistence(POSTGRES.getJdbcUrl());
             var session = context.getBean(EntityManagerFactory.class).createEntityManager()) {
            session.getTransaction().begin();
            session.persist(projection);
            session.getTransaction().commit();
        }
        try (var restarted = startPersistence(POSTGRES.getJdbcUrl());
             var session = restarted.getBean(EntityManagerFactory.class).createEntityManager()) {
            var saved = session.find(TransactionReadModel.class, "tx-123");
            assertThat(saved.getAmount()).isEqualByComparingTo("100.00");
            assertThat(saved.getCurrency()).isEqualTo(Currency.USD);
            assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
            assertThat(repository.findById("tx-123").getVersion()).isEqualTo(1);
            var jdbc = new JdbcTemplate(restarted.getBean(javax.sql.DataSource.class));
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM databasechangelog WHERE id = '001-payment-schema'", Long.class))
                    .isEqualTo(1L);
        }
    }

    @Test
    void protectsExistingPaymentTablesWithoutMigrationHistory() {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("CREATE SCHEMA legacy");
        jdbc.execute("CREATE TABLE legacy.transaction_read_model (transaction_id TEXT PRIMARY KEY)");
        jdbc.update("INSERT INTO legacy.transaction_read_model VALUES (?)", "existing-payment");
        String legacyUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=legacy";

        assertThatThrownBy(() -> startPersistence(legacyUrl).close())
                .hasStackTraceContaining("Existing payment tables require an explicit migration");
        assertThat(jdbc.queryForList("SELECT transaction_id FROM legacy.transaction_read_model", String.class))
                .containsExactly("existing-payment");
        assertThat(jdbc.queryForObject("SELECT to_regclass('legacy.domain_events') IS NULL", Boolean.class))
                .isTrue();
    }

    @Test
    void persistsCreationAndReplaysNotificationPreference() {
        transactions.executeWithoutResult(status -> repository.save(newTransaction()));
        var loaded = repository.findById("tx-123");
        assertThat(loaded.getAmount()).isEqualByComparingTo("100.00");
        assertThat(loaded.getUserId()).isEqualTo("user-1");
        assertThat(loaded.getNotificationType()).isEqualTo(NotificationType.WEBHOOK);
        assertThat(loaded.getVersion()).isEqualTo(1);
        assertThat(loaded.getUncommittedEvents()).isEmpty();
    }

    @Test
    void rolledBackCreationLeavesNoPersistedTransaction() {
        transactions.executeWithoutResult(status -> {
            repository.save(newTransaction());
            status.setRollbackOnly();
        });
        assertThatThrownBy(() -> repository.findById("tx-123")).isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void staleExpectedVersionDoesNotOverwriteHistory() {
        transactions.executeWithoutResult(status -> repository.save(newTransaction()));
        var duplicate = new TransactionCreatedEvent("tx-123", "other-user", BigDecimal.ONE,
                Currency.USD, PaymentMethod.CREDIT_CARD, "Duplicate", null);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                store.saveEvents("tx-123", List.of(duplicate), 0)))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findById("tx-123").getUserId()).isEqualTo("user-1");
        assertThat(store.getEventsForAggregate("tx-123")).hasSize(1);
    }

    @Test
    void missingHistoryRejectsANonzeroExpectedVersionWithoutCreatingASequenceGap() {
        var event = new TransactionCreatedEvent("missing-transaction", "user-1", BigDecimal.ONE,
                Currency.USD, PaymentMethod.CREDIT_CARD, "Missing history", null);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                store.saveEvents("missing-transaction", List.of(event), 3)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(store.getEventsForAggregate("missing-transaction")).isEmpty();
        assertThat(store.getLastVersionForAggregate("missing-transaction")).isEmpty();
    }
}

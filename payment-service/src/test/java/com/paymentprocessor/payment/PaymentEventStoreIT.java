package com.paymentprocessor.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessor.common.command.CreateTransactionCommand;
import com.paymentprocessor.common.event.TransactionCreatedEvent;
import com.paymentprocessor.common.exception.TransactionNotFoundException;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.payment.aggregate.TransactionAggregate;
import com.paymentprocessor.payment.repository.JdbcEventStore;
import com.paymentprocessor.payment.repository.TransactionAggregateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** JDBC boundary tests; production migration/bootstrap validation belongs to B2. */
@Testcontainers
class PaymentEventStoreIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private TransactionAggregateRepository repository;
    private JdbcEventStore store;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        var datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(datasource);
        jdbc.execute("DROP TABLE IF EXISTS domain_events");
        jdbc.execute("""
                CREATE TABLE domain_events (
                    aggregate_identifier VARCHAR(255) NOT NULL,
                    sequence_number BIGINT NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    payload JSONB NOT NULL,
                    timestamp TIMESTAMP NOT NULL,
                    PRIMARY KEY (aggregate_identifier, sequence_number)
                )
                """);
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

package com.paymentprocessor.payment.aggregate;

import com.paymentprocessor.common.event.FraudCheckCompletedEvent;
import com.paymentprocessor.common.event.PaymentFailedEvent;
import com.paymentprocessor.common.event.PaymentProcessedEvent;
import com.paymentprocessor.common.event.PaymentProcessingStartedEvent;
import com.paymentprocessor.common.event.TransactionCreatedEvent;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.payment.command.CreateTransactionCommand;
import com.paymentprocessor.payment.command.ProcessFraudCheckCommand;
import com.paymentprocessor.payment.command.ProcessPaymentCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TransactionAggregateTest {

    private TransactionAggregate aggregate;
    private String transactionId;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID().toString();
        aggregate = new TransactionAggregate(transactionId);
    }

    @Nested
    @DisplayName("Given a new TransactionAggregate")
    class NewTransactionAggregate {

        @Test
        @DisplayName("when handling a CreateTransactionCommand, it should apply a TransactionCreatedEvent and change status to PENDING")
        void handleCreateTransactionCommand() {
            // Given
            var command = new CreateTransactionCommand(
                    transactionId,
                    "user123",
                    new BigDecimal("100.00"),
                    Currency.USD,
                    PaymentMethod.CREDIT_CARD,
                    "Test Transaction"
            );

            // When
            aggregate.handle(command);

            // Then
            assertAll(
                    () -> assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.PENDING),
                    () -> assertThat(aggregate.getUncommittedEvents()).hasSize(1),
                    () -> assertThat(aggregate.getUncommittedEvents().get(0)).isInstanceOf(TransactionCreatedEvent.class)
            );

            var event = (TransactionCreatedEvent) aggregate.getUncommittedEvents().get(0);
            assertAll(
                    () -> assertThat(event.transactionId()).isEqualTo(transactionId),
                    () -> assertThat(event.userId()).isEqualTo("user123"),
                    () -> assertThat(event.amount()).isEqualTo(new BigDecimal("100.00"))
            );
        }
    }

    @Nested
    @DisplayName("Given an existing PENDING TransactionAggregate")
    class ExistingPendingTransactionAggregate {

        @BeforeEach
        void setUp() {
            var createCommand = new CreateTransactionCommand(
                    transactionId,
                    "user123",
                    new BigDecimal("100.00"),
                    Currency.USD,
                    PaymentMethod.CREDIT_CARD,
                    "Test Transaction"
            );
            aggregate.handle(createCommand);
            aggregate.clearUncommittedEvents();
        }

        @Test
        @DisplayName("when handling a ProcessFraudCheckCommand with passed=false, it should apply FraudCheckCompletedEvent and change status to FRAUD_CHECK_FAILED")
        void handleProcessFraudCheckCommandFailed() {
            // Given
            var command = new ProcessFraudCheckCommand(transactionId, false, 95.0, "High risk score");

            // When
            aggregate.handle(command);

            // Then
            assertAll(
                    () -> assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.FRAUD_CHECK_FAILED),
                    () -> assertThat(aggregate.getUncommittedEvents()).hasSize(1),
                    () -> assertThat(aggregate.getUncommittedEvents().get(0)).isInstanceOf(FraudCheckCompletedEvent.class)
            );

            var event = (FraudCheckCompletedEvent) aggregate.getUncommittedEvents().get(0);
            assertThat(event.passed()).isFalse();
        }

        @Test
        @DisplayName("when handling a ProcessFraudCheckCommand with passed=true, it should apply FraudCheckCompletedEvent and change status to FRAUD_CHECK_PASSED")
        void handleProcessFraudCheckCommandPassed() {
            // Given
            var command = new ProcessFraudCheckCommand(transactionId, true, 10.0, "Low risk");

            // When
            aggregate.handle(command);

            // Then
            assertAll(
                    () -> assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.FRAUD_CHECK_PASSED),
                    () -> assertThat(aggregate.getUncommittedEvents()).hasSize(1),
                    () -> assertThat(aggregate.getUncommittedEvents().get(0)).isInstanceOf(FraudCheckCompletedEvent.class)
            );

            var event = (FraudCheckCompletedEvent) aggregate.getUncommittedEvents().get(0);
            assertThat(event.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Given an existing FRAUD_CHECK_PASSED TransactionAggregate")
    class ExistingFraudCheckPassedTransactionAggregate {

        @BeforeEach
        void setUp() {
            // Initial state
            aggregate.on(new TransactionCreatedEvent(transactionId, "user123", new BigDecimal("200.00"), Currency.EUR, PaymentMethod.BANK_TRANSFER, "desc"));
            aggregate.on(new FraudCheckCompletedEvent(transactionId, true, 5.0, "Low risk"));
            aggregate.clearUncommittedEvents();
        }

        @Test
        @DisplayName("when handling a ProcessPaymentCommand, it should apply PaymentProcessingStartedEvent and PaymentProcessedEvent and change status to COMPLETED")
        void handleProcessPaymentCommand() {
            // Given
            var command = new ProcessPaymentCommand(transactionId, new BigDecimal("200.00"), "Stripe");

            // When
            aggregate.handle(command);

            // Then
            assertAll(
                    () -> assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.COMPLETED),
                    () -> assertThat(aggregate.getUncommittedEvents()).hasSize(2),
                    () -> assertThat(aggregate.getUncommittedEvents().get(0)).isInstanceOf(PaymentProcessingStartedEvent.class),
                    () -> assertThat(aggregate.getUncommittedEvents().get(1)).isInstanceOf(PaymentProcessedEvent.class)
            );

            var event = (PaymentProcessedEvent) aggregate.getUncommittedEvents().get(1);
            assertThat(event.paymentGatewayTransactionId()).isNotNull();
        }
    }
}

package com.paymentprocessor.payment.aggregate;

import com.paymentprocessor.common.command.*;
import com.paymentprocessor.common.event.*;
import com.paymentprocessor.common.exception.InvalidTransactionException;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.common.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TransactionAggregateTest {
    private static final String ID = "tx-123";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 19, 10, 0);
    private static final LocalDateTime COMPLETED_AT = CREATED_AT.plusMinutes(1);

    private CreateTransactionCommand create(String id, String webhook) {
        return new CreateTransactionCommand(id, "user-1", AMOUNT, Currency.USD,
                PaymentMethod.CREDIT_CARD, "Order", webhook);
    }

    private TransactionCreatedEvent created(String webhook) {
        return new TransactionCreatedEvent("created-event", ID, CREATED_AT, "user-1", AMOUNT, Currency.USD,
                PaymentMethod.CREDIT_CARD, "Order", webhook);
    }

    @Test
    void firstCreationIsAcceptedForAnAllocatedIdentity() {
        var aggregate = new TransactionAggregate(ID);
        aggregate.handle(create(ID, "https://merchant.example/events"));
        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(aggregate.getVersion()).isEqualTo(1);
        assertThat(aggregate.getUncommittedEvents()).singleElement()
                .isInstanceOfSatisfying(TransactionCreatedEvent.class, event -> {
                    assertThat(event.transactionId()).isEqualTo(ID);
                    assertThat(event.amount()).isEqualByComparingTo(AMOUNT);
                    assertThat(event.webhookUrl()).isEqualTo("https://merchant.example/events");
                });
    }

    @Test
    void duplicateCreationLeavesExistingStateAndEventsUnchanged() {
        var aggregate = new TransactionAggregate(ID);
        aggregate.loadFromHistory(List.of(created(null)));
        assertThatThrownBy(() -> aggregate.handle(create(ID, null)))
                .isInstanceOf(InvalidTransactionException.class);
        assertThat(aggregate.getVersion()).isEqualTo(1);
        assertThat(aggregate.getUncommittedEvents()).isEmpty();
    }

    @Test
    void commandCannotReplaceAllocatedIdentity() {
        var aggregate = new TransactionAggregate(ID);
        assertThatThrownBy(() -> aggregate.handle(create("another-transaction", null)))
                .isInstanceOf(InvalidTransactionException.class);
        assertThat(aggregate.getTransactionId()).isEqualTo(ID);
        assertThat(aggregate.getVersion()).isZero();
        assertThat(aggregate.getUncommittedEvents()).isEmpty();
    }

    @Test
    void notificationPreferenceSurvivesHistoryReplay() {
        var aggregate = new TransactionAggregate(ID);
        aggregate.loadFromHistory(List.of(created("https://merchant.example/events")));
        assertThat(aggregate.getNotificationType()).isEqualTo(NotificationType.WEBHOOK);
        assertThat(aggregate.getWebhookUrl()).isEqualTo("https://merchant.example/events");
        assertThat(aggregate.getUncommittedEvents()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void absentWebhookRetainsEmailFallback(String webhook) {
        var aggregate = new TransactionAggregate(ID);
        aggregate.loadFromHistory(List.of(created(webhook)));
        assertThat(aggregate.getNotificationType()).isEqualTo(NotificationType.EMAIL);
    }

    @Test
    void passedFraudCheckEnablesPaymentProcessing() {
        var aggregate = new TransactionAggregate(ID);
        aggregate.loadFromHistory(List.of(created(null)));
        aggregate.handle(new ProcessFraudCheckCommand(ID, true, 0.1, "Low risk"));
        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.FRAUD_CHECK_PASSED);
        assertThat(aggregate.getUncommittedEvents()).singleElement().isInstanceOf(FraudCheckCompletedEvent.class);
    }

    @Test
    void declinedFraudCheckPreventsPaymentProcessing() {
        var aggregate = new TransactionAggregate(ID);
        aggregate.loadFromHistory(List.of(created(null)));
        aggregate.handle(new ProcessFraudCheckCommand(ID, false, 0.9, "High risk"));
        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.FRAUD_CHECK_FAILED);
        assertThatThrownBy(() -> aggregate.handle(new ProcessPaymentCommand(ID, AMOUNT, "test")))
                .isInstanceOf(InvalidTransactionException.class);
        assertThat(aggregate.getUncommittedEvents()).hasSize(1);
    }

    @Test
    void paymentCommandStartsProcessingWithoutInventingProviderSuccess() {
        var aggregate = new TransactionAggregate(ID);
        aggregate.loadFromHistory(List.of(created(null), new FraudCheckCompletedEvent(ID, true, 0.1, "Low risk")));
        aggregate.handle(new ProcessPaymentCommand(ID, AMOUNT, "test"));
        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.PAYMENT_PROCESSING);
        assertThat(aggregate.getPaymentGatewayTransactionId()).isNull();
        assertThat(aggregate.getCompletedAt()).isNull();
        assertThat(aggregate.getUncommittedEvents()).singleElement().isInstanceOf(PaymentProcessingStartedEvent.class);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"COMPLETED", "FAILED"})
    void completionTransitionsProcessingToItsRecordedOutcomeAndReplaysWithoutNewEvents(TransactionStatus finalStatus) {
        var completion = new TransactionCompletedEvent("completed-event", ID, COMPLETED_AT, finalStatus,
                AMOUNT, "user-1", NotificationType.EMAIL, null);
        var history = List.<BaseEvent>of(created(null),
                new FraudCheckCompletedEvent("fraud-event", ID, CREATED_AT.plusSeconds(1), true, 0.1, "Low risk"),
                new PaymentProcessingStartedEvent("processing-event", ID, CREATED_AT.plusSeconds(2),
                        AMOUNT, PaymentMethod.CREDIT_CARD, "test"));
        var aggregate = new TransactionAggregate(ID);
        aggregate.loadFromHistory(history);
        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.PAYMENT_PROCESSING);
        assertThat(aggregate.getCompletedAt()).isNull();

        aggregate.apply(completion);

        assertThat(aggregate.getStatus()).isEqualTo(finalStatus);
        assertThat(aggregate.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(aggregate.getVersion()).isEqualTo(4);
        assertThat(aggregate.getUncommittedEvents()).containsExactly(completion);
        var replay = new TransactionAggregate(ID);
        var allEvents = new java.util.ArrayList<>(history);
        allEvents.add(completion);
        replay.loadFromHistory(allEvents);
        assertThat(replay.getStatus()).isEqualTo(finalStatus);
        assertThat(replay.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(replay.getVersion()).isEqualTo(4);
        assertThat(replay.getUncommittedEvents()).isEmpty();
    }

    @Test
    void completionReplayPreservesProviderReferenceAndUsesTheCompletionTimestamp() {
        var processedAt = COMPLETED_AT.minusSeconds(5);
        var history = List.<BaseEvent>of(created(null),
                new FraudCheckCompletedEvent("fraud-event", ID, CREATED_AT.plusSeconds(1), true, 0.1, "Low risk"),
                new PaymentProcessingStartedEvent("processing-event", ID, CREATED_AT.plusSeconds(2),
                        AMOUNT, PaymentMethod.CREDIT_CARD, "test"),
                new PaymentProcessedEvent("processed-event", ID, processedAt, AMOUNT, "provider-123", "test"),
                new TransactionCompletedEvent("completed-event", ID, COMPLETED_AT, TransactionStatus.COMPLETED,
                        AMOUNT, "user-1", NotificationType.EMAIL, null));
        var replay = new TransactionAggregate(ID);

        replay.loadFromHistory(history);

        assertThat(replay.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(replay.getPaymentGatewayTransactionId()).isEqualTo("provider-123");
        assertThat(replay.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(replay.getVersion()).isEqualTo(5);
        assertThat(replay.getUncommittedEvents()).isEmpty();
    }
}

package com.paymentprocessor.payment.query;

import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.common.model.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_read_model")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TransactionReadModel {

    @Id
    @Column(name = "transaction_id")
    @EqualsAndHashCode.Include
    private String transactionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "fraud_reason")
    private String fraudReason;

    @Column(name = "payment_gateway_transaction_id")
    private String paymentGatewayTransactionId;

    @Version
    @Column(name = "version")
    private Long version;

    // Factory method for creating from transaction created event
    public static TransactionReadModel fromTransactionCreated (
            String transactionId, String userId, BigDecimal amount,
            Currency currency, PaymentMethod paymentMethod,
            String description, LocalDateTime createdAt) {
        return TransactionReadModel.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .description(description)
                .status(TransactionStatus.PENDING)
                .createdAt(createdAt)
                .build();

    }

    // Helper methods for status updates
    public void updateFraudCheckResult(boolean passed, double riskScore,
                                       String reason, LocalDateTime timestamp) {
        this.riskScore = riskScore;
        this.fraudReason = reason;
        this.status = passed ? TransactionStatus.FRAUD_CHECK_PASSED : TransactionStatus.FRAUD_CHECK_FAILED;
        if (!passed) {
            this.completedAt = timestamp;
        }
    }

    public void updatePaymentProcessingStarted() {
        this.status = TransactionStatus.PAYMENT_PROCESSING;
    }

    public void updatePaymentCompleted(String gatewayTransactionId, LocalDateTime timestamp) {
        this.status = TransactionStatus.COMPLETED;
        this.paymentGatewayTransactionId = gatewayTransactionId;
        this.completedAt = timestamp;
    }

    public void updatePaymentFailed(LocalDateTime timestamp) {
        this.status = TransactionStatus.FAILED;
        this.completedAt = timestamp;
    }
}

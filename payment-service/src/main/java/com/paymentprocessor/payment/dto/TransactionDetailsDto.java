package com.paymentprocessor.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.common.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDetailsDto(
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("userId") String userId,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("currency") Currency currency,
        @JsonProperty("paymentMethod") PaymentMethod paymentMethod,
        @JsonProperty("description") String description,
        @JsonProperty("status") TransactionStatus status,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("completedAt") LocalDateTime completedAt,
        @JsonProperty("riskScore") Double riskScore,
        @JsonProperty("fraudReason") String fraudReason,
        @JsonProperty("paymentGatewayTransactionId") String paymentGatewayTransactionId
) {
    public static TransactionDetailsDto from(com.paymentprocessor.payment.query.TransactionReadModel readModel) {
        return new TransactionDetailsDto(
                readModel.getTransactionId(),
                readModel.getUserId(),
                readModel.getAmount(),
                readModel.getCurrency(),
                readModel.getPaymentMethod(),
                readModel.getDescription(),
                readModel.getStatus(),
                readModel.getCreatedAt(),
                readModel.getCompletedAt(),
                readModel.getRiskScore(),
                readModel.getFraudReason(),
                readModel.getPaymentGatewayTransactionId()
        );
    }
}

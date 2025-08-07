package com.paymentprocessor.payment.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProcessPaymentCommand(
        @NotNull String transactionId,
        @NotNull @Positive BigDecimal amount,
        @NotNull String paymentGateway
) implements BaseCommand {

    public ProcessPaymentCommand {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (paymentGateway == null || paymentGateway.isBlank()) {
            throw new IllegalArgumentException("Payment gateway cannot be null or blank");
        }
    }
}
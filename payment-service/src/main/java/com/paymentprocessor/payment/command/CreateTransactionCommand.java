package com.paymentprocessor.payment.command;

import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransactionCommand(
        @NotNull String transactionId,
        @NotNull String userId,
        @NotNull @Positive BigDecimal amount,
        @NotNull Currency currency,
        @NotNull PaymentMethod paymentMethod,
        String description
) implements BaseCommand {

    public CreateTransactionCommand {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
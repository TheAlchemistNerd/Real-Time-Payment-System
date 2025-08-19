package com.paymentprocessor.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record TransactionSummaryDto(
       @JsonProperty("totalTransactions") long totalTransactions,
       @JsonProperty("completedTransactions") long completedTransactions,
       @JsonProperty("failedTransactions") long failedTransactions,
       @JsonProperty("pendingTransactions") long pendingTransactions,
       @JsonProperty("totalAmountToday") BigDecimal totalAmountToday
) {}

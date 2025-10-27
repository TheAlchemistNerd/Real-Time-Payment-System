package com.paymentprocessor.payment.dto;

public record PaymentResponse(
    String gatewayTransactionId,
    boolean success,
    String message
) {}

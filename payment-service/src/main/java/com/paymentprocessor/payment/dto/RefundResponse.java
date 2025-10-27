package com.paymentprocessor.payment.dto;

public record RefundResponse(
    String gatewayRefundId,
    boolean success,
    String message
) {}

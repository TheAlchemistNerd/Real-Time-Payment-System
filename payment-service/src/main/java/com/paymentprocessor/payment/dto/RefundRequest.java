package com.paymentprocessor.payment.dto;

import java.math.BigDecimal;

public record RefundRequest(
    String originalGatewayTransactionId,
    BigDecimal amount,
    String reason
) {}

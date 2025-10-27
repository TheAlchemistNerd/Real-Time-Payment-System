package com.paymentprocessor.payment.dto;

import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import java.math.BigDecimal;

public record PaymentRequest(
    String transactionId,
    BigDecimal amount,
    Currency currency,
    PaymentMethod paymentMethod,
    String cardNumber,
    String cardExpiry,
    String cardCvv
) {}

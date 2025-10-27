package com.paymentprocessor.common.model;

public enum TransactionStatus {
    PENDING,
    FRAUD_CHECK_PASSED,
    FRAUD_CHECK_FAILED,
    PAYMENT_PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED
}
package com.paymentprocessor.common.util;

import java.util.UUID;

public class TransactionIdGenerator {

    private static final String PREFIX = "TX";

    public String generateTransactionId() {
        return PREFIX + "-" + UUID.randomUUID().toString().replace("_", "");
    }

    public boolean isValidTransactionId(String transactionId) {
        return transactionId != null &&
                transactionId.startsWith(PREFIX + "-") &&
                transactionId.length() == 35; // TX- + 32 CHARS
    }
}

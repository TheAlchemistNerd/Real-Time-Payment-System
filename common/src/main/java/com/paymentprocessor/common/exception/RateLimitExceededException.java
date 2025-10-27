package com.paymentprocessor.common.exception;

public class RateLimitExceededException extends PaymentProcessingException {
    private final String clientId;
    private final int currentRate;
    private final int maxRate;


    public RateLimitExceededException(String clientId, int currentRate, int maxRate) {
        super("RATE_LIMIT_EXCEEDED",
                String.format("Rate limit exceeded for client %s: %d/%d requests",
                        clientId, currentRate, maxRate),
                "Too many requests. Please try again later");
        this.clientId = clientId;
        this.currentRate = currentRate;
        this.maxRate = maxRate;
    }

    public String getClientId() {
        return clientId;
    }

    public int getCurrentRate() {
        return currentRate;
    }

    public int getMaxRate() {
        return maxRate;
    }


}

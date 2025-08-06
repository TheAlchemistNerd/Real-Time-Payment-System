package com.paymentprocessor.common.exception;

public class CircuitBreakerOpenException extends PaymentProcessingException {
    private final String serviceName;

    public CircuitBreakerOpenException(String serviceName) {
        super("CIRCUIT_BREAKER_OPEN",
                "Circuit breaker open for service: " + serviceName,
                "Service is temporarily unavailable");
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}

package com.paymentprocessor.common.exception;

public class ExternalServiceException extends PaymentProcessingException{
    private final String serviceName;


    public ExternalServiceException(String serviceName, String message) {
        super("EXTERNAL_SERVICE_ERROR",
                "External service error [" + serviceName + "]: " + message,
                "A required service is temporarily unavailable");
        this.serviceName = serviceName;
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super("EXTERNAL_SERVICE_ERROR",
                "External service error [" + serviceName + "]: " + message,
                "A required service is temporarily unavailable", cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}

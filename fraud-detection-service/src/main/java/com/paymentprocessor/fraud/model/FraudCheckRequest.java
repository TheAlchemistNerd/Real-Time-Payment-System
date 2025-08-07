package com.paymentprocessor.fraud.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class FraudCheckRequest {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("user_agent")
    private String userAgent;

    @JsonProperty("transaction_id")
    private String transactionId;

    public FraudCheckRequest() {}

    public FraudCheckRequest(String userId, BigDecimal transactionAmount, String currency,
                             String ipAddress, String userAgent, String transactionId) {
        this.userId = userId;
        this.transactionAmount = transactionAmount;
        this.currency = currency;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.transactionId = transactionId;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
}
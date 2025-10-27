package com.paymentprocessor.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("status")
    private int status;

    @JsonProperty("error")
    private String error;

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("path")
    private String path;

    @JsonProperty("additionalInfo")
    private Map<String, Object> additionalInfo;

    private ErrorResponse() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ErrorResponse errorResponse = new ErrorResponse();

        public Builder timestamp(LocalDateTime timestamp) {
            errorResponse.timestamp = timestamp;
            return this;
        }

        public Builder status(int status) {
            errorResponse.status = status;
            return this;
        }

        public Builder error(String error) {
            errorResponse.error = error;
            return this;
        }

        public Builder errorCode(String errorCode) {
            errorResponse.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            errorResponse.message = message;
            return this;
        }

        public Builder path(String path) {
            errorResponse.path = path;
            return this;
        }

        public Builder additionalInfo(Map<String, Object> additionalInfo) {
            errorResponse.additionalInfo = additionalInfo;
            return  this;
        }

        public ErrorResponse build() {
            return errorResponse;
        }
    }

    public LocalDateTime getTimestamp() {return timestamp; }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Map<String, Object> getAdditionalInfo() {
        return additionalInfo;
    }
}

package com.paymentprocessor.fraud.client;

import com.paymentprocessor.common.exception.ExternalServiceException;
import com.paymentprocessor.fraud.model.FraudCheckRequest;
import com.paymentprocessor.fraud.model.FraudCheckResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class ExternalFraudApiClient {
    private final WebClient webClient;
    private final String apiKey;

    public ExternalFraudApiClient(WebClient.Builder webClientBuilder,
                                  @Value("${fraud.api.base-url:https://api.siftscience.com}")String baseUrl,
                                  @Value("${fraud.api.key:test-key}") String apiKey) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.apiKey = apiKey;
    }

    @CircuitBreaker(name = "fraudApi", fallbackMethod = "checkFraudFallback")
    @Retry(name = "fraudApi")
    @TimeLimiter(name = "fraudApi")
    @Timed(value = "fraud.api.call.time", description = "Time taken for fraud API calls")
    @Counted(value = "fraud.api.call.count", description = "Number of fraud API calls")
    public CompletableFuture<FraudCheckResponse> checkFraud(FraudCheckRequest request) {
        log.info("Calling external fraud API for transaction: {}", request.transactionId());
        return webClient.post()
                .uri("v1/transactions/check")
                .header("Authorization", "Bearer" + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FraudCheckResponse.class)
                .timeout(Duration.ofSeconds(2))
                .doOnSuccess(response ->
                        log.info("Fraud API call successful for transaction: {} with risk score: {}",
                        request.transactionId(), response.riskScore()))
                .doOnError(response ->
                        log.info("Fraud API call failed for transaction: {}",
                                request.transactionId()))
                .onErrorMap(WebClientResponseException.class, this::mapWebClientException)
                .toFuture();
    }


    // Circuit Breaker fallback method
    public CompletableFuture<FraudCheckResponse> checkFraudFallback(FraudCheckRequest request, Exception ex) {
        log.warn("Fraud API fallback triggered for transaction: {} - {}",
                request.transactionId(), ex.getMessage());
        // Return a safe default response (low risk score to allow transaction)
        FraudCheckResponse fallbackResponse = new FraudCheckResponse(
                request.transactionId(),
                0.1,
                "APPROVE",
                "Fraud service unavailable - approved with low risk score",
                0.5
        );
        return CompletableFuture.completedFuture(fallbackResponse);
    }

    private ExternalServiceException mapWebClientException(WebClientResponseException e) {
        String message = String.format("Fraud API returned status %d: %s",
                e.getStatusCode().value(),
                e.getResponseBodyAsString());
        return new ExternalServiceException("FRAUDAPI", message, e);
    }
}

package com.paymentprocessor.payment.config;

import io.github.resilience4j.core.IntervalFunction;
import com.paymentprocessor.common.exception.PaymentProcessingFailedException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public RetryRegistry retryRegistry() {
        var config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2.0))
                .retryOnException(throwable ->
                        throwable instanceof PaymentProcessingFailedException ||
                                throwable instanceof org.springframework.dao.TransientDataAccessException)
                .build();

        return RetryRegistry.of(config);
    }

    @Bean
    public Retry sagaProcessingRetry(RetryRegistry retryRegistry) {
        return retryRegistry.retry("sagaProcessing");
    }

    @Bean
    public Retry commandProcessingRetry(RetryRegistry retryRegistry) {
        var config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(500), 1.5))
                .retryOnException(throwable -> throwable instanceof org.springframework.dao.OptimisticLockingFailureException)
                .build();

        retryRegistry.addConfiguration("commandProcessingConfig", config);
        return retryRegistry.retry("commandProcessing", "commandProcessingConfig");
    }
}
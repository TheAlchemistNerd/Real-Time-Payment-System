package com.paymentprocessor.transaction.config;

import com.paymentprocessor.common.util.CorrelationIdGenerator;
import com.paymentprocessor.common.util.TransactionIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit dependencies of transaction intake, without scanning unrelated shared components. */
@Configuration(proxyBeanMethods = false)
public class TransactionConfiguration {
    @Bean
    TransactionIdGenerator transactionIdGenerator() {
        return new TransactionIdGenerator();
    }

    @Bean
    CorrelationIdGenerator correlationIdGenerator() {
        return new CorrelationIdGenerator();
    }
}

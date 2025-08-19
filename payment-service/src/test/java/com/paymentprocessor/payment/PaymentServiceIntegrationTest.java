package com.paymentprocessor.payment;

import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.payment.aggregate.TransactionAggregate;
import com.paymentprocessor.payment.command.CreateTransactionCommand;
import com.paymentprocessor.payment.repository.TransactionAggregateRepository;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@Transactional
public class PaymentServiceIntegrationTest {

    @Autowired
    private TransactionAggregateRepository  repository;

    @Test
    public void shouldCreateAndPersistTransaction() {
        // Given
        String transactionId = UUID.randomUUID().toString();
        var command = new CreateTransactionCommand(
                transactionId,
                "user123",
                new BigDecimal("100.00"),
                Currency.USD,
                PaymentMethod.CREDIT_CARD,
                "Test transaction"
        );

        // When
        var aggregate = new TransactionAggregate(transactionId);
        aggregate.handle(command);
        repository.save(aggregate);

        // Then
        var savedAggregate = repository.findById(transactionId);
        assertThat(savedAggregate).isNotNull();
        assertThat(savedAggregate.getUserId()).isEqualTo("user123");
        assertThat(savedAggregate.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(savedAggregate.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }
}

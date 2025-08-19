package com.paymentprocessor.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.transaction.dto.CreateTransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
public class TransactionServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void shouldCreateTransaction_whenValidRequest_returnsAccepted() throws Exception {
        // Given
        var request = new CreateTransactionRequest(
                "user123",
                new BigDecimal("150.00"),
                Currency.EUR,
                PaymentMethod.BANK_TRANSFER,
                "Integration Test Transaction",
                "http://webhook.site/test",
                "127.0.0.1",
                "Test-User-Agent"
        );

        // When & Then
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionId").isString())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // Verify kafka interaction
        verify(kafkaTemplate).send(eq("transaction-created"), any(String.class), any());
    }

    @Test
    void shouldReturnBadRequest_whenInvalidAmount() throws Exception {
        // Given
        var request = new CreateTransactionRequest(
                "user456",
                new BigDecimal("-50.00"), // Invalid amount
                Currency.GBP,
                PaymentMethod.CREDIT_CARD,
                "Invalid Test Transaction",
                null,
                "192.168.1.1",
                "Test-User-Agent"
        );

        // When & Then
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

package com.paymentprocessor.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessor.common.command.CreateTransactionCommand;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.transaction.dto.CreateTransactionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TransactionHttpIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void configureProducerAcknowledgement() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

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
        var result = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionId").isString())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        var transactionId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("transactionId").asText();
        var command = ArgumentCaptor.forClass(CreateTransactionCommand.class);
        verify(kafkaTemplate).send(eq("create-transaction"), eq(transactionId), command.capture());
        assertThat(command.getValue().transactionId()).isEqualTo(transactionId);
        assertThat(command.getValue().userId()).isEqualTo(request.userId());
        assertThat(command.getValue().amount()).isEqualByComparingTo(request.amount());
        assertThat(command.getValue().currency()).isEqualTo(request.currency());
        assertThat(command.getValue().paymentMethod()).isEqualTo(request.paymentMethod());
        assertThat(command.getValue().webhookUrl()).isEqualTo(request.webhookUrl());
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
        verifyNoInteractions(kafkaTemplate);
    }
}

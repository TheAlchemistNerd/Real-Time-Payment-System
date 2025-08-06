package com.paymentprocessor.transaction.controller;

import com.paymentprocessor.common.util.CorrelationIdGenerator;
import com.paymentprocessor.transaction.dto.CreateTransactionRequest;
import com.paymentprocessor.transaction.dto.CreateTransactionResponse;
import com.paymentprocessor.transaction.service.TransactionService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transaction API", description = "Payment transaction management")
public class TransactionController {

    private final TransactionService transactionService;
    private final CorrelationIdGenerator correlationIdGenerator;

    public TransactionController(TransactionService transactionService,
                                 CorrelationIdGenerator correlationIdGenerator) {
        this.transactionService = transactionService;
        this.correlationIdGenerator = correlationIdGenerator;
    }

    @PostMapping
    @RateLimiter(name = "CreateTransaction", fallbackMethod = "CreateTransactionFallback")
    @Timed(value = "transaction.creation.time", description = "Time taken to create a transaction")
    @Counted(value = "transaction.creation.count", description = "Number of transaction creation requests")
    @Operation(summary = "Create a new payment transaction",
            description = "Initiates a new payment transaction and returns transaction ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Transaction accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<CreateTransactionResponse> createTransaction (
            @Valid @RequestBody CreateTransactionRequest request,
            HttpServletRequest httpRequest) {
        var correlationId = correlationIdGenerator.generateCorrelationId();
        correlationIdGenerator.setCorrelationId(correlationId);

        try {
            // Extract and enrich request with metadata
            var enrichedRequest = enrichRequestWithMetadata(request, httpRequest);

            log.info("Creating transaction for user: {} with correlation ID: {}",
                    enrichedRequest.userId(), correlationId);

            var response = transactionService.createTransaction(enrichedRequest,
                    correlationId);

            log.info("Transaction created successfully: {} with correlation ID: {}",
                    response.transactionId(), correlationId);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } finally {
            correlationIdGenerator.clearCorrelationId();
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint", description = "Returns service health status")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Transaction Service is healthy");
    }

    private CreateTransactionRequest enrichRequestWithMetadata(@Valid CreateTransactionRequest request,
                                                               HttpServletRequest httpRequest) {
        var clientIp = extractClientIpAddress(httpRequest);
        var userAgent = httpRequest.getHeader("User-Agent");

        // Create new request with metadata if not already present
        if(request.ipAddress() == null || request.userAgent() == null) {
            return new CreateTransactionRequest(
                    request.userId(),
                    request.amount(),
                    request.currency(),
                    request.paymentMethod(),
                    request.description(),
                    request.webhookUrl(),
                    clientIp,
                    userAgent
            );
        }

        return request;
    }

    private String extractClientIpAddress(HttpServletRequest httpRequest) {
        var xForwardedFor =httpRequest.getHeader("X-Forwarded-For");
        if(xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        var xRealIP = httpRequest.getHeader("X-Real-IP");
        if(xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return httpRequest.getRemoteAddr();
    }

}

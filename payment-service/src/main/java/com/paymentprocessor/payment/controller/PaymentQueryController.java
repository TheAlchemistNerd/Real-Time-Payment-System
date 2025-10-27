package com.paymentprocessor.payment.controller;

import com.paymentprocessor.common.exception.TransactionNotFoundException;
import com.paymentprocessor.payment.dto.TransactionDetailsDto;
import com.paymentprocessor.payment.dto.TransactionSummaryDto;
import com.paymentprocessor.payment.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment Query API", description = "Query payment transaction information")
public class PaymentQueryController {

    private final TransactionQueryService queryService;

    public PaymentQueryController(TransactionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction details", description = "Retrieve details of a specific transaction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<TransactionDetailsDto> getTransaction(
            @Parameter(description = "Transaction ID") @PathVariable String transactionId) {

        log.info("Retrieving transaction details for: {}", transactionId);

        return queryService.findByTransactionId(transactionId)
                .map(TransactionDetailsDto::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user transactions", description = "Retrieve all transactions for a specific user")
    public ResponseEntity<List<TransactionDetailsDto>> getUserTransactions(
            @Parameter(description = "User ID") @PathVariable String userId) {

        log.info("Retrieving transactions for user: {}", userId);

        var transactions = queryService.findByUserId(userId)
                .stream()
                .map(TransactionDetailsDto::from)
                .toList();

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/user/{userId}/paged")
    @Operation(summary = "Get user transactions (paged)", description = "Retrieve paginated transactions for a user")
    public ResponseEntity<Page<TransactionDetailsDto>> getUserTransactionsPaged(
            @Parameter(description = "User ID") @PathVariable String userId,
            Pageable pageable) {

        log.info("Retrieving paged transactions for user: {} with page: {}", userId, pageable);

        var transactions = queryService.findByUserIdPaged(userId, pageable)
                .map(TransactionDetailsDto::from);

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/user/{userId}/active")
    @Operation(summary = "Get active user transactions", description = "Retrieve active transactions for a user")
    public ResponseEntity<List<TransactionDetailsDto>> getActiveUserTransactions(
            @Parameter(description = "User ID") @PathVariable String userId) {

        log.info("Retrieving active transactions for user: {}", userId);

        var transactions = queryService.findActiveTransactionsByUser(userId)
                .stream()
                .map(TransactionDetailsDto::from)
                .toList();

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/date-range")
    @Operation(summary = "Get transactions by date range", description = "Retrieve transactions within a date range")
    public ResponseEntity<List<TransactionDetailsDto>> getTransactionsByDateRange(
            @Parameter(description = "Start date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("Retrieving transactions from {} to {}", startDate, endDate);

        var transactions = queryService.findByDateRange(startDate, endDate)
                .stream()
                .map(TransactionDetailsDto::from)
                .toList();

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/summary")
    @Operation(summary = "Get transaction summary", description = "Retrieve transaction statistics summary")
    public ResponseEntity<TransactionSummaryDto> getTransactionSummary() {
        log.info("Retrieving transaction summary");

        var summary = queryService.getTransactionSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check payment service health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Payment Service is healthy");
    }
}

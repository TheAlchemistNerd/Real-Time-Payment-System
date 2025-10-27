package com.paymentprocessor.payment.service;

import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.payment.dto.TransactionSummaryDto;
import com.paymentprocessor.payment.query.TransactionReadModel;
import com.paymentprocessor.payment.query.TransactionReadModelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Transactional(readOnly = true)
public class TransactionQueryService {

    private final TransactionReadModelRepository repository;

    public TransactionQueryService(TransactionReadModelRepository repository) {
        this.repository = repository;
    }

    public Optional<TransactionReadModel> findByTransactionId(String transactionId) {
        log.debug("Querying transaction: {}", transactionId);
        return repository.findById(transactionId);
    }

    public List<TransactionReadModel> findByUserId(String userId) {
        log.debug("Querying transactions for user: {}", userId);
        return repository.findByUserId(userId);
    }

    public Page<TransactionReadModel> findByUserIdPaged(String userId, Pageable pageable) {
        log.debug("Querying paged transactions for user: {}", userId);
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public List<TransactionReadModel> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Querying transactions from {} to {}", startDate, endDate);
        return repository.findByDateRange(startDate, endDate);
    }

    public TransactionSummaryDto getTransactionSummary() {
        log.debug("Generating transaction summary");

        var totalCount = repository.count();
        var completedCount = repository.countByStatus(TransactionStatus.COMPLETED);
        var failedCount = repository.countByStatus(TransactionStatus.FAILED);
        var pendingCount = repository.countByStatus(TransactionStatus.PENDING);

        var today = LocalDateTime.now().toLocalDate().atStartOfDay();
        var totalAmountToday = repository.sumAmountByStatusSince(TransactionStatus.COMPLETED, today);

        return new TransactionSummaryDto(
                totalCount,
                completedCount,
                failedCount,
                pendingCount,
                totalAmountToday != null ? totalAmountToday : BigDecimal.ZERO
        );
    }

    public List<TransactionReadModel> findActiveTransactionsByUser(String userId) {
        log.debug("Querying active transactions for user: {}", userId);
        var activeStatuses = List.of(
                TransactionStatus.PENDING,
                TransactionStatus.FRAUD_CHECK_PASSED,
                TransactionStatus.PAYMENT_PROCESSING
        );
        return repository.findByUserIdAndStatusIn(userId, activeStatuses);
    }
}

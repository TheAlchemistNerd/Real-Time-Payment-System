package com.paymentprocessor.payment.query;

import com.paymentprocessor.common.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionReadModelRepository extends JpaRepository <TransactionReadModel, String> {
    List<TransactionReadModel> findByUserId(String userId);
    Page<TransactionReadModel> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("SELECT T FROM TransactionReadModel t WHERE t.createdAt BETWEEN :startDate AND :endDate")
    List<TransactionReadModel> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
            );

    @Query("SELECT COUNT(t) FROM TransactionReadModel t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    @Query("SELECT SUM(t.amount) FROM TransactionReadModel t WHERE t.status = :status AND t.createdAt >= :since")
    BigDecimal sumAmountByStatusSince(
            @Param("status") TransactionStatus status,
            @Param("since") LocalDateTime since);

    @Query("SELECT t FROM TransactionReadModel t WHERE t.userId = :userId AND t.status IN :statuses")
    List<TransactionReadModel> findByUserIdAndStatusIn(
            @Param("userId") String userId,
            @Param("statuses") List<TransactionStatus> statuses);
}

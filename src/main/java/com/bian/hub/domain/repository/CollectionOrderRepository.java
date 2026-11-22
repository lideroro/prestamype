package com.bian.hub.domain.repository;

import com.bian.hub.domain.model.CollectionOrder;
import com.bian.hub.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionOrderRepository extends JpaRepository<CollectionOrder, UUID> {

    Optional<CollectionOrder> findByFineractLoanIdAndInstallmentNumber(Long fineractLoanId, Integer installmentNumber);

    List<CollectionOrder> findByLastBatchId(UUID batchId);

    @Query("""
        SELECT o FROM CollectionOrder o
        WHERE (:loanId IS NULL OR o.fineractLoanId = :loanId)
          AND (:dueDateTo IS NULL OR o.dueDate <= :dueDateTo)
          AND (:status IS NULL OR o.status = :status)
        ORDER BY o.fineractLoanId ASC, o.installmentNumber ASC
    """)
    List<CollectionOrder> searchOrders(
            @Param("loanId") Long loanId,
            @Param("dueDateTo") LocalDate dueDateTo,
            @Param("status") OrderStatus status
    );
}
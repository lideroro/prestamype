package com.bian.hub.application.service;

import com.bian.hub.domain.model.*;
import com.bian.hub.domain.repository.CollectionBatchRepository;
import com.bian.hub.domain.repository.CollectionOrderRepository;
import com.bian.hub.infrastructure.fineract.FineractApiClient;
import com.bian.hub.infrastructure.fineract.FineractDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncCollectionService {

    private final FineractApiClient fineractClient;
    private final CollectionBatchRepository batchRepository;
    private final CollectionOrderRepository orderRepository;

    @Transactional
    public CollectionBatch executeSync(List<Long> loanIds, LocalDate cutoffDate, int horizonDays) {
        LocalDate horizonEndDate = cutoffDate.plusDays(horizonDays);
        OffsetDateTime now = OffsetDateTime.now();

        CollectionBatch batch = CollectionBatch.builder()
                .id(UUID.randomUUID())
                .cutoffDate(cutoffDate)
                .horizonDays(horizonDays)
                .horizonEndDate(horizonEndDate)
                .status(BatchStatus.COMPLETED)
                .processedOrders(0)
                .createdAt(now)
                .build();
        batchRepository.save(batch);

        int count = 0;

        for (Long loanId : loanIds) {
            FineractLoanResponse loan = fineractClient.getLoanWithSchedule(loanId);
            String clientExternalId = resolveClientExternalId(loan);

            if (loan.repaymentSchedule() == null || loan.repaymentSchedule().periods() == null) {
                continue;
            }

            for (FineractPeriod period : loan.repaymentSchedule().periods()) {
                if (period.period() == null || period.period() <= 0) {
                    continue; // Ignora el período 0 (desembolso)
                }

                LocalDate dueDate = period.getDueDateAsLocalDate();
                if (dueDate == null || dueDate.isAfter(horizonEndDate)) {
                    continue; // Fuera del horizonte de consulta
                }

                BigDecimal principalDue = safeAmount(period.principalOutstanding());
                BigDecimal interestDue = safeAmount(period.interestOutstanding());
                BigDecimal totalDue = safeAmount(period.totalOutstandingForPeriod());

                Optional<CollectionOrder> existingOrder = orderRepository
                        .findByFineractLoanIdAndInstallmentNumber(loan.id(), period.period());

                // Si no hay saldo pendiente y nunca se había generado orden, no se crea nueva
                if (totalDue.compareTo(BigDecimal.ZERO) == 0 && existingOrder.isEmpty()) {
                    continue;
                }

                OrderStatus status = resolveOrderStatus(period, totalDue);

                CollectionOrder order = existingOrder.orElseGet(() -> CollectionOrder.builder()
                        .id(UUID.randomUUID())
                        .fineractClientId(loan.clientId())
                        .fineractLoanId(loan.id())
                        .installmentNumber(period.period())
                        .build());

                order.setLastBatch(batch);
                order.setClientExternalId(clientExternalId);
                order.setClientName(loan.clientName());
                order.setLoanAccountNo(loan.accountNo());
                order.setLoanExternalId(loan.externalId());
                order.setDueDate(dueDate);
                order.setCurrencyCode(loan.currency() != null ? loan.currency().code() : "PEN");
                order.setPrincipalDue(principalDue);
                order.setInterestDue(interestDue);
                order.setTotalDue(totalDue);
                order.setStatus(status);
                order.setUpdatedAt(now);

                orderRepository.save(order);
                count++;
            }
        }

        batch.setProcessedOrders(count);
        return batchRepository.save(batch);
    }

    private String resolveClientExternalId(FineractLoanResponse loan) {
        if (loan.clientExternalId() != null) {
            return loan.clientExternalId();
        }
        FineractClientResponse client = fineractClient.getClient(loan.clientId());
        return client != null ? client.externalId() : null;
    }

    private OrderStatus resolveOrderStatus(FineractPeriod period, BigDecimal totalDue) {
        if (totalDue.compareTo(BigDecimal.ZERO) == 0) {
            return OrderStatus.PAID;
        }
        BigDecimal originalTotal = safeAmount(period.principalOriginalDue())
                .add(safeAmount(period.interestOriginalDue()));
        if (totalDue.compareTo(originalTotal) < 0) {
            return OrderStatus.PARTIALLY_PAID;
        }
        return OrderStatus.PENDING;
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}

package com.bian.hub.infrastructure.rest;

import com.bian.hub.domain.model.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ApiDtos {

    public record SyncRequest(
            @NotEmpty(message = "Debe indicar al menos un préstamo")
            List<Long> loanIds,
            @NotNull(message = "La fecha de corte es obligatoria")
            LocalDate cutoffDate,
            @Min(value = 0, message = "El horizonte debe ser >= 0 días")
            int horizonDays
    ) {}

    public record BatchResponse(
            UUID batchId,
            LocalDate cutoffDate,
            int horizonDays,
            LocalDate horizonEndDate,
            BatchStatus status,
            int processedOrders,
            OffsetDateTime createdAt,
            List<OrderResponse> orders
    ) {
        public static BatchResponse from(CollectionBatch batch, List<CollectionOrder> orders) {
            return new BatchResponse(
                    batch.getId(),
                    batch.getCutoffDate(),
                    batch.getHorizonDays(),
                    batch.getHorizonEndDate(),
                    batch.getStatus(),
                    batch.getProcessedOrders(),
                    batch.getCreatedAt(),
                    orders.stream().map(OrderResponse::from).toList()
            );
        }
    }

    public record OrderResponse(
            UUID orderId,
            UUID lastBatchId,
            Long fineractClientId,
            String clientExternalId,
            String clientName,
            Long fineractLoanId,
            String loanAccountNo,
            String loanExternalId,
            Integer installmentNumber,
            LocalDate dueDate,
            String currencyCode,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal totalDue,
            OrderStatus status,
            OffsetDateTime updatedAt
    ) {
        public static OrderResponse from(CollectionOrder o) {
            return new OrderResponse(
                    o.getId(),
                    o.getLastBatch().getId(),
                    o.getFineractClientId(),
                    o.getClientExternalId(),
                    o.getClientName(),
                    o.getFineractLoanId(),
                    o.getLoanAccountNo(),
                    o.getLoanExternalId(),
                    o.getInstallmentNumber(),
                    o.getDueDate(),
                    o.getCurrencyCode(),
                    o.getPrincipalDue(),
                    o.getInterestDue(),
                    o.getTotalDue(),
                    o.getStatus(),
                    o.getUpdatedAt()
            );
        }
    }
}

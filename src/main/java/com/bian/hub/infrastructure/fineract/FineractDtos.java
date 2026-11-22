package com.bian.hub.infrastructure.fineract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FineractDtos {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FineractLoanResponse(
            Long id,
            String accountNo,
            String externalId,
            Long clientId,
            String clientName,
            String clientExternalId,
            FineractCurrency currency,
            FineractRepaymentSchedule repaymentSchedule
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FineractCurrency(
            String code,
            Integer decimalPlaces
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FineractRepaymentSchedule(
            List<FineractPeriod> periods
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FineractPeriod(
            Integer period,
            List<Integer> dueDate, // Fineract devuelve fechas como [2026, 10, 15]
            Boolean complete,
            BigDecimal principalOriginalDue,
            BigDecimal principalOutstanding,
            BigDecimal interestOriginalDue,
            BigDecimal interestOutstanding,
            BigDecimal totalOutstandingForPeriod
    ) {
        public LocalDate getDueDateAsLocalDate() {
            if (dueDate == null || dueDate.size() < 3) return null;
            return LocalDate.of(dueDate.get(0), dueDate.get(1), dueDate.get(2));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FineractClientResponse(
            Long id,
            String externalId,
            String displayName
    ) {}
}

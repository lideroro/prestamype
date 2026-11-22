package com.bian.hub.application.service;

import com.bian.hub.domain.model.*;
import com.bian.hub.domain.repository.CollectionBatchRepository;
import com.bian.hub.domain.repository.CollectionOrderRepository;
import com.bian.hub.infrastructure.fineract.FineractApiClient;
import com.bian.hub.infrastructure.fineract.FineractDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncCollectionServiceTest {

    @Mock
    private FineractApiClient fineractClient;

    @Mock
    private CollectionBatchRepository batchRepository;

    @Mock
    private CollectionOrderRepository orderRepository;

    @InjectMocks
    private SyncCollectionService syncService;

    @BeforeEach
    void setUp() {
        when(batchRepository.save(any(CollectionBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(CollectionOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Escenario 1: Sincronización inicial filtra horizonte de 31 días y crea 2 órdenes PENDING")
    void shouldSyncInstallmentsWithinHorizon() {
        FineractLoanResponse mockLoan = buildMockLoan(
                new BigDecimal("1000.00"), new BigDecimal("120.00"), new BigDecimal("1120.00"), false
        );
        when(fineractClient.getLoanWithSchedule(1L)).thenReturn(mockLoan);
        when(orderRepository.findByFineractLoanIdAndInstallmentNumber(anyLong(), anyInt()))
                .thenReturn(Optional.empty());

        CollectionBatch batch = syncService.executeSync(List.of(1L), LocalDate.of(2026, 10, 15), 31);

        assertThat(batch.getProcessedOrders()).isEqualTo(2);
        assertThat(batch.getHorizonEndDate()).isEqualTo(LocalDate.of(2026, 11, 15));

        ArgumentCaptor<CollectionOrder> orderCaptor = ArgumentCaptor.forClass(CollectionOrder.class);
        verify(orderRepository, times(2)).save(orderCaptor.capture());

        List<CollectionOrder> savedOrders = orderCaptor.getAllValues();
        assertThat(savedOrders.get(0).getInstallmentNumber()).isEqualTo(1);
        assertThat(savedOrders.get(0).getTotalDue()).isEqualByComparingTo("1120.00");
        assertThat(savedOrders.get(0).getStatus()).isEqualTo(OrderStatus.PENDING);

        assertThat(savedOrders.get(1).getInstallmentNumber()).isEqualTo(2);
        assertThat(savedOrders.get(1).getTotalDue()).isEqualByComparingTo("1110.00");
    }

    @Test
    @DisplayName("Escenario 2 y 3: Actualiza orden existente a PARTIALLY_PAID y luego a PAID tras pagos")
    void shouldUpdateOrderStatusAfterPartialAndFullPayments() {
        CollectionOrder existingOrder1 = CollectionOrder.builder()
                .id(UUID.randomUUID())
                .fineractLoanId(1L)
                .installmentNumber(1)
                .status(OrderStatus.PENDING)
                .build();

        // Tras pago de S/ 500 (120 interés + 380 capital -> queda 620 capital)
        FineractLoanResponse afterPartialLoan = buildMockLoan(
                new BigDecimal("620.00"), BigDecimal.ZERO, new BigDecimal("620.00"), false
        );
        when(fineractClient.getLoanWithSchedule(1L)).thenReturn(afterPartialLoan);
        when(orderRepository.findByFineractLoanIdAndInstallmentNumber(1L, 1)).thenReturn(Optional.of(existingOrder1));
        when(orderRepository.findByFineractLoanIdAndInstallmentNumber(1L, 2)).thenReturn(Optional.empty());

        syncService.executeSync(List.of(1L), LocalDate.of(2026, 10, 15), 31);

        assertThat(existingOrder1.getPrincipalDue()).isEqualByComparingTo("620.00");
        assertThat(existingOrder1.getInterestDue()).isEqualByComparingTo("0.00");
        assertThat(existingOrder1.getStatus()).isEqualTo(OrderStatus.PARTIALLY_PAID);

        // Tras pago de S/ 620 restante -> saldo 0.00
        FineractLoanResponse afterFullLoan = buildMockLoan(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true
        );
        when(fineractClient.getLoanWithSchedule(1L)).thenReturn(afterFullLoan);

        syncService.executeSync(List.of(1L), LocalDate.of(2026, 10, 15), 31);

        assertThat(existingOrder1.getTotalDue()).isEqualByComparingTo("0.00");
        assertThat(existingOrder1.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private FineractLoanResponse buildMockLoan(BigDecimal p1Principal, BigDecimal p1Interest,
                                               BigDecimal p1Total, boolean p1Complete) {
        FineractPeriod disbursement = new FineractPeriod(
                0, List.of(2026, 9, 15), true,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        FineractPeriod period1 = new FineractPeriod(
                1, List.of(2026, 10, 15), p1Complete,
                new BigDecimal("1000.00"), p1Principal,
                new BigDecimal("120.00"), p1Interest, p1Total
        );
        FineractPeriod period2 = new FineractPeriod(
                2, List.of(2026, 11, 15), false,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                new BigDecimal("110.00"), new BigDecimal("110.00"), new BigDecimal("1110.00")
        );
        FineractPeriod period3OutOfHorizon = new FineractPeriod(
                3, List.of(2026, 12, 15), false,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("1100.00")
        );

        return new FineractLoanResponse(
                1L, "000000001", "LOAN-DEMO-001",
                1L, "Ana Demo Integración", "CLIENT-DEMO-001",
                new FineractCurrency("PEN", 2),
                new FineractRepaymentSchedule(List.of(disbursement, period1, period2, period3OutOfHorizon))
        );
    }
}

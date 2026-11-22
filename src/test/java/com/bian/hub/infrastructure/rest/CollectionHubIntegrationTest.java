package com.bian.hub.infrastructure.rest;

import com.bian.hub.infrastructure.fineract.FineractApiClient;
import com.bian.hub.infrastructure.fineract.FineractDtos.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectionHubIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FineractApiClient fineractApiClient;

    @Test
    @DisplayName("POST /api/v1/batches/sync y GET /api/v1/collection-orders persisten y filtran órdenes")
    void shouldExecuteSyncEndpointAndQueryOrders() throws Exception {
        FineractLoanResponse loanResponse = new FineractLoanResponse(
                1L, "000000001", "LOAN-DEMO-001",
                1L, "Ana Demo Integración", "CLIENT-DEMO-001",
                new FineractCurrency("PEN", 2),
                new FineractRepaymentSchedule(List.of(
                        new FineractPeriod(1, List.of(2026, 10, 15), false,
                                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                                new BigDecimal("120.00"), new BigDecimal("120.00"), new BigDecimal("1120.00"))
                ))
        );
        when(fineractApiClient.getLoanWithSchedule(1L)).thenReturn(loanResponse);

        String requestJson = """
            {
              "loanIds": [1],
              "cutoffDate": "2026-10-15",
              "horizonDays": 31
            }
            """;

        mockMvc.perform(post("/api/v1/batches/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processedOrders").value(1))
                .andExpect(jsonPath("$.orders[0].clientExternalId").value("CLIENT-DEMO-001"))
                .andExpect(jsonPath("$.orders[0].totalDue").value(1120.00));

        mockMvc.perform(get("/api/v1/collection-orders")
                        .param("loanId", "1")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currencyCode").value("PEN"));
    }
}

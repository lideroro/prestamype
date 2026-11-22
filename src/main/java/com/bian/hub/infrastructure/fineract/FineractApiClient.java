package com.bian.hub.infrastructure.fineract;

import com.bian.hub.infrastructure.fineract.FineractDtos.FineractClientResponse;
import com.bian.hub.infrastructure.fineract.FineractDtos.FineractLoanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class FineractApiClient {

    private final RestClient fineractRestClient;

    public FineractLoanResponse getLoanWithSchedule(Long loanId) {
        return fineractRestClient.get()
                .uri("/loans/{loanId}?associations=repaymentSchedule", loanId)
                .retrieve()
                .body(FineractLoanResponse.class);
    }

    public FineractClientResponse getClient(Long clientId) {
        return fineractRestClient.get()
                .uri("/clients/{clientId}", clientId)
                .retrieve()
                .body(FineractClientResponse.class);
    }
}

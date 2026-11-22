package com.bian.hub.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collection_order", uniqueConstraints = {
        @UniqueConstraint(name = "uq_loan_installment", columnNames = {"fineract_loan_id", "installment_number"})
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class CollectionOrder {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_batch_id", nullable = false)
    private CollectionBatch lastBatch;

    @Column(name = "fineract_client_id", nullable = false)
    private Long fineractClientId;

    @Column(name = "client_external_id", length = 100)
    private String clientExternalId;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(name = "fineract_loan_id", nullable = false)
    private Long fineractLoanId;

    @Column(name = "loan_account_no", nullable = false, length = 100)
    private String loanAccountNo;

    @Column(name = "loan_external_id", length = 100)
    private String loanExternalId;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "principal_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestDue;

    @Column(name = "total_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

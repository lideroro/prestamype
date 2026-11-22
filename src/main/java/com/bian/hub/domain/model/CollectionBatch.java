package com.bian.hub.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collection_batch")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class CollectionBatch {

    @Id
    private UUID id;

    @Column(name = "cutoff_date", nullable = false)
    private LocalDate cutoffDate;

    @Column(name = "horizon_days", nullable = false)
    private Integer horizonDays;

    @Column(name = "horizon_end_date", nullable = false)
    private LocalDate horizonEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "processed_orders", nullable = false)
    private Integer processedOrders;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

package com.bian.hub.infrastructure.rest;

import com.bian.hub.application.service.SyncCollectionService;
import com.bian.hub.domain.model.*;
import com.bian.hub.domain.repository.CollectionBatchRepository;
import com.bian.hub.domain.repository.CollectionOrderRepository;
import com.bian.hub.infrastructure.rest.ApiDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Collection Hub (BIAN Open Item Management)", description = "Sincronización y consulta de órdenes de cobranza")
public class CollectionHubController {

    private final SyncCollectionService syncService;
    private final CollectionBatchRepository batchRepository;
    private final CollectionOrderRepository orderRepository;

    @PostMapping("/batches/sync")
    @Operation(summary = "Ejecuta un lote de sincronización de préstamos desde Apache Fineract")
    public ResponseEntity<BatchResponse> syncLoans(@Valid @RequestBody SyncRequest request) {
        CollectionBatch batch = syncService.executeSync(
                request.loanIds(),
                request.cutoffDate(),
                request.horizonDays()
        );
        List<CollectionOrder> orders = orderRepository.findByLastBatchId(batch.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(BatchResponse.from(batch, orders));
    }

    @GetMapping("/batches/{batchId}")
    @Operation(summary = "Consulta el detalle de un lote y sus órdenes asociadas")
    public ResponseEntity<BatchResponse> getBatch(@PathVariable UUID batchId) {
        CollectionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lote no encontrado"));
        List<CollectionOrder> orders = orderRepository.findByLastBatchId(batchId);
        return ResponseEntity.ok(BatchResponse.from(batch, orders));
    }

    @GetMapping("/collection-orders")
    @Operation(summary = "Filtra órdenes de cobranza por préstamo, vencimiento máximo y situación")
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam(required = false) Long loanId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) OrderStatus status) {

        List<OrderResponse> results = orderRepository.searchOrders(loanId, dueDateTo, status)
                .stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(results);
    }
}

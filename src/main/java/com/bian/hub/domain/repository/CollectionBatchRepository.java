package com.bian.hub.domain.repository;

import com.bian.hub.domain.model.CollectionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CollectionBatchRepository extends JpaRepository<CollectionBatch, UUID> {
}
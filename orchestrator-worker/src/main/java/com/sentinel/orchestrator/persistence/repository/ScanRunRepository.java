package com.sentinel.orchestrator.persistence.repository;

import com.sentinel.orchestrator.persistence.entity.ScanRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScanRunRepository extends JpaRepository<ScanRun, UUID> {
}

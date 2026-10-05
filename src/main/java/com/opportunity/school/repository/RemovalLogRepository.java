package com.opportunity.school.repository;

import com.opportunity.school.model.RemovalLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemovalLogRepository extends JpaRepository<RemovalLog, Long> {

    List<RemovalLog> findByEntityTypeOrderByCreatedAtDesc(String entityType);
}

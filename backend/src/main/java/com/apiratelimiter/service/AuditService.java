package com.apiratelimiter.service;

import com.apiratelimiter.entity.AuditLog;
import com.apiratelimiter.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String action, String entityType, String entityId, String username, String details) {
        try {
            AuditLog auditLog = new AuditLog(action, entityType, entityId, username, details);
            auditLogRepository.save(auditLog);
            log.info("AUDIT: user={}, action={}, entityType={}, entityId={}", username, action, entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }
}

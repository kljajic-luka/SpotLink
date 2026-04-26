package com.spotlink.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogs, Clock clock) {
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID actorUserId, String action, String resourceType, String resourceId, String metadata) {
        record(actorUserId, action, resourceType, resourceId, metadata, Instant.now(clock));
    }

    @Transactional
    public void record(UUID actorUserId, String action, String resourceType, String resourceId, String metadata, Instant occurredAt) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setMetadata(metadata);
        log.setOccurredAt(occurredAt);
        auditLogs.save(log);
    }
}

package com.spotlink.admin;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository auditEvents;

    public AuditService(AuditEventRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Transactional
    public void record(UUID actorUserId, String action, String resourceType, String resourceId, String metadata) {
        AuditEvent event = new AuditEvent();
        event.setActorUserId(actorUserId);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setMetadata(metadata);
        auditEvents.save(event);
    }
}

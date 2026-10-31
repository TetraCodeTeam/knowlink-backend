package com.knowlink.api.admin.common.audit;

import java.util.UUID;

public interface IAdminAuditService {

    void record(UUID adminUserId, AdminAuditAction action, String targetType, UUID targetId);
}
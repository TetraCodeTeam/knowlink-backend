package com.knowlink.api.admin.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IAdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
}
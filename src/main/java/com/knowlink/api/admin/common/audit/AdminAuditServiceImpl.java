package com.knowlink.api.admin.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditServiceImpl implements IAdminAuditService {

    private final IAdminAuditLogRepository repository;

    @Override
    @Transactional
    public void record(UUID adminUserId, AdminAuditAction action, String targetType, UUID targetId) {
        repository.save(AdminAuditLog.builder()
                .adminUserId(adminUserId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .build());
        log.info("Admin audit: adminId={} action={} targetType={} targetId={}",
                adminUserId, action, targetType, targetId);
    }
}
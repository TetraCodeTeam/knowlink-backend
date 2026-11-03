package com.knowlink.api.users.listeners;

import com.knowlink.api.materials.service.interfaces.IProfileImageService;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.users.events.AccountDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Removes external storage objects after the account deletion transaction commits (US-24).
 * A storage failure must never roll back the deletion, so every operation is isolated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountDeletedStorageCleanupListener {

    private final ISupabaseStorageService storageService;
    private final IProfileImageService profileImageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAccountDeleted(AccountDeletedEvent event) {
        UUID userId = event.userId();

        try {
            profileImageService.deleteByUserId(userId);
        } catch (RuntimeException ex) {
            log.warn("Storage cleanup failed for userId '{}'", userId, ex);
        }

        for (String storagePath : event.storagePaths()) {
            if (storagePath == null || storagePath.isBlank()) {
                continue;
            }
            try {
                storageService.delete(storagePath);
            } catch (RuntimeException ex) {
                log.warn("Storage cleanup failed for userId '{}'", userId, ex);
            }
        }
    }
}
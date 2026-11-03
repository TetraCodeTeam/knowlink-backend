package com.knowlink.api.users.listeners;

import com.knowlink.api.materials.service.interfaces.IProfileImageService;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.users.events.AccountDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountDeletedStorageCleanupListenerTest {

    @Mock
    private ISupabaseStorageService storageService;
    @Mock
    private IProfileImageService profileImageService;

    private AccountDeletedStorageCleanupListener listener;

    @BeforeEach
    void setUp() {
        listener = new AccountDeletedStorageCleanupListener(storageService, profileImageService);
    }

    @Test
    @DisplayName("Deletes the profile image and every community attachment path")
    void deletesProfileImageAndCommunityAttachments() {
        UUID userId = UUID.randomUUID();

        listener.handleAccountDeleted(new AccountDeletedEvent(userId, List.of("a.png", "b.pdf")));

        verify(profileImageService).deleteByUserId(userId);
        verify(storageService).delete("a.png");
        verify(storageService).delete("b.pdf");
    }

    @Test
    @DisplayName("A storage failure is logged and swallowed so the deletion is never reversed")
    void storageFailuresAreSwallowedAndRemainingPathsAreProcessed() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("profile failure")).when(profileImageService).deleteByUserId(userId);
        doThrow(new RuntimeException("attachment failure")).when(storageService).delete("a.png");

        assertThatCode(() -> listener.handleAccountDeleted(
                new AccountDeletedEvent(userId, List.of("a.png", "b.pdf"))))
                .doesNotThrowAnyException();

        verify(storageService).delete("b.pdf");
    }

    @Test
    @DisplayName("Blank storage paths are skipped")
    void blankPathsAreSkipped() {
        UUID userId = UUID.randomUUID();

        listener.handleAccountDeleted(new AccountDeletedEvent(userId, List.of("", "   ")));

        verify(storageService, never()).delete(anyString());
    }
}
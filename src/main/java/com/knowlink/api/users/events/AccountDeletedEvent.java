package com.knowlink.api.users.events;

import java.util.List;
import java.util.UUID;

/** Published after the account deletion transaction commits (US-24). */
public record AccountDeletedEvent(UUID userId, List<String> storagePaths) {
}
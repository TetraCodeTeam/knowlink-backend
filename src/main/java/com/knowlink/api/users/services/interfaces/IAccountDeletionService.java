package com.knowlink.api.users.services.interfaces;

import java.util.UUID;

public interface IAccountDeletionService {

    void deleteAccount(UUID userId, String rawPassword);
}
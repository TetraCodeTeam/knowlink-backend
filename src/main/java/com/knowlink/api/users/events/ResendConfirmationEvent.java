package com.knowlink.api.users.events;

import com.knowlink.api.users.data.models.User;
import java.util.UUID;

public record ResendConfirmationEvent(
        User user,
        UUID tokenId
) {}
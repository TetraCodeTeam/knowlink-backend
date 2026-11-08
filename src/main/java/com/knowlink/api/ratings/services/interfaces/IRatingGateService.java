package com.knowlink.api.ratings.services.interfaces;

import com.knowlink.api.ratings.controllers.responses.PendingRatingsResponse;

import java.util.UUID;

public interface IRatingGateService {

    /**
     * Used by the MVC interceptor. Negative results are cached; blocked results are never cached.
     */
    boolean isBlocked(UUID userId);

    /**
     * Used by the pending ratings endpoint. Always queries the database.
     */
    PendingRatingsResponse getPending(UUID userId);
}

package com.knowlink.api.payments.events;

import java.util.UUID;

public record FundsSuspendedByClaimEvent(UUID bookingId, UUID claimId) {}
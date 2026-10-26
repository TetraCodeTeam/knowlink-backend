package com.knowlink.api.ratings.events;

import java.util.UUID;

public record RatingSubmittedEvent(UUID bookingId, UUID raterUserId) {
}
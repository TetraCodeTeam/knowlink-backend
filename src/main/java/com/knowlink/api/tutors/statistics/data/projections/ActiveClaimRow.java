package com.knowlink.api.tutors.statistics.data.projections;

import java.util.UUID;

/** Reclamo todavía abierto o en revisión de una reserva del tutor. */
public record ActiveClaimRow(UUID bookingId, UUID claimId) {
}

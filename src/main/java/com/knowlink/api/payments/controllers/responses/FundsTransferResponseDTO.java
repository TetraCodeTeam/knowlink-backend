package com.knowlink.api.payments.controllers.responses;

import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundsTransferResponseDTO(
        UUID fundsTransferId,
        UUID bookingId,
        BigDecimal originalAmount,
        BigDecimal systemRetentionPercentage,
        BigDecimal transferredAmount,
        FundsRecipient recipient,
        String concept,
        FundsStatus fundsStatus,
        Instant processedAt,
        UUID blockingClaimId
) {}
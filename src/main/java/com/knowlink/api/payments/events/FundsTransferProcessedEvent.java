package com.knowlink.api.payments.events;

import com.knowlink.api.payments.domain.FundsRecipient;

import java.math.BigDecimal;
import java.util.UUID;

public record FundsTransferProcessedEvent(
        UUID bookingId,
        FundsRecipient recipient,
        BigDecimal transferredAmount,
        String concept
) {}
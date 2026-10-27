package com.knowlink.api.claims.controllers.responses;

import com.knowlink.api.payments.domain.FundsStatus;

import java.math.BigDecimal;

public record ClaimFundsSummaryResponse(
        BigDecimal originalAmount,
        BigDecimal protectedAmount,
        BigDecimal protectedPercentage,
        FundsStatus status,
        boolean retained,
        String currency) {}

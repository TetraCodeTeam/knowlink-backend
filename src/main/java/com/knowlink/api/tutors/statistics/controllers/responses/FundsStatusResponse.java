package com.knowlink.api.tutors.statistics.controllers.responses;

import java.math.BigDecimal;

public record FundsStatusResponse(
        FundsAmountResponse released,
        FundsAmountResponse pendingRelease,
        FundsAmountResponse withheldByClaim,
        BigDecimal commissionPercentage) {
}

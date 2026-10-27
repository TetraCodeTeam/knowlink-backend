package com.knowlink.api.tutors.statistics.controllers.responses;

import java.math.BigDecimal;

public record FundsAmountResponse(BigDecimal amount, int sessions) {
}

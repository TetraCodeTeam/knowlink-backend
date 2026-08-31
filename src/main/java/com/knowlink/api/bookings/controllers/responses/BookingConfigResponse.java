package com.knowlink.api.bookings.controllers.responses;

import java.math.BigDecimal;

public record BookingConfigResponse(BigDecimal serviceFeeRate) {}
package com.knowlink.api.payments.domain;

import java.util.Set;

public enum FundsStatus {
    HELD,
    SUSPENDED_BY_CLAIM,
    RELEASED_TO_TUTOR,
    REFUNDED_TO_STUDENT;

    /** Funds that are still retained and must be resolved before an account can be deactivated. */
    public static final Set<FundsStatus> RETAINED_STATUSES = Set.of(HELD, SUSPENDED_BY_CLAIM);
}

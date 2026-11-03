package com.knowlink.api.users.data.enums;

public enum AccountStatus {
    ACTIVE,
    INACTIVE,     // registered but email not verified yet
    DELETED,
    DEACTIVATED   // temporarily deactivated by the user (US-04); reversible (US-40)
}

package com.knowlink.api.shared.utils;

import java.time.ZoneId;

public final class AppTimeZone {

    private AppTimeZone() {
    }

    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");
}
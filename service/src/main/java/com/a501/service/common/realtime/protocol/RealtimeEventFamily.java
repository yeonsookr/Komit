package com.a501.service.common.realtime.protocol;

import java.util.Locale;

public enum RealtimeEventFamily {
    SYSTEM,
    RTC,
    CHAT;

    public static RealtimeEventFamily of(String type) {
        if (type == null) {
            return null;
        }
        int separator = type.indexOf('_');
        if (separator <= 0) {
            return null;
        }
        String prefix = type.substring(0, separator).toUpperCase(Locale.ROOT);
        for (RealtimeEventFamily family : values()) {
            if (family.name().equals(prefix)) {
                return family;
            }
        }
        return null;
    }
}

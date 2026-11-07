package dev.nexobedrock.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class Time {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FILE_SAFE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private Time() {
    }

    public static String isoNow() {
        return ISO.format(Instant.now());
    }

    public static String fileSafeNow() {
        return FILE_SAFE.format(Instant.now());
    }
}

package dev.nexobedrock.conversion;

import dev.nexobedrock.report.Report;

import java.nio.file.Path;

public record ConversionResult(
        boolean success,
        boolean fromCache,
        String message,
        Report report,
        Path mcpack,
        Path mappings,
        Path backupDir,
        long durationMs
) {
    public static ConversionResult busy(long durationMs) {
        return new ConversionResult(false, false,
                "Ja existe uma conversao em andamento.", null, null, null, null, durationMs);
    }

    public static ConversionResult failure(String message, Report report, long durationMs) {
        return new ConversionResult(false, false, message, report, null, null, null, durationMs);
    }
}

package dev.nexobedrock.report;

import java.util.List;
import java.util.Map;

public record Report(
        String generatedAt,
        String pluginVersion,
        String source,
        String sourceHash,
        String trigger,
        long durationMs,
        Stats stats,
        List<ItemReport> items,
        List<String> warnings,
        List<String> errors,
        Map<String, String> outputs
) {
    public Report withOutputs(Map<String, String> newOutputs) {
        return new Report(generatedAt, pluginVersion, source, sourceHash, trigger, durationMs,
                stats, items, warnings, errors, newOutputs);
    }
}

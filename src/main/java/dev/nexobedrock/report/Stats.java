package dev.nexobedrock.report;

public record Stats(
        int total,
        int converted,
        int flat,
        int threeD,
        int unsupported,
        int skipped,
        int failed
) {
}

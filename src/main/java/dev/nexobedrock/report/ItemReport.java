package dev.nexobedrock.report;

import java.util.List;

public record ItemReport(
        String id,
        String javaItem,
        String mode,
        Integer customModelData,
        String itemModel,
        String modelPath,
        String bedrockIdentifier,
        String type,
        String status,
        String reason,
        String icon,
        String geometry,
        String attachable,
        String texture,
        List<String> warnings
) {
    public static ItemReport converted(String id, String javaItem, String mode,
                                       Integer customModelData, String itemModel,
                                       String modelPath, String bedrockIdentifier,
                                       String type, String icon, String geometry,
                                       String attachable, String texture,
                                       List<String> warnings) {
        return new ItemReport(id, javaItem, mode, customModelData, itemModel, modelPath,
                bedrockIdentifier, type, "converted", null, icon, geometry, attachable,
                texture, warnings == null ? List.of() : List.copyOf(warnings));
    }

    public static ItemReport unsupported(String id, String javaItem, String mode,
                                         Integer customModelData, String itemModel,
                                         String modelPath, String reason) {
        return new ItemReport(id, javaItem, mode, customModelData, itemModel, modelPath,
                null, "unsupported", "unsupported", reason, null, null, null, null,
                List.of());
    }

    public static ItemReport skipped(String id, String javaItem, String mode,
                                     Integer customModelData, String itemModel,
                                     String modelPath, String reason) {
        return new ItemReport(id, javaItem, mode, customModelData, itemModel, modelPath,
                null, "skipped", "skipped", reason, null, null, null, null, List.of());
    }

    public static ItemReport failed(String id, String javaItem, String mode,
                                    Integer customModelData, String itemModel,
                                    String modelPath, String reason) {
        return new ItemReport(id, javaItem, mode, customModelData, itemModel, modelPath,
                null, "error", "failed", reason, null, null, null, null, List.of());
    }
}

package dev.nexobedrock.bedrock;

import java.util.List;
import java.util.Map;

public record AtlasResult(
        byte[] png,
        int width,
        int height,
        Map<FaceKey, UvRect> uvs,
        byte[] iconPng,
        List<String> warnings
) {
}

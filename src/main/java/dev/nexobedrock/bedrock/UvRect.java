package dev.nexobedrock.bedrock;

/**
 * Retangulo UV em pixels do atlas. width/height podem ser negativos para
 * preservar a orientacao original da face.
 */
public record UvRect(float u, float v, float width, float height) {
}

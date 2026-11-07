package dev.nexobedrock.pack;

import java.nio.file.Path;

public record PackSource(PackSourceKind kind, Path path) {
}

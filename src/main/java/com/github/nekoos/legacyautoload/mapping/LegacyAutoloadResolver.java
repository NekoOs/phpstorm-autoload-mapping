package com.github.nekoos.legacyautoload.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class LegacyAutoloadResolver {
    public @NotNull Optional<String> resolveFile(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String globalClassName
    ) {
        return mapping.classPattern()
                .match(globalClassName)
                .flatMap(mapping.filePattern()::render)
                .filter(this::isSafeRelativePath);
    }

    public @NotNull Optional<String> resolveClass(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String relativeFilePath
    ) {
        String normalizedPath = relativeFilePath.replace('\\', '/');
        if (!isSafeRelativePath(normalizedPath)) {
            return Optional.empty();
        }
        return mapping.filePattern()
                .match(normalizedPath)
                .flatMap(mapping.classPattern()::render);
    }

    private boolean isSafeRelativePath(@NotNull String path) {
        return !path.isBlank()
                && !path.startsWith("/")
                && !path.equals("..")
                && !path.startsWith("../")
                && !path.contains("/../")
                && !path.matches("^[A-Za-z]:/.*");
    }
}


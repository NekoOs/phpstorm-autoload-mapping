package com.github.nekoos.legacyautoload.mapping;

import com.github.nekoos.legacyautoload.mapping.pattern.ClassPattern;
import com.github.nekoos.legacyautoload.mapping.pattern.FilePattern;
import com.github.nekoos.legacyautoload.mapping.transformation.TransformationRegistry;
import org.jetbrains.annotations.NotNull;

public record LegacyAutoloadMapping(
        @NotNull String name,
        @NotNull SourceRoot sourceRoot,
        @NotNull ClassPattern classPattern,
        @NotNull FilePattern filePattern
) {
    public LegacyAutoloadMapping {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Mapping name must not be blank");
        }
    }

    public static @NotNull LegacyAutoloadMapping create(
            @NotNull String name,
            @NotNull String sourceRoot,
            @NotNull String classPattern,
            @NotNull String filePattern,
            @NotNull TransformationRegistry transformations
    ) {
        return new LegacyAutoloadMapping(
                name.trim(),
                new SourceRoot(sourceRoot),
                new ClassPattern(classPattern, transformations),
                new FilePattern(filePattern, transformations)
        );
    }
}


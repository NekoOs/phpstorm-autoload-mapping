package com.github.nekoos.legacyautoload.mapping;

import org.jetbrains.annotations.NotNull;

public record LegacyAutoloadPreset(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull String classPattern,
        @NotNull String filePattern
) {
}


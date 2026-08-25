package com.github.nekoos.legacyautoload.mapping;

import org.jetbrains.annotations.NotNull;

public record LegacyAutoloadRuleTemplate(
        @NotNull String name,
        @NotNull String relativeBaseDirectory,
        @NotNull String classPattern,
        @NotNull String filePattern
) {
}

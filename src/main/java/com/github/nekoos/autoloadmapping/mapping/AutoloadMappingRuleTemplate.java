package com.github.nekoos.autoloadmapping.mapping;

import org.jetbrains.annotations.NotNull;

public record AutoloadMappingRuleTemplate(
        @NotNull String name,
        @NotNull String relativeBaseDirectory,
        @NotNull String classPattern,
        @NotNull String filePattern
) {
}

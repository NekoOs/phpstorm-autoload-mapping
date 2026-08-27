package com.github.nekoos.autoloadmapping.mapping.pattern;

import com.github.nekoos.autoloadmapping.mapping.transformation.TransformationRegistry;
import com.github.nekoos.autoloadmapping.mapping.value.StructuredValue;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FilePattern {
    private final TemplatePattern pattern;

    public FilePattern(@NotNull String source, @NotNull TransformationRegistry transformations) {
        pattern = TemplatePattern.compile(source, transformations);
    }

    public @NotNull String source() {
        return pattern.source();
    }

    public @NotNull Optional<Map<String, StructuredValue>> match(@NotNull String relativeFilePath) {
        return matchResult(relativeFilePath).uniqueVariables();
    }

    public @NotNull PatternMatch matchResult(@NotNull String relativeFilePath) {
        return pattern.match(relativeFilePath.replace('\\', '/'));
    }

    public @NotNull Optional<String> render(@NotNull Map<String, StructuredValue> variables) {
        return pattern.render(variables);
    }

    public @NotNull Set<String> variables() {
        return pattern.variables();
    }

    public boolean deterministicExtraction() {
        return pattern.deterministicExtraction();
    }
}

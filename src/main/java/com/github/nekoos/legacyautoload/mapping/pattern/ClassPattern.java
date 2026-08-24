package com.github.nekoos.legacyautoload.mapping.pattern;

import com.github.nekoos.legacyautoload.mapping.transformation.TransformationRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public final class ClassPattern {
    private final TemplatePattern pattern;

    public ClassPattern(@NotNull String source, @NotNull TransformationRegistry transformations) {
        pattern = TemplatePattern.compile(source, transformations);
    }

    public @NotNull String source() {
        return pattern.source();
    }

    public @NotNull Optional<Map<String, String>> match(@NotNull String className) {
        return pattern.match(className);
    }

    public @NotNull Optional<String> render(@NotNull Map<String, String> variables) {
        return pattern.render(variables);
    }
}


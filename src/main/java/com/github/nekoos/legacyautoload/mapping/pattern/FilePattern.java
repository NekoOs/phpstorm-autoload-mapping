package com.github.nekoos.legacyautoload.mapping.pattern;

import com.github.nekoos.legacyautoload.mapping.transformation.TransformationRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public final class FilePattern {
    private final TemplatePattern pattern;

    public FilePattern(@NotNull String source, @NotNull TransformationRegistry transformations) {
        pattern = TemplatePattern.compile(source.replace('\\', '/'), transformations);
    }

    public @NotNull String source() {
        return pattern.source();
    }

    public @NotNull Optional<Map<String, String>> match(@NotNull String relativeFilePath) {
        return pattern.match(relativeFilePath.replace('\\', '/'));
    }

    public @NotNull Optional<String> render(@NotNull Map<String, String> variables) {
        return pattern.render(variables).map(value -> value.replace('\\', '/'));
    }
}


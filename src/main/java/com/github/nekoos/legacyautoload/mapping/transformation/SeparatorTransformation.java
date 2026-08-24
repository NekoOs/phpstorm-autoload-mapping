package com.github.nekoos.legacyautoload.mapping.transformation;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class SeparatorTransformation implements PathTransformation {
    private final String id;
    private final char classSeparator;
    private final char fileSeparator;

    public SeparatorTransformation(@NotNull String id, char classSeparator, char fileSeparator) {
        this.id = id;
        this.classSeparator = classSeparator;
        this.fileSeparator = fileSeparator;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull String apply(@NotNull String value) {
        return value.replace(classSeparator, fileSeparator);
    }

    @Override
    public @NotNull Optional<String> reverse(@NotNull String value) {
        return Optional.of(value.replace(fileSeparator, classSeparator));
    }
}


package com.github.nekoos.legacyautoload.mapping.transformation;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class IdentityTransformation implements ValueTransformation {
    private final String id;

    public IdentityTransformation(@NotNull String id) {
        this.id = id;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull String apply(@NotNull String value) {
        return value;
    }

    @Override
    public @NotNull Optional<String> reverse(@NotNull String value) {
        return Optional.of(value);
    }
}


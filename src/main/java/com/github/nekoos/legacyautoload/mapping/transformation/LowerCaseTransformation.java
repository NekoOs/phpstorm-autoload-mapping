package com.github.nekoos.legacyautoload.mapping.transformation;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

public final class LowerCaseTransformation implements CaseTransformation {
    @Override
    public @NotNull String id() {
        return "lower";
    }

    @Override
    public @NotNull String apply(@NotNull String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public @NotNull Optional<String> reverse(@NotNull String value) {
        if (value.isEmpty() || !value.equals(value.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Optional.of(Character.toUpperCase(value.charAt(0)) + value.substring(1));
    }
}


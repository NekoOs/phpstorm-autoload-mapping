package com.github.nekoos.legacyautoload.mapping.transformation;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class KebabCaseTransformation implements CaseTransformation {
    @Override
    public @NotNull String id() {
        return "kebab";
    }

    @Override
    public @NotNull String apply(@NotNull String value) {
        return String.join("-", WordCase.split(value));
    }

    @Override
    public @NotNull Optional<String> reverse(@NotNull String value) {
        String reversed = WordCase.pascal(value);
        return reversed.isEmpty() || !apply(reversed).equals(value)
                ? Optional.empty()
                : Optional.of(reversed);
    }
}


package com.github.nekoos.legacyautoload.mapping.transformation;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Converts a captured class-pattern value to its file-pattern representation and back.
 * Implementations must be deterministic. A transformation that cannot safely reverse a value
 * returns {@link Optional#empty()}.
 */
public interface ValueTransformation {
    @NotNull String id();

    @NotNull String apply(@NotNull String value);

    @NotNull Optional<String> reverse(@NotNull String value);
}


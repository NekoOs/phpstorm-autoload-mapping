package com.github.nekoos.autoloadmapping.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record MappingResolution(@NotNull Status status, @NotNull String value) {
    public enum Status {
        NO_MATCH,
        UNIQUE,
        AMBIGUOUS
    }

    public static @NotNull MappingResolution noMatch() {
        return new MappingResolution(Status.NO_MATCH, "");
    }

    public static @NotNull MappingResolution unique(@NotNull String value) {
        return new MappingResolution(Status.UNIQUE, value);
    }

    public static @NotNull MappingResolution ambiguous() {
        return new MappingResolution(Status.AMBIGUOUS, "");
    }

    public @NotNull Optional<String> uniqueValue() {
        return status == Status.UNIQUE ? Optional.of(value) : Optional.empty();
    }
}

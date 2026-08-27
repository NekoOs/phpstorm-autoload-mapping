package com.github.nekoos.autoloadmapping.mapping.pattern;

import com.github.nekoos.autoloadmapping.mapping.value.StructuredValue;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public record PatternMatch(@NotNull Status status, @NotNull Map<String, StructuredValue> variables) {
    public enum Status { NO_MATCH, UNIQUE, AMBIGUOUS }

    public static @NotNull PatternMatch noMatch() { return new PatternMatch(Status.NO_MATCH, Map.of()); }
    public static @NotNull PatternMatch unique(@NotNull Map<String, StructuredValue> variables) {
        return new PatternMatch(Status.UNIQUE, Map.copyOf(variables));
    }
    public static @NotNull PatternMatch ambiguous() { return new PatternMatch(Status.AMBIGUOUS, Map.of()); }

    public @NotNull Optional<Map<String, StructuredValue>> uniqueVariables() {
        return status == Status.UNIQUE ? Optional.of(variables) : Optional.empty();
    }
}

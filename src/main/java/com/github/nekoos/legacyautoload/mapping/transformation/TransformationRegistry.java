package com.github.nekoos.legacyautoload.mapping.transformation;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class TransformationRegistry {
    private final Map<String, ValueTransformation> transformations;

    public TransformationRegistry(@NotNull Iterable<? extends ValueTransformation> transformations) {
        Map<String, ValueTransformation> byId = new LinkedHashMap<>();
        for (ValueTransformation transformation : transformations) {
            ValueTransformation previous = byId.put(transformation.id(), transformation);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate transformation: " + transformation.id());
            }
        }
        this.transformations = Map.copyOf(byId);
    }

    public static @NotNull TransformationRegistry defaults() {
        return new TransformationRegistry(java.util.List.of(
                new IdentityTransformation("identity"),
                new IdentityTransformation("_"),
                new SeparatorTransformation("underscorePath", '_', '/'),
                new SeparatorTransformation("/", '_', '/'),
                new KebabCaseTransformation(),
                new SnakeCaseTransformation(),
                new LowerCaseTransformation()
        ));
    }

    public @NotNull Optional<ValueTransformation> find(@NotNull String id) {
        return Optional.ofNullable(transformations.get(id));
    }
}


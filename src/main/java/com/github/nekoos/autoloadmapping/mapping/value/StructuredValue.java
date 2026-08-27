package com.github.nekoos.autoloadmapping.mapping.value;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Canonical value with declared structural-component and word boundaries. */
public record StructuredValue(@NotNull List<Component> components) {
    public StructuredValue {
        components = List.copyOf(components);
        if (components.isEmpty()) {
            throw new IllegalArgumentException("A structured value needs at least one component");
        }
    }

    public static @NotNull StructuredValue ofRawComponents(@NotNull List<String> values) {
        List<Component> components = new ArrayList<>(values.size());
        for (String value : values) {
            components.add(new Component(List.of(value)));
        }
        return new StructuredValue(components);
    }

    public record Component(@NotNull List<String> words) {
        public Component {
            words = List.copyOf(words);
            if (words.isEmpty()) {
                throw new IllegalArgumentException("A structured component needs at least one word");
            }
        }
    }
}

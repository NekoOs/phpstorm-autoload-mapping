package com.github.nekoos.legacyautoload.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Independent operations a rule can calculate without guessing. */
public record MappingCapabilities(
        boolean validateExistingPair,
        boolean deriveFileFromClass,
        boolean deriveExpectedNameFromFile,
        @NotNull List<String> notices
) {
    public MappingCapabilities {
        notices = List.copyOf(notices);
    }

    public @NotNull String label() {
        if (deriveFileFromClass && deriveExpectedNameFromFile) {
            return "Bidireccional";
        }
        if (deriveFileFromClass) {
            return "Solo clase → archivo";
        }
        if (deriveExpectedNameFromFile) {
            return "Solo archivo → clase";
        }
        return validateExistingPair ? "Solo validación" : "Sin resolución determinista";
    }
}

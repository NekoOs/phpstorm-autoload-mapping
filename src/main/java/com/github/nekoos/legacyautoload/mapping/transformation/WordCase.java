package com.github.nekoos.legacyautoload.mapping.transformation;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WordCase {
    private WordCase() {
    }

    static @NotNull List<String> split(@NotNull String value) {
        String normalized = value
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");

        List<String> words = new ArrayList<>();
        for (String word : normalized.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        return words;
    }

    static @NotNull String pascal(@NotNull String value) {
        StringBuilder result = new StringBuilder();
        for (String word : split(value)) {
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}


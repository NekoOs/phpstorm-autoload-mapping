package com.github.nekoos.autoloadmapping.mapping.transformation;

import com.github.nekoos.autoloadmapping.mapping.value.StructuredValue.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Case conversion is deliberately independent from structural separators. */
public enum CaseFormat {
    RAW("raw"), LOWER("lower"), UPPER("upper"), PASCAL("pascal"),
    CAMEL("camel"), SNAKE("snake"), KEBAB("kebab"), DOT("dot");

    private final String id;

    CaseFormat(@NotNull String id) {
        this.id = id;
    }

    public static @NotNull Optional<CaseFormat> find(@NotNull String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }

    public @NotNull Optional<Component> parse(@NotNull String rendered) {
        if (rendered.isEmpty()) {
            return Optional.empty();
        }
        List<String> words = switch (this) {
            case RAW -> isAtomic(rendered) ? List.of(rendered) : List.of();
            case LOWER -> isAtomic(rendered) && isNormalized(rendered, false)
                    ? List.of(rendered.toLowerCase(Locale.ROOT)) : List.of();
            case UPPER -> isAtomic(rendered) && isNormalized(rendered, true)
                    ? List.of(rendered.toLowerCase(Locale.ROOT)) : List.of();
            case PASCAL -> splitPascalOrCamel(rendered, true);
            case CAMEL -> splitPascalOrCamel(rendered, false);
            case SNAKE -> splitDelimited(rendered, '_');
            case KEBAB -> splitDelimited(rendered, '-');
            case DOT -> splitDelimited(rendered, '.');
        };
        if (words.isEmpty()) {
            return Optional.empty();
        }
        Component component = new Component(words);
        return render(component).equals(rendered) ? Optional.of(component) : Optional.empty();
    }

    public @NotNull String render(@NotNull Component component) {
        return switch (this) {
            case RAW -> String.join("", component.words());
            case LOWER -> String.join("", component.words()).toLowerCase(Locale.ROOT);
            case UPPER -> String.join("", component.words()).toUpperCase(Locale.ROOT);
            case PASCAL -> component.words().stream().map(CaseFormat::capitalize).reduce("", String::concat);
            case CAMEL -> {
                String pascal = component.words().stream().map(CaseFormat::capitalize).reduce("", String::concat);
                yield Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
            }
            case SNAKE -> String.join("_", lowerWords(component));
            case KEBAB -> String.join("-", lowerWords(component));
            case DOT -> String.join(".", lowerWords(component));
        };
    }

    private static boolean isNormalized(@NotNull String value, boolean upper) {
        return value.equals(upper ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT));
    }

    private static boolean isAtomic(@NotNull String value) {
        return value.chars().noneMatch(character -> character == '/'
                || character == '\\' || character == '_'
                || character == '-' || character == '.'
                || Character.isWhitespace(character));
    }

    private static @NotNull List<String> lowerWords(@NotNull Component component) {
        return component.words().stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    private static @NotNull List<String> splitDelimited(@NotNull String value, char separator) {
        String[] pieces = value.split(java.util.regex.Pattern.quote(String.valueOf(separator)), -1);
        if (Arrays.stream(pieces).anyMatch(String::isEmpty)
                || Arrays.stream(pieces).anyMatch(piece -> !isAtomic(piece))
                || Arrays.stream(pieces).anyMatch(piece -> !piece.equals(piece.toLowerCase(Locale.ROOT)))) {
            return List.of();
        }
        return Arrays.stream(pieces).map(piece -> piece.toLowerCase(Locale.ROOT)).toList();
    }

    private static @NotNull List<String> splitPascalOrCamel(@NotNull String value, boolean pascal) {
        if (value.isEmpty() || (pascal != Character.isUpperCase(value.charAt(0)))) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        int start = 0;
        for (int index = 1; index < value.length(); index++) {
            char current = value.charAt(index);
            char previous = value.charAt(index - 1);
            if (Character.isUpperCase(current)
                    && (Character.isLowerCase(previous) || Character.isDigit(previous))) {
                words.add(value.substring(start, index).toLowerCase(Locale.ROOT));
                start = index;
            }
        }
        words.add(value.substring(start).toLowerCase(Locale.ROOT));
        return words.stream().allMatch(CaseFormat::isAtomic) ? words : List.of();
    }

    private static @NotNull String capitalize(@NotNull String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

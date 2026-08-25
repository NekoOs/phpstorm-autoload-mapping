package com.github.nekoos.legacyautoload.mapping.transformation;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** Compiles only the canonical, intentionally small transformation DSL. */
public final class TransformationRegistry {
    public static @NotNull TransformationRegistry defaults() {
        return new TransformationRegistry();
    }

    public @NotNull Optional<TransformationPipeline> find(@NotNull String expression) {
        String normalized = expression.isBlank() ? "raw" : expression;
        String[] stages = normalized.split("\\|", -1);
        if (stages.length > 4) {
            return Optional.empty();
        }
        CaseFormat caseFormat = null;
        Character separator = null;
        int length = -1;
        boolean nonempty = false;
        int lastStageOrder = -1;
        for (String stage : stages) {
            Optional<CaseFormat> format = CaseFormat.find(stage);
            if (format.isPresent()) {
                if (caseFormat != null || lastStageOrder > 0) {
                    return Optional.empty();
                }
                caseFormat = format.get();
                lastStageOrder = 0;
                continue;
            }
            Optional<Character> parsedSeparator = separator(stage);
            if (parsedSeparator.isPresent()) {
                if (separator != null || lastStageOrder > 1) {
                    return Optional.empty();
                }
                separator = parsedSeparator.get();
                lastStageOrder = 1;
                continue;
            }
            Optional<Integer> parsedLength = length(stage);
            if (parsedLength.isPresent()) {
                if (length >= 0) {
                    return Optional.empty();
                }
                length = parsedLength.get();
                lastStageOrder = 2;
                continue;
            }
            if (stage.equals("nonempty") && !nonempty) {
                nonempty = true;
                lastStageOrder = 2;
                continue;
            }
            return Optional.empty();
        }
        return Optional.of(new TransformationPipeline(
                caseFormat == null ? CaseFormat.RAW : caseFormat,
                separator,
                length,
                nonempty
        ));
    }

    private @NotNull Optional<Character> separator(@NotNull String stage) {
        return switch (stage) {
            case "sep(_)" -> Optional.of('_');
            case "sep(/)" -> Optional.of('/');
            case "sep(\\)" -> Optional.of('\\');
            default -> Optional.empty();
        };
    }

    private @NotNull Optional<Integer> length(@NotNull String stage) {
        if (!stage.startsWith("length(") || !stage.endsWith(")")) {
            return Optional.empty();
        }
        String argument = stage.substring("length(".length(), stage.length() - 1);
        if (argument.isEmpty() || argument.length() > 4 || !argument.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(argument);
            return value > 0 && value <= 1024 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}

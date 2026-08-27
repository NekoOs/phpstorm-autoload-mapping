package com.github.nekoos.autoloadmapping.mapping.pattern;

import com.github.nekoos.autoloadmapping.mapping.transformation.TransformationPipeline;
import com.github.nekoos.autoloadmapping.mapping.transformation.TransformationRegistry;
import com.github.nekoos.autoloadmapping.mapping.value.StructuredValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TemplatePattern {
    private static final int MAX_PATTERN_LENGTH = 512;
    private static final int MAX_TOKENS = 16;
    private static final int MAX_MATCH_STEPS = 10_000;

    private final String source;
    private final List<Part> parts;
    private final boolean deterministicExtraction;

    private TemplatePattern(@NotNull String source, @NotNull List<Part> parts) {
        this.source = source;
        this.parts = List.copyOf(parts);
        this.deterministicExtraction = !hasAdjacentVariableLengthTokens(parts);
    }

    static @NotNull TemplatePattern compile(
            @NotNull String source,
            @NotNull TransformationRegistry transformations
    ) {
        if (source.isBlank()) {
            throw new PatternSyntaxException("Pattern must not be empty");
        }
        if (source.length() > MAX_PATTERN_LENGTH) {
            throw new PatternSyntaxException("Pattern must be at most " + MAX_PATTERN_LENGTH + " characters");
        }
        List<Part> parts = new ArrayList<>();
        int offset = 0;
        int tokens = 0;
        while (offset < source.length()) {
            int open = source.indexOf('{', offset);
            if (open < 0) {
                addLiteral(parts, source.substring(offset));
                break;
            }
            if (open > offset) {
                addLiteral(parts, source.substring(offset, open));
            }
            int close = source.indexOf('}', open + 1);
            if (close < 0) {
                throw new PatternSyntaxException("Unclosed pattern token");
            }
            int nested = source.indexOf('{', open + 1);
            if (nested >= 0 && nested < close) {
                throw new PatternSyntaxException("Nested pattern tokens are not supported");
            }
            String expression = source.substring(open + 1, close);
            int colon = expression.indexOf(':');
            String variable = colon < 0 ? expression : expression.substring(0, colon);
            String pipelineSource = colon < 0 ? "raw" : expression.substring(colon + 1);
            validateIdentifier(variable);
            TransformationPipeline pipeline = transformations.find(pipelineSource).orElseThrow(() ->
                    new PatternSyntaxException("Unknown or invalid transformation pipeline: " + pipelineSource));
            parts.add(new TokenPart(variable, pipeline));
            if (++tokens > MAX_TOKENS) {
                throw new PatternSyntaxException("Pattern must contain at most " + MAX_TOKENS + " variables");
            }
            offset = close + 1;
        }
        if (tokens == 0) {
            throw new PatternSyntaxException("Pattern must contain at least one {variable}");
        }
        return new TemplatePattern(source, parts);
    }

    private static void addLiteral(@NotNull List<Part> parts, @NotNull String value) {
        if (value.indexOf('}') >= 0) {
            throw new PatternSyntaxException("Unexpected closing brace");
        }
        if (!value.isEmpty()) {
            parts.add(new LiteralPart(value));
        }
    }

    private static void validateIdentifier(@NotNull String value) {
        if (value.isEmpty() || !Character.isLetter(value.charAt(0))) {
            throw new PatternSyntaxException("Variable names must start with a letter");
        }
        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                throw new PatternSyntaxException("Invalid variable name: " + value);
            }
        }
    }

    private static boolean hasAdjacentVariableLengthTokens(@NotNull List<Part> parts) {
        for (int index = 1; index < parts.size(); index++) {
            if (parts.get(index - 1) instanceof TokenPart previous
                    && parts.get(index) instanceof TokenPart current
                    && previous.pipeline().fixedWidth() < 0
                    && current.pipeline().fixedWidth() < 0) {
                return true;
            }
        }
        return false;
    }

    @NotNull String source() { return source; }
    boolean deterministicExtraction() { return deterministicExtraction; }

    boolean usesNamespaceStructure() {
        return parts.stream().anyMatch(part -> part instanceof LiteralPart literal
                ? literal.value().indexOf('\\') >= 0
                : ((TokenPart) part).pipeline().usesNamespaceSeparator());
    }

    @NotNull Set<String> variables() {
        Set<String> result = new LinkedHashSet<>();
        for (Part part : parts) {
            if (part instanceof TokenPart token) {
                result.add(token.variable());
            }
        }
        return Set.copyOf(result);
    }

    @NotNull PatternMatch match(@NotNull String value) {
        if (!deterministicExtraction) {
            return PatternMatch.ambiguous();
        }
        MatchSearch search = new MatchSearch();
        matchPart(0, 0, value, new LinkedHashMap<>(), search);
        if (search.exhausted() || search.matches().size() > 1) {
            return PatternMatch.ambiguous();
        }
        return search.matches().isEmpty()
                ? PatternMatch.noMatch()
                : PatternMatch.unique(search.matches().getFirst());
    }

    private void matchPart(
            int partIndex,
            int valueOffset,
            @NotNull String value,
            @NotNull Map<String, StructuredValue> variables,
            @NotNull MatchSearch search
    ) {
        if (!search.enterStep() || search.matches().size() > 1) {
            return;
        }
        if (partIndex == parts.size()) {
            if (valueOffset == value.length() && !search.matches().contains(variables)) {
                search.matches().add(Map.copyOf(variables));
            }
            return;
        }
        Part part = parts.get(partIndex);
        if (part instanceof LiteralPart literal) {
            if (value.startsWith(literal.value(), valueOffset)) {
                matchPart(partIndex + 1, valueOffset + literal.value().length(), value, variables, search);
            }
            return;
        }
        TokenPart token = (TokenPart) part;
        int width = token.pipeline().fixedWidth();
        if (width >= 0) {
            int end = valueOffset + width;
            if (end <= value.length()) {
                matchToken(token, value.substring(valueOffset, end), partIndex, end, value, variables, search);
            }
            return;
        }
        int maximumEnd = value.length() - minimumRenderedLength(partIndex + 1);
        for (int end = valueOffset + 1; end <= maximumEnd; end++) {
            matchToken(token, value.substring(valueOffset, end), partIndex, end, value, variables, search);
            if (search.exhausted() || search.matches().size() > 1) {
                return;
            }
        }
    }

    private void matchToken(
            @NotNull TokenPart token,
            @NotNull String rendered,
            int partIndex,
            int nextOffset,
            @NotNull String value,
            @NotNull Map<String, StructuredValue> variables,
            @NotNull MatchSearch search
    ) {
        Optional<StructuredValue> reversed = token.pipeline().reverse(rendered);
        if (reversed.isEmpty()) {
            return;
        }
        StructuredValue previous = variables.get(token.variable());
        if (previous != null && !previous.equals(reversed.get())) {
            return;
        }
        Map<String, StructuredValue> next = new LinkedHashMap<>(variables);
        next.put(token.variable(), reversed.get());
        matchPart(partIndex + 1, nextOffset, value, next, search);
    }

    private int minimumRenderedLength(int fromPart) {
        int minimum = 0;
        for (int index = fromPart; index < parts.size(); index++) {
            Part part = parts.get(index);
            minimum += part instanceof LiteralPart literal
                    ? literal.value().length()
                    : Math.max(1, ((TokenPart) part).pipeline().fixedWidth());
        }
        return minimum;
    }

    @NotNull Optional<String> render(@NotNull Map<String, StructuredValue> variables) {
        StringBuilder result = new StringBuilder();
        for (Part part : parts) {
            if (part instanceof LiteralPart literal) {
                result.append(literal.value());
            } else {
                TokenPart token = (TokenPart) part;
                StructuredValue value = variables.get(token.variable());
                if (value == null) {
                    return Optional.empty();
                }
                Optional<String> rendered = token.pipeline().apply(value);
                if (rendered.isEmpty()) {
                    return Optional.empty();
                }
                result.append(rendered.get());
            }
        }
        return Optional.of(result.toString());
    }

    private sealed interface Part permits LiteralPart, TokenPart {}
    private record LiteralPart(@NotNull String value) implements Part {}
    private record TokenPart(@NotNull String variable, @NotNull TransformationPipeline pipeline) implements Part {}

    private static final class MatchSearch {
        private final List<Map<String, StructuredValue>> matches = new ArrayList<>(2);
        private int steps;
        private boolean exhausted;

        boolean enterStep() {
            if (++steps > MAX_MATCH_STEPS) {
                exhausted = true;
                return false;
            }
            return true;
        }
        @NotNull List<Map<String, StructuredValue>> matches() { return matches; }
        boolean exhausted() { return exhausted; }
    }
}

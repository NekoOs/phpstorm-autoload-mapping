package com.github.nekoos.legacyautoload.mapping.pattern;

import com.github.nekoos.legacyautoload.mapping.transformation.IdentityTransformation;
import com.github.nekoos.legacyautoload.mapping.transformation.TransformationRegistry;
import com.github.nekoos.legacyautoload.mapping.transformation.ValueTransformation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TemplatePattern {
    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)(?::([^{}]+))?}");
    private static final ValueTransformation IDENTITY = new IdentityTransformation("identity");

    private final String source;
    private final List<Part> parts;
    private final Pattern matchingPattern;

    private TemplatePattern(@NotNull String source, @NotNull List<Part> parts, @NotNull Pattern matchingPattern) {
        this.source = source;
        this.parts = List.copyOf(parts);
        this.matchingPattern = matchingPattern;
    }

    static @NotNull TemplatePattern compile(
            @NotNull String source,
            @NotNull TransformationRegistry transformations
    ) {
        if (source.isBlank()) {
            throw new PatternSyntaxException("Pattern must not be empty");
        }

        Matcher matcher = TOKEN.matcher(source);
        List<Part> parts = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        int offset = 0;
        int tokenCount = 0;
        while (matcher.find()) {
            if (matcher.start() > offset) {
                String literal = source.substring(offset, matcher.start());
                parts.add(new LiteralPart(literal));
                regex.append(Pattern.quote(literal));
            }

            String variable = matcher.group(1);
            String transformationId = matcher.group(2);
            ValueTransformation transformation = transformationId == null
                    ? IDENTITY
                    : transformations.find(transformationId).orElseThrow(() ->
                            new PatternSyntaxException("Unknown transformation: " + transformationId));
            parts.add(new TokenPart(variable, transformation));
            regex.append("(.+)");
            tokenCount++;
            offset = matcher.end();
        }

        if (offset < source.length()) {
            String literal = source.substring(offset);
            parts.add(new LiteralPart(literal));
            regex.append(Pattern.quote(literal));
        }
        regex.append('$');

        if (tokenCount == 0) {
            throw new PatternSyntaxException("Pattern must contain at least one {variable}");
        }
        if (source.indexOf('{') >= 0 && !source.substring(source.indexOf('{')).contains("}")) {
            throw new PatternSyntaxException("Unclosed pattern token");
        }
        return new TemplatePattern(source, parts, Pattern.compile(regex.toString()));
    }

    @NotNull String source() {
        return source;
    }

    @NotNull Optional<Map<String, String>> match(@NotNull String value) {
        Matcher matcher = matchingPattern.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        Map<String, String> variables = new LinkedHashMap<>();
        int group = 1;
        for (Part part : parts) {
            if (!(part instanceof TokenPart token)) {
                continue;
            }
            String captured = matcher.group(group++);
            Optional<String> reversed = token.transformation().reverse(captured);
            if (reversed.isEmpty() || !token.transformation().apply(reversed.get()).equals(captured)) {
                return Optional.empty();
            }
            String previous = variables.putIfAbsent(token.variable(), reversed.get());
            if (previous != null && !previous.equals(reversed.get())) {
                return Optional.empty();
            }
        }
        return Optional.of(Map.copyOf(variables));
    }

    @NotNull Optional<String> render(@NotNull Map<String, String> variables) {
        StringBuilder result = new StringBuilder();
        for (Part part : parts) {
            if (part instanceof LiteralPart literal) {
                result.append(literal.value());
            } else if (part instanceof TokenPart token) {
                String value = variables.get(token.variable());
                if (value == null) {
                    return Optional.empty();
                }
                result.append(token.transformation().apply(value));
            }
        }
        return Optional.of(result.toString());
    }

    private sealed interface Part permits LiteralPart, TokenPart {
    }

    private record LiteralPart(@NotNull String value) implements Part {
    }

    private record TokenPart(
            @NotNull String variable,
            @NotNull ValueTransformation transformation
    ) implements Part {
    }
}


package com.github.nekoos.legacyautoload.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.github.nekoos.legacyautoload.mapping.pattern.PatternMatch.Status.AMBIGUOUS;

public final class LegacyAutoloadResolver {
    public @NotNull Optional<String> resolveFile(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String globalClassName
    ) {
        return resolveFileResult(mapping, globalClassName).uniqueValue();
    }

    public @NotNull MappingResolution resolveFileResult(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String globalClassName
    ) {
        if (!mapping.capabilities().deriveFileFromClass()) {
            return mapping.classPattern().deterministicExtraction()
                    ? MappingResolution.noMatch() : MappingResolution.ambiguous();
        }
        var match = mapping.classPattern().matchResult(normalizeClassName(globalClassName));
        if (match.status() == AMBIGUOUS) {
            return MappingResolution.ambiguous();
        }
        return match.uniqueVariables()
                .flatMap(mapping.filePattern()::render)
                .filter(this::isSafeRelativePath)
                .map(MappingResolution::unique)
                .orElseGet(MappingResolution::noMatch);
    }

    public @NotNull Optional<String> resolveClass(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String relativeFilePath
    ) {
        return resolveClassResult(mapping, relativeFilePath).uniqueValue();
    }

    public @NotNull MappingResolution resolveClassResult(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String relativeFilePath
    ) {
        String normalizedPath = relativeFilePath.replace('\\', '/');
        if (!isSafeRelativePath(normalizedPath)) {
            return MappingResolution.noMatch();
        }
        if (!mapping.capabilities().deriveExpectedNameFromFile()) {
            return mapping.filePattern().deterministicExtraction()
                    ? MappingResolution.noMatch() : MappingResolution.ambiguous();
        }
        var match = mapping.filePattern().matchResult(normalizedPath);
        if (match.status() == AMBIGUOUS) {
            return MappingResolution.ambiguous();
        }
        return match.uniqueVariables()
                .flatMap(mapping.classPattern()::render)
                .map(MappingResolution::unique)
                .orElseGet(MappingResolution::noMatch);
    }

    public boolean validatesPair(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String className,
            @NotNull String relativeFilePath
    ) {
        String normalizedClass = normalizeClassName(className);
        String normalizedPath = relativeFilePath.replace('\\', '/');
        if (!isSafeRelativePath(normalizedPath)) {
            return false;
        }
        if (mapping.capabilities().deriveFileFromClass()
                && resolveFile(mapping, normalizedClass).filter(normalizedPath::equals).isPresent()) {
            return true;
        }
        return mapping.capabilities().deriveExpectedNameFromFile()
                && resolveClass(mapping, normalizedPath).filter(normalizedClass::equals).isPresent();
    }

    public boolean matchesClassPattern(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String className
    ) {
        return mapping.classPattern().matchResult(normalizeClassName(className)).status()
                == com.github.nekoos.legacyautoload.mapping.pattern.PatternMatch.Status.UNIQUE;
    }

    private @NotNull String normalizeClassName(@NotNull String className) {
        return className.startsWith("\\") ? className.substring(1) : className;
    }

    private boolean isSafeRelativePath(@NotNull String path) {
        return !path.isBlank()
                && !path.startsWith("/")
                && path.indexOf('\\') < 0
                && path.indexOf('\0') < 0
                && !path.equals("..")
                && !path.startsWith("../")
                && !path.contains("/../")
                && !path.endsWith("/..")
                && !isWindowsAbsolutePath(path);
    }

    private boolean isWindowsAbsolutePath(@NotNull String path) {
        return path.length() >= 3
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':'
                && path.charAt(2) == '/';
    }
}

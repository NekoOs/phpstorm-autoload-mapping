package com.github.nekoos.autoloadmapping.mapping;

import com.github.nekoos.autoloadmapping.mapping.pattern.ClassPattern;
import com.github.nekoos.autoloadmapping.mapping.pattern.FilePattern;
import com.github.nekoos.autoloadmapping.mapping.transformation.TransformationRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

public record AutoloadMapping(
        @NotNull String ruleId,
        @NotNull String name,
        @NotNull BaseDirectory baseDirectory,
        @NotNull ClassPattern classPattern,
        @NotNull FilePattern filePattern,
        @NotNull String fallbackChainId,
        int fallbackOrder,
        @NotNull AmbiguousQuickFixPolicy quickFixPolicy
) {
    public AutoloadMapping {
        if (ruleId.isBlank()) {
            throw new IllegalArgumentException("Rule ID must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Mapping name must not be blank");
        }
        if (fallbackOrder < 0) {
            throw new IllegalArgumentException("Fallback order must not be negative");
        }
        if (fallbackChainId.isBlank() && fallbackOrder != 0) {
            throw new IllegalArgumentException("Standalone rules cannot have a fallback order");
        }
    }

    public static @NotNull AutoloadMapping create(
            @NotNull String name,
            @NotNull String baseDirectory,
            @NotNull String classPattern,
            @NotNull String filePattern,
            @NotNull TransformationRegistry transformations
    ) {
        return create(
                java.util.UUID.randomUUID().toString(),
                name,
                baseDirectory,
                classPattern,
                filePattern,
                "",
                0,
                AmbiguousQuickFixPolicy.SAFE_ONLY,
                transformations
        );
    }

    public static @NotNull AutoloadMapping create(
            @NotNull String ruleId,
            @NotNull String name,
            @NotNull String baseDirectory,
            @NotNull String classPattern,
            @NotNull String filePattern,
            @NotNull String fallbackChainId,
            int fallbackOrder,
            @NotNull AmbiguousQuickFixPolicy quickFixPolicy,
            @NotNull TransformationRegistry transformations
    ) {
        ClassPattern compiledClassPattern = new ClassPattern(classPattern, transformations);
        FilePattern compiledFilePattern = new FilePattern(filePattern, transformations);
        return new AutoloadMapping(
                ruleId.trim(),
                name.trim(),
                new BaseDirectory(baseDirectory),
                compiledClassPattern,
                compiledFilePattern,
                fallbackChainId.trim(),
                fallbackOrder,
                quickFixPolicy
        );
    }

    public boolean isFallback() {
        return !fallbackChainId.isEmpty();
    }

    public @NotNull MappingCapabilities capabilities() {
        Set<String> classVariables = classPattern.variables();
        Set<String> fileVariables = filePattern.variables();
        boolean classExtraction = classPattern.deterministicExtraction();
        boolean fileExtraction = filePattern.deterministicExtraction();
        boolean classToFile = classExtraction && classVariables.containsAll(fileVariables);
        boolean fileToClass = fileExtraction && fileVariables.containsAll(classVariables);
        var notices = new ArrayList<String>();
        classVariables.stream().filter(variable -> !fileVariables.contains(variable)).sorted().forEach(variable ->
                notices.add("La variable {" + variable + "} no aparece en el patrón de archivo."));
        fileVariables.stream().filter(variable -> !classVariables.contains(variable)).sorted().forEach(variable ->
                notices.add("La variable {" + variable + "} no aparece en el patrón de clase."));
        if (!classExtraction) {
            notices.add("El patrón de clase no permite separar sus variables de forma determinista.");
        }
        if (!fileExtraction) {
            notices.add("El patrón de archivo no permite separar sus variables de forma determinista.");
        }
        return new MappingCapabilities(classToFile || fileToClass, classToFile, fileToClass, notices);
    }
}

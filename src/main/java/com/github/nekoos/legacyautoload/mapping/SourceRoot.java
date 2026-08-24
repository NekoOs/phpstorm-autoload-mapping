package com.github.nekoos.legacyautoload.mapping;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

public record SourceRoot(@NotNull String projectRelativePath) {
    public SourceRoot {
        projectRelativePath = normalize(projectRelativePath);
    }

    public static @NotNull String normalize(@NotNull String path) {
        String normalized = FileUtil.toSystemIndependentName(path.trim());
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.endsWith("/") && !normalized.isEmpty()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.equals(".")) {
            return "";
        }
        if (normalized.startsWith("/")
                || normalized.equals("..")
                || normalized.startsWith("../")
                || normalized.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("Source root must be project-relative: " + path);
        }
        return normalized;
    }
}


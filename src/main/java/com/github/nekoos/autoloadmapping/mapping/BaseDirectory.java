package com.github.nekoos.autoloadmapping.mapping;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Project-relative directory from which a rule's file pattern is resolved. */
public record BaseDirectory(@NotNull String projectRelativePath) {
    public BaseDirectory {
        projectRelativePath = normalize(projectRelativePath);
    }

    public static @NotNull String normalize(@NotNull String path) {
        String normalized = FileUtil.toSystemIndependentName(path.trim());
        if (normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:(?:/.*)?$")) {
            throw new IllegalArgumentException("Base directory must be project-relative: " + path);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new IllegalArgumentException("Base directory must stay inside the project: " + path);
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }
}

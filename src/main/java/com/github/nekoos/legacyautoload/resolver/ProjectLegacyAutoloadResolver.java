package com.github.nekoos.legacyautoload.resolver;

import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadMapping;
import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadResolver;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ProjectLegacyAutoloadResolver {
    private final LegacyAutoloadResolver mappingResolver;

    public ProjectLegacyAutoloadResolver() {
        this(new LegacyAutoloadResolver());
    }

    ProjectLegacyAutoloadResolver(@NotNull LegacyAutoloadResolver mappingResolver) {
        this.mappingResolver = mappingResolver;
    }

    public @NotNull List<FileResolution> resolveFile(
            @NotNull Project project,
            @NotNull VirtualFile file,
            @NotNull List<LegacyAutoloadMapping> mappings
    ) {
        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
        if (projectDirectory == null || file.isDirectory()) {
            return List.of();
        }

        List<FileResolution> resolutions = new ArrayList<>();
        for (int index = 0; index < mappings.size(); index++) {
            LegacyAutoloadMapping mapping = mappings.get(index);
            VirtualFile root = findRoot(projectDirectory, mapping);
            if (root == null || !VfsUtilCore.isAncestor(root, file, true)) {
                continue;
            }
            String relativePath = VfsUtilCore.getRelativePath(file, root, '/');
            if (relativePath == null) {
                continue;
            }
            int mappingIndex = index;
            mappingResolver.resolveClass(mapping, relativePath)
                    .map(className -> new FileResolution(
                            mapping,
                            root,
                            relativePath,
                            className,
                            mappingIndex
                    ))
                    .ifPresent(resolutions::add);
        }

        return resolutions.stream()
                .sorted(Comparator
                        .comparingInt((FileResolution resolution) -> resolution.root().getPath().length())
                        .reversed()
                        .thenComparingInt(FileResolution::mappingIndex))
                .toList();
    }

    public @NotNull List<ClassResolution> resolveClass(
            @NotNull Project project,
            @NotNull String globalClassName,
            @NotNull List<LegacyAutoloadMapping> mappings
    ) {
        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
        if (projectDirectory == null) {
            return List.of();
        }

        List<ClassResolution> resolutions = new ArrayList<>();
        for (LegacyAutoloadMapping mapping : mappings) {
            VirtualFile root = findRoot(projectDirectory, mapping);
            if (root == null) {
                continue;
            }
            mappingResolver.resolveFile(mapping, globalClassName)
                    .map(relativePath -> new ClassResolution(
                            mapping,
                            root,
                            relativePath,
                            root.findFileByRelativePath(relativePath)
                    ))
                    .ifPresent(resolutions::add);
        }
        return List.copyOf(resolutions);
    }

    public @NotNull Optional<VirtualFile> findMoveTargetDirectory(
            @NotNull FileResolution currentResolution,
            @NotNull VirtualFile currentFile,
            @NotNull String actualClassName
    ) {
        return mappingResolver.resolveFile(currentResolution.mapping(), actualClassName)
                .flatMap(relativePath -> findExistingTargetDirectory(
                        currentResolution.root(),
                        currentFile,
                        relativePath
                ));
    }

    private @NotNull Optional<VirtualFile> findExistingTargetDirectory(
            @NotNull VirtualFile root,
            @NotNull VirtualFile currentFile,
            @NotNull String targetRelativePath
    ) {
        int slash = targetRelativePath.lastIndexOf('/');
        String targetFileName = slash < 0 ? targetRelativePath : targetRelativePath.substring(slash + 1);
        if (!targetFileName.equals(currentFile.getName())) {
            return Optional.empty();
        }

        String targetDirectoryPath = slash < 0 ? "" : targetRelativePath.substring(0, slash);
        VirtualFile targetDirectory = targetDirectoryPath.isEmpty()
                ? root
                : root.findFileByRelativePath(targetDirectoryPath);
        if (targetDirectory == null
                || !targetDirectory.isDirectory()
                || targetDirectory.equals(currentFile.getParent())
                || targetDirectory.findChild(currentFile.getName()) != null) {
            return Optional.empty();
        }
        return Optional.of(targetDirectory);
    }

    private VirtualFile findRoot(
            @NotNull VirtualFile projectDirectory,
            @NotNull LegacyAutoloadMapping mapping
    ) {
        String path = mapping.sourceRoot().projectRelativePath();
        VirtualFile root = path.isEmpty() ? projectDirectory : projectDirectory.findFileByRelativePath(path);
        return root != null && root.isDirectory() ? root : null;
    }

    public record FileResolution(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull VirtualFile root,
            @NotNull String relativeFilePath,
            @NotNull String expectedClassName,
            int mappingIndex
    ) {
    }

    public record ClassResolution(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull VirtualFile root,
            @NotNull String relativeFilePath,
            VirtualFile file
    ) {
    }
}


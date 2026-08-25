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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Resolves project-relative base directories and evaluates every applicable flat rule. */
public final class ProjectLegacyAutoloadResolver {
    private final LegacyAutoloadResolver mappingResolver;

    public ProjectLegacyAutoloadResolver() {
        this(new LegacyAutoloadResolver());
    }

    ProjectLegacyAutoloadResolver(@NotNull LegacyAutoloadResolver mappingResolver) {
        this.mappingResolver = mappingResolver;
    }

    public @NotNull FileEvaluation evaluateFile(
            @NotNull Project project,
            @NotNull VirtualFile file,
            @NotNull List<Declaration> declarations,
            @NotNull List<LegacyAutoloadMapping> mappings
    ) {
        List<ApplicableRule> rules = applicableRules(project, file, mappings);
        if (rules.isEmpty() || declarations.isEmpty()) {
            return FileEvaluation.empty();
        }
        List<DeclarationCandidate> candidates = new ArrayList<>();
        for (ApplicableRule rule : rules) {
            if (!rule.mapping().capabilities().validateExistingPair()) {
                continue;
            }
            for (Declaration declaration : declarations) {
                if (declaration.namespaced() != rule.mapping().classPattern().supportsNamespaces()
                        || !mappingResolver.matchesClassPattern(rule.mapping(), declaration.qualifiedName())) {
                    continue;
                }
                Optional<String> expectedClass = mappingResolver.resolveClass(
                        rule.mapping(), rule.relativeFilePath());
                Optional<String> expectedFile = mappingResolver.resolveFile(
                        rule.mapping(), declaration.qualifiedName());
                candidates.add(new DeclarationCandidate(
                        declaration,
                        rule.mapping(),
                        rule.root(),
                        rule.relativeFilePath(),
                        expectedClass,
                        expectedFile,
                        mappingResolver.validatesPair(
                                rule.mapping(), declaration.qualifiedName(), rule.relativeFilePath()),
                        rule.mappingIndex()
                ));
            }
        }
        return new FileEvaluation(candidates);
    }

    private @NotNull List<ApplicableRule> applicableRules(
            @NotNull Project project,
            @NotNull VirtualFile file,
            @NotNull List<LegacyAutoloadMapping> mappings
    ) {
        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
        if (projectDirectory == null || file.isDirectory()) {
            return List.of();
        }
        List<ApplicableRule> rules = new ArrayList<>();
        for (int index = 0; index < mappings.size(); index++) {
            LegacyAutoloadMapping mapping = mappings.get(index);
            VirtualFile root = findRoot(projectDirectory, mapping);
            if (root == null || !VfsUtilCore.isAncestor(root, file, true)) {
                continue;
            }
            String relativePath = VfsUtilCore.getRelativePath(file, root, '/');
            if (relativePath != null) {
                rules.add(new ApplicableRule(mapping, root, relativePath, index));
            }
        }
        return rules.stream().sorted(Comparator
                .comparingInt((ApplicableRule rule) -> rule.root().getPath().length()).reversed()
                .thenComparing(rule -> rule.mapping().fallbackChainId())
                .thenComparingInt(rule -> rule.mapping().fallbackOrder())
                .thenComparingInt(ApplicableRule::mappingIndex)).toList();
    }

    public @NotNull Optional<MoveTarget> findMoveTarget(
            @NotNull DeclarationCandidate candidate,
            @NotNull VirtualFile currentFile
    ) {
        return candidate.expectedRelativeFilePath().flatMap(expectedPath ->
                findMoveTarget(candidate.root(), expectedPath, currentFile));
    }

    private @NotNull Optional<MoveTarget> findMoveTarget(
            @NotNull VirtualFile root,
            @NotNull String targetRelativePath,
            @NotNull VirtualFile currentFile
    ) {
        int slash = targetRelativePath.lastIndexOf('/');
        String targetFileName = slash < 0 ? targetRelativePath : targetRelativePath.substring(slash + 1);
        String targetDirectoryPath = slash < 0 ? "" : targetRelativePath.substring(0, slash);
        VirtualFile targetDirectory = targetDirectoryPath.isEmpty()
                ? root : root.findFileByRelativePath(targetDirectoryPath);
        if (targetDirectory == null || !targetDirectory.isDirectory()) {
            return Optional.empty();
        }
        VirtualFile conflict = targetDirectory.findChild(targetFileName);
        if (conflict != null && !conflict.equals(currentFile)) {
            return Optional.empty();
        }
        if (targetDirectory.equals(currentFile.getParent()) && targetFileName.equals(currentFile.getName())) {
            return Optional.empty();
        }
        return Optional.of(new MoveTarget(targetDirectory, targetFileName));
    }

    private VirtualFile findRoot(
            @NotNull VirtualFile projectDirectory,
            @NotNull LegacyAutoloadMapping mapping
    ) {
        String path = mapping.baseDirectory().projectRelativePath();
        VirtualFile root = path.isEmpty() ? projectDirectory : projectDirectory.findFileByRelativePath(path);
        return root != null && root.isDirectory() ? root : null;
    }

    public record Declaration(
            @NotNull String qualifiedName,
            @NotNull String simpleName,
            boolean namespaced
    ) {
        public Declaration {
            if (qualifiedName.startsWith("\\")) {
                qualifiedName = qualifiedName.substring(1);
            }
        }
    }

    public record DeclarationCandidate(
            @NotNull Declaration declaration,
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull VirtualFile root,
            @NotNull String currentRelativeFilePath,
            @NotNull Optional<String> expectedQualifiedClassName,
            @NotNull Optional<String> expectedRelativeFilePath,
            boolean exactMatch,
            int mappingIndex
    ) {
    }

    public record FileEvaluation(@NotNull List<DeclarationCandidate> candidates) {
        public FileEvaluation {
            candidates = List.copyOf(candidates);
        }

        public static @NotNull FileEvaluation empty() {
            return new FileEvaluation(List.of());
        }

        public boolean hasExactMatch() {
            return candidates.stream().anyMatch(DeclarationCandidate::exactMatch);
        }

        public @NotNull Set<Declaration> candidateDeclarations() {
            Set<Declaration> result = new LinkedHashSet<>();
            candidates.forEach(candidate -> result.add(candidate.declaration()));
            return java.util.Collections.unmodifiableSet(result);
        }

        public @NotNull List<DeclarationCandidate> mismatches() {
            return candidates.stream().filter(candidate -> !candidate.exactMatch()).toList();
        }
    }

    public record MoveTarget(@NotNull VirtualFile directory, @NotNull String fileName) {}

    private record ApplicableRule(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull VirtualFile root,
            @NotNull String relativeFilePath,
            int mappingIndex
    ) {}
}

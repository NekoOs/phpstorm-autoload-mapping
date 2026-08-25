package com.github.nekoos.legacyautoload.inspection;

import com.github.nekoos.legacyautoload.LegacyAutoloadBundle;
import com.github.nekoos.legacyautoload.mapping.AmbiguousQuickFixPolicy;
import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadMapping;
import com.github.nekoos.legacyautoload.quickfix.MoveFileToMappedPathQuickFix;
import com.github.nekoos.legacyautoload.quickfix.RenameClassToMappedNameQuickFix;
import com.github.nekoos.legacyautoload.resolver.ProjectLegacyAutoloadResolver;
import com.github.nekoos.legacyautoload.resolver.ProjectLegacyAutoloadResolver.Declaration;
import com.github.nekoos.legacyautoload.resolver.ProjectLegacyAutoloadResolver.DeclarationCandidate;
import com.github.nekoos.legacyautoload.resolver.ProjectLegacyAutoloadResolver.FileEvaluation;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Inspects a recognized PHP file as a whole, so no declaration is selected arbitrarily. */
public final class LegacyAutoloadPathMismatchInspection extends LocalInspectionTool {
    private final ProjectLegacyAutoloadResolver resolver = new ProjectLegacyAutoloadResolver();

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        List<LegacyAutoloadMapping> mappings = LegacyAutoloadProjectSettings
                .getInstance(holder.getProject()).getMappings();
        if (mappings.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (file instanceof PhpFile phpFile) {
                    inspectFile(phpFile, mappings, holder);
                }
            }
        };
    }

    private void inspectFile(
            @NotNull PhpFile phpFile,
            @NotNull List<LegacyAutoloadMapping> mappings,
            @NotNull ProblemsHolder holder
    ) {
        VirtualFile file = phpFile.getVirtualFile();
        if (file == null) {
            return;
        }
        Map<Declaration, PhpClass> classes = supportedTopLevelClasses(phpFile);
        if (classes.isEmpty()) {
            return;
        }
        FileEvaluation evaluation = resolver.evaluateFile(
                holder.getProject(), file, List.copyOf(classes.keySet()), mappings);
        if (evaluation.hasExactMatch() || evaluation.candidates().isEmpty()) {
            return;
        }

        Set<Declaration> declarations = evaluation.candidateDeclarations();
        boolean declarationAmbiguity = declarations.size() > 1;
        List<LocalQuickFix> quickFixes = quickFixes(
                evaluation.mismatches(), file, declarationAmbiguity);
        PsiElement problemElement;
        if (declarationAmbiguity) {
            problemElement = phpFile;
        } else {
            PhpClass phpClass = classes.get(declarations.iterator().next());
            problemElement = phpClass.getNameIdentifier() == null ? phpClass : phpClass.getNameIdentifier();
        }
        holder.registerProblem(
                problemElement,
                problemMessage(evaluation.mismatches(), declarations),
                quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY)
        );
    }

    private @NotNull List<LocalQuickFix> quickFixes(
            @NotNull List<DeclarationCandidate> candidates,
            @NotNull VirtualFile file,
            boolean declarationAmbiguity
    ) {
        List<LocalQuickFix> fixes = new ArrayList<>();
        Map<String, DeclarationCandidate> renameTargets = new LinkedHashMap<>();
        for (DeclarationCandidate candidate : candidates) {
            candidate.expectedQualifiedClassName().ifPresent(expected ->
                    renameTargets.putIfAbsent(candidate.declaration().qualifiedName() + "\0" + expected, candidate));
        }
        boolean uniqueRename = renameTargets.size() == 1 && !declarationAmbiguity;
        for (Map.Entry<String, DeclarationCandidate> entry : renameTargets.entrySet()) {
            DeclarationCandidate candidate = entry.getValue();
            String expectedQualified = candidate.expectedQualifiedClassName().orElseThrow();
            String expectedSimple = simpleName(expectedQualified);
            if (!sameNamespace(candidate.declaration().qualifiedName(), expectedQualified)
                    || !isSafeClassName(expectedSimple)
                    || (!uniqueRename && candidate.mapping().quickFixPolicy()
                    != AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES)) {
                continue;
            }
            fixes.add(new RenameClassToMappedNameQuickFix(
                    expectedSimple,
                    uniqueRename ? "" : candidate.mapping().name(),
                    candidate.declaration().qualifiedName(),
                    declarationAmbiguity ? candidate.declaration().simpleName() : ""
            ));
        }

        Map<String, DeclarationCandidate> moveTargets = new LinkedHashMap<>();
        for (DeclarationCandidate candidate : candidates) {
            candidate.expectedRelativeFilePath().ifPresent(expected ->
                    moveTargets.putIfAbsent(candidate.root().getPath() + "/" + expected, candidate));
        }
        boolean uniqueMove = moveTargets.size() == 1 && !declarationAmbiguity;
        for (DeclarationCandidate candidate : moveTargets.values()) {
            if (!uniqueMove && candidate.mapping().quickFixPolicy()
                    != AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES) {
                continue;
            }
            resolver.findMoveTarget(candidate, file).map(target -> new MoveFileToMappedPathQuickFix(
                    target.directory().getUrl(),
                    target.fileName(),
                    uniqueMove ? "" : candidate.mapping().name()
            )).ifPresent(fixes::add);
        }
        return fixes;
    }

    private @NotNull String problemMessage(
            @NotNull List<DeclarationCandidate> candidates,
            @NotNull Set<Declaration> declarations
    ) {
        if (declarations.size() > 1) {
            String names = declarations.stream().map(Declaration::simpleName).sorted()
                    .reduce((left, right) -> left + ", " + right).orElse("");
            return LegacyAutoloadBundle.message("inspection.problem.declarations", names);
        }
        Declaration declaration = declarations.iterator().next();
        List<String> expectedNames = candidates.stream()
                .filter(candidate -> candidate.declaration().equals(declaration))
                .flatMap(candidate -> candidate.expectedQualifiedClassName().stream())
                .map(this::simpleName).distinct().sorted().toList();
        if (expectedNames.size() == 1) {
            String mappingName = candidates.stream()
                    .filter(candidate -> candidate.expectedQualifiedClassName().isPresent())
                    .map(candidate -> candidate.mapping().name()).findFirst().orElse("");
            return LegacyAutoloadBundle.message(
                    "inspection.problem", mappingName, expectedNames.getFirst(), declaration.simpleName());
        }
        String paths = candidates.stream().flatMap(candidate -> candidate.expectedRelativeFilePath().stream())
                .distinct().sorted().reduce((left, right) -> left + ", " + right).orElse("");
        return LegacyAutoloadBundle.message("inspection.problem.paths", declaration.simpleName(), paths);
    }

    private @NotNull Map<Declaration, PhpClass> supportedTopLevelClasses(@NotNull PhpFile phpFile) {
        Collection<?> topLevelDefinitions = phpFile.getTopLevelDefs().values();
        Map<Declaration, PhpClass> result = new LinkedHashMap<>();
        for (Object definition : topLevelDefinitions) {
            if (!(definition instanceof PhpClass phpClass)
                    || phpClass.isAnonymous()
                    || phpClass.isInterface()
                    || phpClass.isTrait()
                    || phpClass.isEnum()
                    || phpClass.getName().isEmpty()) {
                continue;
            }
            String qualified = normalizeQualifiedName(phpClass.getFQN());
            result.put(new Declaration(
                    qualified,
                    phpClass.getName(),
                    qualified.indexOf('\\') >= 0
            ), phpClass);
        }
        return result;
    }

    private @NotNull String normalizeQualifiedName(@NotNull String name) {
        return name.startsWith("\\") ? name.substring(1) : name;
    }

    private @NotNull String simpleName(@NotNull String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('\\');
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
    }

    private boolean sameNamespace(@NotNull String left, @NotNull String right) {
        int leftSeparator = left.lastIndexOf('\\');
        int rightSeparator = right.lastIndexOf('\\');
        return left.substring(0, Math.max(0, leftSeparator))
                .equals(right.substring(0, Math.max(0, rightSeparator)));
    }

    private boolean isSafeClassName(@NotNull String className) {
        if (className.isEmpty() || !(Character.isLetter(className.charAt(0)) || className.charAt(0) == '_')) {
            return false;
        }
        for (int index = 1; index < className.length(); index++) {
            char character = className.charAt(index);
            if (!(Character.isLetterOrDigit(character) || character == '_')) {
                return false;
            }
        }
        return true;
    }
}

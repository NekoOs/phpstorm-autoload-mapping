package com.github.nekoos.legacyautoload.inspection;

import com.github.nekoos.legacyautoload.LegacyAutoloadBundle;
import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadMapping;
import com.github.nekoos.legacyautoload.quickfix.MoveFileToMappedPathQuickFix;
import com.github.nekoos.legacyautoload.quickfix.RenameClassToMappedNameQuickFix;
import com.github.nekoos.legacyautoload.resolver.ProjectLegacyAutoloadResolver;
import com.github.nekoos.legacyautoload.resolver.ProjectLegacyAutoloadResolver.FileResolution;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class LegacyAutoloadPathMismatchInspection extends LocalInspectionTool {
    private final ProjectLegacyAutoloadResolver resolver = new ProjectLegacyAutoloadResolver();

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        List<LegacyAutoloadMapping> mappings = LegacyAutoloadProjectSettings
                .getInstance(holder.getProject())
                .getMappings();
        if (mappings.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new PhpElementVisitor() {
            @Override
            public void visitPhpClass(PhpClass phpClass) {
                inspectClass(phpClass, mappings, holder);
            }
        };
    }

    private void inspectClass(
            @NotNull PhpClass phpClass,
            @NotNull List<LegacyAutoloadMapping> mappings,
            @NotNull ProblemsHolder holder
    ) {
        if (!isTopLevelGlobalClass(phpClass)) {
            return;
        }

        VirtualFile file = phpClass.getContainingFile().getVirtualFile();
        if (file == null) {
            return;
        }

        String actualName = phpClass.getName();
        List<FileResolution> resolutions = resolver.resolveFile(holder.getProject(), file, mappings);
        if (resolutions.isEmpty()
                || resolutions.stream().anyMatch(value -> value.expectedClassName().equals(actualName))) {
            return;
        }

        FileResolution selectedResolution = resolutions.getFirst();
        String expectedName = selectedResolution.expectedClassName();
        List<LocalQuickFix> quickFixes = new ArrayList<>();
        if (isSafeGlobalClassName(expectedName)) {
            quickFixes.add(new RenameClassToMappedNameQuickFix(expectedName));
        }
        resolver.findMoveTargetDirectory(selectedResolution, file, actualName)
                .map(VirtualFile::getUrl)
                .map(MoveFileToMappedPathQuickFix::new)
                .ifPresent(quickFixes::add);

        PsiElement problemElement = phpClass.getNameIdentifier();
        holder.registerProblem(
                problemElement == null ? phpClass : problemElement,
                LegacyAutoloadBundle.message(
                        "inspection.problem",
                        selectedResolution.mapping().name(),
                        expectedName,
                        actualName
                ),
                quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY)
        );
    }

    private boolean isTopLevelGlobalClass(@NotNull PhpClass phpClass) {
        if (phpClass.isAnonymous()
                || phpClass.isInterface()
                || phpClass.isTrait()
                || phpClass.isEnum()
                || !isGlobalNamespace(phpClass.getNamespaceName())) {
            return false;
        }
        if (!(phpClass.getContainingFile() instanceof PhpFile phpFile)) {
            return false;
        }
        Collection<?> topLevelDefinitions = phpFile.getTopLevelDefs().values();
        return topLevelDefinitions.contains(phpClass);
    }

    private boolean isGlobalNamespace(@NotNull String namespaceName) {
        return namespaceName.isEmpty() || namespaceName.equals("\\");
    }

    private boolean isSafeGlobalClassName(@NotNull String className) {
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


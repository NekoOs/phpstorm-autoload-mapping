package com.github.nekoos.autoloadmapping.quickfix;

import com.github.nekoos.autoloadmapping.AutoloadMappingBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class RenameClassToMappedNameQuickFix implements LocalQuickFix {
    private final String expectedName;
    private final String mappingName;
    private final String targetQualifiedName;
    private final String declarationLabel;

    public RenameClassToMappedNameQuickFix(@NotNull String expectedName) {
        this(expectedName, "", "", "");
    }

    public RenameClassToMappedNameQuickFix(@NotNull String expectedName, @NotNull String mappingName) {
        this(expectedName, mappingName, "", "");
    }

    public RenameClassToMappedNameQuickFix(
            @NotNull String expectedName,
            @NotNull String mappingName,
            @NotNull String targetQualifiedName,
            @NotNull String declarationLabel
    ) {
        this.expectedName = expectedName;
        this.mappingName = mappingName;
        this.targetQualifiedName = normalize(targetQualifiedName);
        this.declarationLabel = declarationLabel;
    }

    @Override
    public @NotNull @Nls String getFamilyName() {
        return AutoloadMappingBundle.message("quickfix.rename");
    }

    @Override
    public @NotNull @Nls String getName() {
        if (!declarationLabel.isEmpty()) {
            return AutoloadMappingBundle.message("quickfix.rename.declaration", declarationLabel, expectedName);
        }
        return mappingName.isEmpty()
                ? getFamilyName()
                : AutoloadMappingBundle.message("quickfix.rename.named", expectedName, mappingName);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement problemElement = descriptor.getPsiElement();
        PhpClass phpClass = problemElement instanceof PhpClass directClass
                ? directClass
                : PsiTreeUtil.getParentOfType(problemElement, PhpClass.class, false);
        if (!targetQualifiedName.isEmpty() && problemElement.getContainingFile() instanceof PhpFile phpFile) {
            phpClass = phpFile.getTopLevelDefs().values().stream()
                    .filter(PhpClass.class::isInstance)
                    .map(PhpClass.class::cast)
                    .filter(candidate -> normalize(candidate.getFQN()).equals(targetQualifiedName))
                    .findFirst()
                    .orElse(null);
        }
        if (phpClass == null || !phpClass.isValid() || expectedName.equals(phpClass.getName())) {
            return;
        }
        new RenameProcessor(project, phpClass, expectedName, false, false).run();
    }

    private static @NotNull String normalize(@NotNull String value) {
        return value.startsWith("\\") ? value.substring(1) : value;
    }
}

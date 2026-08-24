package com.github.nekoos.legacyautoload.quickfix;

import com.github.nekoos.legacyautoload.LegacyAutoloadBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class RenameClassToMappedNameQuickFix implements LocalQuickFix {
    private final String expectedName;

    public RenameClassToMappedNameQuickFix(@NotNull String expectedName) {
        this.expectedName = expectedName;
    }

    @Override
    public @NotNull @Nls String getFamilyName() {
        return LegacyAutoloadBundle.message("quickfix.rename");
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
        if (phpClass == null || !phpClass.isValid() || expectedName.equals(phpClass.getName())) {
            return;
        }
        new RenameProcessor(project, phpClass, expectedName, false, false).run();
    }
}


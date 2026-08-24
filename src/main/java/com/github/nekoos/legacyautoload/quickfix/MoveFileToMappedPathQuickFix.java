package com.github.nekoos.legacyautoload.quickfix;

import com.github.nekoos.legacyautoload.LegacyAutoloadBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class MoveFileToMappedPathQuickFix implements LocalQuickFix {
    private final String targetDirectoryUrl;

    public MoveFileToMappedPathQuickFix(@NotNull String targetDirectoryUrl) {
        this.targetDirectoryUrl = targetDirectoryUrl;
    }

    @Override
    public @NotNull @Nls String getFamilyName() {
        return LegacyAutoloadBundle.message("quickfix.move");
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiFile file = descriptor.getPsiElement().getContainingFile();
        VirtualFile targetVirtualDirectory = VirtualFileManager.getInstance().findFileByUrl(targetDirectoryUrl);
        if (file == null || !file.isValid() || targetVirtualDirectory == null) {
            return;
        }

        PsiDirectory targetDirectory = PsiManager.getInstance(project).findDirectory(targetVirtualDirectory);
        if (targetDirectory == null || targetDirectory.findFile(file.getName()) != null) {
            return;
        }

        new MoveFilesOrDirectoriesProcessor(
                project,
                new PsiElement[]{file},
                targetDirectory,
                false,
                false,
                null,
                null
        ).run();
    }
}


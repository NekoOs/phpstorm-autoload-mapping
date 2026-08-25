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
    private final String targetFileName;
    private final String mappingName;

    public MoveFileToMappedPathQuickFix(@NotNull String targetDirectoryUrl) {
        this(targetDirectoryUrl, "", "");
    }

    public MoveFileToMappedPathQuickFix(
            @NotNull String targetDirectoryUrl,
            @NotNull String targetFileName,
            @NotNull String mappingName
    ) {
        this.targetDirectoryUrl = targetDirectoryUrl;
        this.targetFileName = targetFileName;
        this.mappingName = mappingName;
    }

    @Override
    public @NotNull @Nls String getFamilyName() {
        return LegacyAutoloadBundle.message("quickfix.move");
    }

    @Override
    public @NotNull @Nls String getName() {
        return mappingName.isEmpty()
                ? getFamilyName()
                : LegacyAutoloadBundle.message("quickfix.move.named", mappingName);
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
        String originalFileName = file.getName();
        String desiredFileName = targetFileName.isEmpty() ? originalFileName : targetFileName;
        PsiFile existingTarget = targetDirectory == null ? null : targetDirectory.findFile(desiredFileName);
        if (targetDirectory == null || (existingTarget != null && !existingTarget.equals(file))) {
            return;
        }

        if (targetDirectory.equals(file.getContainingDirectory())) {
            renameFile(project, file, desiredFileName);
            return;
        }

        new MoveFilesOrDirectoriesProcessor(
                project,
                new PsiElement[]{file},
                targetDirectory,
                false,
                false,
                () -> {
                    PsiFile movedFile = targetDirectory.findFile(originalFileName);
                    if (movedFile != null) {
                        renameFile(project, movedFile, desiredFileName);
                    }
                },
                null
        ).run();
    }

    private void renameFile(@NotNull Project project, @NotNull PsiFile file, @NotNull String desiredFileName) {
        if (!desiredFileName.equals(file.getName())) {
            new com.intellij.refactoring.rename.RenameProcessor(
                    project,
                    file,
                    desiredFileName,
                    false,
                    false
            ).run();
        }
    }
}

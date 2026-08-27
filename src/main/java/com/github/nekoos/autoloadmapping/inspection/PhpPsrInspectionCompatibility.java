package com.github.nekoos.autoloadmapping.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import org.jetbrains.annotations.NotNull;

/**
 * Coordinates with PhpStorm's built-in PSR-0/PSR-4 path inspection without
 * coupling the mapping resolver to PhpStorm inspection internals.
 */
public final class PhpPsrInspectionCompatibility {
    public static final String INSPECTION_SHORT_NAME = "PhpIllegalPsrClassPathInspection";

    private PhpPsrInspectionCompatibility() {
    }

    public static boolean isEnabled(@NotNull Project project) {
        HighlightDisplayKey key = HighlightDisplayKey.find(INSPECTION_SHORT_NAME);
        return key != null && currentProfile(project).isToolEnabled(key);
    }

    public static boolean canDisableForProject(@NotNull Project project) {
        return currentProfile(project).isProjectLevel();
    }

    public static void openSettings(@NotNull Project project) {
        if (HighlightDisplayKey.find(INSPECTION_SHORT_NAME) == null) {
            return;
        }
        EditInspectionToolsSettingsAction.editToolSettings(
                project,
                currentProfile(project),
                INSPECTION_SHORT_NAME
        );
    }

    public static boolean disableForProject(@NotNull Project project) {
        InspectionProfileImpl profile = currentProfile(project);
        if (!profile.isProjectLevel() || HighlightDisplayKey.find(INSPECTION_SHORT_NAME) == null) {
            return false;
        }
        profile.setToolEnabled(INSPECTION_SHORT_NAME, false, project);
        return true;
    }

    private static @NotNull InspectionProfileImpl currentProfile(@NotNull Project project) {
        return InspectionProfileManager.getInstance(project).getCurrentProfile();
    }
}

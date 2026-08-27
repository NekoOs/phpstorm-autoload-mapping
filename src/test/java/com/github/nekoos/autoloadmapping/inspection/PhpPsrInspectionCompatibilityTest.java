package com.github.nekoos.autoloadmapping.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

public final class PhpPsrInspectionCompatibilityTest extends BasePlatformTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new TestPhpPsrPathInspection());
    }

    public void testDisablesOfficialInspectionOnlyInProjectProfile() {
        InspectionProfileImpl profile = InspectionProfileManager.getInstance(getProject()).getCurrentProfile();
        boolean wasProjectLevel = profile.isProjectLevel();

        try {
            assertNotNull(HighlightDisplayKey.find(PhpPsrInspectionCompatibility.INSPECTION_SHORT_NAME));
            profile.setProjectLevel(true);

            profile.setToolEnabled(PhpPsrInspectionCompatibility.INSPECTION_SHORT_NAME, true, getProject());
            assertTrue(PhpPsrInspectionCompatibility.isEnabled(getProject()));
            assertTrue(PhpPsrInspectionCompatibility.canDisableForProject(getProject()));

            assertTrue(PhpPsrInspectionCompatibility.disableForProject(getProject()));
            assertFalse(PhpPsrInspectionCompatibility.isEnabled(getProject()));
        } finally {
            profile.setToolEnabled(PhpPsrInspectionCompatibility.INSPECTION_SHORT_NAME, true, getProject());
            profile.setProjectLevel(wasProjectLevel);
        }
    }

    public void testRefusesToDisableInspectionInSharedProfile() {
        InspectionProfileImpl profile = InspectionProfileManager.getInstance(getProject()).getCurrentProfile();
        boolean wasProjectLevel = profile.isProjectLevel();

        try {
            profile.setProjectLevel(false);
            profile.setToolEnabled(PhpPsrInspectionCompatibility.INSPECTION_SHORT_NAME, true, getProject());

            assertFalse(PhpPsrInspectionCompatibility.canDisableForProject(getProject()));
            assertFalse(PhpPsrInspectionCompatibility.disableForProject(getProject()));
            assertTrue(PhpPsrInspectionCompatibility.isEnabled(getProject()));
        } finally {
            profile.setProjectLevel(wasProjectLevel);
        }
    }

    private static final class TestPhpPsrPathInspection extends LocalInspectionTool {
        @Override
        public @NotNull String getShortName() {
            return PhpPsrInspectionCompatibility.INSPECTION_SHORT_NAME;
        }

        @Override
        public @NotNull String getDisplayName() {
            return "Test PhpStorm PSR path inspection";
        }

        @Override
        public @NotNull String getGroupDisplayName() {
            return "PHP";
        }
    }
}

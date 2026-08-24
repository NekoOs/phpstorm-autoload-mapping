package com.github.nekoos.legacyautoload.inspection;

import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadPresets;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.MappingState;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class LegacyAutoloadPathMismatchInspectionTest extends BasePlatformTestCase {
    private static final String PROBLEM_PREFIX = "Legacy autoload mapping";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new LegacyAutoloadPathMismatchInspection());
    }

    public void testUnitClassWithNestedPsr0NameIsValid() {
        assertPsr0ProblemCount(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_Job_FooTest extends PHPUnit_Framework_TestCase {}",
                0
        );
    }

    public void testUnitClassWithMissingPathSegmentIsInvalid() {
        assertPsr0ProblemCount(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_FooTest extends PHPUnit_Framework_TestCase {}",
                1
        );
    }

    public void testProductionClassWithPsr0NameIsValid() {
        assertPsr0ProblemCount(
                "website/application/classes",
                "App/Dao/User.php",
                "<?php class App_Dao_User extends App_Dao_Abstract {}",
                0
        );
    }

    public void testProductionClassWithMissingPathSegmentIsInvalid() {
        assertPsr0ProblemCount(
                "website/application/classes",
                "App/Dao/User.php",
                "<?php class App_User extends App_Dao_Abstract {}",
                1
        );
    }

    public void testKebabModelMappingIsValid() {
        assertCustomProblemCount(
                "legacy/models",
                "my-class.model.php",
                "<?php class MyClass_Model {}",
                "{class}_Model",
                "{class:kebab}.model.php",
                0
        );
    }

    public void testKebabModelMappingDetectsWrongClass() {
        assertCustomProblemCount(
                "legacy/models",
                "my-class.model.php",
                "<?php class OtherClass_Model {}",
                "{class}_Model",
                "{class:kebab}.model.php",
                1
        );
    }

    public void testControllerMappingWithFixedDirectoryIsValid() {
        assertCustomProblemCount(
                "legacy",
                "controllers/customer.controller.php",
                "<?php class CustomerController {}",
                "{class}Controller",
                "controllers/{class:snake}.controller.php",
                0
        );
    }

    public void testNamespacedClassIsIgnored() {
        assertPsr0ProblemCount(
                "website/application/classes",
                "App/Service/Foo.php",
                "<?php namespace App\\Service; class WrongName {}",
                0
        );
    }

    public void testFileOutsideConfiguredMappingsIsIgnored() {
        PsiFile file = myFixture.addFileToProject("src/App/Dao/User.php", "<?php class WrongName {}");
        LegacyAutoloadProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                MappingState.fromPreset(LegacyAutoloadPresets.PSR0_PEAR, "website/application/classes")
        ));
        assertProblemCount(file, 0);
    }

    public void testMultipleConfiguredRoots() {
        PsiFile unitFile = myFixture.addFileToProject(
                "tests/unit/Cli/Job/FooTest.php",
                "<?php class Cli_FooTest {}"
        );
        PsiFile productionFile = myFixture.addFileToProject(
                "website/application/classes/App/Dao/User.php",
                "<?php class App_User {}"
        );
        configurePsr0Mappings(List.of(
                new RootedFile(unitFile, 3),
                new RootedFile(productionFile, 3)
        ));

        assertProblemCount(unitFile, 1);
        assertProblemCount(productionFile, 1);
    }

    public void testDeeplyNestedDirectories() {
        assertPsr0ProblemCount(
                "tests/unit",
                "Cli/Job/Report/Daily/FooTest.php",
                "<?php class Cli_Job_Report_Daily_FooTest {}",
                0
        );
    }

    public void testMostSpecificConfiguredRootWins() {
        PsiFile file = myFixture.addFileToProject(
                "tests/unit/Cli/Job/FooTest.php",
                "<?php class Cli_Job_FooTest {}"
        );
        configurePsr0Mappings(List.of(
                new RootedFile(file, 4),
                new RootedFile(file, 3)
        ));

        assertProblemCount(file, 0);
    }

    public void testPhpFileWithoutClassIsIgnored() {
        assertPsr0ProblemCount(
                "tests/unit",
                "Cli/Job/functions.php",
                "<?php function legacy_helper() { return true; }",
                0
        );
    }

    public void testEveryTopLevelGlobalClassIsValidated() {
        assertPsr0ProblemCount(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_Job_FooTest {} class Cli_Job_Helper {}",
                1
        );
    }

    public void testInterfaceTraitAndEnumAreIgnored() {
        assertPsr0ProblemCount(
                "tests/unit",
                "Cli/Job/Types.php",
                "<?php interface WrongInterface {} trait WrongTrait {} enum WrongEnum {}",
                0
        );
    }

    public void testDiagnosticContainsMappingExpectedAndActualNames() {
        PsiFile file = addPsr0File(
                "tests/unit",
                "Cli/Job/ReportSubscriptionsScdfWorkerTest.php",
                "<?php class Cli_Job_ReportSubscriptionsWorkerTest {}"
        );

        List<HighlightInfo> problems = legacyProblems(file);
        assertSize(1, problems);
        assertEquals(
                "Legacy autoload mapping 'PSR-0 / PEAR underscores to directories' does not match class and file. "
                        + "Expected class: Cli_Job_ReportSubscriptionsScdfWorkerTest. "
                        + "Actual: Cli_Job_ReportSubscriptionsWorkerTest.",
                problems.getFirst().getDescription()
        );
    }

    public void testRenameQuickFixIsAvailable() {
        PsiFile file = addPsr0File(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_FooTest {}"
        );
        configureAtClassName(file);

        assertNotNull(myFixture.findSingleIntention("Rename class to expected legacy autoload name"));
    }

    public void testRenameQuickFixUpdatesCodeReferences() {
        PsiFile file = addPsr0File(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_FooTest {} function create_test() { return new Cli_FooTest(); }"
        );
        configureAtClassName(file);

        IntentionAction rename = myFixture.findSingleIntention(
                "Rename class to expected legacy autoload name"
        );
        myFixture.launchAction(rename);

        myFixture.checkResult(
                "<?php class Cli_Job_FooTest {} function create_test() { return new Cli_Job_FooTest(); }"
        );
    }

    public void testMoveQuickFixIsAvailableForUnambiguousExistingDirectory() {
        myFixture.addFileToProject("tests/unit/Cli/Keep.php", "<?php");
        PsiFile file = addPsr0File(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_FooTest {}"
        );
        configureAtClassName(file);

        assertNotNull(myFixture.findSingleIntention("Move file to mapped autoload path"));
    }

    private void assertPsr0ProblemCount(
            @NotNull String rootPath,
            @NotNull String relativeFilePath,
            @NotNull String contents,
            int expectedCount
    ) {
        assertProblemCount(addPsr0File(rootPath, relativeFilePath, contents), expectedCount);
    }

    private @NotNull PsiFile addPsr0File(
            @NotNull String rootPath,
            @NotNull String relativeFilePath,
            @NotNull String contents
    ) {
        PsiFile file = myFixture.addFileToProject(rootPath + "/" + relativeFilePath, contents);
        configurePsr0Mappings(List.of(new RootedFile(file, segmentCount(relativeFilePath))));
        return file;
    }

    private void assertCustomProblemCount(
            @NotNull String rootPath,
            @NotNull String relativeFilePath,
            @NotNull String contents,
            @NotNull String classPattern,
            @NotNull String filePattern,
            int expectedCount
    ) {
        PsiFile file = myFixture.addFileToProject(rootPath + "/" + relativeFilePath, contents);
        String resolvedRoot = resolveRoot(file, segmentCount(relativeFilePath));
        LegacyAutoloadProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState("Custom", resolvedRoot, classPattern, filePattern, "")
        ));
        assertProblemCount(file, expectedCount);
    }

    private void assertProblemCount(@NotNull PsiFile file, int expectedCount) {
        assertEquals(expectedCount, legacyProblems(file).size());
    }

    private @NotNull List<HighlightInfo> legacyProblems(@NotNull PsiFile file) {
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        List<HighlightInfo> result = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            String description = info.getDescription();
            if (description != null && description.startsWith(PROBLEM_PREFIX)) {
                result.add(info);
            }
        }
        return result;
    }

    private void configurePsr0Mappings(@NotNull List<RootedFile> rootedFiles) {
        List<MappingState> mappings = rootedFiles.stream()
                .map(rootedFile -> MappingState.fromPreset(
                        LegacyAutoloadPresets.PSR0_PEAR,
                        resolveRoot(rootedFile.file(), rootedFile.relativePathSegmentCount())
                ))
                .toList();
        LegacyAutoloadProjectSettings.getInstance(getProject()).setMappingStates(mappings);
    }

    private @NotNull String resolveRoot(@NotNull PsiFile file, int relativePathSegmentCount) {
        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(getProject());
        assertNotNull(projectDirectory);
        VirtualFile root = file.getVirtualFile();
        for (int index = 0; index < relativePathSegmentCount; index++) {
            root = root.getParent();
            assertNotNull(root);
        }
        String relativeRoot = VfsUtilCore.getRelativePath(root, projectDirectory, '/');
        assertNotNull(relativeRoot);
        return relativeRoot;
    }

    private int segmentCount(@NotNull String path) {
        return path.split("/").length;
    }

    private void configureAtClassName(@NotNull PsiFile file) {
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PhpClass phpClass = assertOneElement(PsiTreeUtil.findChildrenOfType(file, PhpClass.class));
        assertNotNull(phpClass.getNameIdentifier());
        myFixture.getEditor().getCaretModel().moveToOffset(phpClass.getNameIdentifier().getTextOffset());
        myFixture.doHighlighting();
    }

    private record RootedFile(@NotNull PsiFile file, int relativePathSegmentCount) {
    }
}


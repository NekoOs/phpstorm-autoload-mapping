package com.github.nekoos.autoloadmapping.inspection;

import com.github.nekoos.autoloadmapping.mapping.AmbiguousQuickFixPolicy;
import com.github.nekoos.autoloadmapping.mapping.AutoloadMappingRulePack;
import com.github.nekoos.autoloadmapping.mapping.AutoloadMappingRulePackCatalog;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.MappingState;
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

public final class AutoloadMappingPathMismatchInspectionTest extends BasePlatformTestCase {
    private static final String PROBLEM_PREFIX = "Autoload mapping";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new AutoloadMappingPathMismatchInspection());
    }

    public void testUnitClassWithNestedPsr0NameIsValid() {
        assertPsr0ProblemCount(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_Job_FooTest extends PHPUnit_Framework_TestCase {}",
                0
        );
    }

    public void testZendFramework1ApplicationSchemaUsesApplicationResourcePaths() {
        PsiFile file = myFixture.addFileToProject(
                "website/application/models/Report/User.php",
                "<?php class App_Model_Report_User {}"
        );
        configureRulePack(AutoloadMappingRulePackCatalog.ZEND_FRAMEWORK_1_APPLICATION, "website");

        assertProblemCount(file, 0);
    }

    public void testLaravel3ApplicationSchemaUsesNestedControllerPaths() {
        PsiFile file = myFixture.addFileToProject(
                "legacy/application/controllers/admin/panel.php",
                "<?php class Admin_Panel_Controller extends Base_Controller {}"
        );
        configureRulePack(AutoloadMappingRulePackCatalog.LARAVEL_3_APPLICATION, "legacy");

        assertProblemCount(file, 0);
    }

    public void testCodeIgniter2ApplicationSchemaUsesLowercaseModelFiles() {
        PsiFile file = myFixture.addFileToProject(
                "app/application/models/user_model.php",
                "<?php class User_model extends CI_Model {}"
        );
        configureRulePack(AutoloadMappingRulePackCatalog.CODEIGNITER_2_APPLICATION, "app");

        assertProblemCount(file, 0);
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
                "{class:pascal}_Model",
                "{class:kebab}.model.php",
                0
        );
    }

    public void testKebabModelMappingDetectsWrongClass() {
        assertCustomProblemCount(
                "legacy/models",
                "my-class.model.php",
                "<?php class OtherClass_Model {}",
                "{class:pascal}_Model",
                "{class:kebab}.model.php",
                1
        );
    }

    public void testControllerMappingWithFixedDirectoryIsValid() {
        assertCustomProblemCount(
                "legacy",
                "controllers/customer.controller.php",
                "<?php class CustomerController {}",
                "{class:pascal}Controller",
                "controllers/{class:snake}.controller.php",
                0
        );
    }

    public void testIndependentRulesInSameSourceActAsOr() {
        PsiFile file = myFixture.addFileToProject(
                "app/controllers/customer.controller.php",
                "<?php class CustomerController {}"
        );
        String root = resolveRoot(file, 1);
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState(
                        "Modern controllers", root, "{name:pascal}Controller", "{name:pascal}.php"
                ),
                new MappingState(
                        "Legacy controllers", root, "{name:pascal}Controller", "{name:lower}.controller.php"
                )
        ));

        assertProblemCount(file, 0);
    }

    public void testExplicitFallbackOffersEveryConfiguredMoveAlternative() {
        PsiFile file = myFixture.addFileToProject(
                "app/controllers/legacy-customer.php",
                "<?php class CustomerController {}"
        );
        String root = resolveRoot(file, 1);
        MappingState modern = new MappingState(
                "modern-rule", "Modern controllers", root,
                "{name:pascal}Controller", "{name:pascal}.php", "controller-chain", 0,
                AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name()
        );
        MappingState legacy = new MappingState(
                "legacy-rule", "Legacy controllers", root,
                "{name:pascal}Controller", "{name:lower}.controller.php", "controller-chain", 1,
                AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name()
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(modern, legacy));
        configureAtClassName(file);

        List<String> intentions = myFixture.getAvailableIntentions().stream()
                .map(IntentionAction::getText)
                .filter(text -> text.startsWith("Move file using"))
                .sorted()
                .toList();
        assertEquals(List.of(
                "Move file using \"Legacy controllers\"",
                "Move file using \"Modern controllers\""
        ), intentions);
    }

    public void testMoreSpecificRootDoesNotImplicitlyHideIndependentGeneralRule() {
        PsiFile file = myFixture.addFileToProject(
                "app/controllers/CustomerController.php",
                "<?php class CustomerController {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState(
                        "Project convention", "", "{name}Controller",
                        "app/controllers/{name}Controller.php"
                ),
                new MappingState(
                        "Application exception", "app", "{name}Controller",
                        "legacy/{name}.php"
                )
        ));

        assertProblemCount(file, 0);
    }

    public void testWordPressPatternOffersCanonicalRename() {
        PsiFile file = myFixture.addFileToProject(
                "wordpress/class-wp-error.php",
                "<?php class WP_Wrong {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState(
                        "WordPress class files", "wordpress",
                        "WP_{name:pascal}", "class-wp-{name:kebab}.php"
                )
        ));
        configureAtClassName(file);

        assertNotNull(myFixture.findSingleIntention("Rename class to expected mapped autoload name"));
    }

    public void testAmbiguousClassExtractionDoesNotOfferMove() {
        myFixture.addFileToProject("legacy/My/Keep.php", "<?php");
        PsiFile file = myFixture.addFileToProject(
                "legacy/wrong.php",
                "<?php class Some_My_Example_barReport_service {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState(
                        "Ambiguous extraction", "legacy",
                        "{vendor:upper}{name:pascal}", "{vendor:lower}/{name:lower}.php"
                )
        ));
        configureAtClassName(file);

        assertNull(myFixture.getAvailableIntention("Move file to mapped autoload path"));
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
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                fromPsr0Pack("website/application/classes")
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

    public void testOneExactDeclarationMakesTheExistingPairValid() {
        assertPsr0ProblemCount(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_Job_FooTest {} class Cli_Job_Helper {}",
                0
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

        List<HighlightInfo> problems = autoloadProblems(file);
        assertSize(1, problems);
        assertEquals(
                "Autoload mapping 'PEAR / PSR-0' does not match class and file. "
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

        assertNotNull(myFixture.findSingleIntention("Rename class to expected mapped autoload name"));
    }

    public void testRenameQuickFixUpdatesCodeReferences() {
        PsiFile file = addPsr0File(
                "tests/unit",
                "Cli/Job/FooTest.php",
                "<?php class Cli_FooTest {} function create_test() { return new Cli_FooTest(); }"
        );
        configureAtClassName(file);

        IntentionAction rename = myFixture.findSingleIntention(
                "Rename class to expected mapped autoload name"
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

    public void testMoveQuickFixMovesAndRenamesFileThroughRefactoringApi() throws Exception {
        myFixture.addFileToProject("legacy/controllers/keep.php", "<?php");
        PsiFile file = myFixture.addFileToProject(
                "legacy/wrong.php",
                "<?php class CustomerController {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState(
                        "Controllers",
                        "legacy",
                        "{name:pascal}Controller",
                        "controllers/{name:lower}.controller.php"
                )
        ));
        configureAtClassName(file);

        IntentionAction move = myFixture.findSingleIntention("Move file to mapped autoload path");
        myFixture.launchAction(move);

        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(getProject());
        assertNotNull(projectDirectory);
        assertNull(projectDirectory.findFileByRelativePath("legacy/wrong.php"));
        VirtualFile moved = projectDirectory.findFileByRelativePath(
                "legacy/controllers/customer.controller.php"
        );
        assertNotNull(moved);
        assertEquals("<?php class CustomerController {}", VfsUtilCore.loadText(moved));
    }

    public void testMultipleCandidatesWithoutMatchAreAmbiguousAndHaveNoAutomaticFix() {
        PsiFile file = myFixture.addFileToProject(
                "legacy/controllers/order.php",
                "<?php class CustomerController {} class InvoiceController {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState("Controllers", "legacy", "{name:pascal}Controller",
                        "controllers/{name:lower}.php")
        ));
        configureAtFile(file);

        assertProblemCount(file, 1);
        assertNull(myFixture.getAvailableIntention("Rename class to expected mapped autoload name"));
        assertNull(myFixture.getAvailableIntention("Move file to mapped autoload path"));
    }

    public void testAmbiguousPolicyOffersActionsNamedByDeclaration() {
        PsiFile file = myFixture.addFileToProject(
                "legacy/controllers/order.php",
                "<?php class CustomerController {} class InvoiceController {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState(
                        "controllers", "Controllers", "legacy",
                        "{name:pascal}Controller", "controllers/{name:lower}.php",
                        "", 0, AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name()
                )
        ));
        configureAtFile(file);

        List<String> renames = myFixture.getAvailableIntentions().stream()
                .map(IntentionAction::getText)
                .filter(text -> text.startsWith("Rename Customer") || text.startsWith("Rename Invoice"))
                .sorted().toList();
        assertEquals(List.of(
                "Rename CustomerController to OrderController",
                "Rename InvoiceController to OrderController"
        ), renames);
    }

    public void testMultipleExactDeclarationsAreAccepted() {
        PsiFile file = myFixture.addFileToProject(
                "legacy/daily-orders.php",
                "<?php class DailyCustomerController {} class DailyInvoiceController {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState("Customers", "legacy", "{name:pascal}CustomerController",
                        "{name:lower}-orders.php"),
                new MappingState("Invoices", "legacy", "{name:pascal}InvoiceController",
                        "{name:lower}-orders.php")
        ));

        assertProblemCount(file, 0);
    }

    public void testNonPhpFileIsNotInspectedEvenWhenItContainsPhpText() {
        PsiFile file = myFixture.addFileToProject(
                "legacy/controllers/customer.txt",
                "<?php class WrongController {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState("Controllers", "legacy", "{name:pascal}Controller",
                        "controllers/{name:lower}.txt")
        ));

        assertProblemCount(file, 0);
    }

    public void testExplicitNamespaceRuleValidatesNamespacedDeclaration() {
        PsiFile file = myFixture.addFileToProject(
                "app/src/app/dao/user.model.php",
                "<?php namespace App\\Dao; class User_Model {}"
        );
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState(
                        "Namespaced models", "app",
                        "{namespace:pascal|sep(\\)}\\{name:pascal}_Model",
                        "src/{namespace:lower|sep(/)}/{name:lower}.model.php"
                )
        ));

        assertProblemCount(file, 0);
    }

    public void testClassToFileOnlyRuleOffersOnlyMoveFix() {
        PsiFile file = myFixture.addFileToProject("legacy/wrong.php", "<?php class Core_User {}");
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState("Partial", "legacy", "{module:pascal}_{name:pascal}",
                        "{name:lower}.php")
        ));
        configureAtClassName(file);

        assertNotNull(myFixture.getAvailableIntention("Move file to mapped autoload path"));
        assertNull(myFixture.getAvailableIntention("Rename class to expected mapped autoload name"));
    }

    public void testFileToClassOnlyRuleOffersOnlyRenameFix() {
        PsiFile file = myFixture.addFileToProject("legacy/core/user.php", "<?php class Wrong {}");
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState("Partial", "legacy", "{name:pascal}",
                        "{module:lower}/{name:lower}.php")
        ));
        configureAtClassName(file);

        assertNotNull(myFixture.getAvailableIntention("Rename class to expected mapped autoload name"));
        assertNull(myFixture.getAvailableIntention("Move file to mapped autoload path"));
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
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(List.of(
                new MappingState("Custom", resolvedRoot, classPattern, filePattern)
        ));
        assertProblemCount(file, expectedCount);
    }

    private void assertProblemCount(@NotNull PsiFile file, int expectedCount) {
        assertEquals(expectedCount, autoloadProblems(file).size());
    }

    private @NotNull List<HighlightInfo> autoloadProblems(@NotNull PsiFile file) {
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
                .map(rootedFile -> fromPsr0Pack(
                        resolveRoot(rootedFile.file(), rootedFile.relativePathSegmentCount())))
                .toList();
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(mappings);
    }

    private static @NotNull MappingState fromPsr0Pack(@NotNull String baseDirectory) {
        return MappingState.fromRulePack(AutoloadMappingRulePackCatalog.PEAR_PSR0, baseDirectory).getFirst();
    }

    private void configureRulePack(@NotNull AutoloadMappingRulePack rulePack, @NotNull String baseRoot) {
        AutoloadMappingProjectSettings.getInstance(getProject()).setMappingStates(
                MappingState.fromRulePack(rulePack, baseRoot)
        );
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

    private void configureAtFile(@NotNull PsiFile file) {
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(0);
        myFixture.doHighlighting();
    }

    private record RootedFile(@NotNull PsiFile file, int relativePathSegmentCount) {
    }
}

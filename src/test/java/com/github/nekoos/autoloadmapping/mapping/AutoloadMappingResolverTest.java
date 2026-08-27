package com.github.nekoos.autoloadmapping.mapping;

import com.github.nekoos.autoloadmapping.mapping.transformation.TransformationRegistry;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public final class AutoloadMappingResolverTest extends TestCase {
    private final TransformationRegistry transformations = TransformationRegistry.defaults();
    private final AutoloadMappingResolver resolver = new AutoloadMappingResolver();

    public void testBuiltInRulePackCatalogContainsDocumentedConventions() {
        assertEquals(
                List.of("PEAR / PSR-0", "Zend Framework 1 application",
                        "Laravel 3 application", "CodeIgniter 2 application"),
                AutoloadMappingRulePackCatalog.defaults().all().stream()
                        .map(AutoloadMappingRulePack::displayName).toList()
        );
    }

    public void testPearPsr0UsesDeclaredStructuralSegments() {
        assertMapping(
                fromRulePack(AutoloadMappingRulePackCatalog.PEAR_PSR0),
                "App_Dao_User",
                "App/Dao/User.php"
        );
    }

    public void testLaravelAndCodeIgniterRulePacksUseCanonicalDsl() {
        assertMapping(
                fromRule(AutoloadMappingRulePackCatalog.LARAVEL_3_APPLICATION, "Laravel 3 controllers"),
                "Admin_Panel_Controller",
                "admin/panel.php"
        );
        AutoloadMapping nested = fromRule(
                AutoloadMappingRulePackCatalog.CODEIGNITER_2_APPLICATION,
                "CodeIgniter 2 nested controllers"
        );
        assertEquals(Optional.of("Shoes"), resolver.resolveClass(nested, "products/shoes.php"));
        assertTrue(resolver.resolveFile(nested, "Shoes").isEmpty());
        assertEquals("Solo archivo → clase", nested.capabilities().label());
    }

    public void testCanonicalLowercaseToPascal() {
        assertMapping(custom("{name:pascal}", "{name:lower}.php"), "Database", "database.php");
    }

    public void testCanonicalLowercaseToUppercase() {
        assertMapping(custom("{name:upper}", "{name:lower}.php"), "DATABASE", "database.php");
    }

    public void testStructuralPathSegmentsRoundTripToUnderscores() {
        assertMapping(
                custom("{value:pascal|sep(_)}", "{value:lower|sep(/)}.php"),
                "App_Dao_User",
                "app/dao/user.php"
        );
    }

    public void testLiteralsExposeStructureWithoutSplittingLowercaseWords() {
        assertMapping(custom("My{name:pascal}", "my{name:lower}.php"), "MyClass", "myclass.php");
        assertEquals(
                Optional.of("Myclass"),
                resolver.resolveClass(custom("{name:pascal}", "{name:lower}.php"), "myclass.php")
        );
    }

    public void testSuffixesAndDeclaredWordSeparators() {
        assertMapping(custom("My{name:pascal}Controller", "my_{name:snake}.controller"),
                "MyUserController", "my_user.controller");
        assertMapping(custom("My{name:pascal}Controller", "my.{name:dot}.controller"),
                "MyUserController", "my.user.controller");
        assertMapping(custom("My{name:pascal}Controller", "my-{name:kebab}.controller"),
                "MyUserController", "my-user.controller");
        assertMapping(custom("My{name:pascal}Controller", "controller/my-{name:kebab}"),
                "MyUserController", "controller/my-user");
        assertMapping(custom("My{name:pascal}Controller", "controllers/my-{name:kebab}"),
                "MyUserController", "controllers/my-user");
        assertMapping(custom("My{name:pascal}", "my-{name:kebab}.class.php"),
                "MyClass", "my-class.class.php");
    }

    public void testAcronymIsALiteralRatherThanAnInference() {
        assertMapping(
                custom("WP_{name:pascal}", "class-wp-{name:kebab}.php"),
                "WP_Error",
                "class-wp-error.php"
        );
    }

    public void testClassOnlyVariableProducesClassToFileCapability() {
        AutoloadMapping mapping = custom("{module:pascal}_{name:pascal}", "{name:lower}.php");

        assertEquals("Solo clase → archivo", mapping.capabilities().label());
        assertEquals(List.of("La variable {module} no aparece en el patrón de archivo."),
                mapping.capabilities().notices());
        assertEquals(Optional.of("user.php"), resolver.resolveFile(mapping, "Core_User"));
        assertTrue(resolver.resolveClass(mapping, "user.php").isEmpty());
    }

    public void testFileOnlyVariableProducesFileToClassCapability() {
        AutoloadMapping mapping = custom("{name:pascal}",
                "{module:lower|sep(/)}/{name:lower}.php");

        assertEquals("Solo archivo → clase", mapping.capabilities().label());
        assertEquals(List.of("La variable {module} no aparece en el patrón de clase."),
                mapping.capabilities().notices());
        assertEquals(Optional.of("User"), resolver.resolveClass(mapping, "core/user.php"));
        assertTrue(resolver.resolveFile(mapping, "User").isEmpty());
    }

    public void testAdjacentVariablesAreARealExtractionAmbiguity() {
        AutoloadMapping mapping = custom(
                "{vendor:upper}{name:pascal}",
                "{vendor:lower}/{name:lower}.php"
        );

        assertFalse(mapping.capabilities().deriveFileFromClass());
        assertTrue(mapping.capabilities().deriveExpectedNameFromFile());
        assertEquals(MappingResolution.Status.AMBIGUOUS,
                resolver.resolveFileResult(mapping, "ACMEUser").status());
        assertEquals(Optional.of("ACMEUser"), resolver.resolveClass(mapping, "acme/user.php"));
    }

    public void testLengthRestrictionDisambiguatesAdjacentVariables() {
        assertMapping(
                custom("{vendor:upper|length(2)}{name:pascal}",
                        "{vendor:lower}/{name:lower}.php"),
                "WPUser",
                "wp/user.php"
        );
    }

    public void testNonemptyRestrictionIsPartOfTheCanonicalPipeline() {
        assertMapping(custom("{name:pascal|nonempty}", "{name:lower|nonempty}.php"),
                "User", "user.php");
    }

    public void testExplicitNamespaceComponentsParticipate() {
        AutoloadMapping mapping = custom(
                "{namespace:pascal|sep(\\)}\\{name:pascal}_Model",
                "src/{namespace:lower|sep(/)}/{name:snake}.model.php"
        );

        assertTrue(mapping.classPattern().supportsNamespaces());
        assertEquals(com.github.nekoos.autoloadmapping.mapping.pattern.PatternMatch.Status.UNIQUE,
                mapping.filePattern().matchResult("src/app/dao/user.model.php").status());
        assertMapping(mapping, "App\\Dao\\User_Model", "src/app/dao/user.model.php");
    }

    public void testGlobalPatternDoesNotDeclareNamespaceParticipation() {
        assertFalse(custom("{name:pascal}", "{name:lower}.php")
                .classPattern().supportsNamespaces());
    }

    public void testAliasesAndSpecialCompositesAreRejected() {
        assertInvalidPattern("{name:underscorePath}.php");
        assertInvalidPattern("{name:lowerPath}.php");
        assertInvalidPattern("{name:pascalUnderscore}.php");
        assertInvalidPattern("{name:path}.php");
        assertInvalidPattern("{name:/}.php");
        assertInvalidPattern("{name:_}.php");
    }

    public void testUnknownOrDuplicatePipelineStagesAreRejected() {
        assertInvalidPattern("{name:lower|lower}.php");
        assertInvalidPattern("{name:sep(/)|sep(_)}.php");
        assertInvalidPattern("{name:sep(/)|lower}.php");
        assertInvalidPattern("{name:raw|nonempty|length(4)|sep(/)|lower}.php");
    }

    public void testNonMatchingClassPatternDoesNotResolveFile() {
        assertTrue(resolver.resolveFile(
                custom("{name:pascal}_Model", "{name:kebab}.model.php"), "MyClass").isEmpty());
    }

    public void testUnsafeRenderedPathIsRejected() {
        assertTrue(resolver.resolveFile(
                custom("{name:pascal}", "../{name:lower}.php"), "MyClass").isEmpty());
    }

    public void testPairValidationUsesEitherAvailableDirection() {
        AutoloadMapping classToFile = custom("{module:pascal}_{name:pascal}", "{name:lower}.php");
        AutoloadMapping fileToClass = custom("{name:pascal}", "{module:lower}/{name:lower}.php");

        assertTrue(resolver.validatesPair(classToFile, "Core_User", "user.php"));
        assertTrue(resolver.validatesPair(fileToClass, "User", "core/user.php"));
        assertFalse(resolver.validatesPair(classToFile, "Core_User", "other.php"));
    }

    private @NotNull AutoloadMapping fromRulePack(@NotNull AutoloadMappingRulePack rulePack) {
        return fromRule(rulePack.rules().getFirst());
    }

    private @NotNull AutoloadMapping fromRule(
            @NotNull AutoloadMappingRulePack rulePack,
            @NotNull String ruleName
    ) {
        return fromRule(rulePack.rules().stream()
                .filter(candidate -> candidate.name().equals(ruleName)).findFirst().orElseThrow());
    }

    private @NotNull AutoloadMapping fromRule(@NotNull AutoloadMappingRuleTemplate rule) {
        return AutoloadMapping.create(
                rule.name(), "src", rule.classPattern(), rule.filePattern(), transformations);
    }

    private @NotNull AutoloadMapping custom(
            @NotNull String classPattern,
            @NotNull String filePattern
    ) {
        return AutoloadMapping.create("Custom", "src", classPattern, filePattern, transformations);
    }

    private void assertInvalidPattern(@NotNull String filePattern) {
        try {
            custom("{name:raw}", filePattern);
            fail("Expected an invalid canonical transformation pipeline");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("Unknown or invalid transformation pipeline"));
        }
    }

    private void assertMapping(
            @NotNull AutoloadMapping mapping,
            @NotNull String className,
            @NotNull String filePath
    ) {
        assertEquals(Optional.of(filePath), resolver.resolveFile(mapping, className));
        assertEquals(Optional.of(className), resolver.resolveClass(mapping, filePath));
        assertTrue(resolver.validatesPair(mapping, className, filePath));
    }
}

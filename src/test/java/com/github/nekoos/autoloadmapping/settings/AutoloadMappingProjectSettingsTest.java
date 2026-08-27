package com.github.nekoos.autoloadmapping.settings;

import com.github.nekoos.autoloadmapping.mapping.AutoloadMappingRulePack;
import com.github.nekoos.autoloadmapping.mapping.AutoloadMappingRulePackCatalog;
import com.github.nekoos.autoloadmapping.mapping.AutoloadMappingRuleTemplate;
import com.github.nekoos.autoloadmapping.mapping.AmbiguousQuickFixPolicy;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.MappingState;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.SchemeState;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public final class AutoloadMappingProjectSettingsTest extends BasePlatformTestCase {
    public void testNormalizesAndDeduplicatesMappings() {
        MappingState first = fromPsr0Pack("./tests\\unit/");
        MappingState duplicate = fromPsr0Pack("tests/unit");
        MappingState production = fromPsr0Pack(
                " website/application/classes "
        );

        List<MappingState> normalized = AutoloadMappingProjectSettings.normalizeMappings(
                List.of(first, duplicate, production)
        );

        assertEquals(2, normalized.size());
        assertEquals("tests/unit", normalized.get(0).baseDirectory);
        assertEquals("website/application/classes", normalized.get(1).baseDirectory);
    }

    public void testRejectsAbsoluteAndEscapingBaseDirectories() {
        MappingState absolute = fromPsr0Pack("/tmp/classes");
        MappingState parent = fromPsr0Pack("../classes");
        MappingState internalParent = fromPsr0Pack("legacy/../../classes");
        MappingState valid = fromPsr0Pack("tests/unit");

        List<MappingState> normalized = AutoloadMappingProjectSettings.normalizeMappings(
                List.of(absolute, parent, internalParent, valid)
        );

        assertEquals(List.of(valid), normalized);
    }

    public void testKeepsDeclarativePatterns() {
        MappingState custom = new MappingState(
                "Models",
                "legacy",
                "{class:pascal}_Model",
                "{class:kebab}.model.php"
        );

        MappingState normalized = AutoloadMappingProjectSettings.normalizeMappings(List.of(custom)).getFirst();

        assertEquals("{class:pascal}_Model", normalized.classPattern);
        assertEquals("{class:kebab}.model.php", normalized.filePattern);
    }

    public void testCompoundRulePackMaterializesIndependentFlatRules() {
        AutoloadMappingRulePack rulePack = new AutoloadMappingRulePack(
                "controllers",
                "Controller conventions",
                "Two application controller layouts.",
                List.of(
                        new AutoloadMappingRuleTemplate(
                                "Modern controllers", "controllers",
                                "{name:pascal}Controller", "{name:pascal}.php"
                        ),
                        new AutoloadMappingRuleTemplate(
                                "Legacy controllers", "controllers",
                                "{name:pascal}Controller", "{name:lower}.controller.php"
                        )
                )
        );

        List<MappingState> rules = MappingState.fromRulePack(rulePack, "app");

        assertEquals(2, rules.size());
        assertEquals("app/controllers", rules.get(0).baseDirectory);
        assertEquals("app/controllers", rules.get(1).baseDirectory);
        assertFalse(rules.get(0).ruleId.equals(rules.get(1).ruleId));
        assertTrue(rules.stream().allMatch(rule -> rule.fallbackChainId.isEmpty()));
    }

    public void testFrameworkPackCanCombinePsr0AndApplicationSpecificRoots() {
        List<MappingState> frameworkRules = MappingState.fromRulePack(
                AutoloadMappingRulePackCatalog.ZEND_FRAMEWORK_1_APPLICATION,
                "legacy"
        );
        MappingState pearRule = fromPsr0Pack("legacy/library");

        assertEquals(8, frameworkRules.size());
        assertEquals("legacy/application/models/mappers", frameworkRules.get(0).baseDirectory);
        assertEquals("legacy/application/models", frameworkRules.get(1).baseDirectory);
        assertEquals("{app:pascal}_Model_{qualified:raw|sep(_)}", frameworkRules.get(1).classPattern);
        assertEquals("{qualified:raw|sep(/)}.php", frameworkRules.get(1).filePattern);
        assertEquals("legacy/library", pearRule.baseDirectory);
    }

    public void testEveryBuiltInFrameworkSchemaMaterializesValidRules() {
        for (AutoloadMappingRulePack rulePack : AutoloadMappingRulePackCatalog.defaults().all()) {
            List<MappingState> rules = MappingState.fromRulePack(rulePack, "legacy");
            assertFalse(rules.isEmpty());
            rules.forEach(rule -> assertNotNull(rule.toMapping()));
        }
    }

    private static MappingState fromPsr0Pack(String baseDirectory) {
        return MappingState.fromRulePack(AutoloadMappingRulePackCatalog.PEAR_PSR0, baseDirectory).getFirst();
    }

    public void testFallbackMetadataPersistsAndCompiles() {
        MappingState fallback = new MappingState(
                "rule-2",
                "Legacy controllers",
                "app/controllers",
                "{name:pascal}Controller",
                "{name:lower}.controller.php",
                "controller-chain",
                1,
                AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name()
        );

        MappingState normalized = AutoloadMappingProjectSettings.normalizeMappings(List.of(fallback)).getFirst();

        assertEquals("controller-chain", normalized.toMapping().fallbackChainId());
        assertEquals(1, normalized.toMapping().fallbackOrder());
        assertEquals(
                AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES,
                normalized.toMapping().quickFixPolicy()
        );
    }

    public void testProjectSchemeIsCompiled() {
        AutoloadMappingProjectSettings settings = new AutoloadMappingProjectSettings();
        settings.setMappingStates(List.of(fromPsr0Pack("website/application/classes")));

        assertEquals(AutoloadMappingProjectSettings.PROJECT_SCHEME_ID, settings.getSelectedSchemeId());
        assertEquals(1, settings.getMappings().size());
        assertEquals(
                "website/application/classes",
                settings.getMappings().getFirst().baseDirectory().projectRelativePath()
        );
    }

    public void testSchemeStateCopiesDoNotMutatePersistedMappings() {
        AutoloadMappingProjectSettings settings = new AutoloadMappingProjectSettings();
        settings.setMappingStates(List.of(fromPsr0Pack("tests/unit")));

        List<MappingState> copy = settings.getProjectMappingStates();
        copy.getFirst().baseDirectory = "changed";

        assertEquals("tests/unit", settings.getMappingStates().getFirst().baseDirectory);
    }

    public void testSelectedIdeSchemeSuppliesMappingsWithoutCopyingThemIntoProjectState() {
        AutoloadMappingIdeSettings ideSettings = AutoloadMappingIdeSettings.getInstance();
        List<SchemeState> originalSchemes = ideSettings.getSchemeStates();
        try {
            SchemeState shared = new SchemeState(
                    "shared-legacy",
                    "Shared legacy",
                    List.of(fromPsr0Pack("application/classes"))
            );
            ideSettings.setSchemeStates(List.of(shared));

            AutoloadMappingProjectSettings settings = new AutoloadMappingProjectSettings();
            settings.setProjectConfiguration(List.of(fromPsr0Pack("tests/unit")), shared.id);

            assertEquals(shared.id, settings.getSelectedSchemeId());
            assertEquals("tests/unit", settings.getProjectMappingStates().getFirst().baseDirectory);
            assertEquals(
                    "application/classes",
                    settings.getMappings().getFirst().baseDirectory().projectRelativePath()
            );

            ideSettings.setSchemeStates(List.of(new SchemeState(
                    shared.id,
                    shared.name,
                    List.of(fromPsr0Pack("updated/classes"))
            )));
            assertEquals(
                    "updated/classes",
                    settings.getMappings().getFirst().baseDirectory().projectRelativePath()
            );
        } finally {
            ideSettings.setSchemeStates(originalSchemes);
        }
    }
}

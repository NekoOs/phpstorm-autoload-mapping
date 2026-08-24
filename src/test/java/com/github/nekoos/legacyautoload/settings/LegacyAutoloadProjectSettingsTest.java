package com.github.nekoos.legacyautoload.settings;

import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadPresets;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.MappingState;
import junit.framework.TestCase;

import java.util.List;

public final class LegacyAutoloadProjectSettingsTest extends TestCase {
    public void testNormalizesAndDeduplicatesMappings() {
        MappingState first = MappingState.fromPreset(LegacyAutoloadPresets.PSR0_PEAR, "./tests\\unit/");
        MappingState duplicate = MappingState.fromPreset(LegacyAutoloadPresets.PSR0_PEAR, "tests/unit");
        MappingState production = MappingState.fromPreset(
                LegacyAutoloadPresets.PSR0_PEAR,
                " website/application/classes "
        );

        List<MappingState> normalized = LegacyAutoloadProjectSettings.normalizeMappings(
                List.of(first, duplicate, production)
        );

        assertEquals(2, normalized.size());
        assertEquals("tests/unit", normalized.get(0).sourceRoot);
        assertEquals("website/application/classes", normalized.get(1).sourceRoot);
    }

    public void testRejectsAbsoluteAndParentRelativeRoots() {
        MappingState absolute = MappingState.fromPreset(LegacyAutoloadPresets.PSR0_PEAR, "/tmp/classes");
        MappingState parent = MappingState.fromPreset(LegacyAutoloadPresets.PSR0_PEAR, "../classes");
        MappingState valid = MappingState.fromPreset(LegacyAutoloadPresets.PSR0_PEAR, "tests/unit");

        List<MappingState> normalized = LegacyAutoloadProjectSettings.normalizeMappings(
                List.of(absolute, parent, valid)
        );

        assertEquals(List.of(valid), normalized);
    }

    public void testKeepsDeclarativePatterns() {
        MappingState custom = new MappingState(
                "Models",
                "legacy",
                "{class}_Model",
                "{class:kebab}.model.php",
                ""
        );

        MappingState normalized = LegacyAutoloadProjectSettings.normalizeMappings(List.of(custom)).getFirst();

        assertEquals("{class}_Model", normalized.classPattern);
        assertEquals("{class:kebab}.model.php", normalized.filePattern);
    }
}


package com.github.nekoos.autoloadmapping.settings;

import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.MappingState;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.SchemeState;
import junit.framework.TestCase;

import java.util.List;

public final class AutoloadMappingIdeSettingsTest extends TestCase {
    public void testDefaultSchemeIsAlwaysPresentAndCannotBeRenamed() {
        SchemeState suppliedDefault = new SchemeState(
                AutoloadMappingIdeSettings.DEFAULT_SCHEME_ID,
                "Renamed default",
                List.of(new MappingState("Default rule", "src", "{name}", "{name}.php"))
        );

        List<SchemeState> normalized = AutoloadMappingIdeSettings.normalizeSchemes(List.of(suppliedDefault));

        assertEquals(1, normalized.size());
        assertEquals(AutoloadMappingIdeSettings.DEFAULT_SCHEME_ID, normalized.getFirst().id);
        assertEquals(AutoloadMappingIdeSettings.DEFAULT_SCHEME_NAME, normalized.getFirst().name);
        assertEquals("src", normalized.getFirst().mappings.getFirst().baseDirectory);
    }

    public void testIdeSchemesKeepPortableProjectRelativeBaseDirectories() {
        SchemeState shared = new SchemeState(
                "shared",
                "Shared",
                List.of(new MappingState("Rule", "./legacy\\classes/", "{name}", "{name}.php"))
        );

        List<SchemeState> normalized = AutoloadMappingIdeSettings.normalizeSchemes(List.of(shared));

        assertEquals(2, normalized.size());
        assertEquals(AutoloadMappingIdeSettings.DEFAULT_SCHEME_ID, normalized.getFirst().id);
        assertEquals("shared", normalized.get(1).id);
        assertEquals("legacy/classes", normalized.get(1).mappings.getFirst().baseDirectory);
    }
}

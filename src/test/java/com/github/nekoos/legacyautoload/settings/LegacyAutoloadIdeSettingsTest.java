package com.github.nekoos.legacyautoload.settings;

import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.MappingState;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.SchemeState;
import junit.framework.TestCase;

import java.util.List;

public final class LegacyAutoloadIdeSettingsTest extends TestCase {
    public void testDefaultSchemeIsAlwaysPresentAndCannotBeRenamed() {
        SchemeState suppliedDefault = new SchemeState(
                LegacyAutoloadIdeSettings.DEFAULT_SCHEME_ID,
                "Renamed default",
                List.of(new MappingState("Default rule", "src", "{name}", "{name}.php"))
        );

        List<SchemeState> normalized = LegacyAutoloadIdeSettings.normalizeSchemes(List.of(suppliedDefault));

        assertEquals(1, normalized.size());
        assertEquals(LegacyAutoloadIdeSettings.DEFAULT_SCHEME_ID, normalized.getFirst().id);
        assertEquals(LegacyAutoloadIdeSettings.DEFAULT_SCHEME_NAME, normalized.getFirst().name);
        assertEquals("src", normalized.getFirst().mappings.getFirst().baseDirectory);
    }

    public void testIdeSchemesKeepPortableProjectRelativeBaseDirectories() {
        SchemeState shared = new SchemeState(
                "shared",
                "Shared",
                List.of(new MappingState("Rule", "./legacy\\classes/", "{name}", "{name}.php"))
        );

        List<SchemeState> normalized = LegacyAutoloadIdeSettings.normalizeSchemes(List.of(shared));

        assertEquals(2, normalized.size());
        assertEquals(LegacyAutoloadIdeSettings.DEFAULT_SCHEME_ID, normalized.getFirst().id);
        assertEquals("shared", normalized.get(1).id);
        assertEquals("legacy/classes", normalized.get(1).mappings.getFirst().baseDirectory);
    }
}

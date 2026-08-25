package com.github.nekoos.legacyautoload.settings;

import com.github.nekoos.legacyautoload.mapping.AmbiguousQuickFixPolicy;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.MappingState;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.SchemeState;
import junit.framework.TestCase;

import java.util.List;

import static org.junit.Assert.assertThrows;

public final class LegacyAutoloadSchemeCodecTest extends TestCase {
    private final LegacyAutoloadSchemeCodec codec = new LegacyAutoloadSchemeCodec();

    public void testRoundTripsACompleteScheme() {
        MappingState primary = new MappingState(
                "primary-id",
                "Controllers",
                "application/controllers",
                "{name:pascal}Controller",
                "{name:lower}.php",
                "controller-chain",
                0,
                AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name()
        );
        MappingState fallback = new MappingState(
                "fallback-id",
                "Controllers",
                "application/controllers",
                "{name:pascal}Controller",
                "{name:snake}.controller.php",
                "controller-chain",
                1,
                AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name()
        );

        SchemeState decoded = codec.decode(codec.encode(
                new SchemeState("local-id", "Legacy application", List.of(primary, fallback))
        ));

        assertEquals("Legacy application", decoded.name);
        assertEquals(2, decoded.mappings.size());
        assertFalse("Imported schemes receive a local identity", "local-id".equals(decoded.id));
        assertFalse("Imported rules receive local identities", "primary-id".equals(decoded.mappings.getFirst().ruleId));
        assertRuleEqualsIgnoringIdentity(primary, decoded.mappings.getFirst());
        assertRuleEqualsIgnoringIdentity(fallback, decoded.mappings.get(1));
    }

    public void testRejectsUnknownFormatVersion() {
        String xml = "<legacy-autoload-scheme version=\"99\" name=\"Future\"/>";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> codec.decode(xml));

        assertTrue(error.getMessage().contains("Unsupported"));
    }

    public void testRejectsDoctypeAndExternalEntities() {
        String xml = """
                <!DOCTYPE scheme [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <legacy-autoload-scheme version="1" name="&secret;"/>
                """;

        assertThrows(IllegalArgumentException.class, () -> codec.decode(xml));
    }

    public void testRejectsInvalidImportedRuleInsteadOfDroppingIt() {
        String xml = """
                <legacy-autoload-scheme version="1" name="Broken">
                  <rule name="Broken" base-directory="../outside" class-pattern="{class}"
                        file-pattern="{class}.php" fallback-chain="" fallback-order="0"
                        quick-fix-policy="SAFE_ONLY"/>
                </legacy-autoload-scheme>
                """;

        assertThrows(IllegalArgumentException.class, () -> codec.decode(xml));
    }

    public void testDoesNotAcceptExperimentalSourceRootAttribute() {
        String xml = """
                <legacy-autoload-scheme version="1" name="Old experiment">
                  <rule name="Old" source-root="legacy" class-pattern="{class}"
                        file-pattern="{class}.php" fallback-chain="" fallback-order="0"
                        quick-fix-policy="SAFE_ONLY"/>
                </legacy-autoload-scheme>
                """;

        assertThrows(IllegalArgumentException.class, () -> codec.decode(xml));
    }

    private static void assertRuleEqualsIgnoringIdentity(MappingState expected, MappingState actual) {
        assertEquals(expected.name, actual.name);
        assertEquals(expected.baseDirectory, actual.baseDirectory);
        assertEquals(expected.classPattern, actual.classPattern);
        assertEquals(expected.filePattern, actual.filePattern);
        assertEquals(expected.fallbackChainId, actual.fallbackChainId);
        assertEquals(expected.fallbackOrder, actual.fallbackOrder);
        assertEquals(expected.quickFixPolicy, actual.quickFixPolicy);
    }
}

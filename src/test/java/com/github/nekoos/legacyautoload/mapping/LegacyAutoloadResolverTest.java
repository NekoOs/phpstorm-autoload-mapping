package com.github.nekoos.legacyautoload.mapping;

import com.github.nekoos.legacyautoload.mapping.transformation.TransformationRegistry;
import com.github.nekoos.legacyautoload.mapping.transformation.ValueTransformation;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public final class LegacyAutoloadResolverTest extends TestCase {
    private final TransformationRegistry transformations = TransformationRegistry.defaults();
    private final LegacyAutoloadResolver resolver = new LegacyAutoloadResolver();

    public void testPsr0PresetMapsClassesAndFilesInBothDirections() {
        LegacyAutoloadMapping mapping = fromPreset(LegacyAutoloadPresets.PSR0_PEAR);

        assertMapping(mapping, "Foo_Bar_Baz", "Foo/Bar/Baz.php");
        assertMapping(mapping, "App_Dao_Report_ReportExecutions", "App/Dao/Report/ReportExecutions.php");
        assertMapping(mapping, "Doctrine_Record_Abstract", "Doctrine/Record/Abstract.php");
    }

    public void testModelSuffixCanPreserveClassCase() {
        assertMapping(fromPreset(LegacyAutoloadPresets.MODEL_SUFFIX), "MyClass_Model", "MyClass.model.php");
    }

    public void testModelSuffixCanTransformClassToKebabCase() {
        assertMapping(fromPreset(LegacyAutoloadPresets.KEBAB_MODEL_SUFFIX), "MyClass_Model", "my-class.model.php");
    }

    public void testControllerSuffixCanUseFixedDirectoryAndSnakeCase() {
        assertMapping(
                fromPreset(LegacyAutoloadPresets.CONTROLLER_SUFFIX),
                "CustomerController",
                "controllers/customer.controller.php"
        );
    }

    public void testClassCanMapToFixedFileSuffix() {
        assertMapping(fromPreset(LegacyAutoloadPresets.CLS_SUFFIX), "MyClass", "MyClass.cls.php");
    }

    public void testClassCanMapToFixedFilePrefix() {
        assertMapping(fromPreset(LegacyAutoloadPresets.FIXED_CLASS_PREFIX), "Database", "class.database.php");
    }

    public void testNamespaceAndClassVariablesCanUseSeparatorAliases() {
        LegacyAutoloadMapping mapping = LegacyAutoloadMapping.create(
                "Namespace and class",
                "src",
                "{namespace:_}_{class}",
                "{namespace:/}/{class}.php",
                transformations
        );

        assertMapping(mapping, "My_Namespace_Example", "My/Namespace/Example.php");
    }

    public void testNewTransformationCanBeRegisteredWithoutChangingResolver() {
        ValueTransformation reverse = new ValueTransformation() {
            @Override
            public @NotNull String id() {
                return "reverse";
            }

            @Override
            public @NotNull String apply(@NotNull String value) {
                return new StringBuilder(value).reverse().toString();
            }

            @Override
            public @NotNull Optional<String> reverse(@NotNull String value) {
                return Optional.of(apply(value));
            }
        };
        TransformationRegistry registry = new TransformationRegistry(List.of(reverse));
        LegacyAutoloadMapping mapping = LegacyAutoloadMapping.create(
                "Reverse",
                "src",
                "{class}",
                "{class:reverse}.php",
                registry
        );

        assertMapping(mapping, "Legacy", "ycageL.php");
    }

    public void testNonMatchingClassPatternDoesNotResolveFile() {
        LegacyAutoloadMapping mapping = fromPreset(LegacyAutoloadPresets.MODEL_SUFFIX);

        assertTrue(resolver.resolveFile(mapping, "MyClass").isEmpty());
    }

    public void testUnsafeRenderedPathIsRejected() {
        LegacyAutoloadMapping mapping = LegacyAutoloadMapping.create(
                "Unsafe",
                "src",
                "{class}",
                "../{class}.php",
                transformations
        );

        assertTrue(resolver.resolveFile(mapping, "MyClass").isEmpty());
    }

    private @NotNull LegacyAutoloadMapping fromPreset(@NotNull LegacyAutoloadPreset preset) {
        return LegacyAutoloadMapping.create(
                preset.displayName(),
                "src",
                preset.classPattern(),
                preset.filePattern(),
                transformations
        );
    }

    private void assertMapping(
            @NotNull LegacyAutoloadMapping mapping,
            @NotNull String className,
            @NotNull String filePath
    ) {
        assertEquals(Optional.of(filePath), resolver.resolveFile(mapping, className));
        assertEquals(Optional.of(className), resolver.resolveClass(mapping, filePath));
    }
}


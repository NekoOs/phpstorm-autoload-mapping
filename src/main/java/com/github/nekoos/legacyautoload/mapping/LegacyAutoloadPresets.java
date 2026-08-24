package com.github.nekoos.legacyautoload.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public final class LegacyAutoloadPresets {
    public static final LegacyAutoloadPreset PSR0_PEAR = new LegacyAutoloadPreset(
            "psr0-pear",
            "PSR-0 / PEAR underscores to directories",
            "{qualified}",
            "{qualified:underscorePath}.php"
    );
    public static final LegacyAutoloadPreset MODEL_SUFFIX = new LegacyAutoloadPreset(
            "model-suffix",
            "_Model class suffix to .model.php",
            "{class}_Model",
            "{class}.model.php"
    );
    public static final LegacyAutoloadPreset KEBAB_MODEL_SUFFIX = new LegacyAutoloadPreset(
            "kebab-model-suffix",
            "_Model class suffix to kebab-case .model.php",
            "{class}_Model",
            "{class:kebab}.model.php"
    );
    public static final LegacyAutoloadPreset CONTROLLER_SUFFIX = new LegacyAutoloadPreset(
            "controller-suffix",
            "Controller suffix to controllers/*.controller.php",
            "{class}Controller",
            "controllers/{class:snake}.controller.php"
    );
    public static final LegacyAutoloadPreset CLS_SUFFIX = new LegacyAutoloadPreset(
            "cls-suffix",
            "Class name to .cls.php suffix",
            "{class}",
            "{class}.cls.php"
    );
    public static final LegacyAutoloadPreset FIXED_CLASS_PREFIX = new LegacyAutoloadPreset(
            "fixed-class-prefix",
            "class.*.php fixed filename prefix",
            "{class}",
            "class.{class:lower}.php"
    );

    private static final List<LegacyAutoloadPreset> ALL = List.of(
            PSR0_PEAR,
            MODEL_SUFFIX,
            KEBAB_MODEL_SUFFIX,
            CONTROLLER_SUFFIX,
            CLS_SUFFIX,
            FIXED_CLASS_PREFIX
    );

    private LegacyAutoloadPresets() {
    }

    public static @NotNull List<LegacyAutoloadPreset> all() {
        return ALL;
    }

    public static @NotNull Optional<LegacyAutoloadPreset> find(@NotNull String id) {
        return ALL.stream().filter(preset -> preset.id().equals(id)).findFirst();
    }
}


package com.github.nekoos.autoloadmapping;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class AutoloadMappingBundle extends DynamicBundle {
    private static final String BUNDLE = "messages.AutoloadMappingBundle";
    private static final AutoloadMappingBundle INSTANCE = new AutoloadMappingBundle();

    private AutoloadMappingBundle() {
        super(BUNDLE);
    }

    public static @NotNull @Nls String message(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
            Object @NotNull ... params
    ) {
        return INSTANCE.getMessage(key, params);
    }
}


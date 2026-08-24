package com.github.nekoos.legacyautoload;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class LegacyAutoloadBundle extends DynamicBundle {
    private static final String BUNDLE = "messages.LegacyAutoloadBundle";
    private static final LegacyAutoloadBundle INSTANCE = new LegacyAutoloadBundle();

    private LegacyAutoloadBundle() {
        super(BUNDLE);
    }

    public static @NotNull @Nls String message(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
            Object @NotNull ... params
    ) {
        return INSTANCE.getMessage(key, params);
    }
}


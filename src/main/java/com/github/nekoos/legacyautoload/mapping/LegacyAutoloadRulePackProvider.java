package com.github.nekoos.legacyautoload.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Supplies a named group of rule packs without coupling storage or distribution to the engine. */
public interface LegacyAutoloadRulePackProvider {
    @NotNull String id();

    @NotNull String displayName();

    @NotNull List<LegacyAutoloadRulePack> getRulePacks();
}

package com.github.nekoos.autoloadmapping.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Supplies a named group of rule packs without coupling storage or distribution to the engine. */
public interface AutoloadMappingRulePackProvider {
    @NotNull String id();

    @NotNull String displayName();

    @NotNull List<AutoloadMappingRulePack> getRulePacks();
}

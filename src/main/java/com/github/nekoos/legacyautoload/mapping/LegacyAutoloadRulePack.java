package com.github.nekoos.legacyautoload.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** A named creation-time bundle of ordinary mapping rule templates. */
public record LegacyAutoloadRulePack(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull String description,
        @NotNull List<LegacyAutoloadRuleTemplate> rules
) {
    public LegacyAutoloadRulePack {
        rules = List.copyOf(rules);
        if (id.isBlank() || displayName.isBlank() || rules.isEmpty()) {
            throw new IllegalArgumentException("Rule pack ID, name and rules must not be empty");
        }
    }

    public LegacyAutoloadRulePack(
            @NotNull String id,
            @NotNull String displayName,
            @NotNull String description,
            @NotNull String classPattern,
            @NotNull String filePattern
    ) {
        this(id, displayName, description, List.of(new LegacyAutoloadRuleTemplate(
                displayName,
                "",
                classPattern,
                filePattern
        )));
    }
}

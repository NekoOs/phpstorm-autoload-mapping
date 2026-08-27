package com.github.nekoos.autoloadmapping.mapping;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public final class AutoloadMappingRulePackCatalog {
    public static final AutoloadMappingRulePack PEAR_PSR0 = new AutoloadMappingRulePack(
            "psr0-pear",
            "PEAR / PSR-0",
            "Maps underscores in global class names to directory separators.",
            "{qualified:raw|sep(_)}",
            "{qualified:raw|sep(/)}.php"
    );
    public static final AutoloadMappingRulePack ZEND_FRAMEWORK_1_APPLICATION = new AutoloadMappingRulePack(
            "zend-framework-1-application",
            "Zend Framework 1 application",
            "Adds the standard ZF1 application resource and default-controller layouts.",
            List.of(
                    new AutoloadMappingRuleTemplate(
                            "ZF1 model mappers",
                            "application/models/mappers",
                            "{app:pascal}_Model_Mapper_{qualified:raw|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "ZF1 models",
                            "application/models",
                            "{app:pascal}_Model_{qualified:raw|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "ZF1 forms",
                            "application/forms",
                            "{app:pascal}_Form_{qualified:raw|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "ZF1 plugins",
                            "application/plugins",
                            "{app:pascal}_Plugin_{qualified:raw|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "ZF1 services",
                            "application/services",
                            "{app:pascal}_Service_{qualified:raw|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "ZF1 view helpers",
                            "application/views/helpers",
                            "{app:pascal}_View_Helper_{qualified:raw|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "ZF1 view filters",
                            "application/views/filters",
                            "{app:pascal}_View_Filter_{qualified:raw|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "ZF1 default controllers",
                            "application/controllers",
                            "{name:pascal}Controller",
                            "{name:pascal}Controller.php"
                    )
            )
    );
    public static final AutoloadMappingRulePack LARAVEL_3_APPLICATION = new AutoloadMappingRulePack(
            "laravel-3-application",
            "Laravel 3 application",
            "Adds Laravel 3 controllers and the default model/library autoload directories.",
            List.of(
                    new AutoloadMappingRuleTemplate(
                            "Laravel 3 controllers",
                            "application/controllers",
                            "{qualified:pascal|sep(_)}_Controller",
                            "{qualified:lower|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "Laravel 3 models (lowercase)",
                            "application/models",
                            "{qualified:pascal|sep(_)}",
                            "{qualified:lower|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "Laravel 3 models (original case fallback)",
                            "application/models",
                            "{qualified:pascal|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "Laravel 3 libraries (lowercase)",
                            "application/libraries",
                            "{qualified:pascal|sep(_)}",
                            "{qualified:lower|sep(/)}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "Laravel 3 libraries (original case fallback)",
                            "application/libraries",
                            "{qualified:pascal|sep(_)}",
                            "{qualified:raw|sep(/)}.php"
                    )
            )
    );
    public static final AutoloadMappingRulePack CODEIGNITER_2_APPLICATION = new AutoloadMappingRulePack(
            "codeigniter-2-application",
            "CodeIgniter 2 application",
            "Adds CodeIgniter 2 controllers, models, libraries, replacements, and extensions.",
            List.of(
                    new AutoloadMappingRuleTemplate(
                            "CodeIgniter 2 controllers",
                            "application/controllers",
                            "{name:pascal}",
                            "{name:lower}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "CodeIgniter 2 nested controllers",
                            "application/controllers",
                            "{name:pascal}",
                            "{directory:raw|sep(/)}/{name:lower}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "CodeIgniter 2 models",
                            "application/models",
                            "{name:pascal}_model",
                            "{name:lower}_model.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "CodeIgniter 2 nested models",
                            "application/models",
                            "{name:pascal}_model",
                            "{directory:raw|sep(/)}/{name:lower}_model.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "CodeIgniter 2 application libraries",
                            "application/libraries",
                            "{name}",
                            "{name}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "CodeIgniter 2 native library replacements",
                            "application/libraries",
                            "CI_{name:pascal}",
                            "{name:pascal}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "CodeIgniter 2 library extensions",
                            "application/libraries",
                            "MY_{name:pascal}",
                            "MY_{name:pascal}.php"
                    ),
                    new AutoloadMappingRuleTemplate(
                            "CodeIgniter 2 core extensions",
                            "application/core",
                            "MY_{name:pascal}",
                            "MY_{name:pascal}.php"
                    )
            )
    );

    private final List<Group> groups;

    public AutoloadMappingRulePackCatalog(@NotNull List<AutoloadMappingRulePackProvider> providers) {
        groups = providers.stream()
                .map(provider -> new Group(
                        provider.id(),
                        provider.displayName(),
                        provider.getRulePacks()
                ))
                .toList();
    }

    public static @NotNull AutoloadMappingRulePackCatalog defaults() {
        AutoloadMappingRulePackProvider conventions = new AutoloadMappingRulePackProvider() {
            @Override
            public @NotNull String id() {
                return "conventions";
            }

            @Override
            public @NotNull String displayName() {
                return "Naming conventions";
            }

            @Override
            public @NotNull List<AutoloadMappingRulePack> getRulePacks() {
                return List.of(PEAR_PSR0);
            }
        };
        AutoloadMappingRulePackProvider frameworks = new AutoloadMappingRulePackProvider() {
            @Override
            public @NotNull String id() {
                return "frameworks";
            }

            @Override
            public @NotNull String displayName() {
                return "Framework application schemas";
            }

            @Override
            public @NotNull List<AutoloadMappingRulePack> getRulePacks() {
                return List.of(
                        ZEND_FRAMEWORK_1_APPLICATION,
                        LARAVEL_3_APPLICATION,
                        CODEIGNITER_2_APPLICATION
                );
            }
        };
        return new AutoloadMappingRulePackCatalog(List.of(conventions, frameworks));
    }

    public @NotNull List<Group> groups() {
        return groups;
    }

    public @NotNull List<AutoloadMappingRulePack> all() {
        return groups.stream().flatMap(group -> group.rulePacks().stream()).toList();
    }

    public @NotNull Optional<AutoloadMappingRulePack> find(@NotNull String id) {
        return all().stream().filter(rulePack -> rulePack.id().equals(id)).findFirst();
    }

    public record Group(
            @NotNull String id,
            @NotNull String displayName,
            @NotNull List<AutoloadMappingRulePack> rulePacks
    ) {
        public Group {
            rulePacks = List.copyOf(rulePacks);
        }
    }
}

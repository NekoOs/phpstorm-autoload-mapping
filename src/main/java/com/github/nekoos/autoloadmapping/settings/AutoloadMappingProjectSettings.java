package com.github.nekoos.autoloadmapping.settings;

import com.github.nekoos.autoloadmapping.mapping.AmbiguousQuickFixPolicy;
import com.github.nekoos.autoloadmapping.mapping.BaseDirectory;
import com.github.nekoos.autoloadmapping.mapping.AutoloadMapping;
import com.github.nekoos.autoloadmapping.mapping.AutoloadMappingRulePack;
import com.github.nekoos.autoloadmapping.mapping.transformation.TransformationRegistry;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Project-owned mappings plus the project selection of either Project or an IDE scheme. */
@Service(Service.Level.PROJECT)
@State(name = "AutoloadMappingProjectSettings", storages = @Storage("autoloadMapping.xml"))
public final class AutoloadMappingProjectSettings
        implements PersistentStateComponent<AutoloadMappingProjectSettings.SettingsState> {

    public static final String PROJECT_SCHEME_ID = "project";
    public static final String PROJECT_SCHEME_NAME = "Project";
    private static final TransformationRegistry TRANSFORMATIONS = TransformationRegistry.defaults();

    private final SettingsState state = new SettingsState();
    private volatile List<AutoloadMapping> compiledMappings;
    private volatile String compiledSchemeId = "";
    private volatile long compiledIdeRevision = Long.MIN_VALUE;

    public static @NotNull AutoloadMappingProjectSettings getInstance(@NotNull Project project) {
        return project.getService(AutoloadMappingProjectSettings.class);
    }

    @Override
    public @NotNull SettingsState getState() {
        normalizeState();
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState loadedState) {
        XmlSerializerUtil.copyBean(loadedState, state);
        normalizeState();
        invalidateCompiledMappings();
    }

    public @NotNull List<AutoloadMapping> getMappings() {
        String selected = getSelectedSchemeId();
        AutoloadMappingIdeSettings ideSettings = PROJECT_SCHEME_ID.equals(selected)
                ? null : AutoloadMappingIdeSettings.getInstance();
        long ideRevision = ideSettings == null ? -1 : ideSettings.getModificationCount();
        List<AutoloadMapping> cached = compiledMappings;
        if (cached != null && selected.equals(compiledSchemeId) && ideRevision == compiledIdeRevision) {
            return cached;
        }
        synchronized (this) {
            if (compiledMappings == null
                    || !selected.equals(compiledSchemeId)
                    || ideRevision != compiledIdeRevision) {
                List<MappingState> mappings = PROJECT_SCHEME_ID.equals(selected)
                        ? state.projectMappings
                        : Objects.requireNonNull(ideSettings).findScheme(selected)
                        .map(scheme -> scheme.mappings)
                        .orElse(state.projectMappings);
                compiledMappings = compileMappings(mappings);
                compiledSchemeId = selected;
                compiledIdeRevision = ideRevision;
            }
            return compiledMappings;
        }
    }

    private @NotNull List<AutoloadMapping> compileMappings(@NotNull List<MappingState> states) {
        List<AutoloadMapping> mappings = new ArrayList<>();
        for (MappingState mapping : states) {
            try {
                mappings.add(mapping.toMapping());
            } catch (IllegalArgumentException ignored) {
                // Invalid externally edited state must not break editor highlighting.
            }
        }
        return List.copyOf(mappings);
    }

    public @NotNull List<MappingState> getProjectMappingStates() {
        normalizeState();
        return state.projectMappings.stream().map(MappingState::copy).toList();
    }

    /** Convenience API used by tests and project-only callers. */
    public @NotNull List<MappingState> getMappingStates() {
        return getProjectMappingStates();
    }

    public void setMappingStates(@NotNull List<MappingState> mappings) {
        setProjectConfiguration(mappings, PROJECT_SCHEME_ID);
    }

    public @NotNull String getSelectedSchemeId() {
        normalizeState();
        return state.selectedSchemeId;
    }

    public void setProjectConfiguration(
            @NotNull List<MappingState> projectMappings,
            @NotNull String selectedSchemeId
    ) {
        state.projectMappings = normalizeMappings(projectMappings);
        state.selectedSchemeId = selectedSchemeId.isBlank()
                ? PROJECT_SCHEME_ID : selectedSchemeId.trim();
        invalidateCompiledMappings();
    }

    private void normalizeState() {
        state.projectMappings = normalizeMappings(
                state.projectMappings == null ? List.of() : state.projectMappings);
        if (state.selectedSchemeId == null || state.selectedSchemeId.isBlank()) {
            state.selectedSchemeId = PROJECT_SCHEME_ID;
        } else {
            state.selectedSchemeId = state.selectedSchemeId.trim();
        }
    }

    private void invalidateCompiledMappings() {
        compiledMappings = null;
        compiledSchemeId = "";
        compiledIdeRevision = Long.MIN_VALUE;
    }

    static @NotNull List<MappingState> normalizeMappings(@NotNull List<MappingState> mappings) {
        List<MappingState> normalized = new ArrayList<>();
        for (MappingState mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            MappingState copy = mapping.copy();
            copy.name = copy.name == null ? "" : copy.name.trim();
            copy.baseDirectory = copy.baseDirectory == null ? "" : copy.baseDirectory;
            copy.classPattern = copy.classPattern == null ? "" : copy.classPattern.trim();
            copy.filePattern = copy.filePattern == null ? "" : copy.filePattern.trim();
            copy.ruleId = copy.ruleId == null || copy.ruleId.isBlank()
                    ? UUID.randomUUID().toString() : copy.ruleId.trim();
            copy.fallbackChainId = copy.fallbackChainId == null ? "" : copy.fallbackChainId.trim();
            copy.quickFixPolicy = copy.quickFixPolicy == null || copy.quickFixPolicy.isBlank()
                    ? AmbiguousQuickFixPolicy.SAFE_ONLY.name() : copy.quickFixPolicy.trim();
            if (copy.fallbackChainId.isEmpty()) {
                copy.fallbackOrder = 0;
            }
            try {
                copy.baseDirectory = BaseDirectory.normalize(copy.baseDirectory);
                copy.toMapping();
                if (normalized.stream().noneMatch(existing -> existing.sameConfiguration(copy))) {
                    normalized.add(copy);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid persisted mappings are ignored. The settings dialog prevents saving them.
            }
        }
        return normalized;
    }

    public static final class SettingsState {
        public List<MappingState> projectMappings = new ArrayList<>();
        public String selectedSchemeId = PROJECT_SCHEME_ID;
    }

    public static final class SchemeState {
        public String id = "";
        public String name = "";
        public List<MappingState> mappings = new ArrayList<>();

        public SchemeState() {
        }

        public SchemeState(@NotNull String id, @NotNull String name, @NotNull List<MappingState> mappings) {
            this.id = id;
            this.name = name;
            this.mappings = mappings.stream().map(MappingState::copy)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        public @NotNull SchemeState copy() {
            return new SchemeState(id, name, mappings);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SchemeState scheme)) {
                return false;
            }
            return Objects.equals(id, scheme.id)
                    && Objects.equals(name, scheme.name)
                    && Objects.equals(mappings, scheme.mappings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, mappings);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class MappingState {
        public String name = "";
        public String ruleId = "";
        public String baseDirectory = "";
        public String classPattern = "";
        public String filePattern = "";
        public String fallbackChainId = "";
        public int fallbackOrder;
        public String quickFixPolicy = AmbiguousQuickFixPolicy.SAFE_ONLY.name();

        public MappingState() {
        }

        public MappingState(
                @NotNull String name,
                @NotNull String baseDirectory,
                @NotNull String classPattern,
                @NotNull String filePattern
        ) {
            this(UUID.randomUUID().toString(), name, baseDirectory, classPattern, filePattern,
                    "", 0, AmbiguousQuickFixPolicy.SAFE_ONLY.name());
        }

        public MappingState(
                @NotNull String ruleId,
                @NotNull String name,
                @NotNull String baseDirectory,
                @NotNull String classPattern,
                @NotNull String filePattern,
                @NotNull String fallbackChainId,
                int fallbackOrder,
                @NotNull String quickFixPolicy
        ) {
            this.ruleId = ruleId;
            this.name = name;
            this.baseDirectory = baseDirectory;
            this.classPattern = classPattern;
            this.filePattern = filePattern;
            this.fallbackChainId = fallbackChainId;
            this.fallbackOrder = fallbackOrder;
            this.quickFixPolicy = quickFixPolicy;
        }

        public static @NotNull List<MappingState> fromRulePack(
                @NotNull AutoloadMappingRulePack rulePack,
                @NotNull String baseDirectory
        ) {
            return rulePack.rules().stream().map(rule -> new MappingState(
                    rule.name(),
                    combineDirectory(baseDirectory, rule.relativeBaseDirectory()),
                    rule.classPattern(),
                    rule.filePattern()
            )).toList();
        }

        private static @NotNull String combineDirectory(@NotNull String base, @NotNull String relative) {
            if (base.isEmpty()) {
                return relative;
            }
            if (relative.isEmpty()) {
                return base;
            }
            return base + "/" + relative;
        }

        public @NotNull AutoloadMapping toMapping() {
            return AutoloadMapping.create(
                    ruleId,
                    name,
                    baseDirectory,
                    classPattern,
                    filePattern,
                    fallbackChainId,
                    fallbackOrder,
                    AmbiguousQuickFixPolicy.valueOf(quickFixPolicy),
                    TRANSFORMATIONS
            );
        }

        public @NotNull MappingState copy() {
            return new MappingState(ruleId, name, baseDirectory, classPattern, filePattern,
                    fallbackChainId, fallbackOrder, quickFixPolicy);
        }

        private boolean sameConfiguration(@NotNull MappingState mapping) {
            return Objects.equals(name, mapping.name)
                    && Objects.equals(baseDirectory, mapping.baseDirectory)
                    && Objects.equals(classPattern, mapping.classPattern)
                    && Objects.equals(filePattern, mapping.filePattern)
                    && Objects.equals(fallbackChainId, mapping.fallbackChainId)
                    && fallbackOrder == mapping.fallbackOrder
                    && Objects.equals(quickFixPolicy, mapping.quickFixPolicy);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof MappingState mapping)) {
                return false;
            }
            return Objects.equals(name, mapping.name)
                    && Objects.equals(ruleId, mapping.ruleId)
                    && Objects.equals(baseDirectory, mapping.baseDirectory)
                    && Objects.equals(classPattern, mapping.classPattern)
                    && Objects.equals(filePattern, mapping.filePattern)
                    && Objects.equals(fallbackChainId, mapping.fallbackChainId)
                    && fallbackOrder == mapping.fallbackOrder
                    && Objects.equals(quickFixPolicy, mapping.quickFixPolicy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ruleId, name, baseDirectory, classPattern, filePattern,
                    fallbackChainId, fallbackOrder, quickFixPolicy);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

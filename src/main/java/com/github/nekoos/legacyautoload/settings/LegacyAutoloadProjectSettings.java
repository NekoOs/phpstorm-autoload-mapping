package com.github.nekoos.legacyautoload.settings;

import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadMapping;
import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadPreset;
import com.github.nekoos.legacyautoload.mapping.SourceRoot;
import com.github.nekoos.legacyautoload.mapping.transformation.TransformationRegistry;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service(Service.Level.PROJECT)
@State(name = "LegacyAutoloadProjectSettings", storages = @Storage("legacyAutoload.xml"))
public final class LegacyAutoloadProjectSettings
        implements PersistentStateComponent<LegacyAutoloadProjectSettings.SettingsState> {

    private static final TransformationRegistry TRANSFORMATIONS = TransformationRegistry.defaults();
    private final SettingsState state = new SettingsState();

    public static @NotNull LegacyAutoloadProjectSettings getInstance(@NotNull Project project) {
        return project.getService(LegacyAutoloadProjectSettings.class);
    }

    @Override
    public @NotNull SettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState loadedState) {
        XmlSerializerUtil.copyBean(loadedState, state);
        state.mappings = normalizeMappings(state.mappings);
    }

    public @NotNull List<LegacyAutoloadMapping> getMappings() {
        List<LegacyAutoloadMapping> mappings = new ArrayList<>();
        for (MappingState mapping : state.mappings) {
            try {
                mappings.add(mapping.toMapping());
            } catch (IllegalArgumentException ignored) {
                // Invalid externally edited state must not break editor highlighting.
            }
        }
        return List.copyOf(mappings);
    }

    public @NotNull List<MappingState> getMappingStates() {
        return state.mappings.stream().map(MappingState::copy).toList();
    }

    public void setMappingStates(@NotNull List<MappingState> mappings) {
        state.mappings = normalizeMappings(mappings);
    }

    static @NotNull List<MappingState> normalizeMappings(@NotNull List<MappingState> mappings) {
        LinkedHashSet<MappingState> normalized = new LinkedHashSet<>();
        for (MappingState mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            MappingState copy = mapping.copy();
            copy.name = copy.name == null ? "" : copy.name.trim();
            copy.sourceRoot = copy.sourceRoot == null ? "" : copy.sourceRoot;
            copy.classPattern = copy.classPattern == null ? "" : copy.classPattern.trim();
            copy.filePattern = copy.filePattern == null ? "" : copy.filePattern.trim().replace('\\', '/');
            copy.presetId = copy.presetId == null ? "" : copy.presetId.trim();
            try {
                copy.sourceRoot = SourceRoot.normalize(copy.sourceRoot);
                copy.toMapping();
                normalized.add(copy);
            } catch (IllegalArgumentException ignored) {
                // Invalid persisted mappings are ignored. The settings dialog prevents saving them.
            }
        }
        return new ArrayList<>(normalized);
    }

    public static final class SettingsState {
        public List<MappingState> mappings = new ArrayList<>();
    }

    public static final class MappingState {
        public String name = "";
        public String sourceRoot = "";
        public String classPattern = "";
        public String filePattern = "";
        public String presetId = "";

        public MappingState() {
        }

        public MappingState(
                @NotNull String name,
                @NotNull String sourceRoot,
                @NotNull String classPattern,
                @NotNull String filePattern,
                @NotNull String presetId
        ) {
            this.name = name;
            this.sourceRoot = sourceRoot;
            this.classPattern = classPattern;
            this.filePattern = filePattern;
            this.presetId = presetId;
        }

        public static @NotNull MappingState fromPreset(
                @NotNull LegacyAutoloadPreset preset,
                @NotNull String sourceRoot
        ) {
            return new MappingState(
                    preset.displayName(),
                    sourceRoot,
                    preset.classPattern(),
                    preset.filePattern(),
                    preset.id()
            );
        }

        public @NotNull LegacyAutoloadMapping toMapping() {
            return LegacyAutoloadMapping.create(
                    name,
                    sourceRoot,
                    classPattern,
                    filePattern,
                    TRANSFORMATIONS
            );
        }

        public @NotNull MappingState copy() {
            return new MappingState(name, sourceRoot, classPattern, filePattern, presetId);
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
                    && Objects.equals(sourceRoot, mapping.sourceRoot)
                    && Objects.equals(classPattern, mapping.classPattern)
                    && Objects.equals(filePattern, mapping.filePattern)
                    && Objects.equals(presetId, mapping.presetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, sourceRoot, classPattern, filePattern, presetId);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}


package com.github.nekoos.autoloadmapping.settings;

import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.SchemeState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** IDE-owned reusable schemes. Their base directories remain project-relative and portable. */
@Service(Service.Level.APP)
@State(name = "AutoloadMappingIdeSettings", storages = @Storage("autoloadMappingSchemes.xml"))
public final class AutoloadMappingIdeSettings
        implements PersistentStateComponent<AutoloadMappingIdeSettings.SettingsState> {

    public static final String DEFAULT_SCHEME_ID = "ide-default";
    public static final String DEFAULT_SCHEME_NAME = "Default";

    private final SettingsState state = new SettingsState();
    private final AtomicLong modificationCount = new AtomicLong();

    public static @NotNull AutoloadMappingIdeSettings getInstance() {
        return ApplicationManager.getApplication().getService(AutoloadMappingIdeSettings.class);
    }

    @Override
    public @NotNull SettingsState getState() {
        ensureDefaultScheme();
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState loadedState) {
        XmlSerializerUtil.copyBean(loadedState, state);
        state.schemes = normalizeSchemes(state.schemes == null ? List.of() : state.schemes);
        modificationCount.incrementAndGet();
    }

    public @NotNull List<SchemeState> getSchemeStates() {
        ensureDefaultScheme();
        return state.schemes.stream().map(SchemeState::copy).toList();
    }

    public void setSchemeStates(@NotNull List<SchemeState> schemes) {
        state.schemes = normalizeSchemes(schemes);
        modificationCount.incrementAndGet();
    }

    public @NotNull Optional<SchemeState> findScheme(@NotNull String id) {
        ensureDefaultScheme();
        return state.schemes.stream().filter(scheme -> scheme.id.equals(id))
                .findFirst().map(SchemeState::copy);
    }

    public long getModificationCount() {
        return modificationCount.get();
    }

    static @NotNull List<SchemeState> normalizeSchemes(@NotNull List<SchemeState> schemes) {
        List<SchemeState> normalized = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        SchemeState suppliedDefault = schemes.stream()
                .filter(java.util.Objects::nonNull)
                .filter(scheme -> DEFAULT_SCHEME_ID.equals(scheme.id))
                .findFirst().orElse(null);
        normalized.add(new SchemeState(
                DEFAULT_SCHEME_ID,
                DEFAULT_SCHEME_NAME,
                suppliedDefault == null ? List.of()
                        : AutoloadMappingProjectSettings.normalizeMappings(suppliedDefault.mappings)
        ));
        ids.add(DEFAULT_SCHEME_ID);
        for (SchemeState scheme : schemes) {
            if (scheme == null || DEFAULT_SCHEME_ID.equals(scheme.id)) {
                continue;
            }
            SchemeState copy = scheme.copy();
            copy.id = copy.id == null || copy.id.isBlank() || !ids.add(copy.id.trim())
                    ? UUID.randomUUID().toString() : copy.id.trim();
            ids.add(copy.id);
            copy.name = copy.name == null || copy.name.isBlank() ? "Imported scheme" : copy.name.trim();
            copy.mappings = new ArrayList<>(AutoloadMappingProjectSettings.normalizeMappings(
                    copy.mappings == null ? List.of() : copy.mappings));
            normalized.add(copy);
        }
        return normalized;
    }

    private void ensureDefaultScheme() {
        if (state.schemes == null || state.schemes.stream()
                .noneMatch(scheme -> scheme != null && DEFAULT_SCHEME_ID.equals(scheme.id))) {
            state.schemes = normalizeSchemes(state.schemes == null ? List.of() : state.schemes);
        }
    }

    public static final class SettingsState {
        public List<SchemeState> schemes = new ArrayList<>();
    }
}

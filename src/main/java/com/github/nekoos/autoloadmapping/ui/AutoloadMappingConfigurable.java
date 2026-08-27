package com.github.nekoos.autoloadmapping.ui;

import com.github.nekoos.autoloadmapping.AutoloadMappingBundle;
import com.github.nekoos.autoloadmapping.inspection.PhpPsrInspectionCompatibility;
import com.github.nekoos.autoloadmapping.mapping.AmbiguousQuickFixPolicy;
import com.github.nekoos.autoloadmapping.mapping.AutoloadMappingRulePack;
import com.github.nekoos.autoloadmapping.mapping.AutoloadMappingRulePackCatalog;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingIdeSettings;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.MappingState;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingProjectSettings.SchemeState;
import com.github.nekoos.autoloadmapping.settings.AutoloadMappingSchemeCodec;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.DataManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AutoloadMappingConfigurable implements SearchableConfigurable {
    private static final String ID = "com.github.nekoos.php-autoload-mapping.settings";

    private final Project project;
    private final AutoloadMappingSchemeCodec schemeCodec = new AutoloadMappingSchemeCodec();
    private final List<SchemeState> ideSchemes = new ArrayList<>();
    private List<MappingState> projectMappings = new ArrayList<>();
    private DefaultListModel<MappingState> mappingsModel;
    private JBList<MappingState> mappingsList;
    private JComboBox<SchemeChoice> schemeCombo;
    private String selectedSchemeId = AutoloadMappingProjectSettings.PROJECT_SCHEME_ID;
    private boolean updatingSchemeCombo;
    private InlineBanner officialInspectionWarning;
    private JPanel officialInspectionWarningPanel;
    private JPanel panel;

    public AutoloadMappingConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @Nls String getDisplayName() {
        return AutoloadMappingBundle.message("settings.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        mappingsModel = new DefaultListModel<>();
        mappingsList = new JBList<>(mappingsModel);
        mappingsList.getEmptyText().setText(AutoloadMappingBundle.message("settings.mappings.empty"));
        mappingsList.setCellRenderer(new MappingRenderer());

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(mappingsList)
                .setAddAction(button -> addMapping())
                .setEditAction(button -> editSelectedMapping())
                .setRemoveAction(button -> removeSelectedMappings());
        decorator.addExtraAction(new DumbAwareAction(
                AutoloadMappingBundle.message("settings.action.duplicate"),
                null,
                AllIcons.Actions.Copy
        ) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                duplicateSelectedMapping();
            }

            @Override
            public void update(@NotNull AnActionEvent event) {
                event.getPresentation().setEnabled(hasSelectedMapping());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
        decorator.addExtraAction(new DumbAwareAction(
                AutoloadMappingBundle.message("settings.action.addFallback"),
                null,
                AllIcons.Actions.Forward
        ) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                addFallbackMapping();
            }

            @Override
            public void update(@NotNull AnActionEvent event) {
                event.getPresentation().setEnabled(hasSelectedMapping());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
        JPanel mappingsPanel = decorator.createPanel();

        JPanel schemePanel = createSchemePanel();

        JBLabel comment = new JBLabel(AutoloadMappingBundle.message("settings.mappings.comment"));
        comment.setCopyable(true);
        JPanel commentPanel = new JPanel(new BorderLayout());
        commentPanel.setOpaque(false);
        commentPanel.setBorder(JBUI.Borders.emptyTop(8));
        commentPanel.add(comment, BorderLayout.CENTER);

        officialInspectionWarning = new InlineBanner(
                AutoloadMappingBundle.message("settings.officialInspection.warning"),
                EditorNotificationPanel.Status.Warning
        );
        officialInspectionWarning.addAction(
                AutoloadMappingBundle.message("settings.officialInspection.review"),
                () -> {
                    PhpPsrInspectionCompatibility.openSettings(project);
                    refreshOfficialInspectionWarning();
                }
        );
        officialInspectionWarning.addAction(
                AutoloadMappingBundle.message("settings.officialInspection.disable"),
                this::disableOfficialInspection
        );

        officialInspectionWarningPanel = new JPanel(new BorderLayout());
        officialInspectionWarningPanel.setOpaque(false);
        officialInspectionWarningPanel.setBorder(JBUI.Borders.emptyBottom(8));
        officialInspectionWarningPanel.add(officialInspectionWarning, BorderLayout.CENTER);

        panel = new JPanel(new BorderLayout());
        panel.add(FormBuilder.createFormBuilder()
                .addComponent(officialInspectionWarningPanel, 0)
                .addComponent(schemePanel, 0)
                .addComponentFillVertically(mappingsPanel, 0)
                .addComponent(commentPanel, 0)
                .getPanel(), BorderLayout.CENTER);
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        if (mappingsModel == null) {
            return false;
        }
        AutoloadMappingProjectSettings settings = AutoloadMappingProjectSettings.getInstance(project);
        saveCurrentMappingsIntoSelectedScheme();
        return !projectMappings.equals(settings.getProjectMappingStates())
                || !ideSchemes.equals(AutoloadMappingIdeSettings.getInstance().getSchemeStates())
                || !selectedSchemeId.equals(settings.getSelectedSchemeId());
    }

    @Override
    public void apply() {
        if (mappingsModel != null) {
            boolean schemesChanged = isModified();
            saveCurrentMappingsIntoSelectedScheme();
            AutoloadMappingIdeSettings.getInstance().setSchemeStates(ideSchemes);
            AutoloadMappingProjectSettings.getInstance(project).setProjectConfiguration(
                    projectMappings,
                    selectedSchemeId
            );
            if (schemesChanged) {
                DaemonCodeAnalyzer.getInstance(project).restart(this);
            }
        }
        refreshOfficialInspectionWarning();
    }

    @Override
    public void reset() {
        if (mappingsModel == null) {
            return;
        }
        AutoloadMappingProjectSettings settings = AutoloadMappingProjectSettings.getInstance(project);
        projectMappings = new ArrayList<>(settings.getProjectMappingStates());
        ideSchemes.clear();
        ideSchemes.addAll(AutoloadMappingIdeSettings.getInstance().getSchemeStates());
        selectedSchemeId = settings.getSelectedSchemeId();
        if (!isProjectSelected() && findIdeScheme(selectedSchemeId) == null) {
            selectedSchemeId = AutoloadMappingProjectSettings.PROJECT_SCHEME_ID;
        }
        refreshSchemeCombo();
        loadMappings(selectedMappings());
        refreshOfficialInspectionWarning();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        mappingsList = null;
        mappingsModel = null;
        schemeCombo = null;
        ideSchemes.clear();
        projectMappings = new ArrayList<>();
        selectedSchemeId = AutoloadMappingProjectSettings.PROJECT_SCHEME_ID;
        officialInspectionWarning = null;
        officialInspectionWarningPanel = null;
    }

    private void addMapping() {
        MappingState initial = new MappingState(
                AutoloadMappingBundle.message("mapping.custom.defaultName"),
                "",
                "{class}",
                "{class}.php"
        );
        AutoloadMappingDialog dialog = new AutoloadMappingDialog(project, initial);
        if (dialog.showAndGet()) {
            mappingsModel.addElement(dialog.getMappingState());
            mappingsList.setSelectedIndex(mappingsModel.size() - 1);
            refreshOfficialInspectionWarning();
        }
    }

    private @NotNull JPanel createSchemePanel() {
        schemeCombo = new JComboBox<>();
        schemeCombo.setPrototypeDisplayValue(SchemeChoice.scheme(
                "prototype", "A reasonably long scheme name", SchemeScope.IDE));
        schemeCombo.setRenderer(new SchemeChoiceRenderer());
        schemeCombo.addActionListener(event -> {
            if (updatingSchemeCombo) {
                return;
            }
            SchemeChoice selected = (SchemeChoice) schemeCombo.getSelectedItem();
            if (selected == null || selected.header()) {
                refreshSchemeCombo();
                return;
            }
            if (selected.id().equals(selectedSchemeId)) {
                return;
            }
            saveCurrentMappingsIntoSelectedScheme();
            selectedSchemeId = selected.id();
            loadMappings(selectedMappings());
            refreshOfficialInspectionWarning();
        });

        JButton manageButton = new JButton(AllIcons.General.GearPlain);
        manageButton.setToolTipText(AutoloadMappingBundle.message("settings.scheme.manage"));
        manageButton.addActionListener(event -> showSchemeMenu(manageButton));

        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        selector.setOpaque(false);
        selector.add(new JBLabel(AutoloadMappingBundle.message("settings.scheme.label")));
        selector.add(schemeCombo);
        selector.add(manageButton);

        JButton addFromButton = new JButton(AutoloadMappingBundle.message("settings.action.addFrom"));
        addFromButton.setBorder(JBUI.Borders.empty(2, 4));
        addFromButton.setBorderPainted(false);
        addFromButton.setContentAreaFilled(false);
        addFromButton.setFocusPainted(false);
        addFromButton.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED);
        addFromButton.addActionListener(event -> showRulePackMenu(addFromButton));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.emptyBottom(8));
        header.add(selector, BorderLayout.WEST);
        header.add(addFromButton, BorderLayout.EAST);
        return header;
    }

    private void showRulePackMenu(@NotNull JComponent anchor) {
        DefaultActionGroup actions = new DefaultActionGroup();
        AutoloadMappingRulePackCatalog catalog = AutoloadMappingRulePackCatalog.defaults();
        for (AutoloadMappingRulePackCatalog.Group group : catalog.groups()) {
            actions.addSeparator(group.displayName());
            for (AutoloadMappingRulePack rulePack : group.rulePacks()) {
                actions.add(new DumbAwareAction(rulePack.displayName(), rulePack.description(), null) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent actionEvent) {
                        addRulePack(rulePack);
                    }
                });
            }
        }

        JBPopupFactory.getInstance().createActionGroupPopup(
                AutoloadMappingBundle.message("settings.addFrom.title"),
                actions,
                DataManager.getInstance().getDataContext(anchor),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
        ).showUnderneathOf(anchor);
    }

    private void showSchemeMenu(@NotNull JComponent anchor) {
        DefaultActionGroup actions = new DefaultActionGroup();
        if (isProjectSelected()) {
            actions.add(new DumbAwareAction(AutoloadMappingBundle.message("settings.scheme.copyToIde")) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    copySelectedSchemeToIde();
                }
            });
        } else {
            actions.add(new DumbAwareAction(AutoloadMappingBundle.message("settings.scheme.copyToProject")) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    copySelectedSchemeToProject();
                }
            });
            actions.add(new DumbAwareAction(AutoloadMappingBundle.message("settings.scheme.duplicate")) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent event) {
                    duplicateSelectedScheme();
                }
            });
            if (AutoloadMappingIdeSettings.DEFAULT_SCHEME_ID.equals(selectedSchemeId)) {
                actions.add(new DumbAwareAction(AutoloadMappingBundle.message("settings.scheme.restoreDefaults")) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent event) {
                        restoreDefaultScheme();
                    }
                });
            } else {
                actions.add(new DumbAwareAction(AutoloadMappingBundle.message("settings.scheme.rename")) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent event) {
                        renameSelectedScheme();
                    }
                });
                actions.add(new DumbAwareAction(AutoloadMappingBundle.message("settings.scheme.delete")) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent event) {
                        deleteSelectedScheme();
                    }
                });
            }
        }
        actions.addSeparator();
        actions.add(new DumbAwareAction(AutoloadMappingBundle.message("settings.scheme.export")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                exportActiveScheme();
            }
        });
        actions.add(new DumbAwareAction(AutoloadMappingBundle.message("settings.scheme.import")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                importScheme();
            }
        });

        JBPopupFactory.getInstance().createActionGroupPopup(
                AutoloadMappingBundle.message("settings.scheme.manage"),
                actions,
                DataManager.getInstance().getDataContext(anchor),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
        ).showUnderneathOf(anchor);
    }

    private void addRulePack(@NotNull AutoloadMappingRulePack rulePack) {
        String baseDirectory = chooseBaseDirectory();
        if (baseDirectory == null) {
            return;
        }

        int firstInserted = mappingsModel.size();
        MappingState.fromRulePack(rulePack, baseDirectory).forEach(mappingsModel::addElement);
        mappingsList.setSelectionInterval(firstInserted, mappingsModel.size() - 1);
        refreshOfficialInspectionWarning();
    }

    private @Nullable String chooseBaseDirectory() {
        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
        if (projectDirectory == null) {
            return null;
        }
        VirtualFile selected = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle(AutoloadMappingBundle.message("mapping.chooser.title")),
                project,
                projectDirectory
        );
        if (selected == null || !VfsUtilCore.isAncestor(projectDirectory, selected, false)) {
            return null;
        }
        return VfsUtilCore.getRelativePath(selected, projectDirectory, '/');
    }

    private void editSelectedMapping() {
        int index = mappingsList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        AutoloadMappingDialog dialog = new AutoloadMappingDialog(project, mappingsModel.get(index));
        if (dialog.showAndGet()) {
            mappingsModel.set(index, dialog.getMappingState());
            mappingsList.setSelectedIndex(index);
        }
    }

    private void removeSelectedMappings() {
        int[] selectedIndices = mappingsList.getSelectedIndices();
        for (int index = selectedIndices.length - 1; index >= 0; index--) {
            mappingsModel.remove(selectedIndices[index]);
        }
        refreshOfficialInspectionWarning();
    }

    private void duplicateSelectedMapping() {
        int index = mappingsList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        MappingState copy = mappingsModel.get(index).copy();
        copy.ruleId = UUID.randomUUID().toString();
        copy.name = copy.name + " copy";
        copy.fallbackChainId = "";
        copy.fallbackOrder = 0;
        copy.quickFixPolicy = AmbiguousQuickFixPolicy.SAFE_ONLY.name();
        mappingsModel.add(index + 1, copy);
        mappingsList.setSelectedIndex(index + 1);
    }

    private void addFallbackMapping() {
        int index = mappingsList.getSelectedIndex();
        if (index < 0) {
            return;
        }

        MappingState selected = mappingsModel.get(index).copy();
        String chainId = selected.fallbackChainId.isEmpty()
                ? UUID.randomUUID().toString()
                : selected.fallbackChainId;
        selected.fallbackChainId = chainId;
        selected.quickFixPolicy = AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name();
        mappingsModel.set(index, selected);

        int insertionIndex = index + 1;
        int nextOrder = selected.fallbackOrder + 1;
        while (insertionIndex < mappingsModel.size()
                && chainId.equals(mappingsModel.get(insertionIndex).fallbackChainId)) {
            nextOrder = Math.max(nextOrder, mappingsModel.get(insertionIndex).fallbackOrder + 1);
            insertionIndex++;
        }

        MappingState fallback = selected.copy();
        fallback.ruleId = UUID.randomUUID().toString();
        fallback.name = findPrimaryRuleName(chainId, selected.name);
        fallback.fallbackOrder = nextOrder;
        mappingsModel.add(insertionIndex, fallback);
        mappingsList.setSelectedIndex(insertionIndex);
    }

    private boolean hasSelectedMapping() {
        return mappingsList != null && !mappingsList.isSelectionEmpty();
    }

    private void copySelectedSchemeToIde() {
        saveCurrentMappingsIntoSelectedScheme();
        createIdeScheme(
                selectedSchemeName(),
                selectedMappings(),
                "settings.scheme.copyToIdeTitle"
        );
    }

    private void copySelectedSchemeToProject() {
        saveCurrentMappingsIntoSelectedScheme();
        projectMappings = copyMappings(selectedMappings(), false);
        selectedSchemeId = AutoloadMappingProjectSettings.PROJECT_SCHEME_ID;
        refreshSchemeCombo();
        loadMappings(projectMappings);
        refreshOfficialInspectionWarning();
    }

    private void duplicateSelectedScheme() {
        saveCurrentMappingsIntoSelectedScheme();
        createIdeScheme(
                selectedSchemeName() + " copy",
                selectedMappings(),
                "settings.scheme.duplicateTitle"
        );
    }

    private void createIdeScheme(
            @NotNull String suggestedName,
            @NotNull List<MappingState> mappings,
            @NotNull String titleKey
    ) {
        String requested = Messages.showInputDialog(
                project,
                AutoloadMappingBundle.message("settings.scheme.namePrompt"),
                AutoloadMappingBundle.message(titleKey),
                Messages.getQuestionIcon(),
                uniqueSchemeName(suggestedName),
                null
        );
        if (requested == null || requested.isBlank()) {
            return;
        }
        SchemeState copy = new SchemeState(
                UUID.randomUUID().toString(),
                uniqueSchemeName(requested.trim()),
                copyMappings(mappings, true)
        );
        ideSchemes.add(copy);
        selectedSchemeId = copy.id;
        refreshSchemeCombo();
        loadMappings(copy.mappings);
    }

    private void renameSelectedScheme() {
        saveCurrentMappingsIntoSelectedScheme();
        SchemeState current = selectedIdeScheme();
        String requested = Messages.showInputDialog(
                project,
                AutoloadMappingBundle.message("settings.scheme.namePrompt"),
                AutoloadMappingBundle.message("settings.scheme.renameTitle"),
                Messages.getQuestionIcon(),
                current.name,
                null
        );
        if (requested == null || requested.isBlank() || requested.trim().equals(current.name)) {
            return;
        }
        current.name = uniqueSchemeName(requested.trim());
        refreshSchemeCombo();
    }

    private void deleteSelectedScheme() {
        if (isProjectSelected() || AutoloadMappingIdeSettings.DEFAULT_SCHEME_ID.equals(selectedSchemeId)) {
            return;
        }
        SchemeState current = selectedIdeScheme();
        int answer = Messages.showYesNoDialog(
                project,
                AutoloadMappingBundle.message("settings.scheme.deleteConfirm", current.name),
                AutoloadMappingBundle.message("settings.scheme.deleteTitle"),
                Messages.getWarningIcon()
        );
        if (answer != Messages.YES) {
            return;
        }
        ideSchemes.remove(current);
        selectedSchemeId = AutoloadMappingIdeSettings.DEFAULT_SCHEME_ID;
        refreshSchemeCombo();
        loadMappings(selectedMappings());
        refreshOfficialInspectionWarning();
    }

    private void restoreDefaultScheme() {
        if (!AutoloadMappingIdeSettings.DEFAULT_SCHEME_ID.equals(selectedSchemeId)) {
            return;
        }
        selectedIdeScheme().mappings = new ArrayList<>();
        loadMappings(List.of());
        refreshOfficialInspectionWarning();
    }

    private void exportActiveScheme() {
        saveCurrentMappingsIntoSelectedScheme();
        SchemeState current = new SchemeState(selectedSchemeId, selectedSchemeName(), selectedMappings());
        FileSaverDescriptor descriptor = new FileSaverDescriptor(
                AutoloadMappingBundle.message("settings.scheme.exportTitle"),
                AutoloadMappingBundle.message("settings.scheme.exportDescription"),
                "xml"
        );
        FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
        String baseName = current.name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-|-$", "");
        if (baseName.isBlank()) {
            baseName = "autoload-mapping";
        }
        var selected = dialog.save(
                ProjectUtil.guessProjectDir(project),
                baseName + "." + AutoloadMappingSchemeCodec.FILE_EXTENSION
        );
        if (selected == null) {
            return;
        }
        try {
            Files.writeString(selected.getFile().toPath(), schemeCodec.encode(current), StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException exception) {
            showSchemeError("settings.scheme.exportError", exception);
        }
    }

    private void importScheme() {
        VirtualFile selected = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor("xml")
                        .withTitle(AutoloadMappingBundle.message("settings.scheme.importTitle")),
                project,
                ProjectUtil.guessProjectDir(project)
        );
        if (selected == null) {
            return;
        }
        try {
            SchemeState imported = schemeCodec.decode(
                    new String(selected.contentsToByteArray(), StandardCharsets.UTF_8)
            );
            imported.name = uniqueSchemeName(imported.name);
            ideSchemes.add(imported);
            selectedSchemeId = imported.id;
            refreshSchemeCombo();
            loadMappings(imported.mappings);
            refreshOfficialInspectionWarning();
        } catch (IOException | IllegalArgumentException exception) {
            showSchemeError("settings.scheme.importError", exception);
        }
    }

    private void showSchemeError(@NotNull String messageKey, @NotNull Exception exception) {
        String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        Messages.showErrorDialog(
                project,
                AutoloadMappingBundle.message(messageKey, detail),
                AutoloadMappingBundle.message("settings.scheme.errorTitle")
        );
    }

    private @NotNull String uniqueSchemeName(@NotNull String preferred) {
        String base = preferred.isBlank() ? "Imported scheme" : preferred;
        String candidate = base;
        int suffix = 2;
        while (containsSchemeName(candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private boolean containsSchemeName(@NotNull String name) {
        return ideSchemes.stream().anyMatch(scheme -> scheme.name.equalsIgnoreCase(name));
    }

    private void refreshSchemeCombo() {
        if (schemeCombo == null) {
            return;
        }
        updatingSchemeCombo = true;
        try {
            schemeCombo.removeAllItems();
            schemeCombo.addItem(SchemeChoice.header(
                    AutoloadMappingBundle.message("settings.scheme.storedProject")));
            schemeCombo.addItem(SchemeChoice.scheme(
                    AutoloadMappingProjectSettings.PROJECT_SCHEME_ID,
                    AutoloadMappingProjectSettings.PROJECT_SCHEME_NAME,
                    SchemeScope.PROJECT));
            schemeCombo.addItem(SchemeChoice.header(
                    AutoloadMappingBundle.message("settings.scheme.storedIde")));
            for (SchemeState scheme : ideSchemes) {
                schemeCombo.addItem(SchemeChoice.scheme(scheme.id, scheme.name, SchemeScope.IDE));
            }
            for (int index = 0; index < schemeCombo.getItemCount(); index++) {
                SchemeChoice choice = schemeCombo.getItemAt(index);
                if (!choice.header() && choice.id().equals(selectedSchemeId)) {
                    schemeCombo.setSelectedIndex(index);
                    break;
                }
            }
        } finally {
            updatingSchemeCombo = false;
        }
    }

    private void loadMappings(@NotNull List<MappingState> mappings) {
        mappingsModel.clear();
        mappings.stream().map(MappingState::copy).forEach(mappingsModel::addElement);
    }

    private void saveCurrentMappingsIntoSelectedScheme() {
        if (mappingsModel == null) {
            return;
        }
        if (isProjectSelected()) {
            projectMappings = new ArrayList<>(modelValues());
        } else {
            selectedIdeScheme().mappings = new ArrayList<>(modelValues());
        }
    }

    private boolean isProjectSelected() {
        return AutoloadMappingProjectSettings.PROJECT_SCHEME_ID.equals(selectedSchemeId);
    }

    private @Nullable SchemeState findIdeScheme(@NotNull String id) {
        return ideSchemes.stream()
                .filter(scheme -> scheme.id.equals(id))
                .findFirst()
                .orElse(null);
    }

    private @NotNull SchemeState selectedIdeScheme() {
        SchemeState scheme = findIdeScheme(selectedSchemeId);
        if (scheme == null) {
            throw new IllegalStateException("Selected IDE scheme does not exist: " + selectedSchemeId);
        }
        return scheme;
    }

    private @NotNull List<MappingState> selectedMappings() {
        return isProjectSelected() ? projectMappings : selectedIdeScheme().mappings;
    }

    private @NotNull String selectedSchemeName() {
        return isProjectSelected()
                ? AutoloadMappingProjectSettings.PROJECT_SCHEME_NAME
                : selectedIdeScheme().name;
    }

    private static @NotNull List<MappingState> copyMappings(
            @NotNull List<MappingState> mappings,
            boolean replaceRuleIds
    ) {
        List<MappingState> copies = new ArrayList<>(mappings.size());
        Map<String, String> fallbackIds = new HashMap<>();
        for (MappingState mapping : mappings) {
            MappingState copy = mapping.copy();
            if (replaceRuleIds) {
                copy.ruleId = UUID.randomUUID().toString();
                if (!copy.fallbackChainId.isEmpty()) {
                    copy.fallbackChainId = fallbackIds.computeIfAbsent(
                            copy.fallbackChainId,
                            ignored -> UUID.randomUUID().toString()
                    );
                }
            }
            copies.add(copy);
        }
        return copies;
    }

    private @NotNull String findPrimaryRuleName(@NotNull String chainId, @NotNull String defaultName) {
        for (int index = 0; index < mappingsModel.size(); index++) {
            MappingState candidate = mappingsModel.get(index);
            if (chainId.equals(candidate.fallbackChainId) && candidate.fallbackOrder == 0) {
                return candidate.name;
            }
        }
        return defaultName;
    }

    private void refreshOfficialInspectionWarning() {
        if (officialInspectionWarning == null
                || officialInspectionWarningPanel == null
                || mappingsModel == null) {
            return;
        }

        boolean visible = !mappingsModel.isEmpty() && PhpPsrInspectionCompatibility.isEnabled(project);
        officialInspectionWarning.setVisible(visible);
        officialInspectionWarningPanel.setVisible(visible);
    }

    private void disableOfficialInspection() {
        if (!PhpPsrInspectionCompatibility.canDisableForProject(project)) {
            Messages.showInfoMessage(
                    project,
                    AutoloadMappingBundle.message("settings.officialInspection.projectProfileRequired"),
                    AutoloadMappingBundle.message("settings.officialInspection.projectProfileRequiredTitle")
            );
            PhpPsrInspectionCompatibility.openSettings(project);
            refreshOfficialInspectionWarning();
            return;
        }

        int answer = Messages.showYesNoDialog(
                project,
                AutoloadMappingBundle.message("settings.officialInspection.confirm"),
                AutoloadMappingBundle.message("settings.officialInspection.confirmTitle"),
                Messages.getWarningIcon()
        );
        if (answer == Messages.YES) {
            PhpPsrInspectionCompatibility.disableForProject(project);
            refreshOfficialInspectionWarning();
        }
    }

    private @NotNull List<MappingState> modelValues() {
        List<MappingState> mappings = new ArrayList<>(mappingsModel.size());
        Map<String, Integer> nextFallbackOrder = new HashMap<>();
        for (int index = 0; index < mappingsModel.size(); index++) {
            MappingState mapping = mappingsModel.get(index).copy();
            if (!mapping.fallbackChainId.isEmpty()) {
                int order = nextFallbackOrder.getOrDefault(mapping.fallbackChainId, 0);
                mapping.fallbackOrder = order;
                nextFallbackOrder.put(mapping.fallbackChainId, order + 1);
            } else {
                mapping.fallbackOrder = 0;
            }
            mappings.add(mapping);
        }
        return mappings;
    }

    private enum SchemeScope {
        PROJECT,
        IDE
    }

    private record SchemeChoice(
            @NotNull String id,
            @NotNull String name,
            @Nullable SchemeScope scope,
            boolean header
    ) {
        private static @NotNull SchemeChoice header(@NotNull String name) {
            return new SchemeChoice("", name, null, true);
        }

        private static @NotNull SchemeChoice scheme(
                @NotNull String id,
                @NotNull String name,
                @NotNull SchemeScope scope
        ) {
            return new SchemeChoice(id, name, scope, false);
        }

        @Override
        public @NotNull String toString() {
            return name;
        }
    }

    private static final class SchemeChoiceRenderer extends ColoredListCellRenderer<SchemeChoice> {
        @Override
        protected void customizeCellRenderer(
                @NotNull JList<? extends SchemeChoice> list,
                SchemeChoice value,
                int index,
                boolean selected,
                boolean hasFocus
        ) {
            if (value == null) {
                return;
            }
            if (value.header()) {
                append(value.name(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
            } else {
                append(value.name(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                if (index < 0 && value.scope() != null) {
                    append("  " + (value.scope() == SchemeScope.PROJECT
                                    ? AutoloadMappingBundle.message("settings.scheme.projectScope")
                                    : AutoloadMappingBundle.message("settings.scheme.ideScope")),
                            SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        }
    }

    private static final class MappingRenderer extends ColoredListCellRenderer<MappingState> {
        @Override
        protected void customizeCellRenderer(
                @NotNull JList<? extends MappingState> list,
                MappingState value,
                int index,
                boolean selected,
                boolean hasFocus
        ) {
            append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            append("  " + (value.baseDirectory.isEmpty() ? "." : value.baseDirectory),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("    " + value.classPattern + "  →  " + value.filePattern,
                    SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
            if (!value.fallbackChainId.isEmpty()) {
                String role = value.fallbackOrder == 0
                        ? AutoloadMappingBundle.message("settings.mapping.primary")
                        : AutoloadMappingBundle.message("settings.mapping.fallback", value.fallbackOrder);
                append("    " + role, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }
}

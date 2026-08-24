package com.github.nekoos.legacyautoload.ui;

import com.github.nekoos.legacyautoload.LegacyAutoloadBundle;
import com.github.nekoos.legacyautoload.inspection.PhpPsrInspectionCompatibility;
import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadPresets;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.MappingState;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public final class LegacyAutoloadConfigurable implements SearchableConfigurable {
    private static final String ID = "com.github.nekoos.legacyautoload.settings";

    private final Project project;
    private DefaultListModel<MappingState> mappingsModel;
    private JBList<MappingState> mappingsList;
    private InlineBanner officialInspectionWarning;
    private JPanel panel;

    public LegacyAutoloadConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @Nls String getDisplayName() {
        return LegacyAutoloadBundle.message("settings.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        mappingsModel = new DefaultListModel<>();
        mappingsList = new JBList<>(mappingsModel);
        mappingsList.getEmptyText().setText(LegacyAutoloadBundle.message("settings.mappings.empty"));
        mappingsList.setCellRenderer(new MappingRenderer());

        JPanel mappingsPanel = ToolbarDecorator.createDecorator(mappingsList)
                .setAddAction(button -> addMapping())
                .setEditAction(button -> editSelectedMapping())
                .setRemoveAction(button -> removeSelectedMappings())
                .createPanel();

        JBLabel comment = new JBLabel(LegacyAutoloadBundle.message("settings.mappings.comment"));
        comment.setCopyable(true);

        officialInspectionWarning = new InlineBanner(
                LegacyAutoloadBundle.message("settings.officialInspection.warning"),
                EditorNotificationPanel.Status.Warning
        );
        officialInspectionWarning.addAction(
                LegacyAutoloadBundle.message("settings.officialInspection.review"),
                () -> {
                    PhpPsrInspectionCompatibility.openSettings(project);
                    refreshOfficialInspectionWarning();
                }
        );
        officialInspectionWarning.addAction(
                LegacyAutoloadBundle.message("settings.officialInspection.disable"),
                this::disableOfficialInspection
        );

        panel = new JPanel(new BorderLayout());
        panel.add(FormBuilder.createFormBuilder()
                .addComponent(officialInspectionWarning, 0)
                .addComponentFillVertically(mappingsPanel, 0)
                .addComponent(comment, 0)
                .getPanel(), BorderLayout.CENTER);
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        return mappingsModel != null
                && !modelValues().equals(LegacyAutoloadProjectSettings.getInstance(project).getMappingStates());
    }

    @Override
    public void apply() {
        if (mappingsModel != null) {
            LegacyAutoloadProjectSettings.getInstance(project).setMappingStates(modelValues());
        }
        refreshOfficialInspectionWarning();
    }

    @Override
    public void reset() {
        if (mappingsModel == null) {
            return;
        }
        mappingsModel.clear();
        LegacyAutoloadProjectSettings.getInstance(project).getMappingStates().forEach(mappingsModel::addElement);
        refreshOfficialInspectionWarning();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        mappingsList = null;
        mappingsModel = null;
        officialInspectionWarning = null;
    }

    private void addMapping() {
        MappingState initial = MappingState.fromPreset(LegacyAutoloadPresets.PSR0_PEAR, "");
        LegacyAutoloadMappingDialog dialog = new LegacyAutoloadMappingDialog(project, initial);
        if (dialog.showAndGet()) {
            mappingsModel.addElement(dialog.getMappingState());
            mappingsList.setSelectedIndex(mappingsModel.size() - 1);
            refreshOfficialInspectionWarning();
        }
    }

    private void editSelectedMapping() {
        int index = mappingsList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        LegacyAutoloadMappingDialog dialog = new LegacyAutoloadMappingDialog(project, mappingsModel.get(index));
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

    private void refreshOfficialInspectionWarning() {
        if (officialInspectionWarning == null || mappingsModel == null) {
            return;
        }

        officialInspectionWarning.setVisible(
                !mappingsModel.isEmpty() && PhpPsrInspectionCompatibility.isEnabled(project)
        );
    }

    private void disableOfficialInspection() {
        if (!PhpPsrInspectionCompatibility.canDisableForProject(project)) {
            Messages.showInfoMessage(
                    project,
                    LegacyAutoloadBundle.message("settings.officialInspection.projectProfileRequired"),
                    LegacyAutoloadBundle.message("settings.officialInspection.projectProfileRequiredTitle")
            );
            PhpPsrInspectionCompatibility.openSettings(project);
            refreshOfficialInspectionWarning();
            return;
        }

        int answer = Messages.showYesNoDialog(
                project,
                LegacyAutoloadBundle.message("settings.officialInspection.confirm"),
                LegacyAutoloadBundle.message("settings.officialInspection.confirmTitle"),
                Messages.getWarningIcon()
        );
        if (answer == Messages.YES) {
            PhpPsrInspectionCompatibility.disableForProject(project);
            refreshOfficialInspectionWarning();
        }
    }

    private @NotNull List<MappingState> modelValues() {
        List<MappingState> mappings = new ArrayList<>(mappingsModel.size());
        for (int index = 0; index < mappingsModel.size(); index++) {
            mappings.add(mappingsModel.get(index).copy());
        }
        return mappings;
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
            append("  " + (value.sourceRoot.isEmpty() ? "." : value.sourceRoot),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("    " + value.classPattern + "  →  " + value.filePattern,
                    SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
        }
    }
}

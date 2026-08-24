package com.github.nekoos.legacyautoload.ui;

import com.github.nekoos.legacyautoload.LegacyAutoloadBundle;
import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadPreset;
import com.github.nekoos.legacyautoload.mapping.LegacyAutoloadPresets;
import com.github.nekoos.legacyautoload.mapping.SourceRoot;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.MappingState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

final class LegacyAutoloadMappingDialog extends DialogWrapper {
    private static final String CUSTOM_PRESET = "Custom";

    private final Project project;
    private final ComboBox<String> preset = new ComboBox<>();
    private final JBTextField name = new JBTextField();
    private final JBTextField sourceRoot = new JBTextField();
    private final JBTextField classPattern = new JBTextField();
    private final JBTextField filePattern = new JBTextField();
    private String selectedPresetId;

    LegacyAutoloadMappingDialog(@NotNull Project project, @NotNull MappingState initialState) {
        super(project);
        this.project = project;
        selectedPresetId = initialState.presetId;

        preset.addItem(CUSTOM_PRESET);
        LegacyAutoloadPresets.all().forEach(value -> preset.addItem(value.displayName()));
        LegacyAutoloadPresets.find(initialState.presetId)
                .ifPresentOrElse(
                        value -> preset.setSelectedItem(value.displayName()),
                        () -> preset.setSelectedItem(CUSTOM_PRESET)
                );

        name.setText(initialState.name);
        sourceRoot.setText(initialState.sourceRoot);
        classPattern.setText(initialState.classPattern);
        filePattern.setText(initialState.filePattern);

        preset.addActionListener(event -> applySelectedPreset());
        setTitle(LegacyAutoloadBundle.message("mapping.dialog.title"));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JButton chooseRoot = new JButton(LegacyAutoloadBundle.message("mapping.chooseRoot"));
        chooseRoot.addActionListener(event -> chooseSourceRoot());
        JPanel rootPanel = new JPanel(new BorderLayout(8, 0));
        rootPanel.add(sourceRoot, BorderLayout.CENTER);
        rootPanel.add(chooseRoot, BorderLayout.EAST);

        JPanel form = FormBuilder.createFormBuilder()
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.preset"), preset)
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.name"), name)
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.sourceRoot"), rootPanel)
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.classPattern"), classPattern)
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.filePattern"), filePattern)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        form.setPreferredSize(new java.awt.Dimension(640, 260));
        return form;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        try {
            MappingState state = getMappingState();
            state.toMapping();

            VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
            VirtualFile root = projectDirectory == null
                    ? null
                    : state.sourceRoot.isEmpty()
                    ? projectDirectory
                    : projectDirectory.findFileByRelativePath(state.sourceRoot);
            if (root == null || !root.isDirectory()) {
                return new ValidationInfo(
                        LegacyAutoloadBundle.message("mapping.invalidRoot"),
                        sourceRoot
                );
            }
        } catch (IllegalArgumentException exception) {
            return new ValidationInfo(exception.getMessage() == null
                    ? LegacyAutoloadBundle.message("mapping.invalid")
                    : exception.getMessage());
        }
        return null;
    }

    @NotNull MappingState getMappingState() {
        return new MappingState(
                name.getText(),
                SourceRoot.normalize(sourceRoot.getText()),
                classPattern.getText(),
                filePattern.getText(),
                selectedPresetId
        );
    }

    private void applySelectedPreset() {
        String selectedName = (String) preset.getSelectedItem();
        LegacyAutoloadPreset selected = LegacyAutoloadPresets.all().stream()
                .filter(value -> value.displayName().equals(selectedName))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            selectedPresetId = "";
            return;
        }
        selectedPresetId = selected.id();
        name.setText(selected.displayName());
        classPattern.setText(selected.classPattern());
        filePattern.setText(selected.filePattern());
    }

    private void chooseSourceRoot() {
        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
        if (projectDirectory == null) {
            return;
        }
        VirtualFile initial = sourceRoot.getText().isBlank()
                ? projectDirectory
                : projectDirectory.findFileByRelativePath(sourceRoot.getText());
        VirtualFile selected = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle(LegacyAutoloadBundle.message("mapping.chooser.title")),
                project,
                initial == null ? projectDirectory : initial
        );
        if (selected == null || !VfsUtilCore.isAncestor(projectDirectory, selected, false)) {
            return;
        }
        String relativePath = VfsUtilCore.getRelativePath(selected, projectDirectory, '/');
        if (relativePath != null) {
            sourceRoot.setText(relativePath);
        }
    }
}

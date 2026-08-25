package com.github.nekoos.legacyautoload.ui;

import com.github.nekoos.legacyautoload.LegacyAutoloadBundle;
import com.github.nekoos.legacyautoload.mapping.AmbiguousQuickFixPolicy;
import com.github.nekoos.legacyautoload.mapping.BaseDirectory;
import com.github.nekoos.legacyautoload.settings.LegacyAutoloadProjectSettings.MappingState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

final class LegacyAutoloadMappingDialog extends DialogWrapper {
    private final Project project;
    private final MappingState initialState;
    private final JBTextField name = new JBTextField();
    private final JBTextField baseDirectory = new JBTextField();
    private final JBTextField classPattern = new JBTextField();
    private final JBTextField filePattern = new JBTextField();
    private final JBCheckBox offerAmbiguousQuickFixes = new JBCheckBox(
            LegacyAutoloadBundle.message("mapping.ambiguousQuickFixes")
    );
    private final JBLabel capability = new JBLabel();

    LegacyAutoloadMappingDialog(@NotNull Project project, @NotNull MappingState initialState) {
        super(project);
        this.project = project;
        this.initialState = initialState.copy();

        name.setText(initialState.name);
        baseDirectory.setText(initialState.baseDirectory);
        classPattern.setText(initialState.classPattern);
        filePattern.setText(initialState.filePattern);
        offerAmbiguousQuickFixes.setSelected(
                AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name().equals(initialState.quickFixPolicy)
        );
        DocumentAdapter capabilityUpdater = new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                refreshCapability();
            }
        };
        classPattern.getDocument().addDocumentListener(capabilityUpdater);
        filePattern.getDocument().addDocumentListener(capabilityUpdater);
        refreshCapability();

        setTitle(LegacyAutoloadBundle.message("mapping.dialog.title"));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JButton chooseRoot = new JButton(LegacyAutoloadBundle.message("mapping.chooseRoot"));
        chooseRoot.addActionListener(event -> chooseBaseDirectory());
        JPanel rootPanel = new JPanel(new BorderLayout(8, 0));
        rootPanel.add(baseDirectory, BorderLayout.CENTER);
        rootPanel.add(chooseRoot, BorderLayout.EAST);
        JBLabel baseDirectoryHelp = new JBLabel(
                "<html><small>" + LegacyAutoloadBundle.message("mapping.baseDirectory.help") + "</small></html>"
        );

        JPanel form = FormBuilder.createFormBuilder()
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.name"), name)
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.baseDirectory"), rootPanel)
                .addComponentToRightColumn(baseDirectoryHelp)
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.classPattern"), classPattern)
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.filePattern"), filePattern)
                .addLabeledComponent(LegacyAutoloadBundle.message("mapping.capability"), capability)
                .addComponent(offerAmbiguousQuickFixes)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        form.setPreferredSize(new java.awt.Dimension(640, 220));
        return form;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        try {
            VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
            MappingState state = getMappingState();
            state.toMapping();
            VirtualFile root = projectDirectory == null
                    ? null
                    : state.baseDirectory.isEmpty()
                    ? projectDirectory
                    : projectDirectory.findFileByRelativePath(state.baseDirectory);
            if (root == null || !root.isDirectory()) {
                return new ValidationInfo(
                        LegacyAutoloadBundle.message("mapping.invalidRoot"),
                        baseDirectory
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
                initialState.ruleId,
                name.getText(),
                BaseDirectory.normalize(baseDirectory.getText()),
                classPattern.getText(),
                filePattern.getText(),
                initialState.fallbackChainId,
                initialState.fallbackOrder,
                offerAmbiguousQuickFixes.isSelected()
                        ? AmbiguousQuickFixPolicy.OFFER_NAMED_ALTERNATIVES.name()
                        : AmbiguousQuickFixPolicy.SAFE_ONLY.name()
        );
    }

    private void refreshCapability() {
        try {
            var capabilities = getMappingState().toMapping().capabilities();
            String notices = capabilities.notices().isEmpty()
                    ? ""
                    : "<br><small>" + String.join("<br>", capabilities.notices()) + "</small>";
            capability.setText("<html>" + capabilities.label() + notices + "</html>");
        } catch (IllegalArgumentException exception) {
            capability.setText(LegacyAutoloadBundle.message("mapping.capability.invalid"));
        }
    }

    private void chooseBaseDirectory() {
        VirtualFile projectDirectory = ProjectUtil.guessProjectDir(project);
        if (projectDirectory == null) {
            return;
        }
        VirtualFile initial = baseDirectory.getText().isBlank()
                ? projectDirectory
                : projectDirectory.findFileByRelativePath(baseDirectory.getText());
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
            baseDirectory.setText(relativePath);
        }
    }
}

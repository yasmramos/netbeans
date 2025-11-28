/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.javafx2.scenebuilder.wizard;

import java.awt.Component;
import java.io.File;
import java.net.URL;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.modules.javafx2.scenebuilder.EnhancedSceneBuilderManager;
import org.netbeans.modules.javafx2.scenebuilder.Home;
import org.netbeans.modules.javafx2.scenebuilder.Settings;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 * Wizard for configuring SceneBuilder integration in NetBeans.
 * Provides automatic discovery, manual configuration, and download guidance.
 * 
 * @author MiniMax Agent
 */
public class SceneBuilderConfigurationWizard implements WizardDescriptor.FinishablePanel<WizardDescriptor> {
    
    private SceneBuilderConfigurationPanel panel;
    private WizardDescriptor wizard;
    private EnhancedSceneBuilderManager sbManager;
    
    public SceneBuilderConfigurationWizard() {
        sbManager = EnhancedSceneBuilderManager.getInstance();
    }
    
    @Override
    public Component getComponent() {
        if (panel == null) {
            panel = new SceneBuilderConfigurationPanel();
            panel.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    updateWizardState();
                }
            });
        }
        return panel;
    }
    
    @Override
    public HelpCtx getHelp() {
        return new HelpCtx("org.netbeans.modules.javafx2.scenebuilder.wizard.help");
    }
    
    @Override
    public boolean isValid() {
        String error = getErrorMessage();
        panel.setError(error);
        return error == null;
    }
    
    @Override
    public void addChangeListener(ChangeListener l) {
        panel.addChangeListener(l);
    }
    
    @Override
    public void removeChangeListener(ChangeListener l) {
        panel.removeChangeListener(l);
    }
    
    @Override
    public void readSettings(WizardDescriptor w) {
        wizard = w;
        panel.readSettings(w);
        updateWizardState();
    }
    
    @Override
    public void storeSettings(WizardDescriptor w) {
        if (panel.getAutoConfigRadio().isSelected()) {
            // Auto-configuration - use discovered installation
            Home bestInstall = sbManager.getBestInstallation();
            if (bestInstall != null) {
                Settings settings = Settings.getInstance();
                settings.setSelectedHome(bestInstall);
                settings.store();
                w.putProperty("scenebuilder.configured", true);
            }
        } else if (panel.getManualConfigRadio().isSelected()) {
            // Manual configuration
            String selectedPath = panel.getSelectedPath();
            if (selectedPath != null && !selectedPath.trim().isEmpty()) {
                Home manualHome = createHomeFromPath(selectedPath);
                if (manualHome != null && manualHome.isValid()) {
                    Settings settings = Settings.getInstance();
                    settings.setSelectedHome(manualHome);
                    settings.store();
                    w.putProperty("scenebuilder.configured", true);
                }
            }
        } else if (panel.getDownloadRadio().isSelected()) {
            // User chose to download SceneBuilder
            w.putProperty("scenebuilder.download", true);
        }
        
        panel.storeSettings(w);
    }
    
    private void updateWizardState() {
        boolean valid = isValid();
        if (wizard != null) {
            wizard.putProperty(WizardDescriptor.PROP_WIZARD_AUTO_VALIDATION, Boolean.TRUE);
            wizard.putProperty(WizardDescriptor.PROP_FINISH_ENABLED, Boolean.valueOf(valid));
        }
    }
    
    @Nullable
    private String getErrorMessage() {
        if (panel.getManualConfigRadio().isSelected()) {
            String selectedPath = panel.getSelectedPath();
            if (selectedPath == null || selectedPath.trim().isEmpty()) {
                return NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "ERR_ManualPathRequired");
            }
            
            File selectedDir = new File(selectedPath);
            if (!selectedDir.exists()) {
                return NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "ERR_DirectoryNotExists");
            }
            
            if (!selectedDir.isDirectory()) {
                return NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "ERR_NotDirectory");
            }
            
            Home home = createHomeFromPath(selectedPath);
            if (home == null || !home.isValid()) {
                return NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "ERR_InvalidInstallation");
            }
        }
        return null;
    }
    
    @Nullable
    private Home createHomeFromPath(String path) {
        File dir = new File(path);
        String executableName = findExecutableName(dir);
        if (executableName == null) {
            return null;
        }
        
        String propertiesPath = findPropertiesFile(dir);
        String version = detectVersion(dir);
        
        return new Home(path, executableName, 
                       propertiesPath != null ? 
                           new File(dir, propertiesPath).getAbsolutePath() : "",
                       version);
    }
    
    private String findExecutableName(File dir) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            File exe = new File(dir, "SceneBuilder.exe");
            if (exe.exists()) return "SceneBuilder.exe";
        } else {
            File app = new File(dir, "SceneBuilder");
            if (app.exists()) return "SceneBuilder";
            
            File bundle = new File(dir, "SceneBuilder.app");
            if (bundle.exists()) return "SceneBuilder.app";
        }
        
        return null;
    }
    
    private String findPropertiesFile(File dir) {
        String[] candidates = {
            "SceneBuilder.properties",
            "build.properties",
            "app.properties"
        };
        
        for (String candidate : candidates) {
            File propsFile = new File(dir, candidate);
            if (propsFile.exists()) {
                return candidate;
            }
        }
        
        return null;
    }
    
    private String detectVersion(File dir) {
        File versionFile = new File(dir, "VERSION.txt");
        if (versionFile.exists()) {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(versionFile.toPath());
                if (!lines.isEmpty()) {
                    return lines.get(0).trim();
                }
            } catch (Exception ex) {
                // Ignore
            }
        }
        
        return "Unknown";
    }
    
    @Override
    public boolean isFinishPanel() {
        return true;
    }
    
    /**
     * Panel component for the SceneBuilder configuration wizard.
     */
    private static class SceneBuilderConfigurationPanel extends JPanel {
        
        private javax.swing.ButtonGroup configGroup;
        private JRadioButton autoConfigRadio;
        private JRadioButton manualConfigRadio;
        private JRadioButton downloadRadio;
        private javax.swing.JComboBox<Home> installationCombo;
        private javax.swing.JTextField manualPathField;
        private javax.swing.JButton browseButton;
        private javax.swing.JTextArea descriptionArea;
        private javax.swing.JLabel statusLabel;
        
        public SceneBuilderConfigurationPanel() {
            initComponents();
            setupComponents();
            loadInstallations();
        }
        
        private void initComponents() {
            setLayout(new java.awt.BorderLayout(10, 10));
            
            // Configuration options panel
            javax.swing.JPanel configPanel = new javax.swing.JPanel();
            configPanel.setLayout(new java.awt.GridBagLayout());
            
            java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
            gbc.insets = new java.awt.Insets(5, 5, 5, 5);
            gbc.anchor = java.awt.GridBagConstraints.WEST;
            
            // Configuration radio buttons
            configGroup = new javax.swing.ButtonGroup();
            
            autoConfigRadio = new JRadioButton();
            manualConfigRadio = new JRadioButton();
            downloadRadio = new JRadioButton();
            
            autoConfigRadio.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                "LBL_AutoConfig"));
            manualConfigRadio.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                "LBL_ManualConfig"));
            downloadRadio.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                "LBL_DownloadSceneBuilder"));
            
            configGroup.add(autoConfigRadio);
            configGroup.add(manualConfigRadio);
            configGroup.add(downloadRadio);
            
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
            configPanel.add(autoConfigRadio, gbc);
            
            gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
            configPanel.add(manualConfigRadio, gbc);
            
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
            configPanel.add(downloadRadio, gbc);
            
            // Installation selection combo
            installationCombo = new javax.swing.JComboBox<>();
            gbc.gridx = 1; gbc.gridy = 3; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            configPanel.add(installationCombo, gbc);
            
            // Manual path field and browse button
            manualPathField = new javax.swing.JTextField();
            browseButton = new javax.swing.JButton();
            
            gbc.gridx = 1; gbc.gridy = 4; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            configPanel.add(manualPathField, gbc);
            
            gbc.gridx = 1; gbc.gridy = 5;
            configPanel.add(browseButton, gbc);
            
            // Description area
            descriptionArea = new javax.swing.JTextArea();
            descriptionArea.setEditable(false);
            descriptionArea.setBackground(getBackground());
            descriptionArea.setWrapStyleWord(true);
            descriptionArea.setLineWrap(true);
            descriptionArea.setFont(getFont());
            
            javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(descriptionArea);
            gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            configPanel.add(scrollPane, gbc);
            
            // Status label
            statusLabel = new javax.swing.JLabel();
            gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
            configPanel.add(statusLabel, gbc);
            
            add(configPanel, java.awt.BorderLayout.CENTER);
            
            // Set button texts
            browseButton.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                "LBL_Browse"));
        }
        
        private void setupComponents() {
            browseButton.addActionListener(e -> browseForSceneBuilder());
            autoConfigRadio.addActionListener(e -> updateUI());
            manualConfigRadio.addActionListener(e -> updateUI());
            downloadRadio.addActionListener(e -> updateUI());
            
            updateUI();
        }
        
        private void loadInstallations() {
            EnhancedSceneBuilderManager manager = EnhancedSceneBuilderManager.getInstance();
            List<Home> installations = manager.getDiscoveredInstallations();
            
            installationCombo.removeAllItems();
            for (Home install : installations) {
                installationCombo.addItem(install);
            }
            
            if (!installations.isEmpty()) {
                autoConfigRadio.setEnabled(true);
                installationCombo.setEnabled(true);
                statusLabel.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "LBL_FoundInstallations", installations.size()));
            } else {
                autoConfigRadio.setEnabled(false);
                installationCombo.setEnabled(false);
                statusLabel.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "LBL_NoInstallationsFound"));
            }
        }
        
        private void updateUI() {
            boolean autoConfig = autoConfigRadio.isSelected();
            boolean manualConfig = manualConfigRadio.isSelected();
            boolean download = downloadRadio.isSelected();
            
            installationCombo.setEnabled(autoConfig);
            manualPathField.setEnabled(manualConfig);
            browseButton.setEnabled(manualConfig);
            
            // Update description based on selection
            if (autoConfig) {
                descriptionArea.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "DESC_AutoConfig"));
            } else if (manualConfig) {
                descriptionArea.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "DESC_ManualConfig"));
            } else if (download) {
                descriptionArea.setText(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                    "DESC_Download"));
            }
        }
        
        private void browseForSceneBuilder() {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle(NbBundle.getMessage(SceneBuilderConfigurationWizard.class, 
                "TITLE_SelectSceneBuilderDirectory"));
            
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                manualPathField.setText(selectedDir.getAbsolutePath());
            }
        }
        
        public JRadioButton getAutoConfigRadio() { return autoConfigRadio; }
        public JRadioButton getManualConfigRadio() { return manualConfigRadio; }
        public JRadioButton getDownloadRadio() { return downloadRadio; }
        public String getSelectedPath() { return manualPathField.getText(); }
        
        private void setError(String error) {
            if (error != null) {
                statusLabel.setText("ERROR: " + error);
                statusLabel.setForeground(java.awt.Color.RED);
            } else {
                statusLabel.setText("");
                statusLabel.setForeground(getForeground());
            }
        }
        
        private void readSettings(WizardDescriptor w) {
            // Initialize based on discovered installations
            if (!installationCombo.isEnabled()) {
                downloadRadio.setSelected(true);
            } else {
                autoConfigRadio.setSelected(true);
            }
        }
        
        private void storeSettings(WizardDescriptor w) {
            // Store additional settings if needed
        }
    }
    
    // Bundle messages
    private static final java.util.ResourceBundle BUNDLE = java.util.ResourceBundle.getBundle(
        "org.netbeans.modules.javafx2.scenebuilder.wizard.Bundle");
    
    static {
        // Initialize bundle properties
    }
}
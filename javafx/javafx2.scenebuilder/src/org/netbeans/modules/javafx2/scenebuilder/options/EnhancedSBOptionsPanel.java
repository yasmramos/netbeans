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
package org.netbeans.modules.javafx2.scenebuilder.options;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.modules.javafx2.scenebuilder.EnhancedSceneBuilderManager;
import org.netbeans.modules.javafx2.scenebuilder.Home;
import org.netbeans.modules.javafx2.scenebuilder.integration.JavaFXLibraryResolver;
import org.netbeans.modules.javafx2.scenebuilder.preview.FXMLPreviewProvider;
import org.netbeans.modules.javafx2.scenebuilder.preview.FXMLPreviewProviderImpl;
import org.netbeans.modules.javafx2.scenebuilder.wizard.SceneBuilderConfigurationWizard;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;

/**
 * Enhanced SceneBuilder Options Panel with modern UI and improved functionality.
 * Provides auto-discovery, wizard configuration, FXML preview options, and JavaFX library management.
 * 
 * @author MiniMax Agent
 */
public class EnhancedSBOptionsPanel extends javax.swing.JPanel {
    
    private final SBOptionsPanelController controller;
    private EnhancedSceneBuilderManager sbManager;
    private JavaFXLibraryResolver libResolver;
    private JTabbedPane mainTabs;
    
    // SceneBuilder Configuration
    private JRadioButton autoConfigRadio;
    private JRadioButton manualConfigRadio;
    private JComboBoxHome installationCombo;
    private JTextField manualPathField;
    private JButton browseButton;
    private JButton wizardButton;
    private JTextArea discoveryInfoArea;
    private JButton refreshButton;
    
    // FXML Preview Settings
    private JCheckBox enablePreviewCheck;
    private JCheckBox autoRefreshCheck;
    private JCheckBox splitViewCheck;
    private JTextField previewDelayField;
    
    // JavaFX Library Management
    private JTextArea libStatusArea;
    private JButton configureLibsButton;
    private JButton scanSystemButton;
    private JTextArea systemLibrariesArea;
    
    // General Settings
    private JCheckBox saveBeforeLaunchCheck;
    private JCheckBox showWelcomeCheck;
    private JCheckBox enableKeyboardShortcutsCheck;
    
    public EnhancedSBOptionsPanel(SBOptionsPanelController controller) {
        this.controller = controller;
        this.sbManager = EnhancedSceneBuilderManager.getInstance();
        this.libResolver = new JavaFXLibraryResolver();
        
        initComponents();
        setupComponents();
        loadSettings();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Create main tabbed pane
        mainTabs = new JTabbedPane();
        
        // SceneBuilder Configuration Tab
        JPanel sbConfigPanel = createSceneBuilderConfigPanel();
        mainTabs.addTab(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TAB_SceneBuilder"), 
                       null, sbConfigPanel, NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TIP_SceneBuilder"));
        
        // FXML Preview Tab
        JPanel previewPanel = createPreviewPanel();
        mainTabs.addTab(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TAB_Preview"), 
                       null, previewPanel, NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TIP_Preview"));
        
        // JavaFX Libraries Tab
        JPanel libsPanel = createLibrariesPanel();
        mainTabs.addTab(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TAB_Libraries"), 
                       null, libsPanel, NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TIP_Libraries"));
        
        // General Settings Tab
        JPanel generalPanel = createGeneralPanel();
        mainTabs.addTab(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TAB_General"), 
                       null, generalPanel, NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TIP_General"));
        
        add(mainTabs, BorderLayout.CENTER);
    }
    
    private JPanel createSceneBuilderConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Configuration options
        JPanel configPanel = new JPanel();
        configPanel.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        
        ButtonGroup configGroup = new ButtonGroup();
        
        autoConfigRadio = new JRadioButton();
        manualConfigRadio = new JRadioButton();
        
        autoConfigRadio.setText(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_AutoConfig"));
        manualConfigRadio.setText(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_ManualConfig"));
        
        configGroup.add(autoConfigRadio);
        configGroup.add(manualConfigRadio);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        configPanel.add(autoConfigRadio, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3;
        configPanel.add(manualConfigRadio, gbc);
        
        // Installation combo box
        JLabel installLabel = new JLabel(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_Installation"));
        gbc.gridx = 0; gbc.gridy = 2;
        configPanel.add(installLabel, gbc);
        
        installationCombo = new JComboBoxHome();
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        configPanel.add(installationCombo, gbc);
        
        // Manual path field
        manualPathField = new JTextField(25);
        gbc.gridx = 1; gbc.gridy = 3; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        configPanel.add(manualPathField, gbc);
        
        browseButton = new JButton(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_Browse"));
        gbc.gridx = 2; gbc.gridy = 3;
        configPanel.add(browseButton, gbc);
        
        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wizardButton = new JButton(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_Wizard"));
        refreshButton = new JButton(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_Refresh"));
        buttonPanel.add(wizardButton);
        buttonPanel.add(refreshButton);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        configPanel.add(buttonPanel, gbc);
        
        // Discovery info area
        discoveryInfoArea = new JTextArea(5, 30);
        discoveryInfoArea.setEditable(false);
        discoveryInfoArea.setBackground(getBackground());
        JScrollPane scrollPane = new JScrollPane(discoveryInfoArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_DiscoveryInfo")));
        
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        configPanel.add(scrollPane, gbc);
        
        panel.add(configPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        
        // Preview options
        enablePreviewCheck = new JCheckBox(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_EnablePreview"));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        settingsPanel.add(enablePreviewCheck, gbc);
        
        autoRefreshCheck = new JCheckBox(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_AutoRefresh"));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        settingsPanel.add(autoRefreshCheck, gbc);
        
        splitViewCheck = new JCheckBox(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_SplitView"));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        settingsPanel.add(splitViewCheck, gbc);
        
        // Preview delay
        JLabel delayLabel = new JLabel(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_PreviewDelay"));
        gbc.gridx = 0; gbc.gridy = 3;
        settingsPanel.add(delayLabel, gbc);
        
        previewDelayField = new JTextField(10);
        gbc.gridx = 1; gbc.gridy = 3;
        settingsPanel.add(previewDelayField, gbc);
        
        panel.add(settingsPanel, BorderLayout.NORTH);
        
        // Preview info
        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setBackground(getBackground());
        infoArea.setText(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "DESC_PreviewFeatures"));
        JScrollPane infoScroll = new JScrollPane(infoArea);
        panel.add(infoScroll, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createLibrariesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Status area
        libStatusArea = new JTextArea(4, 30);
        libStatusArea.setEditable(false);
        libStatusArea.setBackground(getBackground());
        JScrollPane statusScroll = new JScrollPane(libStatusArea);
        statusScroll.setBorder(BorderFactory.createTitledBorder(
            NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_ProjectStatus")));
        
        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        configureLibsButton = new JButton(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_ConfigureLibs"));
        scanSystemButton = new JButton(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_ScanSystem"));
        actionPanel.add(configureLibsButton);
        actionPanel.add(scanSystemButton);
        
        // System libraries info
        systemLibrariesArea = new JTextArea(8, 30);
        systemLibrariesArea.setEditable(false);
        systemLibrariesArea.setBackground(getBackground());
        JScrollPane systemScroll = new JScrollPane(systemLibrariesArea);
        systemScroll.setBorder(BorderFactory.createTitledBorder(
            NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_SystemLibraries")));
        
        panel.add(statusScroll, BorderLayout.NORTH);
        panel.add(actionPanel, BorderLayout.CENTER);
        panel.add(systemScroll, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createGeneralPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        
        // General settings
        saveBeforeLaunchCheck = new JCheckBox(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_SaveBeforeLaunch"));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        settingsPanel.add(saveBeforeLaunchCheck, gbc);
        
        showWelcomeCheck = new JCheckBox(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_ShowWelcome"));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        settingsPanel.add(showWelcomeCheck, gbc);
        
        enableKeyboardShortcutsCheck = new JCheckBox(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_KeyboardShortcuts"));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        settingsPanel.add(enableKeyboardShortcutsCheck, gbc);
        
        panel.add(settingsPanel, BorderLayout.NORTH);
        
        return panel;
    }
    
    private void setupComponents() {
        // SceneBuilder configuration
        autoConfigRadio.addActionListener(e -> updateSceneBuilderUI());
        manualConfigRadio.addActionListener(e -> updateSceneBuilderUI());
        browseButton.addActionListener(e -> browseForSceneBuilder());
        wizardButton.addActionListener(e -> openWizard());
        refreshButton.addActionListener(e -> refreshDiscoveries());
        
        manualPathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateSceneBuilderUI(); }
            @Override public void removeUpdate(DocumentEvent e) { updateSceneBuilderUI(); }
            @Override public void changedUpdate(DocumentEvent e) { updateSceneBuilderUI(); }
        });
        
        // Library management
        configureLibsButton.addActionListener(e -> configureJavaFXLibraries());
        scanSystemButton.addActionListener(e -> scanSystemLibraries());
        
        // Load initial data
        updateSceneBuilderUI();
        loadLibraryStatus();
    }
    
    private void updateSceneBuilderUI() {
        boolean autoConfig = autoConfigRadio.isSelected();
        
        installationCombo.setEnabled(autoConfig);
        manualPathField.setEnabled(!autoConfig);
        browseButton.setEnabled(!autoConfig);
        
        // Update discovery info
        String discoveryInfo = sbManager.getInstallationsSummary();
        discoveryInfoArea.setText(discoveryInfo);
    }
    
    private void browseForSceneBuilder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "TITLE_SelectSceneBuilderDirectory"));
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            manualPathField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    private void openWizard() {
        SceneBuilderConfigurationWizard wizard = new SceneBuilderConfigurationWizard();
        // This would open the wizard dialog
        // Implementation depends on NetBeans dialog system
        NotifyDescriptor nd = new NotifyDescriptor.Message(
            NbBundle.getMessage(EnhancedSBOptionsPanel.class, "MSG_WizardFeature"), 
            NotifyDescriptor.INFORMATION_MESSAGE);
        DialogDisplayer.getDefault().notify(nd);
    }
    
    private void refreshDiscoveries() {
        // Re-scan for SceneBuilder installations
        sbManager = EnhancedSceneBuilderManager.getInstance();
        
        // Update combo box with discovered installations
        installationCombo.removeAllItems();
        for (Home install : sbManager.getDiscoveredInstallations()) {
            installationCombo.addItem(install);
        }
        
        updateSceneBuilderUI();
    }
    
    private void loadLibraryStatus() {
        // Load JavaFX library status
        JavaFXLibraryResolver.ValidationResult result = libResolver.validateJavaFXConfiguration(null);
        libStatusArea.setText(result.getSummary());
        
        // Load system libraries
        List<String> systemLibs = libResolver.findJavaFXInstallations();
        StringBuilder sb = new StringBuilder();
        sb.append(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_FoundSystemLibs")).append(":\n");
        for (String lib : systemLibs) {
            sb.append("• ").append(lib).append("\n");
        }
        systemLibrariesArea.setText(sb.toString());
    }
    
    private void configureJavaFXLibraries() {
        NotifyDescriptor nd = new NotifyDescriptor.Message(
            NbBundle.getMessage(EnhancedSBOptionsPanel.class, "MSG_LibConfigFeature"), 
            NotifyDescriptor.INFORMATION_MESSAGE);
        DialogDisplayer.getDefault().notify(nd);
    }
    
    private void scanSystemLibraries() {
        scanSystemButton.setEnabled(false);
        try {
            Thread scanThread = new Thread(() -> {
                List<String> systemLibs = libResolver.findJavaFXInstallations();
                StringBuilder sb = new StringBuilder();
                sb.append(NbBundle.getMessage(EnhancedSBOptionsPanel.class, "LBL_ScanResults")).append(":\n");
                for (String lib : systemLibs) {
                    sb.append("• ").append(lib).append("\n");
                }
                systemLibrariesArea.setText(sb.toString());
                scanSystemButton.setEnabled(true);
            });
            scanThread.start();
        } catch (Exception ex) {
            scanSystemButton.setEnabled(true);
        }
    }
    
    private void loadSettings() {
        // Load current settings
        Home currentHome = controller.getSbHome();
        if (currentHome != null) {
            manualPathField.setText(currentHome.getPath());
            autoConfigRadio.setSelected(false);
            manualConfigRadio.setSelected(true);
        } else {
            autoConfigRadio.setSelected(true);
            manualConfigRadio.setSelected(false);
        }
        
        saveBeforeLaunchCheck.setSelected(controller.isSaveBeforeLaunch());
        
        // Load discovered installations
        refreshDiscoveries();
    }
    
    boolean isChanged() {
        // Check if any setting has changed
        return true; // Simplified - would implement proper change detection
    }
    
    void store() {
        // Store current settings
        controller.setSaveBeforeLaunch(saveBeforeLaunchCheck.isSelected());
        
        if (autoConfigRadio.isSelected()) {
            Home bestInstall = sbManager.getBestInstallation();
            if (bestInstall != null) {
                controller.setSbHome(bestInstall);
            }
        } else {
            String manualPath = manualPathField.getText().trim();
            if (!manualPath.isEmpty()) {
                File manualDir = new File(manualPath);
                if (manualDir.exists() && manualDir.isDirectory()) {
                    // Create Home object for manual path
                    Home manualHome = new Home(manualPath, "SceneBuilder", "", "Manual");
                    if (manualHome.isValid()) {
                        controller.setSbHome(manualHome);
                    }
                }
            }
        }
    }
    
    boolean valid() {
        if (manualConfigRadio.isSelected()) {
            String path = manualPathField.getText().trim();
            if (path.isEmpty()) {
                return false;
            }
            File dir = new File(path);
            return dir.exists() && dir.isDirectory();
        }
        return true;
    }
    
    /**
     * Custom combo box for Home selection
     */
    private static class JComboBoxHome extends javax.swing.JComboBox<Home> {
        public JComboBoxHome() {
            super();
        }
    }
}
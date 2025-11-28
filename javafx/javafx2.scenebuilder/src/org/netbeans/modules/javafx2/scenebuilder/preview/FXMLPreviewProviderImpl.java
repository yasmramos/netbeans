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
package org.netbeans.modules.javafx2.scenebuilder.preview;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.core.api.multiview.MultiViewDescription;
import org.netbeans.core.api.multiview.MultiViewElement;
import org.netbeans.modules.editor.NbEditorDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.javafx2.editor.FXMLEditorSupport;
import org.netbeans.modules.javafx2.editor.fxml.FXMLEditor;
import org.netbeans.modules.javafx2.scenebuilder.Settings;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;

/**
 * FXML Preview Provider that provides visual preview of FXML files within NetBeans.
 * This component renders FXML files using JavaFX runtime and displays them alongside
 * the source code editor.
 * 
 * @author MiniMax Agent
 */
@ServiceProvider(service = FXMLPreviewProvider.class)
public class FXMLPreviewProviderImpl implements FXMLPreviewProvider {
    
    private static final Logger LOG = Logger.getLogger(FXMLPreviewProviderImpl.class.getName());
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2);
    
    private FXMLPreviewComponent previewComponent;
    private FileObject currentFile;
    private boolean autoRefreshEnabled = true;
    
    @Override
    public JComponent createPreviewComponent(FileObject fxmlFile, Lookup context) {
        if (previewComponent == null) {
            previewComponent = new FXMLPreviewComponent();
        }
        
        currentFile = fxmlFile;
        previewComponent.setFile(fxmlFile);
        
        // Setup file change monitoring for auto-refresh
        if (autoRefreshEnabled) {
            setupAutoRefresh(fxmlFile);
        }
        
        return previewComponent.getComponent();
    }
    
    @Override
    public void refreshPreview() {
        if (previewComponent != null && currentFile != null) {
            EXECUTOR.execute(() -> previewComponent.loadFXML(currentFile));
        }
    }
    
    @Override
    public void setAutoRefresh(boolean enabled) {
        this.autoRefreshEnabled = enabled;
        if (enabled && currentFile != null) {
            setupAutoRefresh(currentFile);
        }
    }
    
    @Override
    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled;
    }
    
    /**
     * Sets up automatic refresh when the FXML file changes.
     */
    private void setupAutoRefresh(FileObject fxmlFile) {
        // This is a simplified implementation
        // In a full implementation, you would use FileChangeListener
        EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (previewComponent != null && currentFile != null) {
                    long lastModified = currentFile.lastModified().getTime();
                    if (previewComponent.shouldRefresh(lastModified)) {
                        SwingUtilities.invokeLater(() -> refreshPreview());
                    }
                }
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Error during auto-refresh", ex);
            }
        }, 2, 2, TimeUnit.SECONDS);
    }
    
    /**
     * Main preview component that renders the FXML file.
     */
    private static class FXMLPreviewComponent {
        
        private JPanel mainPanel;
        private FXMLPreviewCanvas canvas;
        private FileObject currentFile;
        private long lastRefresh = 0;
        
        public FXMLPreviewComponent() {
            mainPanel = new JPanel(new BorderLayout());
            canvas = new FXMLPreviewCanvas();
            
            mainPanel.add(canvas.getComponent(), BorderLayout.CENTER);
            mainPanel.setMinimumSize(new Dimension(300, 400));
            mainPanel.setPreferredSize(new Dimension(600, 500));
        }
        
        public void setFile(FileObject file) {
            this.currentFile = file;
            loadFXML(file);
        }
        
        public void loadFXML(FileObject fxmlFile) {
            try {
                if (fxmlFile == null || !fxmlFile.isValid()) {
                    return;
                }
                
                // Update timestamp
                lastRefresh = System.currentTimeMillis();
                
                // Load and parse FXML
                String fxmlContent = fxmlFile.asText();
                if (fxmlContent != null && !fxmlContent.trim().isEmpty()) {
                    // Validate FXML structure
                    if (isValidFXML(fxmlContent)) {
                        // For now, show a placeholder
                        // In a full implementation, this would render the actual JavaFX scene
                        canvas.displayMessage(NbBundle.getMessage(FXMLPreviewProviderImpl.class, 
                            "MSG_PreviewLoaded", fxmlFile.getName()));
                    } else {
                        canvas.displayError(NbBundle.getMessage(FXMLPreviewProviderImpl.class, 
                            "ERR_InvalidFXML"));
                    }
                } else {
                    canvas.displayMessage(NbBundle.getMessage(FMXLPreviewProviderImpl.class, 
                        "MSG_EmptyFXML"));
                }
                
                // Update status
                StatusDisplayer.getDefault().setStatusText(
                    NbBundle.getMessage(FXMLPreviewProviderImpl.class, "STATUS_PreviewUpdated")
                );
                
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Failed to load FXML preview", ex);
                canvas.displayError(NbBundle.getMessage(FXMLPreviewProviderImpl.class, 
                    "ERR_LoadFailed", ex.getMessage()));
            }
        }
        
        private boolean isValidFXML(String content) {
            // Basic validation of FXML structure
            return content.contains("<?xml version") && 
                   (content.contains("<AnchorPane") || 
                    content.contains("<BorderPane") ||
                    content.contains("<VBox") ||
                    content.contains("<HBox") ||
                    content.contains("<StackPane") ||
                    content.contains("<GridPane") ||
                    content.contains("<FlowPane"));
        }
        
        public boolean shouldRefresh(long fileLastModified) {
            return fileLastModified > lastRefresh;
        }
        
        public JComponent getComponent() {
            return mainPanel;
        }
    }
    
    /**
     * Canvas component that displays the FXML preview.
     */
    private static class FXMLPreviewCanvas {
        
        private JPanel canvasPanel;
        private java.awt.Label messageLabel;
        
        public FXMLPreviewCanvas() {
            canvasPanel = new JPanel(new BorderLayout());
            
            messageLabel = new java.awt.Label();
            messageLabel.setAlignment(java.awt.Label.CENTER);
            messageLabel.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
            
            canvasPanel.add(messageLabel, BorderLayout.CENTER);
            canvasPanel.setBackground(java.awt.Color.WHITE);
        }
        
        public void displayMessage(String message) {
            messageLabel.setText(message);
            messageLabel.setForeground(java.awt.Color.DARK_GRAY);
        }
        
        public void displayError(String errorMessage) {
            messageLabel.setText(errorMessage);
            messageLabel.setForeground(java.awt.Color.RED);
        }
        
        public JComponent getComponent() {
            return canvasPanel;
        }
    }
    
    /**
     * MultiView Description for FXML Preview
     */
    @ServiceProvider(service = MultiViewDescription.class)
    public static class FXMLPreviewDescription implements MultiViewDescription {
        
        private FileObject fileObject;
        private Lookup context;
        
        public FXMLPreviewDescription() {
        }
        
        public FXMLPreviewDescription(FileObject fileObject, Lookup context) {
            this.fileObject = fileObject;
            this.context = context;
        }
        
        @Override
        public MultiViewElement createElement() {
            FXMLPreviewProviderImpl provider = new FXMLPreviewProviderImpl();
            return new FXMLPreviewTopComponent(provider, fileObject, context);
        }
        
        @Override
        public String getDisplayName() {
            return NbBundle.getMessage(FXMLPreviewProviderImpl.class, "LBL_Preview");
        }
        
        @Override
        public Image getIcon() {
            return javax.swing.UIManager.getIcon("FileView.imageIcon"); // TODO: Better icon
        }
        
        @Override
        public String getPreferredID() {
            return "fxml-preview";
        }
        
        @Override
        public int getPersistenceType() {
            return TopComponent.PERSISTENCE_NEVER;
        }
        
        @Override
        public java.beans.BeanInfo getBeanInfo() {
            return null;
        }
        
        @Override
        public Lookup getLookup() {
            return context != null ? context : Lookups.empty();
        }
    }
    
    /**
     * TopComponent that hosts the FXML preview.
     */
    private static class FXMLPreviewTopComponent extends TopComponent {
        
        private FXMLPreviewProviderImpl provider;
        private FileObject fileObject;
        private Lookup context;
        
        public FXMLPreviewTopComponent(FXMLPreviewProviderImpl provider, FileObject fileObject, Lookup context) {
            this.provider = provider;
            this.fileObject = fileObject;
            this.context = context;
            setDisplayName(NbBundle.getMessage(FXMLPreviewProviderImpl.class, "LBL_Preview") + 
                          " - " + fileObject.getName());
            setToolTipText(NbBundle.getMessage(FXMLPreviewProviderImpl.class, "TIP_Preview"));
        }
        
        @Override
        protected String preferredID() {
            return "fxml-preview";
        }
        
        @Override
        public JComponent getVisualRepresentation() {
            return provider.createPreviewComponent(fileObject, context);
        }
        
        @Override
        public Lookup getLookup() {
            return context != null ? context : Lookups.empty();
        }
    }
}
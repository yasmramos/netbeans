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

import javax.swing.JComponent;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Service interface for providing FXML preview functionality.
 * Implementations of this interface can provide visual preview of FXML files
 * within the NetBeans IDE alongside the source code editor.
 * 
 * @author MiniMax Agent
 */
public interface FXMLPreviewProvider {
    
    /**
     * Creates a preview component for the given FXML file.
     * 
     * @param fxmlFile the FXML file to preview
     * @param context the lookup context (may contain additional information)
     * @return a JComponent that displays the preview, or null if preview is not available
     */
    JComponent createPreviewComponent(FileObject fxmlFile, Lookup context);
    
    /**
     * Refreshes the current preview with the latest content from the FXML file.
     */
    void refreshPreview();
    
    /**
     * Enables or disables automatic refresh when FXML files are modified.
     * 
     * @param enabled true to enable auto-refresh, false to disable
     */
    void setAutoRefresh(boolean enabled);
    
    /**
     * Checks if automatic refresh is currently enabled.
     * 
     * @return true if auto-refresh is enabled, false otherwise
     */
    boolean isAutoRefreshEnabled();
}
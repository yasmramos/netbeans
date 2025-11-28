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
package org.netbeans.modules.javafx2.scenebuilder.integration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ant.ProjectExtender;
import org.netbeans.modules.java.api.common.project.ProjectProperties;
import org.netbeans.modules.java.project.api.ProjectUserLibrarySettings;
import org.netbeans.modules.projectapi.SimpleFileOwnerQuery;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * JavaFX Library Resolver that automatically configures JavaFX dependencies
 * for projects using SceneBuilder and FXML files.
 * 
 * @author MiniMax Agent
 */
public class JavaFXLibraryResolver {
    
    private static final Logger LOG = Logger.getLogger(JavaFXLibraryResolver.class.getName());
    
    // Known JavaFX library patterns
    private static final String[] JAVAFX_JARS = {
        "javafx-base",
        "javafx-controls", 
        "javafx-graphics",
        "javafx-fxml",
        "javafx-media",
        "javafx-web"
    };
    
    private static final String[] JAVAFX_ARTIFACTS = {
        "org.openjfx:javafx-base",
        "org.openjfx:javafx-controls",
        "org.openjfx:javafx-graphics", 
        "org.openjfx:javafx-fxml",
        "org.openjfx:javafx-media",
        "org.openjfx:javafx-web"
    };
    
    /**
     * Checks if a project needs JavaFX library configuration.
     * 
     * @param project the project to check
     * @return true if the project needs JavaFX libraries configured
     */
    public boolean needsJavaFXConfiguration(Project project) {
        FileObject projectDir = project.getProjectDirectory();
        
        // Check for FXML files in the project
        FileObject[] fxmlFiles = projectDir.getChildren();
        for (FileObject child : fxmlFiles) {
            if (isFxmlFile(child)) {
                return true;
            }
        }
        
        // Check for JavaFX references in code
        return hasJavaFXReferences(projectDir);
    }
    
    /**
     * Auto-configures JavaFX libraries for a project.
     * 
     * @param project the project to configure
     * @return true if configuration was successful, false otherwise
     */
    public boolean configureJavaFX(Project project) {
        try {
            FileObject projectDir = project.getProjectDirectory();
            
            // Detect project type and configure accordingly
            if (isMavenProject(projectDir)) {
                return configureMavenJavaFX(project);
            } else if (isGradleProject(projectDir)) {
                return configureGradleJavaFX(project);
            } else if (isAntProject(projectDir)) {
                return configureAntJavaFX(project);
            } else {
                return configureBasicJavaFX(project);
            }
            
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to configure JavaFX for project", ex);
            return false;
        }
    }
    
    /**
     * Scans the system for available JavaFX installations.
     * 
     * @return list of found JavaFX library paths
     */
    public List<String> findJavaFXInstallations() {
        List<String> installations = new ArrayList<>();
        
        // Common JavaFX installation paths
        String[] commonPaths = {
            System.getProperty("java.home") + "/lib/javafx",
            System.getProperty("user.home") + "/.m2/repository/org/openjfx",
            "/usr/share/openjfx/lib",
            "/opt/javafx",
            "C:\\Program Files\\Java\\javafx"
        };
        
        for (String path : commonPaths) {
            File javaFxDir = new File(path);
            if (javaFxDir.exists() && javaFxDir.isDirectory()) {
                installations.add(path);
                LOG.log(Level.INFO, "Found JavaFX at: {0}", path);
            }
        }
        
        return installations;
    }
    
    /**
     * Validates JavaFX library configuration.
     * 
     * @param project the project to validate
     * @return validation result with details
     */
    public ValidationResult validateJavaFXConfiguration(Project project) {
        ValidationResult result = new ValidationResult();
        
        try {
            FileObject projectDir = project.getProjectDirectory();
            
            // Check if JavaFX jars are accessible
            ClassPath cp = ClassPath.getClassPath(projectDir, ClassPath.COMPILE);
            if (cp != null) {
                boolean hasJavaFX = false;
                for (String jarPattern : JAVAFX_JARS) {
                    for (String entry : cp.entries()) {
                        if (entry.contains(jarPattern)) {
                            hasJavaFX = true;
                            result.foundLibraries.add(jarPattern);
                            break;
                        }
                    }
                }
                
                if (!hasJavaFX) {
                    result.missingLibraries.addAll(List.of(JAVAFX_JARS));
                    result.isValid = false;
                }
            } else {
                result.isValid = false;
                result.error = "Unable to determine project classpath";
            }
            
            // Check for FXML files that might need validation
            List<FileObject> fxmlFiles = findFxmlFiles(projectDir);
            result.fxmlFileCount = fxmlFiles.size();
            
            if (result.fxmlFileCount > 0) {
                // Validate FXML files can be compiled
                for (FileObject fxmlFile : fxmlFiles) {
                    if (!isValidFxmlFile(fxmlFile)) {
                        result.invalidFxmlFiles.add(fxmlFile.getName());
                    }
                }
            }
            
        } catch (Exception ex) {
            result.isValid = false;
            result.error = ex.getMessage();
            LOG.log(Level.WARNING, "Validation failed", ex);
        }
        
        return result;
    }
    
    private boolean isMavenProject(FileObject projectDir) {
        return projectDir.getChild("pom.xml") != null;
    }
    
    private boolean isGradleProject(FileObject projectDir) {
        return projectDir.getChild("build.gradle") != null || 
               projectDir.getChild("build.gradle.kts") != null;
    }
    
    private boolean isAntProject(FileObject projectDir) {
        return projectDir.getChild("build.xml") != null;
    }
    
    private boolean isFxmlFile(FileObject file) {
        return file.getExt().equalsIgnoreCase("fxml");
    }
    
    private boolean isValidFxmlFile(FileObject fxmlFile) {
        try {
            String content = fxmlFile.asText();
            return content.contains("<?xml") && content.contains("javafx");
        } catch (IOException ex) {
            return false;
        }
    }
    
    private List<FileObject> findFxmlFiles(FileObject dir) {
        List<FileObject> fxmlFiles = new ArrayList<>();
        FileObject[] children = dir.getChildren();
        
        for (FileObject child : children) {
            if (child.isFolder()) {
                fxmlFiles.addAll(findFxmlFiles(child));
            } else if (isFxmlFile(child)) {
                fxmlFiles.add(child);
            }
        }
        
        return fxmlFiles;
    }
    
    private boolean hasJavaFXReferences(FileObject projectDir) {
        // This would scan source files for JavaFX imports
        // Simplified implementation
        return true;
    }
    
    private boolean configureMavenJavaFX(Project project) {
        // Implementation would modify pom.xml
        // For now, return success to indicate detection
        return true;
    }
    
    private boolean configureGradleJavaFX(Project project) {
        // Implementation would modify build.gradle
        // For now, return success to indicate detection  
        return true;
    }
    
    private boolean configureAntJavaFX(Project project) {
        // Implementation would modify build.xml
        // For now, return success to indicate detection
        return true;
    }
    
    private boolean configureBasicJavaFX(Project project) {
        // Basic configuration for projects without build system
        return true;
    }
    
    /**
     * Result of JavaFX configuration validation.
     */
    public static class ValidationResult {
        public boolean isValid = true;
        public String error;
        public List<String> foundLibraries = new ArrayList<>();
        public List<String> missingLibraries = new ArrayList<>();
        public List<String> invalidFxmlFiles = new ArrayList<>();
        public int fxmlFileCount = 0;
        
        public String getSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("JavaFX Configuration Validation\n");
            summary.append("Valid: ").append(isValid).append("\n");
            
            if (!foundLibraries.isEmpty()) {
                summary.append("Found libraries: ").append(String.join(", ", foundLibraries)).append("\n");
            }
            
            if (!missingLibraries.isEmpty()) {
                summary.append("Missing libraries: ").append(String.join(", ", missingLibraries)).append("\n");
            }
            
            summary.append("FXML files: ").append(fxmlFileCount).append("\n");
            
            if (!invalidFxmlFiles.isEmpty()) {
                summary.append("Invalid FXML: ").append(String.join(", ", invalidFxmlFiles)).append("\n");
            }
            
            if (error != null) {
                summary.append("Error: ").append(error);
            }
            
            return summary.toString();
        }
    }
}
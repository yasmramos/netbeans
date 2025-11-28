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
package org.netbeans.modules.javafx2.scenebuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.NbBundle;

/**
 * Enhanced SceneBuilder manager with automatic discovery and improved management.
 * Provides automatic detection of SceneBuilder installations across platforms.
 * 
 * @author MiniMax Agent
 */
public class EnhancedSceneBuilderManager {
    
    private static final Logger LOG = Logger.getLogger(EnhancedSceneBuilderManager.class.getName());
    
    // Platform-specific executable names
    private static final String[] SB_EXECUTABLES = {
        "SceneBuilder.exe",      // Windows
        "SceneBuilder",          // Linux/Mac
        "scenebuilder",          // Alternative Linux/Mac
        "SceneBuilder.app"       // Mac bundle
    };
    
    // Common installation directories by platform
    private static final String[][] COMMON_PATHS = {
        // Windows
        {"C:\\Program Files\\Gluon\\SceneBuilder", 
         "C:\\Users\\%USERNAME%\\AppData\\Local\\Gluon\\SceneBuilder",
         "C:\\Program Files (x86)\\Gluon\\SceneBuilder"},
        // Mac
        {"/Applications/Scene Builder",
         "~/Applications/Scene Builder"},
        // Linux
        {"/opt/scene-builder",
         "/usr/local/scene-builder",
         "~/.local/share/scene-builder"}
    };
    
    private static EnhancedSceneBuilderManager instance;
    private List<Home> discoveredInstallations;
    private Home bestInstallation;
    
    private EnhancedSceneBuilderManager() {
        discoverInstallations();
    }
    
    public static synchronized EnhancedSceneBuilderManager getInstance() {
        if (instance == null) {
            instance = new EnhancedSceneBuilderManager();
        }
        return instance;
    }
    
    /**
     * Discovers all SceneBuilder installations on the system.
     */
    private void discoverInstallations() {
        discoveredInstallations = new ArrayList<>();
        
        String osName = System.getProperty("os.name").toLowerCase();
        int platformIndex = getPlatformIndex(osName);
        
        if (platformIndex >= 0) {
            for (String pathTemplate : COMMON_PATHS[platformIndex]) {
                String expandedPath = expandUserPath(pathTemplate);
                scanDirectoryForSceneBuilder(expandedPath);
            }
        }
        
        // Also scan PATH environment variable
        scanPathVariable();
        
        // Sort installations by version (newest first)
        Collections.sort(discoveredInstallations, (h1, h2) -> {
            return compareVersions(h2.getVersion(), h1.getVersion());
        });
        
        // Select best installation
        if (!discoveredInstallations.isEmpty()) {
            bestInstallation = discoveredInstallations.get(0);
            LOG.log(Level.INFO, "Discovered {0} SceneBuilder installations", discoveredInstallations.size());
            for (Home installation : discoveredInstallations) {
                LOG.log(Level.INFO, "Found installation: {0}", installation);
            }
        } else {
            LOG.log(Level.WARNING, "No SceneBuilder installations discovered");
        }
    }
    
    /**
     * Scans a directory recursively for SceneBuilder installations.
     */
    private void scanDirectoryForSceneBuilder(String basePath) {
        Path path = Paths.get(basePath);
        if (!Files.exists(path)) {
            return;
        }
        
        try {
            Files.walk(path)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    scanDirectoryForSceneBuilderInstallation(dir.toString());
                });
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Failed to scan directory: " + basePath, ex);
        }
    }
    
    /**
     * Checks if a directory contains a valid SceneBuilder installation.
     */
    private void scanDirectoryForSceneBuilderInstallation(String directoryPath) {
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        String detectedVersion = detectVersion(directoryPath);
        if (detectedVersion != null) {
            for (String executableName : SB_EXECUTABLES) {
                String executablePath = new File(dir, executableName).getAbsolutePath();
                File executable = new File(executablePath);
                if (executable.exists() && executable.canExecute()) {
                    String propertiesPath = findPropertiesFile(directoryPath);
                    Home home = new Home(directoryPath, executableName, 
                                       propertiesPath != null ? 
                                           new File(directoryPath, propertiesPath).getAbsolutePath() : 
                                           "", 
                                       detectedVersion);
                    if (home.isValid() && !installationAlreadyKnown(home)) {
                        discoveredInstallations.add(home);
                        LOG.log(Level.FINE, "Discovered SceneBuilder at: {0}", directoryPath);
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Detects SceneBuilder version from installation directory or version file.
     */
    private String detectVersion(String installationPath) {
        File versionFile = new File(installationPath, "VERSION.txt");
        if (versionFile.exists()) {
            try {
                String version = Files.readAllLines(versionFile.toPath()).get(0).trim();
                if (isValidVersion(version)) {
                    return version;
                }
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Failed to read version from: " + versionFile, ex);
            }
        }
        
        // Try to extract version from directory name
        String dirName = new File(installationPath).getName();
        if (isValidVersion(dirName)) {
            return dirName;
        }
        
        // Default version for unknown installations
        return "Unknown";
    }
    
    /**
     * Finds SceneBuilder properties file.
     */
    private String findPropertiesFile(String installationPath) {
        String[] candidates = {
            "SceneBuilder.properties",
            "build.properties",
            "app.properties"
        };
        
        for (String candidate : candidates) {
            File propsFile = new File(installationPath, candidate);
            if (propsFile.exists()) {
                return candidate;
            }
        }
        
        return null;
    }
    
    /**
     * Scans PATH environment variable for SceneBuilder executables.
     */
    private void scanPathVariable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(File.pathSeparator);
            for (String path : paths) {
                for (String executableName : SB_EXECUTABLES) {
                    File executable = new File(path, executableName);
                    if (executable.exists() && executable.canExecute()) {
                        String installationPath = executable.getParent();
                        String version = detectVersion(installationPath);
                        String propertiesPath = findPropertiesFile(installationPath);
                        Home home = new Home(installationPath, executableName,
                                           propertiesPath != null ?
                                               new File(installationPath, propertiesPath).getAbsolutePath() :
                                               "",
                                           version);
                        if (home.isValid() && !installationAlreadyKnown(home)) {
                            discoveredInstallations.add(home);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Checks if an installation is already in the discovered list.
     */
    private boolean installationAlreadyKnown(Home home) {
        return discoveredInstallations.stream()
            .anyMatch(existing -> existing.getPath().equals(home.getPath()));
    }
    
    /**
     * Compares two version strings.
     */
    private int compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            int maxParts = Math.max(parts1.length, parts2.length);
            
            for (int i = 0; i < maxParts; i++) {
                int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                
                if (part1 != part2) {
                    return Integer.compare(part1, part2);
                }
            }
            return 0;
        } catch (NumberFormatException ex) {
            // Fallback to string comparison
            return v1.compareToIgnoreCase(v2);
        }
    }
    
    /**
     * Checks if a version string is valid.
     */
    private boolean isValidVersion(String version) {
        return version != null && !version.trim().isEmpty() && 
               !version.equalsIgnoreCase("Unknown");
    }
    
    /**
     * Expands user home directory in path templates.
     */
    private String expandUserPath(String path) {
        if (path.contains("%USERNAME%")) {
            String userName = System.getProperty("user.name");
            path = path.replace("%USERNAME%", userName);
        }
        
        if (path.startsWith("~")) {
            String userHome = System.getProperty("user.home");
            path = path.replace("~", userHome);
        }
        
        return path;
    }
    
    /**
     * Gets platform index for common paths lookup.
     */
    private int getPlatformIndex(String osName) {
        if (osName.contains("win")) return 0;     // Windows
        if (osName.contains("mac")) return 1;     // Mac
        if (osName.contains("nix") || osName.contains("nux")) return 2; // Linux
        return -1; // Unknown
    }
    
    /**
     * Gets all discovered installations.
     */
    public List<Home> getDiscoveredInstallations() {
        return Collections.unmodifiableList(discoveredInstallations);
    }
    
    /**
     * Gets the best available installation (newest version).
     */
    public Home getBestInstallation() {
        return bestInstallation;
    }
    
    /**
     * Checks if SceneBuilder is automatically discoverable.
     */
    public boolean isAutoDiscoverable() {
        return !discoveredInstallations.isEmpty();
    }
    
    /**
     * Auto-configures the best installation if available.
     */
    public boolean autoConfigure() {
        if (bestInstallation != null) {
            Settings settings = Settings.getInstance();
            settings.setSelectedHome(bestInstallation);
            settings.store();
            LOG.log(Level.INFO, "Auto-configured SceneBuilder: {0}", bestInstallation);
            return true;
        }
        return false;
    }
    
    /**
     * Gets installation summary for display.
     */
    @NbBundle.Messages({
        "LBL_Installations_Found=SceneBuilder Installations Found",
        "LBL_Best_Installation=Best Installation: {0}",
        "LBL_Version=Version: {0}",
        "LBL_Location=Location: {0}",
        "LBL_Auto_Config_Available=Auto-configuration available"
    })
    public String getInstallationsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(Bundle.LBL_Installations_Found()).append(": ").append(discoveredInstallations.size()).append("\n");
        
        if (bestInstallation != null) {
            summary.append(Bundle.LBL_Best_Installation()).append(": ").append(bestInstallation).append("\n");
            summary.append(Bundle.LBL_Version()).append(": ").append(bestInstallation.getVersion()).append("\n");
            summary.append(Bundle.LBL_Location()).append(": ").append(bestInstallation.getPath()).append("\n");
            summary.append(Bundle.LBL_Auto_Config_Available()).append("\n");
        }
        
        return summary.toString();
    }
}
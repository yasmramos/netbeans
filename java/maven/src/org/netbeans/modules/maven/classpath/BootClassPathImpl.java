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

package org.netbeans.modules.maven.classpath;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.modules.maven.NbMavenProjectImpl;
import org.netbeans.modules.maven.api.Constants;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.spi.java.classpath.PathResourceImplementation;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author  Milos Kleint
 */
public final class BootClassPathImpl extends AbstractBootPathImpl {

    private final EndorsedClassPathImpl ecpImpl;

    @SuppressWarnings("LeakingThisInConstructor")
    BootClassPathImpl(@NonNull NbMavenProjectImpl project, EndorsedClassPathImpl ecpImpl) {
        super(project);
        this.ecpImpl = ecpImpl;
        ecpImpl.setBCP(this);
        ecpImpl.addPropertyChangeListener(this);
    }

    @Override
    protected List<PathResourceImplementation> createResources() {
        ArrayList<PathResourceImplementation> result = new ArrayList<> ();
                boolean[] includeJDK = { true };
                boolean[] includeFX = { false };
                result.addAll(ecpImpl.getResources(includeJDK, includeFX));
                lastHintValue = project.getHintJavaPlatform();
                if (includeJDK[0]) {
                    JavaPlatform pat = findActivePlatform();
                    boolean hasFx = false;
                    for (ClassPath.Entry entry : pat.getBootstrapLibraries().entries()) {
                        if (entry.getURL().getPath().endsWith("/jfxrt.jar!/")) {
                            hasFx = true;
                        }
                        result.add(ClassPathSupport.createResource(entry.getURL()));
                    }
                    if (includeFX[0] && !hasFx) {
                        PathResourceImplementation fxcp = createFxCPImpl(pat);
                        if (fxcp != null) {
                            result.add(fxcp);
                        }
                    }
                    // JDK 9+ vendors do not expose JavaFX in the platform's
                    // bootstrap libraries; add the bundled javafx.* modules
                    // from the runtime image to the boot classpath so that
                    // Maven projects compile/run against them out of the box.
                    if (!hasFx) {
                        addBundledJavaFxModules(result, pat);
                    }
                    result.addAll(nbPlatformJavaFxCp(project, pat));
                }
        return result;
            }

    public @Override void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        String newVal = project.getHintJavaPlatform();
        if (evt.getSource() == project && evt.getPropertyName().equals(NbMavenProject.PROP_PROJECT)) {
            if (ecpImpl.resetCache()) {
                resetCache();
            } else {
                //Active platform was changed
                if ( (newVal == null && lastHintValue != null) || (newVal != null && !newVal.equals(lastHintValue))) {
                    resetCache ();
                }
            }
        }
        else if (evt.getSource() == ecpImpl) {
                    resetCache();
                }
            }
    
    private Collection<? extends PathResourceImplementation> nbPlatformJavaFxCp(NbMavenProjectImpl project, JavaPlatform pat) {
        List<PathResourceImplementation> toRet = new ArrayList<PathResourceImplementation>();
        //TODO better to iterate dependencies first or check what jdk we are using?
        //this should actually be part of maven.apisupport but there's no viable api right now..
        //TODO do we even need this, once people setup compiler plugin correctly to use jfxrt.jar, it should appear on boot cp anyway
        for (Artifact a : project.getOriginalMavenProject().getArtifacts()) {
            if ("org.netbeans.api".equals(a.getGroupId()) && "org-netbeans-libs-javafx".equals(a.getArtifactId())) {
                PathResourceImplementation fxcp = createFxCPImpl(pat);
                if (fxcp != null) {
                    toRet.add(fxcp);
                }
            }
        }
        return toRet;
    }

    private PathResourceImplementation createFxCPImpl(JavaPlatform pat) {
        for (FileObject fo : pat.getInstallFolders()) {
            FileObject jdk8 = fo.getFileObject("jre/lib/ext/jfxrt.jar"); // NOI18N
            if (jdk8 == null) {
                FileObject jdk7 = fo.getFileObject("jre/lib/jfxrt.jar"); // NOI18N
                if (jdk7 != null) {
                    // jdk7 add the classes on bootclasspath
                    if (FileUtil.isArchiveFile(jdk7)) {
                        return ClassPathSupport.createResource(FileUtil.getArchiveRoot(jdk7.toURL()));
                    }
                }
            }
        }
        return null;
    }

    /**
     * Adds the JavaFX modules bundled with the given platform to the boot
     * classpath resources. Covers modular JDK 9+ runtime images that ship
     * JavaFX inside the image (Azul Zulu FX, BellSoft Liberica Full, etc.),
     * where the modules are not part of the platform's bootstrap libraries.
     * The layout of a modular JDK home is:
     * <pre>
     *   &lt;home&gt;/jmods/javafx.base.jmod      (full JDK with JavaFX)
     *   &lt;home&gt;/lib/javafx.base.jar         (runtime-only image with JavaFX)
     * </pre>
     *
     * @param resources the mutable list of boot classpath resources to extend
     * @param pat the active Java platform
     */
    private void addBundledJavaFxModules(
            @NonNull final List<PathResourceImplementation> resources,
            @NonNull final JavaPlatform pat) {
        for (FileObject installFolder : pat.getInstallFolders()) {
            File installDir = FileUtil.toFile(installFolder);
            if (installDir == null) {
                continue;
            }
            // Prefer the jimage container used by javac on JDK 9+ full JDKs,
            // fall back to the flat lib/ jars of runtime-only distributions.
            FileObject jmods = installFolder.getFileObject("jmods"); // NOI18N
            if (jmods != null && jmods.isFolder() && hasJavaFx(jmods)) {
                resources.add(ClassPathSupport.createResource(toUrl(jmods)));
                return;
            }
            FileObject lib = installFolder.getFileObject("lib");     // NOI18N
            if (lib != null && lib.isFolder() && hasJavaFx(lib)) {
                resources.add(ClassPathSupport.createResource(toUrl(lib)));
                return;
            }
        }
    }

    private static boolean hasJavaFx(@NonNull final FileObject folder) {
        for (FileObject child : folder.getChildren()) {
            String name = child.getNameExt();
            if (name.startsWith(JAVAFX_MODULE_PREFIX)
                    && (name.endsWith(".jmod") || name.endsWith(".jar"))) { // NOI18N
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static java.net.URL toUrl(@NonNull final FileObject folder) {
        try {
            return folder.getURL();
        } catch (MalformedURLException e) {
            LOG.log(Level.FINE, "Invalid URL for " + folder, e);
            throw new IllegalStateException(e);
        }
    }

    private static final Logger LOG = Logger.getLogger(BootClassPathImpl.class.getName());
    private static final String JAVAFX_MODULE_PREFIX = "javafx.";  // NOI18N

}

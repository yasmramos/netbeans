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

package org.netbeans.modules.parsing.lucene.support;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.parsing.lucene.CacheCleaner;
import org.openide.util.Exceptions;

/**
 * A service providing information about
 * low memory condition.
 * @since 1.2
 * @author Tomas Zezula
 */
public final class LowMemoryWatcher {

    private static final Logger LOG = Logger.getLogger(LowMemoryWatcher.class.getName());
    private static final long LOGGER_RATE = Integer.getInteger(
            "%s.logger_rate".formatted(LowMemoryWatcher.class.getName()),   //NOI18N
            1000);   //1s


    //@GuardedBy("LowMemoryWatcher.class")
    private static LowMemoryWatcher instance;

    private final Callable<Boolean> strategy;
    private final AtomicBoolean testEnforcesLowMemory = new AtomicBoolean();

    private LowMemoryWatcher () {
        this.strategy = new DefaultStrategy();
    }

    /**
     * Returns true if the application is in low memory condition.
     * This information can be used by batch file processing.
     * @return true if nearly whole memory is used
     */
    public boolean isLowMemory () {
        if (testEnforcesLowMemory.get()) {
            return true;
        }
        try {
            return strategy.call();
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
            return false;
        }
    }

    /**
     * Tries to free memory.
     * @since 2.12
     */
    public void free() {
        free(false);
    }

    /**
     * Tries to free memory including Lucene caches.
     * @param freeCaches should the Lucene caches be cleaned.
     * @since 2.32
     */
    public void free(final boolean freeCaches) {
        if (freeCaches) {
            try {
                CacheCleaner.clean();
            } catch (Exception e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "Error cleaning caches during memory free", e);
                }
            }
        }
        
        final Runtime rt = Runtime.getRuntime();
        
        // More aggressive memory cleanup for severe conditions
        try {
            // Request garbage collection multiple times for better effect
            for (int i = 0; i < 3; i++) {
                rt.gc();
                Thread.sleep(10); // Small delay between GC calls
            }
            rt.runFinalization();
            
            // Log memory status after cleanup
            if (LOG.isLoggable(Level.FINER)) {
                final MemoryUsage usage = memBean.getHeapMemoryUsage();
                if (usage != null) {
                    long used = usage.getUsed();
                    long max = usage.getMax();
                    LOG.log(Level.FINER, 
                        "Memory cleanup completed - Used: {0}MB, Max: {1}MB ({2}%)", 
                        new Object[]{used / 1024 / 1024, max / 1024 / 1024, (used * 100 / max)});
                }
            }
        } catch (Exception e) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "Error during memory cleanup", e);
            }
        }
    }

    /*test*/ void setLowMemory(final boolean lowMemory) {
        this.testEnforcesLowMemory.set(lowMemory);
    }

    /**
     * Returns an instance of {@link LowMemoryWatcher}
     * @return the {@link LowMemoryWatcher}
     */
    public static synchronized LowMemoryWatcher getInstance() {
        if (instance == null) {
            instance = new LowMemoryWatcher();
        }
        return instance;
    }


    private static class DefaultStrategy implements Callable<Boolean> {

        private static final float heapLimit = 0.85f;  // Increased from 0.8f to be less aggressive
        private static final float aggressiveHeapLimit = 0.92f;  // Secondary threshold for aggressive cleanup
        private static final int memoryCheckIntervalMs = 2000;  // Cache results for 2 seconds
        private final MemoryMXBean memBean;
        private volatile long lastTime;
        private volatile Boolean lastResult;
        private volatile long lastCheckTime;

        DefaultStrategy() {
            this.memBean = ManagementFactory.getMemoryMXBean();
            assert this.memBean != null;
        }

        @Override
        public Boolean call() throws Exception {
            final long now = System.currentTimeMillis();
            
            // Cache the result to avoid frequent memory checks
            if (lastCheckTime > 0 && (now - lastCheckTime) < memoryCheckIntervalMs) {
                return lastResult != null ? lastResult : false;
            }
            
            if (this.memBean != null) {
                try {
                    final MemoryUsage usage = this.memBean.getHeapMemoryUsage();
                    if (usage != null) {
                        long used = usage.getUsed();
                        long max = usage.getMax();
                        
                        // Smart memory detection with multiple thresholds
                        boolean result;
                        if (max <= 0) {
                            // Fallback for systems where max memory is not available
                            result = used > (Runtime.getRuntime().totalMemory() * heapLimit);
                        } else {
                            // Primary threshold - normal low memory condition
                            boolean primaryThreshold = used > max * heapLimit;
                            
                            // Secondary threshold - aggressive cleanup needed
                            boolean aggressiveThreshold = used > max * aggressiveHeapLimit;
                            
                            result = primaryThreshold;
                            
                            // Log different levels based on severity
                            if (aggressiveThreshold && LOG.isLoggable(Level.WARNING)) {
                                LOG.log(Level.WARNING, 
                                    "Aggressive low memory condition detected - Used: {0}MB, Max: {1}MB ({2}%)", 
                                    new Object[]{used / 1024 / 1024, max / 1024 / 1024, (used * 100 / max)});
                            } else if (primaryThreshold && LOG.isLoggable(Level.FINER)) {
                                LOG.log(Level.FINER, 
                                    "Low memory condition detected - Used: {0}MB, Max: {1}MB ({2}%)", 
                                    new Object[]{used / 1024 / 1024, max / 1024 / 1024, (used * 100 / max)});
                            }
                        }
                        
                        // Cache the result
                        lastResult = result;
                        lastCheckTime = now;
                        
                        return result;
                    }
                } catch (Exception e) {
                    // Don't let memory check failures crash the system
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "Error checking memory usage", e);
                    }
                }
            }
            return false;
        }
    }

}

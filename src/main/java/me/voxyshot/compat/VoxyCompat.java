package me.voxyshot.compat;

import me.voxyshot.config.VoxyShotConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VoxyCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxyShot/VoxyCompat");
    public static final boolean VOXY_INSTALLED = FabricLoader.getInstance().isModLoaded("voxy");

    private static float originalSubDivisionSize = -1;
    private static boolean boostApplied = false;

    // Voxy classes & reflection cached handles
    private static Object voxyConfigInstance;
    private static Field fieldSubDivisionSize;

    private static Method methodGetRenderSystem;
    private static Field fieldNodeManager;
    private static Field fieldRenderGen;
    private static Field fieldModelService;

    private static Method methodNodeManagerHasWork;
    private static Method methodRenderGenGetTaskCount;
    private static Method methodModelServiceAreQueuesEmpty;
    private static Method methodModelServiceTick;

    private static Object downloadStreamInstance;
    private static Method methodDownloadStreamFlushWaitClear;

    private static Object uploadStreamInstance;
    private static Method methodUploadStreamTick;

    // Progress tracking (optional bindings)
    private static Field fieldGeometryData;
    private static Method methodGeometryGetSectionCount;
    private static Method methodNodeManagerGetUsedGeometry;
    private static Method methodNodeManagerGetGeometryCapacity;

    // Adaptive boost relaxation when the geometry buffer is (nearly) full
    private static final double GEOMETRY_PRESSURE_THRESHOLD = 0.90;
    private static final long RELAX_INTERVAL_MS = 2000;
    private static final float RELAX_FACTOR = 1.25f;
    private static long lastRelaxTimeMs = 0;

    /**
     * Snapshot of the Voxy streaming state for a single frame.
     *
     * @param idle         true if no Voxy queue reports pending work
     * @param sectionCount number of LOD sections currently resident on the GPU (-1 if unknown)
     * @param usedGeometry used geometry buffer bytes (-1 if unknown)
     * @param geometryCap  geometry buffer capacity in bytes (-1 if unknown)
     */
    public record Status(boolean idle, int sectionCount, long usedGeometry, long geometryCap) {
        public static final Status NO_VOXY = new Status(true, -1, -1, -1);

        public double geometryPressure() {
            if (usedGeometry < 0 || geometryCap <= 0) return 0.0;
            return (double) usedGeometry / (double) geometryCap;
        }
    }

    private static Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, true, VoxyCompat.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (ccl != null) {
                return Class.forName(name, true, ccl);
            }
            throw e;
        }
    }

    static {
        if (VOXY_INSTALLED) {
            try {
                // 1. VoxyConfig.CONFIG.subDivisionSize
                Class<?> cfgClass = findClass("me.cortex.voxy.client.config.VoxyConfig");
                Field cfgField = cfgClass.getField("CONFIG");
                voxyConfigInstance = cfgField.get(null);
                fieldSubDivisionSize = cfgClass.getField("subDivisionSize");

                // 2. IVoxyRenderSystemHolder.getNullable()
                Class<?> holderClass = findClass("me.cortex.voxy.client.core.IVoxyRenderSystemHolder");
                methodGetRenderSystem = holderClass.getMethod("getNullable");

                // 3. VoxyRenderSystem fields
                Class<?> renderSysClass = findClass("me.cortex.voxy.client.core.VoxyRenderSystem");
                fieldNodeManager = renderSysClass.getDeclaredField("nodeManager");
                fieldNodeManager.setAccessible(true);
                fieldRenderGen = renderSysClass.getDeclaredField("renderGen");
                fieldRenderGen.setAccessible(true);
                fieldModelService = renderSysClass.getDeclaredField("modelService");
                fieldModelService.setAccessible(true);

                // 4. Component methods
                Class<?> nodeMgrClass = findClass("me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager");
                methodNodeManagerHasWork = nodeMgrClass.getMethod("hasWork");

                Class<?> renderGenClass = findClass("me.cortex.voxy.client.core.rendering.building.RenderGenerationService");
                methodRenderGenGetTaskCount = renderGenClass.getMethod("getTaskCount");

                Class<?> modelServiceClass = findClass("me.cortex.voxy.client.core.model.ModelBakerySubsystem");
                methodModelServiceAreQueuesEmpty = modelServiceClass.getMethod("areQueuesEmpty");
                methodModelServiceTick = modelServiceClass.getMethod("tick", long.class);

                // 5. DownloadStream.INSTANCE.flushWaitClear()
                Class<?> downloadStreamClass = findClass("me.cortex.voxy.client.core.rendering.util.DownloadStream");
                downloadStreamInstance = downloadStreamClass.getField("INSTANCE").get(null);
                methodDownloadStreamFlushWaitClear = downloadStreamClass.getMethod("flushWaitClear");

                // 6. UploadStream.INSTANCE.tick()
                Class<?> uploadStreamClass = findClass("me.cortex.voxy.client.core.rendering.util.UploadStream");
                uploadStreamInstance = uploadStreamClass.getField("INSTANCE").get(null);
                methodUploadStreamTick = uploadStreamClass.getMethod("tick");

                LOGGER.info("Voxy integration initialized successfully via reflection.");
            } catch (Throwable t) {
                LOGGER.warn("Failed to initialize Voxy reflection bindings", t);
            }

            // Optional progress-tracking bindings (failure here must not disable the core integration)
            try {
                Class<?> renderSysClass = findClass("me.cortex.voxy.client.core.VoxyRenderSystem");
                fieldGeometryData = renderSysClass.getDeclaredField("geometryData");
                fieldGeometryData.setAccessible(true);
                Class<?> geometryDataClass = findClass("me.cortex.voxy.client.core.rendering.section.geometry.IGeometryData");
                methodGeometryGetSectionCount = geometryDataClass.getMethod("getSectionCount");
            } catch (Throwable t) {
                fieldGeometryData = null;
                methodGeometryGetSectionCount = null;
                LOGGER.warn("Voxy section count tracking unavailable; falling back to queue-only settle detection", t);
            }
            try {
                Class<?> nodeMgrClass = findClass("me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager");
                methodNodeManagerGetUsedGeometry = nodeMgrClass.getMethod("getUsedGeometryCapacity");
                methodNodeManagerGetGeometryCapacity = nodeMgrClass.getMethod("getGeometryCapacity");
            } catch (Throwable t) {
                methodNodeManagerGetUsedGeometry = null;
                methodNodeManagerGetGeometryCapacity = null;
                LOGGER.warn("Voxy geometry capacity tracking unavailable; adaptive LOD boost disabled", t);
            }
        }
    }

    public static void prepareCapture() {
        if (!VOXY_INSTALLED) return;

        try {
            // 1. Enable FREX Flawless Frames
            VoxyShotFrexConsumer.setFlawlessFramesActive(true);

            // 2. Apply Voxy LOD Quality Boost
            if (voxyConfigInstance != null && fieldSubDivisionSize != null) {
                float boost = Math.max(1.0f, VoxyShotConfig.INSTANCE.voxyLodBoost);
                if (originalSubDivisionSize < 0) {
                    originalSubDivisionSize = fieldSubDivisionSize.getFloat(voxyConfigInstance);
                }
                float newSubDiv = Math.max(16.0f, originalSubDivisionSize / boost);
                fieldSubDivisionSize.setFloat(voxyConfigInstance, newSubDiv);
                boostApplied = true;
                lastRelaxTimeMs = System.currentTimeMillis();
                LOGGER.info("Applied Voxy LOD Boost: {}x (subDivisionSize: {} -> {})", boost, originalSubDivisionSize, newSubDiv);
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to prepare Voxy for capture", t);
        }
    }

    /**
     * If the Voxy geometry buffer is nearly full, Voxy stops uploading new LOD meshes
     * (AsyncNodeManager requires free capacity), so far-away nodes can never be refined and
     * would stay at a coarse LOD in the screenshot. In that case relax the boost step by step
     * (never beyond the user's original subDivisionSize) so the whole frame can be finished
     * at a uniform quality instead of leaving the distance blurry.
     *
     * @return true if the boost was relaxed this call
     */
    public static boolean relaxBoostIfGeometryFull(Status status) {
        if (!VOXY_INSTALLED || !boostApplied || status == null) return false;
        if (voxyConfigInstance == null || fieldSubDivisionSize == null) return false;
        if (status.geometryPressure() < GEOMETRY_PRESSURE_THRESHOLD) return false;

        long now = System.currentTimeMillis();
        if (now - lastRelaxTimeMs < RELAX_INTERVAL_MS) return false;

        try {
            float current = fieldSubDivisionSize.getFloat(voxyConfigInstance);
            if (current >= originalSubDivisionSize) return false;
            float relaxed = Math.min(originalSubDivisionSize, current * RELAX_FACTOR);
            fieldSubDivisionSize.setFloat(voxyConfigInstance, relaxed);
            lastRelaxTimeMs = now;
            LOGGER.warn("Voxy geometry buffer is {}% full; relaxing LOD boost (subDivisionSize: {} -> {})",
                    Math.round(status.geometryPressure() * 100), current, relaxed);
            return true;
        } catch (Throwable t) {
            LOGGER.error("Failed to relax Voxy LOD boost", t);
            return false;
        }
    }

    public static boolean tickAndCheckVoxySettled() {
        return tickAndGetStatus().idle();
    }

    /**
     * Pumps Voxy's streaming systems once and returns the current streaming status.
     */
    public static Status tickAndGetStatus() {
        if (!VOXY_INSTALLED) return Status.NO_VOXY;

        try {
            if (methodGetRenderSystem == null) return Status.NO_VOXY;
            Object renderer = methodGetRenderSystem.invoke(null);
            if (renderer == null) return Status.NO_VOXY;

            // Flush GPU download stream so GPU-side node descent requests reach CPU
            if (downloadStreamInstance != null && methodDownloadStreamFlushWaitClear != null) {
                methodDownloadStreamFlushWaitClear.invoke(downloadStreamInstance);
            }
            if (uploadStreamInstance != null && methodUploadStreamTick != null) {
                methodUploadStreamTick.invoke(uploadStreamInstance);
            }

            // Progress model bakery
            if (fieldModelService != null && methodModelServiceTick != null) {
                Object modelService = fieldModelService.get(renderer);
                if (modelService != null) {
                    methodModelServiceTick.invoke(modelService, 100_000_000L);
                }
            }

            GL11.glFinish();

            // Check if NodeManager has work
            boolean nodeManagerWorking = false;
            if (fieldNodeManager != null && methodNodeManagerHasWork != null) {
                Object nodeManager = fieldNodeManager.get(renderer);
                if (nodeManager != null) {
                    nodeManagerWorking = (boolean) methodNodeManagerHasWork.invoke(nodeManager);
                }
            }

            // Check if RenderGen has tasks
            int renderGenTasks = 0;
            if (fieldRenderGen != null && methodRenderGenGetTaskCount != null) {
                Object renderGen = fieldRenderGen.get(renderer);
                if (renderGen != null) {
                    renderGenTasks = (int) methodRenderGenGetTaskCount.invoke(renderGen);
                }
            }

            // Check if ModelBakery queues are empty
            boolean modelQueuesEmpty = true;
            if (fieldModelService != null && methodModelServiceAreQueuesEmpty != null) {
                Object modelService = fieldModelService.get(renderer);
                if (modelService != null) {
                    modelQueuesEmpty = (boolean) methodModelServiceAreQueuesEmpty.invoke(modelService);
                }
            }

            boolean idle = !nodeManagerWorking && renderGenTasks == 0 && modelQueuesEmpty;

            // Resident LOD section count (changes whenever LOD refinement progresses)
            int sectionCount = -1;
            if (fieldGeometryData != null && methodGeometryGetSectionCount != null) {
                Object geometryData = fieldGeometryData.get(renderer);
                if (geometryData != null) {
                    sectionCount = (int) methodGeometryGetSectionCount.invoke(geometryData);
                }
            }

            // Geometry buffer usage
            long usedGeometry = -1;
            long geometryCap = -1;
            if (fieldNodeManager != null && methodNodeManagerGetUsedGeometry != null && methodNodeManagerGetGeometryCapacity != null) {
                Object nodeManager = fieldNodeManager.get(renderer);
                if (nodeManager != null) {
                    usedGeometry = (long) methodNodeManagerGetUsedGeometry.invoke(nodeManager);
                    geometryCap = (long) methodNodeManagerGetGeometryCapacity.invoke(nodeManager);
                }
            }

            return new Status(idle, sectionCount, usedGeometry, geometryCap);
        } catch (Throwable t) {
            LOGGER.error("Error during Voxy queue check", t);
            return Status.NO_VOXY;
        }
    }

    public static void restoreAfterCapture() {
        if (!VOXY_INSTALLED) return;

        try {
            // Restore subDivisionSize
            if (boostApplied && originalSubDivisionSize > 0 && voxyConfigInstance != null && fieldSubDivisionSize != null) {
                fieldSubDivisionSize.setFloat(voxyConfigInstance, originalSubDivisionSize);
                LOGGER.info("Restored Voxy subDivisionSize to {}", originalSubDivisionSize);
            }
            originalSubDivisionSize = -1;
            boostApplied = false;

            // Disable FREX Flawless Frames
            VoxyShotFrexConsumer.setFlawlessFramesActive(false);
        } catch (Throwable t) {
            LOGGER.error("Failed to restore Voxy configuration", t);
        }
    }
}

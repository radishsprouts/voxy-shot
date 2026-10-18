package me.voxyshot.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import me.voxyshot.compat.VersionCompat;
import me.voxyshot.compat.VoxyCompat;
import me.voxyshot.config.VoxyShotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenshotTaskController {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxyShot/Controller");
    public static final ScreenshotTaskController INSTANCE = new ScreenshotTaskController();

    public enum State {
        IDLE,
        PREPARING,
        WAITING_FOR_LOD,
        CAPTURING,
        RESTORING
    }

    private State state = State.IDLE;

    private int originalWidth;
    private int originalHeight;
    private boolean originalHideGui;
    private int targetWidth;
    private int targetHeight;

    private int consecutiveIdleFrames = 0;
    private int totalWaitFrames = 0;
    private long startTimeMs = 0;
    private long lastProgressTimeMs = 0;
    private int lastSectionCount = -1;
    private boolean isInternalGrab = false;

    public synchronized boolean isCapturing() {
        return state != State.IDLE;
    }

    public synchronized boolean isInternalGrab() {
        return isInternalGrab;
    }

    public synchronized void startCapture() {
        if (state != State.IDLE) {
            LOGGER.warn("Capture is already in progress!");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (window == null) return;

        VoxyShotConfig cfg = VoxyShotConfig.INSTANCE;
        this.targetWidth = cfg.getTargetWidth();
        this.targetHeight = cfg.getTargetHeight();

        this.originalWidth = window.getWidth();
        this.originalHeight = window.getHeight();
        this.originalHideGui = VersionCompat.isHudHidden(mc);

        this.consecutiveIdleFrames = 0;
        this.totalWaitFrames = 0;
        this.startTimeMs = System.currentTimeMillis();
        this.lastProgressTimeMs = this.startTimeMs;
        this.lastSectionCount = -1;
        this.state = State.PREPARING;

        LOGGER.info("Starting high-resolution screenshot task: {}x{} (original: {}x{})",
                targetWidth, targetHeight, originalWidth, originalHeight);

        if (mc.player != null) {
            mc.player.sendSystemMessage(
                    Component.translatable("voxyshot.capture.capturing", targetWidth, targetHeight)
            );
        }
    }

    /**
     * Called at HEAD of Minecraft#renderFrame.
     */
    public synchronized void onRenderFrameHead() {
        if (state == State.IDLE) return;

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        if (state == State.PREPARING) {
            try {
                // Apply HUD visibility option
                if (VoxyShotConfig.INSTANCE.hideHudDuringCapture) {
                    VersionCompat.setHudHidden(mc, true);
                }

                // Prepare Voxy (LOD boost + FREX)
                VoxyCompat.prepareCapture();

                // Resize window & render targets to target high resolution
                resizeAll(window, mc, targetWidth, targetHeight);

                state = State.WAITING_FOR_LOD;
            } catch (Throwable t) {
                LOGGER.error("Failed to prepare high-res screenshot capture", t);
                abortAndRestore();
            }
        } else if (state == State.WAITING_FOR_LOD) {
            // Keep window resized to target resolution
            ensureResized(window, mc, targetWidth, targetHeight);
        } else if (state == State.CAPTURING) {
            ensureResized(window, mc, targetWidth, targetHeight);
        }
    }

    /**
     * Called at TAIL of GameRenderer#render (immediately after level and post effects finish).
     */
    public synchronized void onRenderFrameTail() {
        if (state == State.IDLE) return;

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        if (state == State.WAITING_FOR_LOD) {
            totalWaitFrames++;
            long now = System.currentTimeMillis();

            VoxyCompat.Status status = VoxyCompat.tickAndGetStatus();

            // LOD refinement happens one hierarchy level per traversal and the mesh-gen queue counter
            // drops to 0 while the last meshes are still being built/uploaded, so a short "idle" moment
            // does NOT mean the distant terrain is finished. Treat any change of the resident section
            // count as progress and require a long, stable idle period before capturing.
            boolean progressed = status.sectionCount() >= 0 && status.sectionCount() != lastSectionCount;
            if (progressed) {
                lastSectionCount = status.sectionCount();
                lastProgressTimeMs = now;
            }

            // If the geometry buffer is full Voxy cannot upload finer LODs anymore; relax the boost
            // so the remaining (mostly distant) area can still be refined.
            if (VoxyCompat.relaxBoostIfGeometryFull(status)) {
                consecutiveIdleFrames = 0;
                lastProgressTimeMs = now;
            }

            if (status.idle() && !progressed) {
                consecutiveIdleFrames++;
            } else {
                consecutiveIdleFrames = 0;
            }

            long elapsedMs = now - startTimeMs;
            long stableMs = now - lastProgressTimeMs;
            long maxWaitMs = Math.max(5, VoxyShotConfig.INSTANCE.maxWaitSeconds) * 1000L;
            int requiredStableFrames = Math.max(2, VoxyShotConfig.INSTANCE.settleFrames);
            long requiredStableMs = Math.max(0, VoxyShotConfig.INSTANCE.settleMillis);

            if (totalWaitFrames % 60 == 0) {
                LOGGER.info("Waiting for Voxy LODs... frame={}, idle={}, sections={}, geometry={}MB/{}MB, stableFrames={}",
                        totalWaitFrames, status.idle(), status.sectionCount(),
                        status.usedGeometry() >> 20, status.geometryCap() >> 20, consecutiveIdleFrames);
            }

            boolean settled = consecutiveIdleFrames >= requiredStableFrames && stableMs >= requiredStableMs;
            boolean timedOut = elapsedMs > maxWaitMs;
            if (settled || timedOut) {
                if (settled) {
                    LOGGER.info("LOD streaming settled after {} frames ({} ms, {} sections). Proceeding to capture.",
                            totalWaitFrames, elapsedMs, status.sectionCount());
                } else {
                    LOGGER.warn("LOD streaming did not settle within {} s (sections={}, idle={}). Capturing anyway; distant terrain may be lower quality.",
                            maxWaitMs / 1000, status.sectionCount(), status.idle());
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(
                                Component.translatable("voxyshot.capture.timeout", maxWaitMs / 1000)
                        );
                    }
                }
                state = State.CAPTURING;
            }
        } else if (state == State.CAPTURING) {
            try {
                RenderTarget target = VersionCompat.getMainRenderTarget(mc);
                isInternalGrab = true;
                Screenshot.grab(
                        mc.gameDirectory,
                        target,
                        msg -> {
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendSystemMessage(msg);
                                }
                            });
                        }
                );
            } catch (Throwable t) {
                LOGGER.error("Failed to grab screenshot framebuffer", t);
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(
                                Component.translatable("voxyshot.capture.failed", t.getMessage())
                        );
                    }
                });
            } finally {
                isInternalGrab = false;
                state = State.RESTORING;
                restoreAll(window, mc);
            }
        }
    }

    private void resizeAll(Window window, Minecraft mc, int w, int h) {
        window.setWidth(w);
        window.setHeight(h);
        mc.gameRenderer.resize(w, h);
        mc.resizeGui();
    }

    private void ensureResized(Window window, Minecraft mc, int w, int h) {
        if (window.getWidth() != w || window.getHeight() != h) {
            resizeAll(window, mc, w, h);
        }
    }

    private void restoreAll(Window window, Minecraft mc) {
        try {
            VoxyCompat.restoreAfterCapture();
            VersionCompat.setHudHidden(mc, originalHideGui);
            resizeAll(window, mc, originalWidth, originalHeight);
            LOGGER.info("Restored window and Voxy settings after capture.");
        } catch (Throwable t) {
            LOGGER.error("Error restoring settings after capture", t);
        } finally {
            state = State.IDLE;
        }
    }

    public synchronized void abortAndRestore() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        restoreAll(window, mc);
    }
}

package me.voxyshot.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public final class VersionCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxyShot/Compat");
    private static final MethodHandle GET_MAIN_RENDER_TARGET;

    static {
        MethodHandle mh = null;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        // 1. Try Minecraft#getMainRenderTarget() (Minecraft 26.1.x and earlier)
        try {
            Method m = Minecraft.class.getMethod("getMainRenderTarget");
            mh = lookup.unreflect(m);
            LOGGER.info("Using Minecraft#getMainRenderTarget() for RenderTarget access");
        } catch (Throwable ignored) {
        }

        // 2. Try GameRenderer#mainRenderTarget() (Minecraft 26.2+)
        if (mh == null) {
            try {
                Method m = GameRenderer.class.getMethod("mainRenderTarget");
                MethodHandle gameRendererMh = lookup.unreflect(m);
                // Bind to extract from Minecraft -> GameRenderer
                mh = MethodHandles.filterReturnValue(
                        lookup.findGetter(Minecraft.class, "gameRenderer", GameRenderer.class),
                        gameRendererMh
                );
                LOGGER.info("Using GameRenderer#mainRenderTarget() for RenderTarget access");
            } catch (Exception e) {
                LOGGER.warn("Failed to locate mainRenderTarget method via public lookup, trying reflection fallback", e);
            }
        }

        // 3. Fallback search on GameRenderer methods returning RenderTarget
        if (mh == null) {
            for (Method m : GameRenderer.class.getMethods()) {
                if (RenderTarget.class.isAssignableFrom(m.getReturnType()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        MethodHandle fallbackMh = MethodHandles.lookup().unreflect(m);
                        mh = MethodHandles.filterReturnValue(
                                MethodHandles.lookup().findGetter(Minecraft.class, "gameRenderer", GameRenderer.class),
                                fallbackMh
                        );
                        LOGGER.info("Using fallback GameRenderer#{}() for RenderTarget access", m.getName());
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        GET_MAIN_RENDER_TARGET = mh;
    }

    public static RenderTarget getMainRenderTarget(Minecraft mc) {
        if (GET_MAIN_RENDER_TARGET != null) {
            try {
                return (RenderTarget) GET_MAIN_RENDER_TARGET.invokeExact(mc);
            } catch (Throwable t) {
                LOGGER.error("Failed to invoke getMainRenderTarget", t);
            }
        }
        // Direct fallback: check field if possible
        try {
            var field = Minecraft.class.getDeclaredField("mainRenderTarget");
            field.setAccessible(true);
            return (RenderTarget) field.get(mc);
        } catch (Throwable ignored) {
        }
        throw new IllegalStateException("Could not resolve Minecraft main RenderTarget!");
    }
}

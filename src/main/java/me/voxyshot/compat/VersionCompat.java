package me.voxyshot.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Entry point for Minecraft APIs that differ between supported versions.
 * The actual implementation is {@link McCompat}, which is compiled from src/mc/&lt;minecraft_version&gt;/java,
 * so each release jar is built against exactly one Minecraft version.
 */
public final class VersionCompat {
    private VersionCompat() {
    }

    public static Screen getScreen(Minecraft mc) {
        return McCompat.getScreen(mc);
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        McCompat.setScreen(mc, screen);
    }

    public static boolean isHudHidden(Minecraft mc) {
        return McCompat.isHudHidden(mc);
    }

    public static void setHudHidden(Minecraft mc, boolean hidden) {
        McCompat.setHudHidden(mc, hidden);
    }

    public static RenderTarget getMainRenderTarget(Minecraft mc) {
        return McCompat.getMainRenderTarget(mc);
    }
}


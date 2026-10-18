package me.voxyshot.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Version-specific API bridge for Minecraft 26.1.2.
 * Each supported Minecraft version has its own implementation under src/mc/&lt;version&gt;/java.
 */
public final class McCompat {
    /** Input type used for keyboard key mappings. */
    public static final InputConstants.Type KEYBOARD_TYPE = InputConstants.Type.KEYSYM;

    private McCompat() {
    }

    public static Screen getScreen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    public static boolean isHudHidden(Minecraft mc) {
        return mc.options.hideGui;
    }

    public static void setHudHidden(Minecraft mc, boolean hidden) {
        mc.options.hideGui = hidden;
    }

    public static RenderTarget getMainRenderTarget(Minecraft mc) {
        return mc.getMainRenderTarget();
    }
}

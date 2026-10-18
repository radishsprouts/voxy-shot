package me.voxyshot.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Version-specific API bridge for Minecraft 26.3.
 * Screen handling lives in {@code Minecraft#gui}, HUD visibility in {@code Gui#hud},
 * and the main render target in {@code GameRenderer#mainRenderTarget()}.
 */
public final class McCompat {
    /** Input type used for keyboard key mappings (26.3 switched from GLFW to SDL: KEYSYM/SCANCODE became KEYBOARD). */
    public static final InputConstants.Type KEYBOARD_TYPE = InputConstants.Type.KEYBOARD;

    private McCompat() {
    }

    public static Screen getScreen(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    public static boolean isHudHidden(Minecraft mc) {
        return mc.gui.hud.isHidden();
    }

    public static void setHudHidden(Minecraft mc, boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }

    public static RenderTarget getMainRenderTarget(Minecraft mc) {
        return mc.gameRenderer.mainRenderTarget();
    }
}

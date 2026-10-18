package me.voxyshot;

import com.mojang.blaze3d.platform.InputConstants;
import me.voxyshot.capture.ScreenshotTaskController;
import me.voxyshot.config.VoxyShotConfig;
import me.voxyshot.config.VoxyShotConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxyShotClient implements ClientModInitializer {
    public static final String MOD_ID = "voxyshot";
    public static final Logger LOGGER = LoggerFactory.getLogger("VoxyShot");

    public static final KeyMapping KEY_CAPTURE = new KeyMapping(
            "key.voxyshot.capture",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyMapping.Category.MISC
    );

    public static final KeyMapping KEY_CONFIG = new KeyMapping(
            "key.voxyshot.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            KeyMapping.Category.MISC
    );

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing VoxyShot...");
        VoxyShotConfig.load();
    }

    public static void handleKeyInput() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY_CAPTURE.consumeClick()) {
            ScreenshotTaskController.INSTANCE.startCapture();
        }
        while (KEY_CONFIG.consumeClick()) {
            mc.setScreen(new VoxyShotConfigScreen(mc.screen));
        }
    }
}

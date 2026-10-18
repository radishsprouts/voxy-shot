package me.voxyshot;

import com.mojang.blaze3d.platform.InputConstants;
import me.voxyshot.capture.ScreenshotTaskController;
import me.voxyshot.compat.McCompat;
import me.voxyshot.compat.VersionCompat;
import me.voxyshot.config.VoxyShotConfig;
import me.voxyshot.config.VoxyShotConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxyShotClient implements ClientModInitializer {
    public static final String MOD_ID = "voxyshot";
    public static final Logger LOGGER = LoggerFactory.getLogger("VoxyShot");

    /** Dedicated category shown as "VoxyShot" in Options → Controls → Key Binds (lang key: key.category.voxyshot.main). */
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );

    // InputConstants.KEY_* resolve to the correct key codes for each Minecraft version (GLFW up to 26.2, SDL from 26.3)
    public static final KeyMapping KEY_CAPTURE = new KeyMapping(
            "key.voxyshot.capture",
            McCompat.KEYBOARD_TYPE,
            InputConstants.KEY_F9,
            KEY_CATEGORY
    );

    public static final KeyMapping KEY_CONFIG = new KeyMapping(
            "key.voxyshot.config",
            McCompat.KEYBOARD_TYPE,
            InputConstants.KEY_F10,
            KEY_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing VoxyShot...");
        VoxyShotConfig.load();

        // Register via Fabric API so the keys appear in the vanilla Key Binds screen
        // and user-changed bindings are saved to / loaded from options.txt.
        KeyMappingHelper.registerKeyMapping(KEY_CAPTURE);
        KeyMappingHelper.registerKeyMapping(KEY_CONFIG);
    }

    public static void handleKeyInput() {
        Minecraft mc = Minecraft.getInstance();
        while (KEY_CAPTURE.consumeClick()) {
            ScreenshotTaskController.INSTANCE.startCapture();
        }
        while (KEY_CONFIG.consumeClick()) {
            // MixinKeyboardHandler may already have opened the screen for this key press
            Screen current = VersionCompat.getScreen(mc);
            if (!(current instanceof VoxyShotConfigScreen)) {
                VersionCompat.setScreen(mc, new VoxyShotConfigScreen(current));
            }
        }
    }
}

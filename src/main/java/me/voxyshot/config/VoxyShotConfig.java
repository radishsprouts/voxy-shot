package me.voxyshot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class VoxyShotConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxyShot/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("voxyshot.json").toFile();

    public static VoxyShotConfig INSTANCE = new VoxyShotConfig();

    public enum ResolutionPreset {
        FHD_1080P("1080p (FHD 1920x1080)", 1920, 1080),
        WQHD_1440P("1440p (WQHD 2560x1440)", 2560, 1440),
        UHD_4K("4K (UHD 3840x2160)", 3840, 2160),
        UHD_5K("5K (5120x2880)", 5120, 2880),
        UHD_8K("8K (7680x4320)", 7680, 4320),
        CUSTOM("Custom", 3840, 2160);

        private final String label;
        private final int width;
        private final int height;

        ResolutionPreset(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        public String getLabel() {
            return label;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    public ResolutionPreset preset = ResolutionPreset.UHD_4K;
    public int customWidth = 3840;
    public int customHeight = 2160;
    public boolean overrideVanillaF2 = false;
    public float voxyLodBoost = 2.0f; // 1.0f, 1.5f, 2.0f, 3.0f, 4.0f
    public boolean hideHudDuringCapture = true;
    public int maxWaitSeconds = 60;
    // Voxy must report no pending work and no change in resident LOD sections for this many
    // consecutive frames AND this many milliseconds before the screenshot is taken.
    public int settleFrames = 20;
    public int settleMillis = 1500;
    // 0 when missing from an old config file (Gson keeps the field initializer value for absent keys)
    public int configVersion = 0;

    private static final int CURRENT_CONFIG_VERSION = 2;

    /**
     * Upgrades configs written by older versions. Returns true if anything changed.
     */
    private boolean migrate() {
        if (configVersion >= CURRENT_CONFIG_VERSION) return false;
        // v1 used a 15 s default timeout, which is far too short for distant LODs at high resolution
        if (maxWaitSeconds <= 15) {
            maxWaitSeconds = 60;
        }
        if (settleFrames <= 0) settleFrames = 20;
        if (settleMillis < 0) settleMillis = 1500;
        configVersion = CURRENT_CONFIG_VERSION;
        LOGGER.info("Migrated VoxyShot config to version {}", CURRENT_CONFIG_VERSION);
        return true;
    }

    public int getTargetWidth() {
        return preset == ResolutionPreset.CUSTOM ? Math.max(320, customWidth) : preset.getWidth();
    }

    public int getTargetHeight() {
        return preset == ResolutionPreset.CUSTOM ? Math.max(240, customHeight) : preset.getHeight();
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            INSTANCE = new VoxyShotConfig();
            INSTANCE.configVersion = CURRENT_CONFIG_VERSION;
            INSTANCE.save();
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            VoxyShotConfig cfg = GSON.fromJson(reader, VoxyShotConfig.class);
            if (cfg != null) {
                INSTANCE = cfg;
                if (cfg.migrate()) {
                    cfg.save();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load VoxyShot config, using defaults", e);
            INSTANCE = new VoxyShotConfig();
        }
    }

    public void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save VoxyShot config", e);
        }
    }
}

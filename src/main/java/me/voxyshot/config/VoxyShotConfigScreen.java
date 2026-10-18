package me.voxyshot.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class VoxyShotConfigScreen extends Screen {
    private final Screen parent;
    private final VoxyShotConfig config;

    private EditBox customWidthBox;
    private EditBox customHeightBox;

    public VoxyShotConfigScreen(Screen parent) {
        super(Component.translatable("voxyshot.config.title"));
        this.parent = parent;
        this.config = VoxyShotConfig.INSTANCE;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 40;
        int spacing = 24;
        int widgetWidth = 220;

        // 1. Resolution Preset CycleButton
        this.addRenderableWidget(CycleButton.<VoxyShotConfig.ResolutionPreset>builder(
                preset -> Component.literal(preset.getLabel()),
                this.config.preset
        )
        .withValues(VoxyShotConfig.ResolutionPreset.values())
        .create(centerX - widgetWidth / 2, startY, widgetWidth, 20, Component.translatable("voxyshot.config.resolution"), (button, value) -> {
            this.config.preset = value;
            updateCustomBoxVisibility();
        }));

        // 2. Custom Resolution inputs (Width & Height)
        int halfWidth = (widgetWidth - 10) / 2;
        this.customWidthBox = new EditBox(this.font, centerX - widgetWidth / 2, startY + spacing, halfWidth, 20, Component.translatable("voxyshot.config.custom_width"));
        this.customWidthBox.setValue(String.valueOf(this.config.customWidth));
        this.customWidthBox.setResponder(val -> {
            try {
                this.config.customWidth = Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(this.customWidthBox);

        this.customHeightBox = new EditBox(this.font, centerX + 5, startY + spacing, halfWidth, 20, Component.translatable("voxyshot.config.custom_height"));
        this.customHeightBox.setValue(String.valueOf(this.config.customHeight));
        this.customHeightBox.setResponder(val -> {
            try {
                this.config.customHeight = Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(this.customHeightBox);

        updateCustomBoxVisibility();

        // 3. Voxy LOD Boost CycleButton
        List<Float> boostValues = Arrays.asList(1.0f, 1.5f, 2.0f, 3.0f, 4.0f);
        this.addRenderableWidget(CycleButton.<Float>builder(
                boost -> Component.literal(boost + "x"),
                this.config.voxyLodBoost
        )
        .withValues(boostValues)
        .create(centerX - widgetWidth / 2, startY + spacing * 2, widgetWidth, 20, Component.translatable("voxyshot.config.voxy_lod_boost"), (button, value) -> {
            this.config.voxyLodBoost = value;
        }));

        // 4. Override F2 Toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(this.config.overrideVanillaF2)
        .create(centerX - widgetWidth / 2, startY + spacing * 3, widgetWidth, 20, Component.translatable("voxyshot.config.override_f2"), (button, value) -> {
            this.config.overrideVanillaF2 = value;
        }));

        // 5. Hide HUD Toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(this.config.hideHudDuringCapture)
        .create(centerX - widgetWidth / 2, startY + spacing * 4, widgetWidth, 20, Component.translatable("voxyshot.config.hide_hud"), (button, value) -> {
            this.config.hideHudDuringCapture = value;
        }));

        // 6. Max LOD wait time
        List<Integer> waitValues = Arrays.asList(15, 30, 60, 120, 300);
        if (!waitValues.contains(this.config.maxWaitSeconds)) {
            this.config.maxWaitSeconds = 60;
        }
        this.addRenderableWidget(CycleButton.<Integer>builder(
                seconds -> Component.literal(seconds + "s"),
                this.config.maxWaitSeconds
        )
        .withValues(waitValues)
        .create(centerX - widgetWidth / 2, startY + spacing * 5, widgetWidth, 20, Component.translatable("voxyshot.config.max_wait_seconds"), (button, value) -> {
            this.config.maxWaitSeconds = value;
        }));

        // Done button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.config.save();
            this.onClose();
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    private void updateCustomBoxVisibility() {
        boolean custom = (this.config.preset == VoxyShotConfig.ResolutionPreset.CUSTOM);
        if (this.customWidthBox != null) this.customWidthBox.setVisible(custom);
        if (this.customHeightBox != null) this.customHeightBox.setVisible(custom);
    }

    @Override
    public void onClose() {
        this.config.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

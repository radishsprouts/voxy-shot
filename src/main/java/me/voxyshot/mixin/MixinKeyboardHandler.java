package me.voxyshot.mixin;

import me.voxyshot.VoxyShotClient;
import me.voxyshot.config.VoxyShotConfigScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void voxyshot$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && window == this.minecraft.getWindow().handle()) {
            if (VoxyShotClient.KEY_CONFIG.matches(event)) {
                // If config screen is already open, do not open duplicate
                if (!(this.minecraft.screen instanceof VoxyShotConfigScreen)) {
                    this.minecraft.setScreen(new VoxyShotConfigScreen(this.minecraft.screen));
                }
            }
        }
    }
}

package me.voxyshot.mixin;

import me.voxyshot.VoxyShotClient;
import me.voxyshot.capture.ScreenshotTaskController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void voxyshot$onRenderFrameHead(boolean renderLevel, CallbackInfo ci) {
        ScreenshotTaskController.INSTANCE.onRenderFrameHead();
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    // No descriptor: the signature differs between versions
                    // (26.1.2/26.2: render(DeltaTracker, boolean), 26.3: render())
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render",
                    shift = At.Shift.AFTER
            )
    )
    private void voxyshot$onRenderFrameTail(boolean renderLevel, CallbackInfo ci) {
        ScreenshotTaskController.INSTANCE.onRenderFrameTail();
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void voxyshot$onHandleKeybinds(CallbackInfo ci) {
        VoxyShotClient.handleKeyInput();
    }
}

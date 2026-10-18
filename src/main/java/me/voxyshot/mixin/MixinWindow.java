package me.voxyshot.mixin;

import com.mojang.blaze3d.platform.Window;
import me.voxyshot.capture.ScreenshotTaskController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {

    @Inject(method = "onFramebufferResize", at = @At("HEAD"), cancellable = true)
    private void voxyshot$protectCaptureResolution(long handle, int width, int height, CallbackInfo ci) {
        if (ScreenshotTaskController.INSTANCE.isCapturing()) {
            ci.cancel();
        }
    }
}

package me.voxyshot.mixin;

import com.mojang.blaze3d.platform.Window;
import me.voxyshot.capture.ScreenshotTaskController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {

    // Target arguments are omitted on purpose: the signature differs between versions
    // (26.1.2/26.2: (long, int, int), 26.3: (int, int))
    @Inject(method = "onFramebufferResize", at = @At("HEAD"), cancellable = true)
    private void voxyshot$protectCaptureResolution(CallbackInfo ci) {
        if (ScreenshotTaskController.INSTANCE.isCapturing()) {
            ci.cancel();
        }
    }
}

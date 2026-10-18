package me.voxyshot.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.voxyshot.capture.ScreenshotTaskController;
import me.voxyshot.config.VoxyShotConfig;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public class MixinScreenshot {

    @Inject(
            method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void voxyshot$interceptGrab(File gameDirectory, String customName, RenderTarget renderTarget, int downscale, Consumer<Component> messageConsumer, CallbackInfo ci) {
        ScreenshotTaskController controller = ScreenshotTaskController.INSTANCE;
        if (controller.isInternalGrab()) {
            // Internal call from VoxyShot - proceed normally to save file
            return;
        }

        if (VoxyShotConfig.INSTANCE.overrideVanillaF2) {
            // Cancel vanilla instant capture and initiate Voxy high-res capture sequence
            ci.cancel();
            controller.startCapture();
        }
    }
}

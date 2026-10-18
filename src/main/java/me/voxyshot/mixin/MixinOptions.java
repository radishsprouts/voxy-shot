package me.voxyshot.mixin;

import me.voxyshot.VoxyShotClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class MixinOptions {
    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "load", at = @At("HEAD"))
    private void voxyshot$registerKeyMappings(CallbackInfo ci) {
        this.keyMappings = ArrayUtils.addAll(
                this.keyMappings,
                VoxyShotClient.KEY_CAPTURE,
                VoxyShotClient.KEY_CONFIG
        );
    }
}

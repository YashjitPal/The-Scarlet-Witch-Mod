package com.yashjit.scarlet.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.yashjit.scarlet.client.hex.EraAudio;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every sound takes on the sound of the era the listener is in as it starts; a streamed one, music, keeps taking it
 * as it plays.
 */
@Mixin(Channel.class)
abstract class ChannelMixin {

    @Shadow
    @Final
    private int source;

    @Inject(method = "play", at = @At("HEAD"))
    private void scarlet$soundOfTheEra(CallbackInfo ci) {
        EraAudio.apply(source);
    }

    @Inject(method = "updateStream", at = @At("HEAD"))
    private void scarlet$soundOfTheEraStreaming(CallbackInfo ci) {
        EraAudio.apply(source);
    }
}

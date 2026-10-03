package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.magic.MindControlClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inside a mind you hold, your keys steer it and your own body stands still. Held yourself, your body moves the way
 * whoever holds you steers it, and your keys only fight them.
 */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void scarlet$mindControl(CallbackInfo ci) {
        if (MindControlClient.inside()) {
            MindControlClient.steer(keyPresses, moveVector);
            keyPresses = Input.EMPTY;
            moveVector = Vec2.ZERO;
        } else if (MindControlClient.held()) {
            keyPresses = MindControlClient.puppetKeys(keyPresses);
            moveVector = MindControlClient.puppetMove();
        }
    }
}

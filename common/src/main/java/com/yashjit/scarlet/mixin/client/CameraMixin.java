package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.fx.DreamFx;
import com.yashjit.scarlet.client.hex.Founding;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets founding a Hex take the view: circling the caster's home as it rises, and shuddering as the Hex bursts out. Sat
 * down to dreamwalk, the view sinks and rises with the body.
 */
@Mixin(Camera.class)
abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract Vec3 position();

    @Inject(method = "update", at = @At("TAIL"))
    private void scarlet$founding(DeltaTracker deltaTracker, CallbackInfo ci) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Founding.Shot shot = Founding.shot(partialTick);
        if (shot != null) {
            setRotation(shot.yaw(), shot.pitch());
            setPosition(shot.position());
        }
        float[] shake = Founding.shake(partialTick);
        if (shake[0] != 0.0F || shake[1] != 0.0F) {
            setRotation(yRot() + shake[0], xRot() + shake[1]);
        }
        float drop = DreamFx.eyeDrop(partialTick);
        if (drop != 0.0F) {
            setPosition(position().add(0.0, drop, 0.0));
        }
    }
}

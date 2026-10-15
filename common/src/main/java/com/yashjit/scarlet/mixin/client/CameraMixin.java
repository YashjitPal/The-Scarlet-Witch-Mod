package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.fx.DreamFx;
import com.yashjit.scarlet.client.hex.Ejections;
import com.yashjit.scarlet.client.hex.Founding;
import com.yashjit.scarlet.entity.ParkedCar;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets founding a Hex take the view: circling the caster's home as it rises, and shuddering as the Hex bursts out. Sat
 * down to dreamwalk, the view sinks and rises with the body. Riding in a car, the view from behind stands back far
 * enough to take it all in.
 */
@Mixin(Camera.class)
abstract class CameraMixin {

    /** How far behind someone in a car the view stands. */
    private static final float CAR_VIEW = 6.5F;

    @Shadow
    private Entity entity;

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

    @ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float scarlet$standBackFromCars(float distance) {
        return entity != null && entity.getVehicle() instanceof ParkedCar ? Math.max(distance, CAR_VIEW) : distance;
    }

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
        float[] thrown = Ejections.shake(partialTick);
        if (thrown[0] != 0.0F || thrown[1] != 0.0F) {
            setRotation(yRot() + thrown[0], xRot() + thrown[1]);
        }
        float drop = DreamFx.eyeDrop(partialTick) + Ejections.eyeDrop(partialTick);
        if (drop != 0.0F) {
            setPosition(position().add(0.0, drop, 0.0));
        }
    }
}

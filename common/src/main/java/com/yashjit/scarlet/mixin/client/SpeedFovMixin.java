package com.yashjit.scarlet.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.yashjit.scarlet.player.Speed;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
abstract class SpeedFovMixin {
    // Let the vanilla sprint widen the view. Only a small part of Tommy's extra acceleration reaches the camera.
    @ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double scarlet$easeSprintFov(double speed) {
        AttributeInstance movement = ((AbstractClientPlayer) (Object) this).getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier stride = movement == null ? null : movement.getModifier(Speed.STRIDE);
        if (stride == null) return speed;
        return speed / (1.0 + stride.amount()) * (1.0 + stride.amount() * 0.04);
    }
}

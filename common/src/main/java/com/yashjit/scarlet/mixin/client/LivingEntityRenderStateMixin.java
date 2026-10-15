package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.anim.ThrownRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
abstract class LivingEntityRenderStateMixin implements ThrownRenderState {

    @Unique
    private float scarlet$thrownTilt;

    @Unique
    private float scarlet$thrownRoll;

    @Unique
    private float scarlet$thrownFlail;

    @Unique
    private float scarlet$thrownLie;

    @Override
    public float scarlet$thrownTilt() {
        return scarlet$thrownTilt;
    }

    @Override
    public float scarlet$thrownRoll() {
        return scarlet$thrownRoll;
    }

    @Override
    public float scarlet$thrownFlail() {
        return scarlet$thrownFlail;
    }

    @Override
    public float scarlet$thrownLie() {
        return scarlet$thrownLie;
    }

    @Override
    public void scarlet$setThrown(float tilt, float roll, float flail, float lie) {
        scarlet$thrownTilt = tilt;
        scarlet$thrownRoll = roll;
        scarlet$thrownFlail = flail;
        scarlet$thrownLie = lie;
    }
}

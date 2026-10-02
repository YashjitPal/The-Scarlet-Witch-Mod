package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.anim.CastPoseState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
abstract class AvatarRenderStateMixin implements CastPoseState {

    @Unique
    private float scarlet$rightStrike;

    @Unique
    private float scarlet$leftStrike;

    @Unique
    private float scarlet$shield;

    @Unique
    private float scarlet$recoil;

    @Unique
    private float scarlet$levitate;

    @Unique
    private float scarlet$lean;

    @Unique
    private float scarlet$burst;

    @Override
    public float scarlet$burst() {
        return scarlet$burst;
    }

    @Override
    public float scarlet$rightStrike() {
        return scarlet$rightStrike;
    }

    @Override
    public float scarlet$leftStrike() {
        return scarlet$leftStrike;
    }

    @Override
    public float scarlet$shield() {
        return scarlet$shield;
    }

    @Override
    public float scarlet$recoil() {
        return scarlet$recoil;
    }

    @Override
    public float scarlet$levitate() {
        return scarlet$levitate;
    }

    @Override
    public float scarlet$lean() {
        return scarlet$lean;
    }

    @Override
    public void scarlet$set(float rightStrike, float leftStrike, float shield, float recoil, float levitate, float lean, float burst) {
        scarlet$rightStrike = rightStrike;
        scarlet$leftStrike = leftStrike;
        scarlet$shield = shield;
        scarlet$recoil = recoil;
        scarlet$levitate = levitate;
        scarlet$lean = lean;
        scarlet$burst = burst;
    }
}

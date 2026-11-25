package com.yashjit.scarlet.client.entity;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.entity.WanderingWitch;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class WanderingWitchRenderer extends MobRenderer<WanderingWitch,WanderingWitchRenderer.State,HumanoidModel<WanderingWitchRenderer.State>> {
    public static final class State extends HumanoidRenderState {
        Identifier robe;
        boolean casting;
    }

    public WanderingWitchRenderer(EntityRendererProvider.Context context) {
        super(context,new HumanoidModel<State>(context.bakeLayer(ModelLayers.PLAYER_SLIM)) {
            @Override public void setupAnim(State state) {
                super.setupAnim(state);
                if (state.casting) {
                    rightArm.xRot = -1.25F + 0.07F * Mth.sin(state.ageInTicks * 0.24F);
                    leftArm.xRot = -0.88F;
                    rightArm.yRot = -0.24F;
                    leftArm.yRot = 0.36F;
                    leftArm.zRot = -0.20F;
                }
            }
        },0.42F);
    }

    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return state.robe; }
    @Override public void extractRenderState(WanderingWitch witch, State state, float partial) {
        super.extractRenderState(witch,state,partial);
        int face = Math.floorMod(witch.getUUID().hashCode(),3);
        String name=(witch.gold() ? "gold_" : "teal_") + witch.tier() + (face==0 ? "" : "_" + face);
        state.robe=Scarlet.id("textures/entity/coven/" + name + ".png");
        state.casting=witch.casting();
        state.speedValue=1;
    }
}

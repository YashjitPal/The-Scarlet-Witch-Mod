package com.yashjit.scarlet.client.costume;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Posed exactly like the player. The head stays bare so the face and crown show, and the costume's outer layer is
 * always drawn regardless of the player's skin customization settings.
 */
public final class CostumeModel extends PlayerModel {

    public CostumeModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        this.head.visible = false;
        this.jacket.visible = true;
        this.leftSleeve.visible = true;
        this.rightSleeve.visible = true;
        this.leftPants.visible = true;
        this.rightPants.visible = true;
    }
}

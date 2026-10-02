package com.yashjit.scarlet.client.costume;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.platform.ClientPlatform;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;

/**
 * The costume is the player model again, slightly inflated so it sits over the skin and its outer layer, textured with
 * a costume painted in the skin layout. The cape is a longer version of the vanilla cape.
 */
public final class CostumeModels {

    public static final ModelLayerLocation WIDE = new ModelLayerLocation(Scarlet.id("costume"), "main");
    public static final ModelLayerLocation SLIM = new ModelLayerLocation(Scarlet.id("costume_slim"), "main");
    public static final ModelLayerLocation CAPE = new ModelLayerLocation(Scarlet.id("costume_cape"), "main");

    private static final CubeDeformation OVER_SKIN = new CubeDeformation(0.3F);

    private CostumeModels() {
    }

    public static void register(ClientPlatform platform) {
        platform.registerLayerDefinition(WIDE, () -> LayerDefinition.create(PlayerModel.createMesh(OVER_SKIN, false), 64, 64));
        platform.registerLayerDefinition(SLIM, () -> LayerDefinition.create(PlayerModel.createMesh(OVER_SKIN, true), 64, 64));
        platform.registerLayerDefinition(CAPE, CostumeModels::cape);
    }

    /**
     * Same skeleton and pivot as the vanilla cape so its swing animation applies, but reaching the ankles.
     */
    private static LayerDefinition cape() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition root = mesh.getRoot().clearRecursively();
        root.getChild("body").addOrReplaceChild("cape",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10.0F, 22.0F, 1.0F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, (float) Math.PI, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}

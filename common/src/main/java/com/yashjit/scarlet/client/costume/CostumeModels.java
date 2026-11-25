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
        platform.registerLayerDefinition(WIDE, () -> costume(false));
        platform.registerLayerDefinition(SLIM, () -> costume(true));
        platform.registerLayerDefinition(CAPE, CostumeModels::cape);
        AgathaLook.register(platform);
    }

    private static LayerDefinition costume(boolean slim) {
        MeshDefinition mesh = PlayerModel.createMesh(OVER_SKIN, slim);
        PartDefinition head = mesh.getRoot().getChild("head");
        // Three staggered, swept-back locks break up the silhouette above Tommy's goggles.
        for (int i = 0; i < 3; i++) {
            head.addOrReplaceChild("quiff_" + i, CubeListBuilder.create().texOffs(24, 0).addBox(-1.5F, -1.0F, -2, 3, 2, 5),
                    PartPose.offsetAndRotation(-2.3F + i * 2.15F, -8.5F - i * 0.28F, -0.65F + i * 0.6F,
                            0.14F + i * 0.12F, -0.3F, -0.12F));
        }
        return LayerDefinition.create(mesh, 64, 64);
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

package com.yashjit.scarlet.client.costume;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.platform.ClientPlatform;
import com.yashjit.scarlet.player.AgathaForm;
import java.util.EnumMap;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.util.Mth;

public final class AgathaLook extends PlayerModel {
    public interface Pose {
        boolean scarlet$agathaHair();
        void scarlet$setAgathaHair(boolean visible);
    }
    static final Identifier EMBROIDERY = Scarlet.id("textures/entity/costume/agatha_stitchlight.png");
    final Identifier texture;
    private final AgathaForm form;
    private final ModelPart leftTrain;
    private final ModelPart rightTrain;

    private AgathaLook(EntityModelSet models, boolean slim, AgathaForm form) {
        super(models.bakeLayer(location(form, slim)), slim);
        this.form = form;
        this.texture = Scarlet.id("textures/entity/costume/" + name(form, slim) + ".png");
        this.leftTrain = form == AgathaForm.FINALE ? body.getChild("train_1") : null;
        this.rightTrain = form == AgathaForm.FINALE ? body.getChild("train_-1") : null;
    }

    static void register(ClientPlatform platform) {
        for (AgathaForm form : AgathaForm.values()) {
            platform.registerLayerDefinition(location(form, false), () -> layer(form, false));
            platform.registerLayerDefinition(location(form, true), () -> layer(form, true));
        }
    }

    static EnumMap<AgathaForm, AgathaLook> bake(EntityModelSet models, boolean slim) {
        var looks = new EnumMap<AgathaForm, AgathaLook>(AgathaForm.class);
        for (AgathaForm form : AgathaForm.values()) looks.put(form, new AgathaLook(models, slim, form));
        return looks;
    }

    private static String name(AgathaForm form, boolean slim) {
        String base = form == AgathaForm.TRAVELING ? "agatha" : "agatha_" + form.getSerializedName();
        return base + (slim ? "_slim" : "");
    }

    private static ModelLayerLocation location(AgathaForm form, boolean slim) {
        return new ModelLayerLocation(Scarlet.id(name(form, slim)), "main");
    }

    public static void pinBrooch(AvatarRenderState state) {
        // Its visible model is on the collar. The underlying offhand stack still belongs to the player.
        if (state.mainArm == HumanoidArm.RIGHT) {
            state.leftHandItemState.clear();
            state.leftArmPose = ArmPose.EMPTY;
        } else {
            state.rightHandItemState.clear();
            state.rightArmPose = ArmPose.EMPTY;
        }
    }

    private static LayerDefinition layer(AgathaForm form, boolean slim) {
        MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.33F), slim);
        PartDefinition root = mesh.getRoot();
        // Keep the hat child for the hairline. Its transparent opening reveals the actual player face.
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        hair(head, form);
        for (int sign : new int[]{-1, 1}) {
            PartDefinition leg = root.getChild(sign < 0 ? "right_leg" : "left_leg");
            if (form == AgathaForm.TRAVELING) {
                leg.addOrReplaceChild("coat_back", CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, 0, 4, 8, 1),
                    PartPose.offsetAndRotation(sign * 0.18F, -0.4F, 2.25F, 0.12F, 0, -sign * 0.025F));
                leg.addOrReplaceChild("coat_side", CubeListBuilder.create().texOffs(10, 0).addBox(-0.5F, 0, -2.5F, 1, 7, 5),
                    PartPose.offsetAndRotation(sign * 2.24F, -0.35F, 0.65F, 0.09F, 0, -sign * 0.06F));
            } else if (form == AgathaForm.AGNES) {
                leg.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 77)
                                .addBox(-2.5F, 0, -2.5F, 5, 7, 5),
                        PartPose.offsetAndRotation(sign * 0.5F, -0.6F, 0, 0, 0, -sign * 0.09F));
            } else {
                root.getChild("body").addOrReplaceChild("train_" + sign,
                        CubeListBuilder.create().texOffs(22, 78).addBox(-2.5F, 0, -0.5F, 5, 12, 1),
                        PartPose.offsetAndRotation(sign * 2.15F, 11.0F, 3, 0.24F, sign * 0.14F, -sign * 0.10F));
                leg.addOrReplaceChild("overdress", CubeListBuilder.create().texOffs(0, 92).addBox(-2.5F, 0, -2.5F, 5, 10, 5),
                        PartPose.offsetAndRotation(sign * 0.44F, -0.55F, 0, 0, 0, -sign * 0.10F));
                var arm = root.getChild(sign < 0 ? "right_arm" : "left_arm");
                float center = slim ? 0.5F : 1;
                arm.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(37, 93)
                                .addBox(-2, 0, -2.5F, 4, 5, 5, new CubeDeformation(slim ? -0.12F : 0.22F, 0.2F, 0.10F)),
                        PartPose.offsetAndRotation(sign * center, -2.5F, 0, 0, 0, -sign * 0.09F));
                arm.addOrReplaceChild("hanging_sleeve", CubeListBuilder.create().texOffs(28, 94)
                                .addBox(-1.5F, 0, 0, 3, 8, 1), PartPose.offsetAndRotation(sign * center, 4.9F, 1.8F, 0.22F, sign * 0.14F, 0));
                root.getChild("body").addOrReplaceChild("standing_collar_" + sign,
                        CubeListBuilder.create().texOffs(40, 78).addBox(-1.5F, -3, -0.5F, 3, 4, 1),
                        PartPose.offsetAndRotation(sign * 2.8F, 1, 1.4F, -0.25F, sign * 0.75F, sign * 0.40F));
            }
        }
        if (form == AgathaForm.FINALE) {
            root.getChild("left_leg").addOrReplaceChild("sash", CubeListBuilder.create().texOffs(35, 108)
                            .addBox(-1.5F, 0, -0.5F, 3, 10, 1),
                    PartPose.offsetAndRotation(-0.5F, -0.55F, -2.82F, -0.04F, -0.12F, 0.08F));
        }
        root.getChild("body").addOrReplaceChild("cameo", CubeListBuilder.create().texOffs(22, 0)
                        .addBox(-1.5F, 0, -1, 3, 3, 1, new CubeDeformation(-0.44F, -0.23F, -0.22F)),
                PartPose.offsetAndRotation(1.35F, 2.12F, -2.64F, 0, 0, -0.10F));
        return LayerDefinition.create(mesh, 64, 128);
    }

    // Each lock has a broad root and a smaller return curl. Their different axes give the waves depth from the side.
    private static void hair(PartDefinition head, AgathaForm form) {
        head.addOrReplaceChild("hairline", CubeListBuilder.create().texOffs(0, 110)
                .addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.54F)), PartPose.ZERO);
        boolean set = form == AgathaForm.AGNES;
        boolean loose = form == AgathaForm.FINALE;
        if (set) {
            lock(head, "rolled_fringe", 1.85F, -8.25F, -2.92F, 0.05F, 0.16F, 1.38F, 1.06F, 1.25F);
            lock(head, "small_roll", -2.0F, -7.85F, -2.45F, 0.26F, -0.18F, 0.80F, 0.81F, 0.70F);
        } else {
            lock(head, "part", -1.8F, loose ? -8.9F : -8.17F, -2.8F, -0.20F, -0.16F, 0.82F, 1.08F, 0.82F);
            lock(head, "sweep", 0.7F, loose ? -9.2F : -8.25F, -2.65F, -0.14F, 0.28F, -1.05F, 1.05F, 1.10F);
        }
        for (int side : new int[]{-1, 1}) {
            if (set) {
                lock(head, "ear_roll_" + side, side * 4.03F, -3.2F, -0.75F, 0.48F,
                        side * 0.16F, -side * 0.63F, 0.97F, 0.72F);
                lock(head, "nape_roll_" + side, side * 2.6F, -2.1F, 3.55F, -0.36F,
                        side * 0.19F, side * 0.91F, 1.02F, 0.77F);
                continue;
            }
            float lift = side < 0 ? 0.65F : 0;
            PartDefinition temple = lock(head, "temple_" + side, side * 3.85F, -6.5F + lift, -1.9F,
                    -0.12F, side * 0.24F, -side * (loose ? 0.55F : 0.29F), 0.86F, 0.91F);
            PartDefinition turn = lock(temple, "return", 0, 2.9F, 0.1F,
                    0.28F, -side * 0.3F, side * 0.72F, 0.89F, 0.99F);
            lock(turn, "tip", 0, 2.9F, -0.15F, -0.40F, side * 0.18F, -side * 0.56F, 0.66F, loose ? 1.13F : 0.85F);
            PartDefinition back = lock(head, "back_" + side, side * 3.0F, -4.0F, 2.7F,
                    0.30F, side * 0.31F, -side * (loose ? 0.55F : 0.21F), 1.09F, 1.0F);
            PartDefinition wave = lock(back, "wave", 0.12F * side, 3.0F, 0,
                    -0.41F, -side * 0.15F, side * 0.59F, 0.90F, 1.18F);
            lock(wave, "tip", 0, 3, 0, 0.30F, 0, -side * 0.58F, 0.78F, loose ? 1.22F : 0.73F);
        }
        if (loose) {
            PartDefinition nape = lock(head, "nape", 0, -1.7F, 3.65F, 0.28F, 0.16F, 0.20F, 1.12F, 1.33F);
            lock(nape, "tip", 0, 3.0F, 0, -0.39F, 0, -0.48F, 0.86F, 1.09F);
            PartDefinition streak = head.addOrReplaceChild("silver_lock", CubeListBuilder.create().texOffs(14, 64)
                            .addBox(-1.5F, 0, -1.5F, 3, 4, 3),
                    new PartPose(-3.9F, -5.5F, -3.25F, -0.18F, -0.22F, 0.64F, 0.79F, 1.05F, 0.78F));
            PartDefinition cheek = lock(streak, "bend", 0, 3.35F, -0.2F, -0.36F, 0.2F, -1.0F, 0.87F, 1.18F);
            lock(cheek, "tip", 0, 3.45F, 0, 0.5F, -0.24F, 0.65F, 0.72F, 1.1F);
        }
    }

    private static PartDefinition lock(PartDefinition parent, String name, float x, float y, float z,
                                       float pitch, float yaw, float roll, float width, float length) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, 0, -1.5F, 3, 4, 3),
                new PartPose(x, y, z, pitch, yaw, roll, width, length, width));
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        head.visible = state.headEquipment.isEmpty() && ((Pose)state).scarlet$agathaHair();
        hat.visible = true;
        jacket.visible = rightSleeve.visible = leftSleeve.visible = rightPants.visible = leftPants.visible = true;
        if (form == AgathaForm.FINALE) {
            float wind = Mth.sin(state.ageInTicks * 0.075F) * 0.045F;
            float lift = Math.max(0, Math.max(leftLeg.xRot, rightLeg.xRot)) * 0.7F;
            leftTrain.xRot += lift + wind;
            rightTrain.xRot += lift - wind;
            head.getChild("back_-1").zRot += wind;
            head.getChild("back_1").zRot += wind;
        }
    }
}

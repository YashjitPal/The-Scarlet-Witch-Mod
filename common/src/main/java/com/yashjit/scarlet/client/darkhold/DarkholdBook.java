package com.yashjit.scarlet.client.darkhold;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.magic.MagicLayer;
import com.yashjit.scarlet.darkhold.DarkholdItem;
import com.yashjit.scarlet.registry.ScarletItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The Darkhold as it is read: it rises out of the reader's hand and floats open before them, as it does for the Scarlet
 * Witch, held there by the magic of their hands rather than in them. It bobs and sways a little, turns its pages by
 * itself, a page each time one is read, and its stone and runes burn. Shut again, it sinks back into the hand.
 *
 * <p>Drawn on the reader for everyone in the third person, and before the view in the first, among the hands.
 */
public final class DarkholdBook {

    private static final Identifier TEXTURE = Scarlet.id("textures/entity/darkhold.png");
    private static final Identifier GLOW = Scarlet.id("textures/entity/darkhold_glow.png");
    /** How far each cover swings open from shut, in radians. */
    private static final float OPEN = 1.2F;
    /** Where it floats in the third person, in the reader's model space (blocks; down and back are positive). */
    private static final float FLOAT_Y = 0.24F;
    private static final float FLOAT_Z = -0.66F;
    private static final float FLOAT_SCALE = 0.9F;
    /** And in the first, from the eyes: below the middle of the view. */
    private static final float VIEW_Y = -0.26F;
    private static final float VIEW_Z = -0.82F;
    private static final float VIEW_SCALE = 0.42F;

    private static final ItemStackRenderState CARRIED = new ItemStackRenderState();

    private static @Nullable Book book;
    private static @Nullable HumanoidArm carriedIn;

    private DarkholdBook() {
    }

    /**
     * Whether a reader's book is out of their hand right now, floating or on its way there or back.
     */
    public static boolean floating(Player player) {
        return PoseBlends.of(player).read > 0.01F;
    }

    /**
     * Whether {@code stack}, in {@code hand} of the player you are, is your Darkhold out of your hand: then the hand is
     * drawn empty.
     */
    public static boolean outOf(ItemStack stack, InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && stack.is(ScarletItems.DARKHOLD.get()) && floating(player) && hand(player) == hand;
    }

    /**
     * Whether {@code stack} is your Darkhold carried shut, in the first person: vanilla shows a held item on its own,
     * so the arm is drawn holding it, as it is held for everyone else to see.
     */
    public static boolean carried(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && stack.is(ScarletItems.DARKHOLD.get()) && !floating(player);
    }

    /**
     * Which of your arms is being drawn holding it shut in the first person, while it is.
     */
    public static void holdIn(@Nullable HumanoidArm arm) {
        carriedIn = arm;
    }

    /**
     * Called as one of your arms is drawn in the first person, in the pose it is drawn in: the tome in its hand, placed
     * just as it is in the hand in the third person.
     */
    public static void submitCarried(PlayerModel model, ModelPart arm, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        HumanoidArm holding = carriedIn;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (holding == null || player == null || arm != (holding == HumanoidArm.RIGHT ? model.rightArm : model.leftArm)) {
            return;
        }
        ItemStack stack = holding == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
        if (!stack.is(ScarletItems.DARKHOLD.get())) {
            return;
        }
        boolean left = holding == HumanoidArm.LEFT;
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        minecraft.getItemModelResolver().updateForTopItem(CARRIED, stack, context, player.level(), player, player.getId() + context.ordinal());
        poseStack.pushPose();
        arm.translateAndRotate(poseStack);
        poseStack.rotateDegrees(Axis.XP, -90.0F);
        poseStack.rotateDegrees(Axis.YP, 180.0F);
        poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        CARRIED.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * The hand the book is read from, or was.
     */
    private static InteractionHand hand(Player player) {
        if (player.isUsingItem()) {
            return player.getUsedItemHand();
        }
        return player.getMainHandItem().is(ScarletItems.DARKHOLD.get()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static HumanoidArm arm(Player player) {
        return hand(player) == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    /**
     * Where it floats in the world, for what rises off its pages. In the first person it is drawn among the hands rather
     * than in the world: this is out beyond it in the same direction, so what rises comes up from behind it.
     */
    public static Vec3 position(Player player) {
        if (seenFromInside(player)) {
            Vec3 look = player.getLookAngle();
            return player.getEyePosition().add(look.scale(-VIEW_Z * 2.0)).add(0.0, VIEW_Y * 2.0, 0.0);
        }
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        return player.position().add(0.0, (1.5 - FLOAT_Y) * player.getScale(), 0.0).add(forward.scale(-FLOAT_Z * player.getScale()));
    }

    /**
     * Whether this is you, reading in the first person.
     */
    public static boolean seenFromInside(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == player && minecraft.getCameraEntity() == player && minecraft.options.getCameraType().isFirstPerson();
    }

    /**
     * Before a reader in the third person, in the space of their model, {@code read} how far into reading they are.
     */
    public static void submitFloating(PoseStack poseStack, SubmitNodeCollector collector, int light, PlayerModel model, boolean slim, Player player,
                                      float read, float time, float partialTick) {
        if (read < 0.01F) {
            return;
        }
        float rise = smooth(read);
        boolean right = arm(player) == HumanoidArm.RIGHT;
        Vector3f palm = palm(model, right, slim);
        float bob = Mth.sin(time * 0.08F) * 0.022F;
        poseStack.pushPose();
        poseStack.translate(Mth.lerp(rise, palm.x, 0.0F), Mth.lerp(rise, palm.y, FLOAT_Y + bob), Mth.lerp(rise, palm.z, FLOAT_Z));
        poseStack.rotateDegrees(Axis.YP, Mth.sin(time * 0.045F) * 5.0F * rise);
        // its pages turned up toward the reader's eyes
        poseStack.rotateDegrees(Axis.XP, 46.0F * rise);
        poseStack.rotateDegrees(Axis.YP, -90.0F);
        float scale = Mth.lerp(rise, 0.38F, FLOAT_SCALE);
        poseStack.scale(scale, scale, scale);
        submit(poseStack, collector, light, state(player, time, rise, partialTick), rise);
        poseStack.popPose();
    }

    /**
     * Before your view in the first person, drawn among the hands: out of the hand at the bottom of the view to below
     * its middle.
     */
    public static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, int light, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float read = PoseBlends.of(player).read;
        if (read < 0.01F) {
            return;
        }
        float rise = smooth(read);
        float side = arm(player) == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float time = player.tickCount + partialTick;
        float bob = Mth.sin(time * 0.08F) * 0.012F;
        poseStack.pushPose();
        poseStack.translate(Mth.lerp(rise, side * 0.5F, 0.0F), Mth.lerp(rise, -0.8F, VIEW_Y + bob), Mth.lerp(rise, -0.7F, VIEW_Z));
        poseStack.rotateDegrees(Axis.YP, Mth.sin(time * 0.045F) * 3.0F * rise);
        poseStack.rotateDegrees(Axis.XP, -44.0F * rise);
        // the model is made upside down, as entity models are
        poseStack.rotateDegrees(Axis.YP, -90.0F);
        poseStack.rotateDegrees(Axis.XP, 180.0F);
        poseStack.scale(VIEW_SCALE, VIEW_SCALE, VIEW_SCALE);
        submit(poseStack, collector, light, state(player, time, rise, partialTick), rise);
        poseStack.popPose();
    }

    private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, Book.State state, float rise) {
        Book model = book();
        collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, -1, null, 0);
        // its stone and runes burn brighter as it opens
        float burn = Ease.clamp01((rise - 0.3F) / 0.7F);
        collector.submitModel(model, state, poseStack, RenderTypes.eyes(GLOW), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ARGB.white(0.35F + 0.65F * burn), null, 0);
    }

    /**
     * Opening once it has risen, and a page turned each time one is read; as it first opens, a couple of leaves
     * riffle over.
     */
    private static Book.State state(Player player, float time, float rise, float partialTick) {
        float open = smooth(Ease.clamp01((rise - 0.35F) / 0.65F));
        float openness = OPEN * open + 0.04F * Mth.sin(time * 0.06F) * open;
        if (!player.isUsingItem()) {
            return new Book.State(openness, 0.0F, 0.0F);
        }
        float ticks = player.getTicksUsingItem() + partialTick;
        float page = ticks % DarkholdItem.PAGE_TICKS;
        boolean opening = ticks < 20.0F;
        float first = opening ? turn(ticks, 5.0F, 10.0F) : turn(page, DarkholdItem.PAGE_TICKS - 14.0F, 14.0F);
        float second = opening ? turn(ticks, 8.0F, 9.0F) : turn(page, DarkholdItem.PAGE_TICKS - 10.0F, 10.0F);
        return new Book.State(openness, first, second);
    }

    /**
     * A leaf turning over from {@code start}, taking {@code length}, 0 to 1.
     */
    private static float turn(float t, float start, float length) {
        return smooth(Ease.clamp01((t - start) / length));
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * The palm of an arm, in the space of the model.
     */
    private static Vector3f palm(PlayerModel model, boolean right, boolean slim) {
        PoseStack local = new PoseStack();
        model.root().translateAndRotate(local);
        (right ? model.rightArm : model.leftArm).translateAndRotate(local);
        float axisX = (right ? -1.0F : 1.0F) * (slim ? 0.5F : 1.0F) / 16.0F;
        return local.last().pose().transformPosition(axisX, MagicLayer.PALM_Y, 0.0F, new Vector3f());
    }

    private static Book book() {
        Book model = book;
        if (model == null) {
            model = new Book(Book.layer().bakeRoot());
            book = model;
        }
        return model;
    }

    /**
     * The tome open, after the book on an enchanting table but heavier: stone covers hinged on a bronze spine, a thick
     * block of pages riding on each, and two single leaves to turn between them, which lie hidden in a block when they
     * are not turning.
     */
    static final class Book extends Model<Book.State> {

        private final ModelPart leftLid;
        private final ModelPart rightLid;
        private final ModelPart leftPages;
        private final ModelPart rightPages;
        private final ModelPart firstLeaf;
        private final ModelPart secondLeaf;

        Book(ModelPart root) {
            super(root, RenderTypes::entityCutout);
            this.leftLid = root.getChild("left_lid");
            this.rightLid = root.getChild("right_lid");
            this.leftPages = root.getChild("left_pages");
            this.rightPages = root.getChild("right_pages");
            this.firstLeaf = root.getChild("first_leaf");
            this.secondLeaf = root.getChild("second_leaf");
        }

        static LayerDefinition layer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("left_lid", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -5.5F, -0.5F, 7.0F, 11.0F, 1.0F),
                    PartPose.offset(0.0F, 0.0F, -2.5F));
            root.addOrReplaceChild("right_lid", CubeListBuilder.create().texOffs(16, 0).addBox(0.0F, -5.5F, -0.5F, 7.0F, 11.0F, 1.0F),
                    PartPose.offset(0.0F, 0.0F, 2.5F));
            root.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(32, 0).addBox(-3.0F, -5.5F, -1.0F, 6.0F, 11.0F, 1.0F),
                    PartPose.rotation(0.0F, (float) (Math.PI / 2), 0.0F));
            // a hair proud of the leaves at their faces, so a leaf at rest lies inside its block
            root.addOrReplaceChild("left_pages", CubeListBuilder.create().texOffs(0, 16).addBox(0.0F, -5.0F, -1.99F, 6.0F, 10.0F, 2.0F), PartPose.ZERO);
            root.addOrReplaceChild("right_pages", CubeListBuilder.create().texOffs(16, 16).addBox(0.0F, -5.0F, -0.01F, 6.0F, 10.0F, 2.0F), PartPose.ZERO);
            CubeListBuilder leaf = CubeListBuilder.create().texOffs(32, 16).addBox(0.0F, -5.0F, 0.0F, 6.0F, 10.0F, 0.005F);
            root.addOrReplaceChild("first_leaf", leaf, PartPose.ZERO);
            root.addOrReplaceChild("second_leaf", leaf, PartPose.ZERO);
            return LayerDefinition.create(mesh, 64, 32);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            float openness = state.openness();
            leftLid.yRot = (float) Math.PI + openness;
            rightLid.yRot = -openness;
            leftPages.yRot = openness;
            rightPages.yRot = -openness;
            firstLeaf.yRot = openness - openness * 2.0F * state.firstLeaf();
            secondLeaf.yRot = openness - openness * 2.0F * state.secondLeaf();
            // the pages ride out on the covers as they open
            float out = Mth.sin(openness) * 2.0F;
            leftPages.x = out;
            rightPages.x = out;
            firstLeaf.x = out;
            secondLeaf.x = out;
        }

        record State(float openness, float firstLeaf, float secondLeaf) {
        }
    }
}

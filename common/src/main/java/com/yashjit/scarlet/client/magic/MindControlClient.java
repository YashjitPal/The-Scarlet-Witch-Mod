package com.yashjit.scarlet.client.magic;

import com.yashjit.scarlet.client.fx.MindControlFx;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MindControl;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.ControlPayload;
import com.yashjit.scarlet.network.PossessPayload;
import com.yashjit.scarlet.network.PuppetPayload;
import com.yashjit.scarlet.network.StrugglePayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * Mind Control as this client knows it: whose minds are held and by whom, and what is still loyal.
 *
 * <p>For yourself: once your hold has taken, the view moves into what you hold and your controls steer it, the mouse
 * turning its head and your keys moving it, while your own body stands still. If someone holds you, your own body
 * moves the way they steer it, your mouse no longer turns your head, and every key you press fights them.
 */
public final class MindControlClient {

    private static final int NOTHING = -1;

    private static final Int2ObjectMap<Link> LINKS = new Int2ObjectOpenHashMap<>();
    private static final Int2DoubleMap LOYAL = new Int2DoubleOpenHashMap();

    private static @Nullable ClientLevel seenLevel;

    /** What your view has moved into, and where you look from inside it. */
    private static @Nullable Entity inside;
    private static float yaw;
    private static float pitch;
    private static boolean attackQueued;
    private static Input pressed = Input.EMPTY;
    private static Vec2 moved = Vec2.ZERO;
    /** When your view last moved in or came back, and which. */
    private static double movedAt = -1.0E9;
    private static boolean movedIn;

    /** Who holds you, since when, and how they steer you. */
    private static int heldBy = NOTHING;
    private static double heldSince;
    private static @Nullable ControlPayload puppet;
    private static int struggles;
    private static Input ownLast = Input.EMPTY;
    private static boolean attackWasDown;
    private static boolean useWasDown;

    private MindControlClient() {
    }

    public static void receive(PossessPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime();
        int caster = payload.casterId();
        switch (payload.stage()) {
            case PossessPayload.SEIZING, PossessPayload.INSIDE -> {
                Link link = LINKS.get(caster);
                if (link == null || link.targetId != payload.targetId()) {
                    link = new Link(payload.targetId(), now);
                    LINKS.put(caster, link);
                }
                if (payload.stage() == PossessPayload.INSIDE && link.insideAt < 0.0) {
                    link.insideAt = now;
                }
                LOYAL.remove(payload.targetId());
            }
            case PossessPayload.RELEASED -> {
                Link link = LINKS.remove(caster);
                if (link != null) {
                    MindControlFx.released(caster, link.targetId);
                }
            }
            case PossessPayload.LOYAL -> LOYAL.put(payload.targetId(), now);
            default -> {
            }
        }
        LocalPlayer player = minecraft.player;
        if (player != null && payload.targetId() == player.getId()) {
            if (payload.stage() == PossessPayload.RELEASED) {
                letGo();
            } else if (heldBy != caster) {
                heldBy = caster;
                heldSince = now;
                struggles = 0;
                ownLast = Input.EMPTY;
            }
        }
    }

    public static void receive(PuppetPayload payload) {
        if (heldBy != NOTHING) {
            puppet = payload.control();
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level != seenLevel) {
            seenLevel = level;
            LINKS.clear();
            LOYAL.clear();
            inside = null;
            letGo();
        }
        if (level == null || player == null) {
            return;
        }
        // a hold that ended without word reaching us, or a caster who left view
        LINKS.int2ObjectEntrySet().removeIf(entry -> !(level.getEntity(entry.getIntKey()) instanceof Player caster)
                || !Magic.state(caster).channeling(Spell.MIND_CONTROL));
        double now = level.getGameTime();
        LOYAL.int2DoubleEntrySet().removeIf(entry -> now - entry.getDoubleValue() > MindControl.LOYAL_TICKS || level.getEntity(entry.getIntKey()) == null);
        Link own = LINKS.get(player.getId());
        Entity target = own == null ? null : level.getEntity(own.targetId);
        if (inside != null) {
            if (own == null || own.insideAt < 0.0 || target != inside || !inside.isAlive()) {
                comeBack(minecraft, now);
            } else {
                int flags = (pressed.jump() ? ControlPayload.JUMP : 0) | (pressed.shift() ? ControlPayload.SNEAK : 0)
                        | (pressed.sprint() ? ControlPayload.SPRINT : 0) | (attackQueued ? ControlPayload.ATTACK : 0);
                attackQueued = false;
                Services.NETWORK.sendToServer(new ControlPayload(moved.y, moved.x, flags, yaw, pitch));
            }
        } else if (own != null && own.insideAt >= 0.0 && target != null && target.isAlive()) {
            moveIn(minecraft, target, now);
        }
        if (heldBy != NOTHING && !(level.getEntity(heldBy) instanceof Player)) {
            letGo();
        }
        // a fresh press of a mouse button fights them too; holding one down does not
        boolean attackDown = minecraft.options.keyAttack.isDown();
        boolean useDown = minecraft.options.keyUse.isDown();
        if (heldBy != NOTHING && (attackDown && !attackWasDown || useDown && !useWasDown)) {
            struggle();
        }
        attackWasDown = attackDown;
        useWasDown = useDown;
        if (puppet != null) {
            // your head turns the way theirs does
            player.setYRot(Mth.rotLerp(0.6F, player.getYRot(), puppet.yaw()));
            player.setXRot(Mth.lerp(0.6F, player.getXRot(), puppet.pitch()));
            player.setYHeadRot(player.getYRot());
        }
        MindControlFx.tick(minecraft, inside != null, heldBy != NOTHING);
    }

    private static void moveIn(Minecraft minecraft, Entity target, double now) {
        inside = target;
        yaw = target.getYHeadRot();
        pitch = target.getXRot();
        movedAt = now;
        movedIn = true;
        attackQueued = false;
        minecraft.setCameraEntity(target);
    }

    private static void comeBack(Minecraft minecraft, double now) {
        inside = null;
        movedAt = now;
        movedIn = false;
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
    }

    private static void letGo() {
        heldBy = NOTHING;
        puppet = null;
        struggles = 0;
    }

    // ---------------------------------------------------------------- steering what you hold

    /**
     * Whether your view is inside a mind you hold.
     */
    public static boolean inside() {
        return inside != null;
    }

    public static @Nullable Entity insideOf() {
        return inside;
    }

    /**
     * Whether this entity's head turns with your mouse, because you are looking out through it.
     */
    public static boolean looksThrough(Entity entity) {
        return inside == entity && entity.level().isClientSide();
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    /**
     * The mouse, while inside: turns the head of what you hold rather than your own.
     */
    public static void turn(double dx, double dy) {
        yaw += (float) dx * 0.15F;
        pitch = Mth.clamp(pitch + (float) dy * 0.15F, -90.0F, 90.0F);
    }

    /**
     * Your keys, while inside: kept to steer what you hold, and taken from your own body, which stands still.
     */
    public static void steer(Input keys, Vec2 move) {
        pressed = keys;
        moved = move;
    }

    /**
     * @return true if attacking was taken to strike with what you hold
     */
    public static boolean attack() {
        if (inside == null) {
            return false;
        }
        attackQueued = true;
        return true;
    }

    /**
     * Ticks since your view last moved in or came back; {@link #movedIn} tells which.
     */
    public static double sinceMoved(double now) {
        return now - movedAt;
    }

    public static boolean movedIn() {
        return movedIn;
    }

    // ---------------------------------------------------------------- being held

    /**
     * Whether someone holds your mind, so your own keys and mouse are not yours to use.
     */
    public static boolean held() {
        return heldBy != NOTHING;
    }

    public static double heldSince() {
        return heldSince;
    }

    public static int struggles() {
        return struggles;
    }

    /**
     * While held: each key you press fights them, and your body moves the way they steer it instead.
     */
    public static Input puppetKeys(Input own) {
        boolean fought = own.forward() && !ownLast.forward() || own.backward() && !ownLast.backward() || own.left() && !ownLast.left()
                || own.right() && !ownLast.right() || own.jump() && !ownLast.jump() || own.shift() && !ownLast.shift();
        ownLast = own;
        if (fought) {
            struggle();
        }
        ControlPayload control = puppet;
        if (control == null) {
            return Input.EMPTY;
        }
        return new Input(control.forward() > 0.1F, control.forward() < -0.1F, control.strafe() > 0.1F, control.strafe() < -0.1F,
                control.has(ControlPayload.JUMP), control.has(ControlPayload.SNEAK), control.has(ControlPayload.SPRINT));
    }

    public static Vec2 puppetMove() {
        ControlPayload control = puppet;
        return control == null ? Vec2.ZERO : new Vec2(control.strafe(), control.forward());
    }

    /**
     * A fresh press of one of your own keys while held, fighting them.
     */
    public static void struggle() {
        if (heldBy != NOTHING) {
            struggles++;
            Services.NETWORK.sendToServer(StrugglePayload.INSTANCE);
            MindControlFx.struggled();
        }
    }

    // ---------------------------------------------------------------- for the effects

    public static Int2ObjectMap<Link> links() {
        return LINKS;
    }

    /**
     * How loyal a creature still is to whoever let it go, 1 just after fading to 0 as its loyalty runs out.
     */
    public static float loyalty(Entity entity, double now) {
        if (!LOYAL.containsKey(entity.getId())) {
            return 0.0F;
        }
        float t = (float) ((now - LOYAL.get(entity.getId())) / MindControl.LOYAL_TICKS);
        return Math.clamp(1.0F - t, 0.0F, 1.0F);
    }

    public static Int2DoubleMap loyal() {
        return LOYAL;
    }

    public static final class Link {
        public final int targetId;
        public final double since;
        /** When the caster's view moved into it, or below 0 while the tendrils are still reaching in. */
        public double insideAt = -1.0;

        Link(int targetId, double since) {
            this.targetId = targetId;
            this.since = since;
        }
    }
}

package com.yashjit.scarlet.magic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.network.MagicEventPayload;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.CandleBlock;

/** Private, time-limited links. An invitation is never a link until the sleeper accepts. */
public final class DreamCircle {
    private record Thread(UUID partner, long until, boolean joined, boolean host) {}
    private static final Map<ServerPlayer, Thread> THREADS = new WeakHashMap<>();
    private DreamCircle() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wiccan")
                .then(Commands.literal("return").executes(c -> WiccanMagic.leaveRoad(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("dream")
                        .then(Commands.literal("accept").executes(c -> accept(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("leave").executes(c -> { leave(c.getSource().getPlayerOrException()); return 1; }))
                        .then(Commands.literal("share").executes(c -> share(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("say").then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(c -> say(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message")) ? 1 : 0)))));
    }

    static boolean invite(ServerPlayer host, long now) {
        if (THREADS.containsKey(host)) return WiccanMagic.tip(host, "busy");
        ServerPlayer sleeper = host.level().players().stream().filter(p -> p != host && p.isSleeping() && host.distanceToSqr(p) <= 64
                && !THREADS.containsKey(p)).min(Comparator.comparingDouble(p -> host.distanceToSqr(p))).orElse(null);
        if (sleeper == null) return WiccanMagic.tip(host, "sleeper");
        int flames = 0;
        for (BlockPos pos : BlockPos.betweenClosed(sleeper.blockPosition().offset(-3,-1,-3), sleeper.blockPosition().offset(3,1,3))) {
            var state = host.level().getBlockState(pos);
            if (state.is(BlockTags.CANDLES) && state.getValue(CandleBlock.LIT)) flames++;
        }
        if (flames < 4) return WiccanMagic.tip(host, "candles");
        link(host, sleeper, now + 400, false);
        sleeper.sendSystemMessage(Component.translatable("wiccan.invitation", host.getDisplayName()).withColor(0x9DDBFF));
        WiccanMagic.event(host, MagicEventPayload.PENTACLE, sleeper.position());
        WiccanMagic.tip(host, "invited");
        return true;
    }

    private static void link(ServerPlayer host, ServerPlayer guest, long until, boolean joined) {
        THREADS.put(host, new Thread(guest.getUUID(), until, joined, true));
        THREADS.put(guest, new Thread(host.getUUID(), until, joined, false));
    }

    private static ServerPlayer partner(ServerPlayer p, Thread t) {
        if (t == null || t.until <= p.level().getGameTime() || !p.isAlive()) return null;
        ServerPlayer other = p.level().getServer().getPlayerList().getPlayer(t.partner);
        Thread back = THREADS.get(other);
        if (other == null || back == null || !back.partner.equals(p.getUUID()) || !other.isAlive() || other.level() != p.level()) return null;
        ServerPlayer host = t.host ? p : other;
        return CrownItem.isWiccan(host) || com.yashjit.scarlet.player.AgathaForm.worn(host) ? other : null;
    }

    private static boolean accept(ServerPlayer guest) {
        Thread t = THREADS.get(guest);
        ServerPlayer host = partner(guest, t);
        if (host == null || t.host || t.joined || host.distanceToSqr(guest) > 64) return WiccanMagic.tip(guest, "no_link");
        link(host, guest, host.level().getGameTime() + 2400, true);
        for (ServerPlayer p : new ServerPlayer[]{host, guest}) p.sendSystemMessage(Component.translatable("wiccan.joined").withColor(0x9DDBFF));
        return true;
    }

    private static boolean say(ServerPlayer p, String message) {
        Thread t = THREADS.get(p);
        ServerPlayer other = partner(p, t);
        if (other == null || !t.joined) return WiccanMagic.tip(p, "no_link");
        if (message.isBlank() || message.length() > 256) return false;
        Component line = Component.translatable("wiccan.whisper", p.getDisplayName(), message).withColor(0xB7DEFF);
        p.sendSystemMessage(line);
        other.sendSystemMessage(line);
        return true;
    }

    private static boolean share(ServerPlayer p) {
        Thread t = THREADS.get(p);
        ServerPlayer other = partner(p, t);
        if (other == null || !t.joined) return WiccanMagic.tip(p, "no_link");
        var pack = Component.empty();
        for (var stack : p.getInventory().getNonEquipmentItems()) if (!stack.isEmpty()) {
            if (!pack.getSiblings().isEmpty()) pack.append(", ");
            pack.append(stack.getHoverName()).append(" x" + stack.getCount());
        }
        other.sendSystemMessage(Component.translatable("wiccan.share", p.getDisplayName(), pack).withColor(0xB7DEFF));
        WiccanMagic.tip(p, "shared");
        return true;
    }

    public static void leave(ServerPlayer p) {
        Thread t = THREADS.remove(p);
        if (t == null) return;
        ServerPlayer other = p.level().getServer().getPlayerList().getPlayer(t.partner);
        Thread back = THREADS.get(other);
        if (back != null && back.partner.equals(p.getUUID())) {
            THREADS.remove(other);
            WiccanMagic.tip(other, "ended");
        }
        WiccanMagic.tip(p, "ended");
    }

    static void tick(ServerPlayer p, long now) {
        Thread t = THREADS.get(p);
        if (t == null || now % 10 != 0) return;
        if (partner(p, t) == null) leave(p);
        else if (t.joined || !t.host) WiccanMagic.event(p, MagicEventPayload.PENTACLE, p.position());
    }
}

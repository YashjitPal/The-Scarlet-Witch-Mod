package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletDamageTypes;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import com.yashjit.scarlet.registry.ScarletSounds;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WiccanMagic {
    static final ResourceKey<Level> ROAD = ResourceKey.create(Registries.DIMENSION, Scarlet.id("witches_road"));
    private record Domain(GlobalPos center, long until) {}
    private static final Map<ServerPlayer, Domain> DOMAINS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> FATE = new WeakHashMap<>();

    private WiccanMagic() {}

    static boolean cast(ServerPlayer p, Spell spell, long now) {
        boolean divine = WiccanSpells.ascended(p);
        switch (spell) {
            case STARFIRE -> ray(p, divine ? 38 : 26, divine ? 10 : 5 + Mastery.rank(p) * 0.22F, false);
            case WEAVE -> {
                Vec3 forward = Vec3.directionFromRotation(0, p.getYRot());
                BlockPos pos = BlockPos.containing(p.position().add(forward.scale(2.5))).below();
                if (!p.level().isEmptyBlock(pos)) pos = pos.above();
                boolean placed = false;
                int reach = divine ? 3 : 1;
                for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++) {
                    BlockPos at = pos.offset(x, 0, z);
                    placed |= weave(p, at, divine ? 300 : 160);
                }
                if (!placed) return tip(p, "weave_empty");
                event(p, MagicEventPayload.WEAVE, Vec3.atCenterOf(pos));
            }
            case AEGIS -> {
                if (divine && p.isShiftKeyDown()) {
                    // Lattice ribs meet over the caster, with four gaps to walk through.
                    for (int x=-3; x<=3; x++) for (int z=-3; z<=3; z++) for (int y=0; y<=3; y++) {
                        int d=x*x+y*y+z*z;
                        if (d>=8 && d<=11 && (y>=2 || x!=0 && z!=0)) weave(p,p.blockPosition().offset(x,y,z),120);
                    }
                }
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, divine ? 240 : 120, divine ? 2 : 0));
                p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0));
                event(p, MagicEventPayload.WARD, p.position());
            }
            case SECOND_SIGHT -> {
                int count = 0;
                for (var e : p.level().getEntities(p, p.getBoundingBox().inflate(divine ? 38 : 22),
                        e -> e instanceof Mob || e instanceof LivingEntity living && CrownItem.isWearingCrown(living)
                                || e instanceof ItemEntity item && item.getItem().isEnchanted())) {
                    if (count++ >= 20) break;
                    Services.NETWORK.sendToPlayer(p, new MagicEventPayload(p.getId(), MagicEventPayload.SENSE, e.getBoundingBox().getCenter()));
                }
                p.sendOverlayMessage(Component.translatable("wiccan.sense", Math.min(20, count)).withColor(0xA2DEFF));
            }
            case TRANSMUTE -> {
                var hit = p.level().clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(12)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                BlockPos at = hit.getBlockPos();
                if (hit.getType() != HitResult.Type.BLOCK || !canEdit(p, at)) return tip(p, "transmute_target");
                if (divine && p.getOffhandItem().is(net.minecraft.world.item.Items.PINK_PETALS)) {
                    if (!rewrite(p, at)) return false;
                    break;
                }
                Block old = p.level().getBlockState(at).getBlock();
                if (old == WeaveBlock.BLOCK.get()) {
                    if (!p.level().removeBlock(at, false)) return tip(p, "transmute_target");
                    event(p, MagicEventPayload.UNWOVEN, Vec3.atCenterOf(at));
                    break;
                }
                List<Block> palette = List.of(Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE);
                int i = palette.indexOf(old);
                if (i < 0) return tip(p, "transmute_target");
                if (!p.level().setBlock(at, palette.get((i + 1) % palette.size()).defaultBlockState(), Block.UPDATE_ALL))
                    return tip(p, "transmute_target");
                event(p, MagicEventPayload.TRANSMUTED, Vec3.atCenterOf(at));
            }
            case GLAMOUR -> {
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, divine ? 180 : 100, 0, false, false, true));
                for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12), m -> m.getTarget() == p)) {
                    mob.setTarget(null);
                }
                event(p, MagicEventPayload.MIRROR, p.position());
            }
            case WAYGATE -> {
                ItemStack crown = p.getItemBySlot(EquipmentSlot.HEAD);
                GlobalPos point = crown.get(ScarletDataComponents.WAYPOINT.get());
                if (p.isShiftKeyDown() || point == null) {
                    if (!p.onGround()) return tip(p, "ground");
                    crown.set(ScarletDataComponents.WAYPOINT.get(), GlobalPos.of(p.level().dimension(), p.blockPosition()));
                    tip(p, "anchored");
                    event(p, MagicEventPayload.GATE, p.position());
                } else if (!travel(p, point)) return tip(p, "unsafe");
            }
            case DREAMBIND -> { if (!DreamCircle.invite(p, now)) return false; }
            case DEMIURGE -> event(p, MagicEventPayload.PENTACLE, p.position());
            case FATE -> {
                FATE.put(p, now + 120);
                p.addEffect(new MobEffectInstance(MobEffects.LUCK, 120, 2));
                event(p, MagicEventPayload.FATE, p.position());
            }
            case RUNIC_DOMAIN -> {
                DOMAINS.put(p, new Domain(GlobalPos.of(p.level().dimension(), p.blockPosition()), now + 200));
                event(p, MagicEventPayload.DOMAIN, p.position());
            }
            case WITCHES_ROAD -> {
                if (p.level().dimension() == ROAD) return leaveRoad(p);
                ServerLevel road = p.level().getServer().getLevel(ROAD);
                if (road == null) return tip(p, "road_missing");
                GlobalPos origin = GlobalPos.of(p.level().dimension(), p.blockPosition());
                if (!enterRoad(p, road)) return tip(p, "unsafe");
                Services.PLAYER_DATA.setDreamwalk(p, Services.PLAYER_DATA.dreamwalk(p).withRoadOrigin(java.util.Optional.of(origin)));
                tip(p, "road");
            }
            default -> { return false; }
        }
        if (spell != Spell.STARFIRE) p.level().playSound(null, p.blockPosition(), ScarletSounds.DARKHOLD_WHISPER.get(), SoundSource.PLAYERS, 0.38F, 1.65F);
        return true;
    }

    private static boolean enterRoad(ServerPlayer p, ServerLevel road) {
        // Sample inside one chunk; a never-visited dimension has no loaded heightmap yet.
        road.getChunk(0, 0);
        for (int x : new int[]{8,4,12,2,14}) for (int z : new int[]{8,4,12,2,14}) {
            BlockPos at = road.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x,0,z));
            if (travel(p, GlobalPos.of(ROAD,at))) return true;
        }
        BlockPos deck = road.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(8,0,8)).above();
        if (!road.getWorldBorder().isWithinBounds(deck) || !road.isInWorldBounds(deck.above(2))) return false;
        for (int x=-2; x<=2; x++) for (int z=-2; z<=2; z++) {
            BlockPos tile = deck.offset(x,0,z);
            if (road.isEmptyBlock(tile)) road.setBlock(tile, (Math.abs(x)+Math.abs(z)==3 ? Blocks.AMETHYST_BLOCK
                    : Blocks.POLISHED_DEEPSLATE).defaultBlockState(), Block.UPDATE_ALL);
        }
        return travel(p, GlobalPos.of(ROAD,deck.above()));
    }

    public static boolean leaveRoad(ServerPlayer p) {
        if (p.level().dimension() != ROAD) return tip(p, "not_road");
        var server = p.level().getServer();
        var memory = Services.PLAYER_DATA.dreamwalk(p);
        var origin = memory.roadOrigin().orElse(null);
        var spawn = server.getRespawnData();
        if ((origin == null || !travel(p, origin)) && !travel(p, GlobalPos.of(spawn.dimension(), spawn.pos()))) return tip(p, "unsafe");
        Services.PLAYER_DATA.setDreamwalk(p, Services.PLAYER_DATA.dreamwalk(p).withRoadOrigin(java.util.Optional.empty()));
        return true;
    }

    private static boolean travel(ServerPlayer p, GlobalPos point) {
        ServerLevel level = p.level().getServer().getLevel(point.dimension());
        if (level == null || !level.getWorldBorder().isWithinBounds(point.pos())) return false;
        level.getChunkAt(point.pos());
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, 4}) {
            BlockPos at = point.pos().above(dy);
            Vec3 to = Vec3.atBottomCenterOf(at);
            if (!level.isInWorldBounds(at) || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
                    || level.getBlockState(at.below()).is(Blocks.MAGMA_BLOCK) || level.getBlockState(at).is(net.minecraft.tags.BlockTags.FIRE)
                    || !level.getFluidState(at).isEmpty() || !level.getFluidState(at.above()).isEmpty()
                    || !level.noCollision(p, p.getBoundingBox().move(to.subtract(p.position())))) continue;
            event(p, MagicEventPayload.GATE, p.position());
            p.stopRiding();
            p.teleportTo(level, to.x, to.y, to.z, Set.of(), p.getYRot(), p.getXRot(), true);
            p.resetFallDistance();
            event(p, MagicEventPayload.GATE, p.position());
            return true;
        }
        return false;
    }

    private static boolean canEdit(ServerPlayer p, BlockPos at) {
        return p.mayBuild() && p.level().isInWorldBounds(at) && p.level().getWorldBorder().isWithinBounds(at)
                && p.level().mayInteract(p, at) && !p.blockActionRestricted(p.level(), at, p.gameMode.getGameModeForPlayer());
    }

    private static boolean weave(ServerPlayer p, BlockPos at, int life) {
        if (!canEdit(p, at) || !p.level().isEmptyBlock(at) || !p.level().getEntities(null, new AABB(at)).isEmpty()
                || !p.level().setBlock(at, WeaveBlock.BLOCK.get().defaultBlockState(), Block.UPDATE_ALL)) return false;
        p.level().scheduleTick(at, WeaveBlock.BLOCK.get(), life);
        return true;
    }

    private static boolean rewrite(ServerPlayer p, BlockPos at) {
        if (!com.yashjit.scarlet.config.ScarletServerConfig.get().wiccanBiomeRewrite) return tip(p, "rewrite_disabled");
        // Biomes are stored in 4x4x4 cells. Check every block whose biome may change.
        BlockPos low = new BlockPos((at.getX() & ~3)-4, at.getY() & ~3, (at.getZ() & ~3)-4);
        BlockPos high = low.offset(11, 3, 11);
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) if (!canEdit(p,pos)) return tip(p, "rewrite_denied");
        var cherry = p.level().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE);
        var result = net.minecraft.server.commands.FillBiomeCommand.fill(p.level(), low, high, cherry);
        if (result.left().orElse(0) == 0) return tip(p, "rewrite_denied");
        p.getOffhandItem().consume(1,p);
        event(p, MagicEventPayload.REWRITTEN, Vec3.atCenterOf(at));
        tip(p, "rewritten");
        return true;
    }

    static void beam(ServerPlayer p) {
        ray(p, WiccanSpells.ascended(p) ? 36 : 22, WiccanSpells.ascended(p) ? 7 : 3.5F, true);
    }

    private static void ray(ServerPlayer p, int range, float damage, boolean beam) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        var blockHit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = blockHit.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(p, eye, stop, new AABB(eye, stop).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != p && !p.isAlliedTo(e), eye.distanceToSqr(stop));
        if (hit != null) stop = hit.getLocation();
        Vec3 ward = AgathaMagic.intercept(p.level(), p, eye, stop);
        if (ward != null) stop = ward;
        else if (hit != null) {
            hit.getEntity().hurtServer(p.level(), ScarletDamageTypes.spell(p.level(), ScarletDamageTypes.CHAOS_BOLT, p), damage);
        }
        boolean contact = ward == null && (hit != null || blockHit.getType() == HitResult.Type.BLOCK);
        int kind = beam ? (contact ? MagicEventPayload.BEAM : MagicEventPayload.BEAM_MISS)
                : (contact ? MagicEventPayload.STAR : MagicEventPayload.STAR_MISS);
        event(p, kind, stop);
        if (!beam || p.tickCount % 12 < 4)
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, beam ? 1.6F : 1.95F);
    }

    static void tick(ServerPlayer p, long now) {
        DreamCircle.tick(p, now);
        if (!CrownItem.isWiccan(p) || !p.isAlive() || p.isSpectator()) {
            DOMAINS.remove(p);
            FATE.remove(p);
            return;
        }
        var state = Magic.state(p);
        if ((state.channeling(Spell.ASTRAL) || state.channeling(Spell.DREAMWALK)) && now % 10 == 0)
            event(p, MagicEventPayload.PENTACLE, p.position());
        if (WiccanSpells.ascended(p) && now % 8 == 0) event(p, MagicEventPayload.HALO, p.position());
        Long luck = FATE.get(p);
        if (luck != null) {
            if (now >= luck || !WiccanSpells.ascended(p)) FATE.remove(p);
            else for (Projectile arrow : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3),
                    e -> e.getOwner() != p && e.getDeltaMovement().dot(p.getEyePosition().subtract(e.position())) > 0)) {
                Vec3 away = arrow.position().subtract(p.position()).normalize();
                arrow.setDeltaMovement(away.scale(0.9).add(0, 0.35, 0));
                arrow.needsSync = true;
                event(p, MagicEventPayload.DEFLECT, arrow.position());
                Magic.spend(p, -2, now);
            }
        }
        Domain domain = DOMAINS.get(p);
        if (domain == null) return;
        if (now >= domain.until || !WiccanSpells.ascended(p) || domain.center.dimension() != p.level().dimension()) { DOMAINS.remove(p); return; }
        if (now % 12 == 0) {
            Vec3 center = Vec3.atBottomCenterOf(domain.center.pos());
            event(p, MagicEventPayload.DOMAIN, center);
            if (p.position().distanceToSqr(center) <= 49) p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 25, 1));
            for (Mob mob : p.level().getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(7),
                    mob -> mob instanceof net.minecraft.world.entity.monster.Enemy && mob.distanceToSqr(center) <= 49))
                mob.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 24, 0), p);
        }
    }

    static void event(ServerPlayer p, int kind, Vec3 at) {
        Services.NETWORK.sendToTrackingAndSelf(p, new MagicEventPayload(p.getId(), kind, at));
    }

    static boolean tip(ServerPlayer p, String key) {
        p.sendOverlayMessage(Component.translatable("wiccan." + key).withColor(0xA2DEFF));
        return false;
    }
}

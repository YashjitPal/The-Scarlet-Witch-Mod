package com.yashjit.scarlet.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Scarlet dust that sparkles off the hands while magic is used and while flying: a crisp speck that shrinks away
 * through vanilla's dust sprites. It glows at full brightness, so it shines at night too.
 */
public final class ChaosDust extends SingleQuadParticle {

    private static final int FRAMES = 8;
    private static final int[] COLORS = {0xF5122E, 0xF5122E, 0xF5122E, 0xE0143C, 0xFF5A70, 0xB40A24};

    private final TextureAtlasSprite[] frames;

    private ChaosDust(ClientLevel level, Vec3 position, Vec3 velocity, TextureAtlasSprite[] frames) {
        super(level, position.x, position.y, position.z, frames[0]);
        this.frames = frames;
        this.xd = velocity.x;
        this.yd = velocity.y;
        this.zd = velocity.z;
        this.friction = 0.88F;
        this.gravity = -0.012F;
        this.hasPhysics = false;
        this.lifetime = 9 + random.nextInt(13);
        this.quadSize = 0.03F + random.nextFloat() * 0.035F;
        int rgb = COLORS[random.nextInt(COLORS.length)];
        float brightness = 0.82F + random.nextFloat() * 0.18F;
        setColor(((rgb >> 16) & 0xFF) / 255.0F * brightness, ((rgb >> 8) & 0xFF) / 255.0F * brightness, (rgb & 0xFF) / 255.0F * brightness);
    }

    /**
     * Adds one speck. Respects the effects quality setting through {@link ScarletFx#density()} at the call site.
     */
    public static void spawn(Vec3 position, Vec3 velocity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        TextureAtlas atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
        TextureAtlasSprite[] frames = new TextureAtlasSprite[FRAMES];
        for (int i = 0; i < FRAMES; i++) {
            frames[i] = atlas.getSprite(Identifier.withDefaultNamespace("generic_" + (FRAMES - 1 - i)));
        }
        minecraft.particleEngine.add(new ChaosDust(minecraft.level, position, velocity, frames));
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSprite(frames[Math.min(FRAMES - 1, age * FRAMES / Math.max(1, lifetime))]);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        return quadSize * Math.clamp((age + partialTick) / lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return LightCoordsUtil.withBlock(super.getLightCoords(partialTick), 15);
    }
}

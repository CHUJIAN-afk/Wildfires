/*
 * Adapted from NTM: Space ParticleGasFlame.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires changes: Forge 1.20.1 particle atlas/provider API while preserving EntitySmokeFX's
 * seed motion, render scale, reverse smoke animation and HSB colour quantization.
 */
package first.wildfires.client.space;

import com.mojang.logging.LogUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/** Exact NTM reusable-pod gas flame lifetime, motion damping and yellow-to-red colour curve. */
final class ReturnCapsuleGasFlameParticle extends TextureSheetParticle {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean FIRST_CREATED = new AtomicBoolean();

    private final SpriteSet sprites;
    private float colorMod = 1.0F;

    private ReturnCapsuleGasFlameParticle(ClientLevel level, double x, double y, double z,
                                          double speedX, double speedY, double speedZ,
                                          SpriteSet sprites) {
        // Legacy EntitySmokeFX first creates EntityFX's small random seed velocity, scales it by
        // 0.1, then adds the requested exhaust vector. Supplying that vector to the modern base
        // constructor would normalize it together with the seed and change the plume completely.
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.xd = this.xd * 0.1D + speedX;
        this.yd = this.yd * 0.1D + speedY * 1.5D;
        this.zd = this.zd * 0.1D + speedZ;
        this.sprites = sprites;
        this.hasPhysics = false;
        this.friction = 0.96F;
        this.lifetime = 30 + random.nextInt(13);
        // NTM sets particleScale=6.5, while EntityFX rendering multiplies it by 0.1. The modern
        // SingleQuadParticle size is already in rendered blocks, so 0.65 is the exact port.
        this.quadSize = 0.65F;
        updateColour();
        this.colorMod = 0.8F + random.nextFloat() * 0.2F;
        setLegacySprite();
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        int previousAge = age++;
        // EntitySmokeFX compares the pre-increment age. It therefore renders age==maxAge once,
        // then marks the particle dead on the following update while still completing that update.
        if (previousAge >= lifetime) remove();
        double previousY = yd;
        // EntitySmokeFX adds 0.004 before moving. ParticleGasFlame then restores the pre-super Y
        // velocity, so the offset affects this frame's position but not the next frame's velocity.
        yd += 0.004D;
        move(xd, yd, zd);
        xd *= friction;
        yd *= friction;
        zd *= friction;
        yd = previousY;
        xd *= 0.75D;
        yd += 0.005D;
        zd *= 0.75D;
        updateColour();
        setLegacySprite();
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private void updateColour() {
        float time = lifetime == 0 ? 0.0F : age / (float) lifetime;
        Color colour = Color.getHSBColor(Math.max((60.0F - time * 100.0F) / 360.0F, 0.0F),
                1.0F - time * 0.25F, 1.0F - time * 0.5F);
        setColor(colour.getRed() / 255.0F * colorMod,
                colour.getGreen() / 255.0F * colorMod,
                colour.getBlue() / 255.0F * colorMod);
    }

    private void setLegacySprite() {
        // EntitySmokeFX: setParticleTextureIndex(7 - particleAge * 8 / particleMaxAge).
        int legacyIndex = Math.max(0, 7 - age * 8 / lifetime);
        setSprite(sprites.get(legacyIndex, 7));
    }

    static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double speedX, double speedY, double speedZ) {
            if (FIRST_CREATED.compareAndSet(false, true)) {
                LOGGER.info("[Wildfires return capsule/client] first NTM gas-flame particle created "
                                + "dimension={} position=({}, {}, {})",
                        level.dimension().location(), x, y, z);
            }
            return new ReturnCapsuleGasFlameParticle(level, x, y, z,
                    speedX, speedY, speedZ, sprites);
        }
    }
}

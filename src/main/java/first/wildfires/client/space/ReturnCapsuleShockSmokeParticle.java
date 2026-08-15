/*
 * Adapted from NTM: Space ParticleExSmoke and ExplosionLarge.spawnShock.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires changes: Forge 1.20.1 particle atlas/provider API and VertexConsumer submission while
 * preserving all six deterministic legacy smoke quads per shock particle.
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** NTM shock smoke lifetime, grey range, alpha decay and 0.76 three-axis damping. */
final class ReturnCapsuleShockSmokeParticle extends TextureSheetParticle {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean FIRST_CREATED = new AtomicBoolean();
    private static final double DAMPING = 0.7599999785423279D;
    private static final int QUAD_COUNT = 6;
    private static final AtomicInteger NEXT_RENDER_ID = new AtomicInteger();

    private final int renderId;

    private ReturnCapsuleShockSmokeParticle(ClientLevel level, double x, double y, double z,
                                            double speedX, double speedY, double speedZ,
                                            SpriteSet sprites) {
        // ParticleExSmoke uses EntityFX's position-only constructor, then ClientProxy assigns the
        // shock vector directly. Calling the modern velocity constructor would add vanilla random
        // motion and normalize away NTM's requested shock strength.
        super(level, x, y, z);
        this.xd = speedX;
        this.yd = speedY;
        this.zd = speedZ;
        this.renderId = NEXT_RENDER_ID.getAndIncrement();
        this.hasPhysics = true;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.lifetime = 100 + random.nextInt(40);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        // Exact ParticleExSmoke order: fade from the pre-increment age, increment/expire, damp,
        // then move. In particular its first simulated frame remains fully opaque.
        alpha = 1.0F - age / (float) lifetime;
        ++age;
        if (age == lifetime) remove();
        xd *= DAMPING;
        yd *= DAMPING;
        zd *= DAMPING;
        move(xd, yd, zd);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cameraPosition = camera.getPosition();
        float centreX = (float) (Mth.lerp((double) partialTick, xo, x) - cameraPosition.x());
        float centreY = (float) (Mth.lerp((double) partialTick, yo, y) - cameraPosition.y());
        float centreZ = (float) (Mth.lerp((double) partialTick, zo, z) - cameraPosition.z());

        Quaternionf rotation = camera.rotation();
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTick);

        // NTM reseeds from Entity#getEntityId every frame. A dedicated monotonically assigned
        // client render ID preserves the same property: different particles vary, while all six
        // quads of one particle remain bit-stable instead of crawling between frames.
        Random geometry = new Random(renderId);
        for (int index = 0; index < QUAD_COUNT; index++) {
            float grey = geometry.nextFloat() * 0.25F + 0.25F;
            float scale = geometry.nextFloat() + 0.5F;
            float xOffset = (float) ((geometry.nextGaussian() - 1.0D) * 0.75D);
            float yOffset = (float) ((geometry.nextGaussian() - 1.0D) * 0.75D);
            float zOffset = (float) ((geometry.nextGaussian() - 1.0D) * 0.75D);
            putQuad(buffer, centreX + xOffset, centreY + yOffset, centreZ + zOffset,
                    right, up, scale, grey, light, u0, u1, v0, v1);
        }
    }

    private void putQuad(VertexConsumer buffer, float x, float y, float z,
                         Vector3f right, Vector3f up, float scale, float grey, int light,
                         float u0, float u1, float v0, float v1) {
        float rx = right.x() * scale;
        float ry = right.y() * scale;
        float rz = right.z() * scale;
        float ux = up.x() * scale;
        float uy = up.y() * scale;
        float uz = up.z() * scale;
        vertex(buffer, x - rx - ux, y - ry - uy, z - rz - uz, u1, v1, grey, light);
        vertex(buffer, x - rx + ux, y - ry + uy, z - rz + uz, u1, v0, grey, light);
        vertex(buffer, x + rx + ux, y + ry + uy, z + rz + uz, u0, v0, grey, light);
        vertex(buffer, x + rx - ux, y + ry - uy, z + rz - uz, u0, v1, grey, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z,
                        float u, float v, float grey, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(grey, grey, grey, alpha).uv2(light).endVertex();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public boolean shouldCull() {
        // The six Gaussian-offset quads intentionally extend beyond the tiny motion collider.
        return false;
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
                LOGGER.info("[Wildfires return capsule/client] first NTM shock-smoke particle created "
                                + "dimension={} position=({}, {}, {}) velocity=({}, {}, {})",
                        level.dimension().location(), x, y, z, speedX, speedY, speedZ);
            }
            return new ReturnCapsuleShockSmokeParticle(level, x, y, z,
                    speedX, speedY, speedZ, sprites);
        }
    }
}

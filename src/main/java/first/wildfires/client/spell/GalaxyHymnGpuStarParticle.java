package first.wildfires.client.spell;

/*
 * ParticleEngine/additive render-batch structure adapted from ArcaneVortex
 * 0.6.8 FragmentParticle and FragmentParticleRenderTypes under the user's
 * project-specific visual authorization. Copyright ErChien. Wildfires uses
 * its own blue cross-star behavior and excludes all upstream attack logic.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Short-lived ParticleEngine sprite samples that form the homing projectile body on the GPU. */
public final class GalaxyHymnGpuStarParticle extends TextureSheetParticle {

    public static final int LIFETIME_TICKS = 3;

    private final float baseSize;
    private final float pulsePhase;

    private GalaxyHymnGpuStarParticle(ClientLevel level, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed,
                                      SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        lifetime = LIFETIME_TICKS;
        gravity = 0.0F;
        friction = 1.0F;
        hasPhysics = false;
        // The packet arguments describe the entity's real previous-to-current tick segment.
        // Keep the sample at its authoritative endpoint and let vanilla particle partial-tick
        // interpolation traverse that segment instead of predicting another free-flight step.
        xo = x - xSpeed;
        yo = y - ySpeed;
        zo = z - zSpeed;
        xd = 0.0D;
        yd = 0.0D;
        zd = 0.0D;
        baseSize = 0.72F + random.nextFloat() * 0.10F;
        pulsePhase = random.nextFloat() * Mth.TWO_PI;
        quadSize = baseSize;
        rCol = 0.18F;
        gCol = 0.64F;
        bCol = 1.0F;
        alpha = 0.96F;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = age / (float) lifetime;
        oRoll = roll;
        roll += 0.035F;
        quadSize = baseSize * (0.96F + 0.045F * Mth.sin(age * 0.55F + pulsePhase));
        alpha = 0.96F * (1.0F - Mth.clamp(life, 0.0F, 1.0F));
        if (alpha <= 0.01F) {
            remove();
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return GPU_ADDITIVE_RENDER_TYPE;
    }

    /** ArcaneVortex-style ParticleEngine batch: one textured GPU submission for all live stars. */
    private static final ParticleRenderType GPU_ADDITIVE_RENDER_TYPE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.enableCull();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        @Override
        public String toString() {
            return "WILDFIRES_GALAXY_HYMN_GPU_STAR";
        }
    };

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new GalaxyHymnGpuStarParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}

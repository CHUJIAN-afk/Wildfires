package first.wildfires.client.spell;

/*
 * Adapted from ArcaneVortex 0.6.8 StarlinkParticle and
 * StarlinkParticleRenderTypes under the user's project-specific authorization.
 * Copyright ErChien. Wildfires fixes this spell's particles and connection
 * quads to a deep-blue constellation palette and adds an unlinked spark mode.
 * ArcaneVortex attack-damage, tesseract, shockwave and black-hole code stay excluded.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Deep-blue adaptation of Sky Ripper's drifting, animated, dynamically linked Starlink particle. */
public final class GalaxyHymnStarlinkParticle extends TextureSheetParticle {

    public static final double LINK_RANGE = 6.0D;
    public static final double LINK_RANGE_SQ = LINK_RANGE * LINK_RANGE;
    public static final int MAX_CONNECTIONS_PER_PARTICLE = 3;
    public static final int CONNECTION_REFRESH_TICKS = 15;
    public static final int MIN_LIFETIME = 60;
    public static final int LIFETIME_VARIANTS = 40;
    public static final int IMPACT_STABLE_TICKS = 120;
    public static final int IMPACT_FADE_TICKS = 30;

    private static final List<GalaxyHymnStarlinkParticle> ACTIVE_PARTICLES = new CopyOnWriteArrayList<>();
    private static final Set<Long> GLOBAL_CONNECTIONS = Collections.synchronizedSet(new HashSet<>());
    private static long nextId;

    private final long particleId;
    private final List<GalaxyHymnStarlinkParticle> connectedParticles = new ArrayList<>();
    private final float flickerOffset;
    private final boolean linked;
    private final boolean impactConstellation;
    private float nodeSparkle;
    private int incomingConnectionCount;
    private boolean hasSearchedConnections;
    private int connectionSearchCooldown;

    private GalaxyHymnStarlinkParticle(ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       SpriteSet sprites, boolean linked, boolean impactConstellation) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        particleId = nextId++;
        flickerOffset = random.nextFloat() * Mth.TWO_PI;
        this.linked = linked;
        this.impactConstellation = impactConstellation;
        lifetime = impactConstellation ? IMPACT_STABLE_TICKS + IMPACT_FADE_TICKS
                : linked ? MIN_LIFETIME + random.nextInt(LIFETIME_VARIANTS) : 14 + random.nextInt(12);
        gravity = 0.0F;
        friction = linked ? 0.98F : 0.91F;
        double randomSpeed = linked ? 0.1D : 0.025D;
        xd = xSpeed + (random.nextDouble() - 0.5D) * randomSpeed;
        yd = ySpeed + (random.nextDouble() - 0.5D) * randomSpeed;
        zd = zSpeed + (random.nextDouble() - 0.5D) * randomSpeed;
        quadSize = linked ? 0.4F + random.nextFloat() * 0.4F
                : 0.10F + random.nextFloat() * 0.14F;
        updateBlueColor();
        alpha = linked ? 0.82F + random.nextFloat() * 0.18F : 0.92F;
        connectionSearchCooldown = 3 + random.nextInt(5);
        pickSprite(sprites);
        if (linked) {
            ACTIVE_PARTICLES.add(this);
        }
    }

    @Override
    public void tick() {
        super.tick();
        float lifeProgress = age / (float) lifetime;
        applySmoothLinearFadeOut(lifeProgress);
        updateBlueColor();
        quadSize *= linked ? 1.005F : 0.96F;
        oRoll = roll;
        roll += 0.01F * (random.nextFloat() - 0.5F);
        if (linked) {
            if (connectionSearchCooldown > 0) {
                connectionSearchCooldown--;
            } else if (!hasSearchedConnections) {
                searchAndEstablishConnections();
                hasSearchedConnections = true;
            }
            if (age % CONNECTION_REFRESH_TICKS == 0 && hasSearchedConnections) {
                refreshConnections();
            }
            cleanDeadConnections();
        }
        if (alpha <= 0.001F || quadSize <= 0.01F) {
            remove();
        }
    }

    private void updateBlueColor() {
        float wave = Mth.sin((age * 0.05F + flickerOffset) * 5.0F) * 0.4F + 0.6F;
        float narrowPulse = (float) Math.pow(Math.max(0.0F, wave), 20.0D);
        float slowPhase = (Mth.sin(age * 0.05F + flickerOffset)
                + Mth.sin(age * 0.005F)) * 0.25F + 0.5F;
        nodeSparkle = impactConstellation
                ? Mth.clamp(narrowPulse * (0.6F + slowPhase * 1.4F), 0.0F, 1.0F)
                : 0.5F + 0.5F * Mth.sin(age * 0.18F + flickerOffset);
        float flicker = impactConstellation ? 0.72F + nodeSparkle * 0.48F
                : 0.78F + 0.22F * nodeSparkle;
        rCol = Math.min(1.0F, (linked ? 0.12F : 0.22F) * flicker
                + (impactConstellation ? nodeSparkle * 0.34F : 0.0F));
        gCol = Math.min(1.0F, (linked ? 0.40F : 0.68F) * flicker
                + (impactConstellation ? nodeSparkle * 0.58F : 0.0F));
        bCol = Math.min(1.0F, flicker + 0.16F);
    }

    private void applySmoothLinearFadeOut(float lifeProgress) {
        if (impactConstellation) {
            alpha = age <= IMPACT_STABLE_TICKS ? 1.0F
                    : 1.0F * (1.0F - Mth.clamp(
                    (age - IMPACT_STABLE_TICKS) / (float) IMPACT_FADE_TICKS, 0.0F, 1.0F));
            return;
        }
        if (lifeProgress <= (linked ? 0.7F : 0.45F)) {
            alpha = linked ? 0.96F : 0.92F;
        } else {
            float fadeStart = linked ? 0.7F : 0.45F;
            float fadeProgress = Math.min((lifeProgress - fadeStart) / (1.0F - fadeStart), 1.0F);
            alpha = (linked ? 0.96F : 0.92F) * (1.0F - fadeProgress);
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    private void searchAndEstablishConnections() {
        if (connectedParticles.size() >= MAX_CONNECTIONS_PER_PARTICLE
                || getTotalConnectionCount() >= MAX_CONNECTIONS_PER_PARTICLE) {
            return;
        }
        List<CandidateEntry> candidates = new ArrayList<>();
        for (GalaxyHymnStarlinkParticle other : ACTIVE_PARTICLES) {
            double distanceSquared;
            if (other == this || !other.isAlive()
                    || (distanceSquared = distanceSquaredTo(other)) > LINK_RANGE_SQ
                    || isConnectedWith(other)
                    || other.getTotalConnectionCount() >= MAX_CONNECTIONS_PER_PARTICLE) {
                continue;
            }
            candidates.add(new CandidateEntry(other, distanceSquared));
        }
        if (candidates.isEmpty()) {
            return;
        }
        Collections.shuffle(candidates, new Random());
        candidates.sort(Comparator.comparingDouble(entry -> entry.distanceSquared));
        for (CandidateEntry entry : candidates) {
            if (connectedParticles.size() >= MAX_CONNECTIONS_PER_PARTICLE
                    || getTotalConnectionCount() >= MAX_CONNECTIONS_PER_PARTICLE) {
                break;
            }
            GalaxyHymnStarlinkParticle candidate = entry.particle;
            if (!candidate.isAlive()
                    || candidate.getTotalConnectionCount() >= MAX_CONNECTIONS_PER_PARTICLE
                    || isConnectedWith(candidate)) {
                continue;
            }
            connectedParticles.add(candidate);
            candidate.incomingConnectionCount++;
            GLOBAL_CONNECTIONS.add(encodeConnectionPair(this, candidate));
        }
    }

    private void refreshConnections() {
        Iterator<GalaxyHymnStarlinkParticle> iterator = connectedParticles.iterator();
        while (iterator.hasNext()) {
            GalaxyHymnStarlinkParticle other = iterator.next();
            if (other.isAlive() && distanceSquaredTo(other) <= LINK_RANGE_SQ) {
                continue;
            }
            GLOBAL_CONNECTIONS.remove(encodeConnectionPair(this, other));
            if (other.isAlive()) {
                other.incomingConnectionCount = Math.max(0, other.incomingConnectionCount - 1);
            }
            iterator.remove();
        }
        if (connectedParticles.size() < MAX_CONNECTIONS_PER_PARTICLE
                && getTotalConnectionCount() < MAX_CONNECTIONS_PER_PARTICLE) {
            hasSearchedConnections = false;
            searchAndEstablishConnections();
            hasSearchedConnections = true;
        }
    }

    private void cleanDeadConnections() {
        Iterator<GalaxyHymnStarlinkParticle> iterator = connectedParticles.iterator();
        while (iterator.hasNext()) {
            GalaxyHymnStarlinkParticle other = iterator.next();
            if (other.isAlive()) {
                continue;
            }
            GLOBAL_CONNECTIONS.remove(encodeConnectionPair(this, other));
            iterator.remove();
        }
    }

    @Override
    public void remove() {
        if (!isAlive()) {
            return;
        }
        if (!linked) {
            super.remove();
            return;
        }
        for (GalaxyHymnStarlinkParticle other : new ArrayList<>(connectedParticles)) {
            GLOBAL_CONNECTIONS.remove(encodeConnectionPair(this, other));
            if (other.isAlive()) {
                other.incomingConnectionCount = Math.max(0, other.incomingConnectionCount - 1);
            }
        }
        connectedParticles.clear();
        for (GalaxyHymnStarlinkParticle other : ACTIVE_PARTICLES) {
            if (other == this) {
                continue;
            }
            Iterator<GalaxyHymnStarlinkParticle> iterator = other.connectedParticles.iterator();
            while (iterator.hasNext()) {
                if (iterator.next() != this) {
                    continue;
                }
                GLOBAL_CONNECTIONS.remove(encodeConnectionPair(other, this));
                incomingConnectionCount = Math.max(0, incomingConnectionCount - 1);
                iterator.remove();
            }
        }
        ACTIVE_PARTICLES.remove(this);
        super.remove();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return STARLINK_RENDER_TYPE;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float pulseScale = impactConstellation ? 1.0F + nodeSparkle * 0.38F : 1.0F;
        return super.getQuadSize(partialTick) * pulseScale;
    }

    private boolean isConnectedWith(GalaxyHymnStarlinkParticle other) {
        return GLOBAL_CONNECTIONS.contains(encodeConnectionPair(this, other));
    }

    private int getTotalConnectionCount() {
        return connectedParticles.size() + incomingConnectionCount;
    }

    private double distanceSquaredTo(GalaxyHymnStarlinkParticle other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private Vec3 interpolatedPosition(float partialTick) {
        return new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y),
                Mth.lerp(partialTick, zo, z));
    }

    private static long encodeConnectionPair(GalaxyHymnStarlinkParticle first,
                                             GalaxyHymnStarlinkParticle second) {
        return encodeConnectionPair(first.particleId, second.particleId);
    }

    private static long encodeConnectionPair(long first, long second) {
        long small = Math.min(first, second);
        long large = Math.max(first, second);
        return small << 32 | large & 0xFFFF_FFFFL;
    }

    private record CandidateEntry(GalaxyHymnStarlinkParticle particle, double distanceSquared) {
    }

    /** Authorized sprite/connection pass with Wildfires' fixed deep-blue palette. */
    private static final ParticleRenderType STARLINK_RENDER_TYPE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            renderConnectionLines(tesselator);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        private void renderConnectionLines(Tesselator tesselator) {
            if (ACTIVE_PARTICLES.isEmpty()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            Camera camera = minecraft.gameRenderer.getMainCamera();
            Vec3 cameraPosition = camera.getPosition();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float partialTick = minecraft.getFrameTime();
            Set<Long> renderedConnections = new HashSet<>();
            for (GalaxyHymnStarlinkParticle particle : ACTIVE_PARTICLES) {
                if (!particle.isAlive() || particle.connectedParticles.isEmpty()) {
                    continue;
                }
                Vec3 firstPosition = particle.interpolatedPosition(partialTick);
                for (GalaxyHymnStarlinkParticle target : particle.connectedParticles) {
                    long pairKey = encodeConnectionPair(particle.particleId, target.particleId);
                    if (!target.isAlive() || renderedConnections.contains(pairKey)) {
                        continue;
                    }
                    renderedConnections.add(pairKey);
                    Vec3 secondPosition = target.interpolatedPosition(partialTick);
                    double distance = firstPosition.distanceTo(secondPosition);
                    float distanceFactor = Mth.clamp(1.0F - (float) (distance / 5.0D), 0.28F, 1.0F);
                    float baseAlpha = Math.min(particle.alpha, target.alpha);
                    float lineAlpha = Math.min(baseAlpha * distanceFactor * 1.85F, 1.0F);
                    if (lineAlpha <= 0.01F) {
                        continue;
                    }
                    float x1 = (float) (firstPosition.x - cameraPosition.x);
                    float y1 = (float) (firstPosition.y - cameraPosition.y);
                    float z1 = (float) (firstPosition.z - cameraPosition.z);
                    float x2 = (float) (secondPosition.x - cameraPosition.x);
                    float y2 = (float) (secondPosition.y - cameraPosition.y);
                    float z2 = (float) (secondPosition.z - cameraPosition.z);
                    Vector3f lineDirection = new Vector3f(x2 - x1, y2 - y1, z2 - z1);
                    Vector3f toCamera = new Vector3f(-x1, -y1, -z1);
                    Vector3f cross = new Vector3f(lineDirection).cross(toCamera);
                    if (cross.lengthSquared() < 1.0E-4F) {
                        continue;
                    }
                    float lineWidth = 0.06F * distanceFactor + 0.015F;
                    cross.normalize().mul(lineWidth);
                    float secondAlpha = lineAlpha * 0.85F;
                    builder.vertex(x1 + cross.x(), y1 + cross.y(), z1 + cross.z())
                            .color(0.08F, 0.34F, 1.0F, lineAlpha).endVertex();
                    builder.vertex(x1 - cross.x(), y1 - cross.y(), z1 - cross.z())
                            .color(0.04F, 0.18F, 0.75F, lineAlpha).endVertex();
                    builder.vertex(x2 - cross.x(), y2 - cross.y(), z2 - cross.z())
                            .color(0.05F, 0.26F, 0.88F, secondAlpha).endVertex();
                    builder.vertex(x2 + cross.x(), y2 + cross.y(), z2 + cross.z())
                            .color(0.12F, 0.46F, 1.0F, secondAlpha).endVertex();
                }
            }
            tesselator.end();
            RenderSystem.enableCull();
        }

        @Override
        public String toString() {
            return "WILDFIRES_GALAXY_HYMN_STARLINK";
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
            return new GalaxyHymnStarlinkParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites, true, false);
        }
    }

    public static final class ImpactProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ImpactProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new GalaxyHymnStarlinkParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites, true, true);
        }
    }

    public static final class SparkProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SparkProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new GalaxyHymnStarlinkParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites, false, false);
        }
    }
}

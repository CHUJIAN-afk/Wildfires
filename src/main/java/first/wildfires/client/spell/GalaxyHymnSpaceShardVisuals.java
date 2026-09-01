package first.wildfires.client.spell;

/*
 * Star-space face rendering is adapted from ArcaneVortex 0.6.8 star_sky and
 * its volume field under the user's project-specific visual authorization.
 * Shard motion, planar deformation, collapse and blue starlances are Wildfires additions.
 */
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnSpaceShardMath;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnStarlinkSpawner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Client-local tumbling planar windows emitted by the Galaxy Hymn core burst. */
public final class GalaxyHymnSpaceShardVisuals {

    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final double OUTLINE_BASE_WIDTH = 0.014D;
    private static final double OUTLINE_SCALE_WIDTH = 0.018D;
    private static ClientLevel activeLevel;
    private static Vec3 impactCenter;
    private static long startGameTime;
    private static List<Shard> shards = List.of();
    private static final Set<Integer> emittedCollapseBursts = new HashSet<>();

    private GalaxyHymnSpaceShardVisuals() {
    }

    public static void trigger(ClientLevel level, Vec3 center, int seed) {
        activeLevel = level;
        impactCenter = center;
        startGameTime = level.getGameTime();
        shards = createShards(seed);
        emittedCollapseBursts.clear();
    }

    public static boolean isActive(ClientLevel level, float partialTick) {
        return activeLevel == level && impactCenter != null && !shards.isEmpty()
                && age(partialTick) < GalaxyHymnSpaceShardMath.TOTAL_DURATION_TICKS;
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || minecraft.level == null || !isActive(minecraft.level, event.getPartialTick())) {
            return;
        }
        float age = age(event.getPartialTick());
        emitCollapseBursts(minecraft.level, age);
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        List<RenderedShard> visible = new ArrayList<>(shards.size());
        for (Shard shard : shards) {
            RenderedShard rendered = renderedShard(shard, age, cameraPosition);
            if (rendered != null) {
                visible.add(rendered);
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        try {
            // LevelRenderer dispatches AFTER_PARTICLES before it copies the level PoseStack into
            // RenderSystem's global ModelView stack. These vertices are camera-relative world
            // positions, so make that world -> view rotation explicit for every shard pass.
            modelViewStack.setIdentity();
            modelViewStack.mulPoseMatrix(event.getPoseStack().last().pose());
            RenderSystem.applyModelViewMatrix();

            renderSpaceFaces(visible, camera);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            renderEdgesAndStarlances(visible, age, camera);
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    public static void reset() {
        activeLevel = null;
        impactCenter = null;
        startGameTime = 0L;
        shards = List.of();
        emittedCollapseBursts.clear();
    }

    private static void renderSpaceFaces(List<RenderedShard> visible, Camera camera) {
        if (visible.isEmpty()) {
            return;
        }
        Supplier<ShaderInstance> shader = GalaxyHymnSpaceWindowShader.prepare(camera);
        if (shader == null) {
            return;
        }
        RenderSystem.setShader(shader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        for (RenderedShard shard : visible) {
            Vec3[] points = shard.points();
            for (int index = 1; index < points.length - 1; index++) {
                vertex(builder, points[0]);
                vertex(builder, points[index]);
                vertex(builder, points[index + 1]);
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void renderEdgesAndStarlances(List<RenderedShard> visible, float age, Camera camera) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (RenderedShard rendered : visible) {
            addOutline(builder, rendered);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void emitCollapseBursts(ClientLevel level, float age) {
        for (int index = 0; index < shards.size(); index++) {
            Shard shard = shards.get(index);
            float burstAge = shard.collapseStart() + GalaxyHymnSpaceShardMath.COLLAPSE_DURATION_TICKS;
            if (age < burstAge || !emittedCollapseBursts.add(index)) {
                continue;
            }
            Vec3 collapseCenter = shardWorldCenter(shard, burstAge);
            // Exactly the same ParticleEngine blue-mote burst used by block/entity impacts.
            GalaxyHymnStarlinkSpawner.spawnLocalHitSparks(level, collapseCenter, shard.sparkSeed());
        }
    }

    private static void addOutline(BufferBuilder builder, RenderedShard shard) {
        Vec3[] points = shard.points();
        float alpha = 0.42F + 0.58F * shard.scale();
        double width = OUTLINE_BASE_WIDTH + shard.scale() * OUTLINE_SCALE_WIDTH;
        for (int index = 0; index < points.length; index++) {
            Vec3 first = points[index];
            Vec3 second = points[(index + 1) % points.length];
            Vec3 edge = second.subtract(first);
            if (edge.lengthSqr() < 1.0E-8D) {
                continue;
            }
            Vec3 offset = shard.normal().cross(edge).normalize().scale(width);
            colorVertex(builder, first.add(offset), 0.03F, 0.24F, 1.0F, alpha);
            colorVertex(builder, first.subtract(offset), 0.01F, 0.07F, 0.66F, alpha * 0.88F);
            colorVertex(builder, second.subtract(offset), 0.02F, 0.12F, 0.82F, alpha * 0.88F);
            colorVertex(builder, second.add(offset), 0.06F, 0.36F, 1.0F, alpha);
        }
    }

    private static RenderedShard renderedShard(Shard shard, float age, Vec3 cameraPosition) {
        float collapse = GalaxyHymnSpaceShardMath.collapseScale(age, shard.collapseStart());
        float scale = GalaxyHymnSpaceShardMath.growthScale(age, shard.travelTicks()) * collapse;
        if (scale <= 0.001F) {
            return null;
        }
        Vec3 center = shardWorldCenter(shard, age).subtract(cameraPosition);
        float travelPhase = Mth.clamp(age / shard.travelTicks(), 0.0F, 1.0F);
        float easedRoll = 1.0F - (1.0F - travelPhase) * (1.0F - travelPhase)
                * (1.0F - travelPhase);
        double residualMotionTicks = GalaxyHymnSpaceShardMath.postTravelMotionTicks(
                age, shard.travelTicks());
        float angle = shard.tumbleRadians() * easedRoll
                + (float) (residualMotionTicks * shard.residualSpinRadiansPerTick());
        Quaternionf rotation = new Quaternionf().rotationXYZ(shard.initialPitch(), shard.initialYaw(),
                shard.initialRoll()).rotateAxis(angle, shard.tumbleAxis().x(), shard.tumbleAxis().y(),
                shard.tumbleAxis().z());
        Vector3f normalVector = rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        Vec3 normal = new Vec3(normalVector.x(), normalVector.y(), normalVector.z()).normalize();
        Vec3[] points = new Vec3[shard.vertexCount()];
        for (int index = 0; index < points.length; index++) {
            float morph = age * shard.morphSpeed() + shard.morphPhase() + index * 1.713F;
            float angleOffset = shard.angleOffsets()[index] + Mth.sin(morph * 0.73F) * 0.065F;
            float localAngle = TWO_PI * index / points.length + angleOffset;
            float radius = shard.radius() * scale * (1.0F + shard.radiusOffsets()[index]
                    + Mth.sin(morph) * 0.105F);
            Vector3f local = new Vector3f(Mth.cos(localAngle) * radius,
                    Mth.sin(localAngle) * radius, 0.0F);
            rotation.transform(local);
            points[index] = center.add(local.x(), local.y(), local.z());
        }
        return new RenderedShard(points, normal, scale);
    }

    private static Vec3 shardWorldCenter(Shard shard, float age) {
        double explosiveDistance = GalaxyHymnSpaceShardMath.travelDistance(
                shard.stopDistance(), age, shard.travelTicks());
        double residualDistance = GalaxyHymnSpaceShardMath.postTravelMotionTicks(
                age, shard.travelTicks()) * shard.residualDriftPerTick();
        return impactCenter.add(shard.direction().scale(explosiveDistance + residualDistance));
    }

    private static List<Shard> createShards(int seed) {
        Random random = new Random(seed ^ 0x5A17C0DE);
        List<Shard> result = new ArrayList<>(GalaxyHymnSpaceShardMath.SHARD_COUNT);
        for (int index = 0; index < GalaxyHymnSpaceShardMath.SHARD_COUNT; index++) {
            int vertexCount = random.nextBoolean() ? 3 : 4;
            float[] angleOffsets = new float[vertexCount];
            float[] radiusOffsets = new float[vertexCount];
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                angleOffsets[vertex] = (random.nextFloat() - 0.5F) * 0.24F;
                radiusOffsets[vertex] = (random.nextFloat() - 0.5F) * 0.34F;
            }
            Vector3f tumbleAxis = randomUnitVector(random);
            Vec3 direction = toVec3(randomUnitVector(random));
            int collapseStart = GalaxyHymnSpaceShardMath.COLLAPSE_START_TICKS
                    + random.nextInt(GalaxyHymnSpaceShardMath.COLLAPSE_STAGGER_TICKS + 1);
            int travelTicks = GalaxyHymnSpaceShardMath.MIN_TRAVEL_TICKS
                    + random.nextInt(GalaxyHymnSpaceShardMath.MAX_TRAVEL_TICKS
                    - GalaxyHymnSpaceShardMath.MIN_TRAVEL_TICKS + 1);
            // Preserve the prior random stream so this visual-only particle replacement does not
            // reshuffle the already accepted shard shapes and final orientations.
            for (int discardedSpark = 0; discardedSpark < 3; discardedSpark++) {
                randomUnitVector(random);
                random.nextFloat();
                random.nextFloat();
            }
            result.add(new Shard(vertexCount, direction, 5.5D + random.nextDouble() * 11.5D, travelTicks,
                    0.52F + random.nextFloat() * 1.18F, collapseStart,
                    random.nextFloat() * TWO_PI, random.nextFloat() * TWO_PI,
                    random.nextFloat() * TWO_PI, tumbleAxis,
                    TWO_PI * (1.4F + random.nextFloat() * 3.1F),
                    random.nextFloat() * TWO_PI, 0.025F + random.nextFloat() * 0.025F,
                    angleOffsets, radiusOffsets,
                    0.008D + random.nextDouble() * 0.018D,
                    (random.nextBoolean() ? 1.0F : -1.0F)
                            * (0.0025F + random.nextFloat() * 0.0060F),
                    seed ^ index * 0x6D2B79F5 ^ 0x2A71C9E3));
        }
        return List.copyOf(result);
    }

    private static Vector3f randomUnitVector(Random random) {
        float y = random.nextFloat() * 2.0F - 1.0F;
        float angle = random.nextFloat() * TWO_PI;
        float horizontal = Mth.sqrt(Math.max(0.0F, 1.0F - y * y));
        return new Vector3f(Mth.cos(angle) * horizontal, y, Mth.sin(angle) * horizontal);
    }

    private static Vec3 toVec3(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static void vertex(BufferBuilder builder, Vec3 point) {
        builder.vertex(point.x, point.y, point.z).endVertex();
    }

    private static void colorVertex(BufferBuilder builder, Vec3 point,
                                    float red, float green, float blue, float alpha) {
        builder.vertex(point.x, point.y, point.z).color(red, green, blue, alpha).endVertex();
    }

    private static float age(float partialTick) {
        return activeLevel == null ? GalaxyHymnSpaceShardMath.TOTAL_DURATION_TICKS
                : Math.max(0.0F, activeLevel.getGameTime() - startGameTime + partialTick);
    }

    private record Shard(int vertexCount, Vec3 direction, double stopDistance, int travelTicks, float radius,
                         int collapseStart, float initialPitch, float initialYaw, float initialRoll,
                         Vector3f tumbleAxis, float tumbleRadians,
                         float morphPhase, float morphSpeed, float[] angleOffsets,
                         float[] radiusOffsets, double residualDriftPerTick,
                         float residualSpinRadiansPerTick, int sparkSeed) {
    }

    private record RenderedShard(Vec3[] points, Vec3 normal, float scale) {
    }
}

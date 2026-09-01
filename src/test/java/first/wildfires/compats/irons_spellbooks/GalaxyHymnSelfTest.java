package first.wildfires.compats.irons_spellbooks;

import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnFieldEntity;
import first.wildfires.compats.irons_spellbooks.spell.GalaxyHymnSpell;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Plain-Java contracts for Galaxy Hymn gameplay and the direct Sky Ripper Starlink visual port. */
public final class GalaxyHymnSelfTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/first/wildfires/compats/irons_spellbooks");
    private static final Path CLIENT_ROOT = Path.of("src/main/java/first/wildfires/client/spell");
    private static final Path RESOURCE_ROOT = Path.of("src/main/resources/assets/wildfires");
    private static final Path SHADER_ROOT = RESOURCE_ROOT.resolve("shaders/core");
    private static final Path STAR_TEXTURE_ROOT = RESOURCE_ROOT.resolve("textures/particle/star");
    private static final Path EVIDENCE_TEXTURE_ROOT = Path.of(
            "third_party/arcanevortex/0.6.8/upstream/assets/arcanevortex/textures/particle/star");

    private GalaxyHymnSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        require(Float.floatToIntBits(GalaxyHymnSpell.CORE_EXPLOSION_DAMAGE) == Float.floatToIntBits(200.0F),
                "Core burst damage must remain exactly 200");
        require(Float.floatToIntBits(GalaxyHymnSpell.HOMING_STAR_DAMAGE) == Float.floatToIntBits(20.0F),
                "Homing-star damage must remain exactly 20");
        require(GalaxyHymnSpell.CONSTELLATION_DELAY_TICKS
                        == GalaxyHymnVisualMath.TRACKING_START_TICK,
                "Tracking tooltip must report the second-second gate");
        require(GalaxyHymnFieldEntity.HOMING_STAR_COUNT == 64,
                "The impact burst must release one unified volley of 64 blue cross stars");
        require(GalaxyHymnFieldEntity.CENTER_STAR_HEIGHT == 6.0D,
                "The center, projectile volleys, shards and nebula must share the six-block-high origin");

        assertFriendlyFireContract("entity/GalaxyHymnCoreProjectile.java");
        assertFriendlyFireContract("entity/GalaxyHymnHomingStar.java");
        assertOwnerPersistenceContract("entity/GalaxyHymnCoreProjectile.java");
        assertOwnerPersistenceContract("entity/GalaxyHymnHomingStar.java");
        assertSeededReleaseShell();
        assertExactEmissionContract();
        assertExactParticleContract();
        assertExactSpriteAssets();
        assertImpactVisualContract();
        assertSpaceShardContract();
        assertNebulaContract();
        assertRemovedApproximationContract();

        String fieldSource = read(JAVA_ROOT.resolve("entity/GalaxyHymnFieldEntity.java"));
        require(!fieldSource.contains("getEntitiesOfClass") && !fieldSource.contains("star.setTarget("),
                "The impact field must spawn all stars without preassigning nearest coordinates");
        System.out.println("GalaxyHymnSelfTest passed");
    }

    private static void assertFriendlyFireContract(String relativePath) throws IOException {
        String source = read(JAVA_ROOT.resolve(relativePath));
        require(source.contains("DamageSources.isFriendlyFireBetween"),
                relativePath + " must use Iron's friendly-fire predicate");
    }

    private static void assertOwnerPersistenceContract(String relativePath) throws IOException {
        String source = read(JAVA_ROOT.resolve(relativePath));
        require(source.contains("super.addAdditionalSaveData(tag)")
                        && source.contains("super.readAdditionalSaveData(tag)"),
                relativePath + " must preserve the vanilla Projectile owner across saves");
    }

    private static void assertSeededReleaseShell() throws IOException {
        require(GalaxyHymnVisualMath.VOLLEY_COUNT == 1
                        && GalaxyHymnVisualMath.volleyAge(0) == 0
                        && GalaxyHymnVisualMath.volleySize(0) == 64,
                "One 64-star volley must be released by the impact event");
        Vec3 first = GalaxyHymnVisualMath.homingReleaseOffset(12345, 0);
        Vec3 repeated = GalaxyHymnVisualMath.homingReleaseOffset(12345, 0);
        require(first.distanceToSqr(repeated) == 0.0D,
                "Seeded shell position must be deterministic on server and clients");
        Vec3 previous = null;
        for (int index = 0; index < GalaxyHymnVisualMath.HOMING_STAR_COUNT; index++) {
            Vec3 offset = GalaxyHymnVisualMath.homingReleaseOffset(12345, index);
            require(Double.isFinite(offset.x) && Double.isFinite(offset.y) && Double.isFinite(offset.z),
                    "Homing release shell offset must stay finite");
            double radius = offset.length();
            require(radius >= GalaxyHymnVisualMath.STAR_STOP_MIN_RADIUS
                            && radius < GalaxyHymnVisualMath.STAR_STOP_MAX_RADIUS,
                    "Blue cross-star stop radius must stay inside its non-uniform sphere");
            if (previous != null) {
                require(previous.distanceToSqr(offset) > 1.0E-12D,
                        "Consecutive seeded shell nodes must not collapse");
            }
            previous = offset;

            int releaseTick = GalaxyHymnVisualMath.volleyAge(GalaxyHymnVisualMath.volleyIndex(index));
            int globalTrackingTick = releaseTick + GalaxyHymnVisualMath.trackingStartAge(12345, index);
            require(globalTrackingTick == GalaxyHymnVisualMath.TRACKING_START_TICK,
                    "All impact-frame stars must become target-eligible exactly at the second second");
        }
        double nearWeight = GalaxyHymnVisualMath.proximityTargetWeight(1.0D);
        double middleWeight = GalaxyHymnVisualMath.proximityTargetWeight(36.0D);
        double farWeight = GalaxyHymnVisualMath.proximityTargetWeight(
                GalaxyHymnSpell.TARGET_RANGE * GalaxyHymnSpell.TARGET_RANGE);
        require(Double.isFinite(nearWeight) && farWeight > 0.0D
                        && nearWeight > middleWeight && middleWeight > farWeight,
                "Distance-weighted random targeting must remain finite, positive and favor nearer enemies");
        require(!GalaxyHymnVisualMath.shouldExpireUntargeted(40, false)
                        && !GalaxyHymnVisualMath.shouldExpireUntargeted(119, false)
                        && GalaxyHymnVisualMath.shouldExpireUntargeted(120, false)
                        && !GalaxyHymnVisualMath.shouldExpireUntargeted(120, true),
                "Untargeted stars must wait through six seconds while targeted stars remain active");
        String visualMath = read(JAVA_ROOT.resolve("GalaxyHymnVisualMath.java"));
        require(visualMath.contains("new Random(seed)")
                        && visualMath.contains("STAR_STOP_MIN_RADIUS")
                        && visualMath.contains("STAR_STOP_MAX_RADIUS")
                        && visualMath.contains("VOLLEY_SIZE = HOMING_STAR_COUNT")
                        && visualMath.contains("return TRACKING_START_TICK")
                        && !visualMath.contains("FINAL_VOLLEY")
                        && !visualMath.contains("TRACKING_STAGGER_TICKS"),
                "The unified 64-star seeded spherical release drifted");
    }

    private static void assertExactEmissionContract() throws IOException {
        String spawner = read(JAVA_ROOT.resolve("GalaxyHymnStarlinkSpawner.java"));
        String core = read(JAVA_ROOT.resolve("entity/GalaxyHymnCoreProjectile.java"));
        String homing = read(JAVA_ROOT.resolve("entity/GalaxyHymnHomingStar.java"));
        String field = read(JAVA_ROOT.resolve("entity/GalaxyHymnFieldEntity.java"));
        String clientEvents = read(Path.of("src/main/java/first/wildfires/event/forgeEvent/ClientModEvent.java"));
        String gpuParticle = read(CLIENT_ROOT.resolve("GalaxyHymnGpuStarParticle.java"));

        require(GalaxyHymnStarlinkSpawner.TRAIL_STEP == 0.3D
                        && GalaxyHymnStarlinkSpawner.MAX_TRAIL_STEPS == 10
                        && GalaxyHymnStarlinkSpawner.IMPACT_STAR_COUNT == 400
                        && GalaxyHymnStarlinkSpawner.IMPACT_MIN_RADIUS == 10.0D
                        && GalaxyHymnStarlinkSpawner.IMPACT_MAX_RADIUS == 26.0D
                        && GalaxyHymnStarlinkSpawner.VOLLEY_STARLINKS_PER_HOMING_STAR == 3
                        && GalaxyHymnStarlinkSpawner.VOLLEY_STARLINK_MIN_SPEED == 0.07D
                        && GalaxyHymnStarlinkSpawner.VOLLEY_STARLINK_MAX_SPEED == 0.24D,
                "Sky Ripper trail cadence and 400-star impact shell constants drifted");
        require(spawner.contains("index % 10 == 0")
                        && spawner.contains("level.addParticle(GalaxyHymnRegister.GALAXY_HYMN_STARLINK.get()")
                        && spawner.contains("GalaxyHymnRegister.GALAXY_HYMN_IMPACT_STARLINK.get()")
                        && spawner.contains("new ClientboundLevelParticlesPacket(")
                        && spawner.contains("0.0F, 1)")
                        && spawner.contains("Math.acos(2.0D * random.nextDouble() - 1.0D)"),
                "Starlink trail stepping or forced spherical impact packet semantics drifted");
        require(core.contains("spawnTrailParticles(level(), currentPosition, nextPosition)")
                        && core.contains("&& (owner == null || !DamageSources.isFriendlyFireBetween(owner, target))")
                        && core.contains("sendImpactShell((ServerLevel) level(), position(), getSeed())")
                        && !homing.contains("spawnTrailParticles")
                        && homing.contains("getClientTrailPositions()")
                        && homing.contains("target.getBoundingBox().getCenter()")
                        && homing.contains("DIRECT_INTERCEPT_DISTANCE")
                        && homing.contains("MIN_HOMING_SPEED")
                        && homing.contains("HOMING_ACCELERATION")
                        && homing.contains("double arcWave")
                        && homing.contains("if (target == null)")
                        && homing.contains("shouldExpireUntargeted(tickCount, false)")
                        && homing.contains("entityData.get(TARGET_ID) < 0")
                        && homing.contains("GalaxyHymnVisualMath.proximityTargetWeight(distanceToSqr(candidate))")
                        && homing.contains("random.nextDouble() * totalWeight < weight")
                        && !homing.contains("candidates.get(0)")
                        && !homing.contains(".sorted(")
                        && homing.contains("spawnSparkBurst();")
                        && homing.contains("shatterOnBlockImpact()")
                        && homing.contains("HitResult.Type.BLOCK")
                        && !homing.contains("tickCount % 5 == 0")
                        && homing.contains("GALAXY_HYMN_SPARK")
                        && homing.contains("GALAXY_HYMN_GPU_STAR")
                        && homing.contains("Vec3 visualPosition")
                        && homing.contains("Vec3 previousVisualPosition")
                        && homing.contains("Vec3 visualSegment")
                        && homing.contains("spawnHomingSparkTrail(from, to)")
                        && homing.contains("Vec3 position = from.add(segment.scale(fraction))")
                        && homing.contains("Math.ceil(segment.length() * 4.0D)")
                        && field.contains("releaseHomingStarVolley(volley)")
                        && field.contains("CENTER_STAR_HEIGHT = 6.0D")
                        && field.contains("noCulling = true")
                        && core.contains("field.releaseImpactVolleys()")
                        && field.contains("for (int volley = 0; volley < GalaxyHymnVisualMath.VOLLEY_COUNT; volley++)")
                        && field.contains("sendVolleyConstellation((ServerLevel) level(), burstCenter")
                        && !field.contains("star.setTarget(")
                        && !field.contains("getEntitiesOfClass")
                        && spawner.contains("particleCount = homingStarCount * VOLLEY_STARLINKS_PER_HOMING_STAR")
                        && spawner.contains("VOLLEY_STARLINK_MIN_SPEED + random.nextDouble()")
                        && spawner.contains("GALAXY_HYMN_STARLINK.get(), true")
                        && !field.contains("sendFinalBurstVisual"),
                "Core constellation trail, unified random-speed volley or weighted curved homing drifted");
        require(field.contains("CONSTELLATION_LIFETIME = 150"),
                "The field/impact sequence must cover the six-second stable constellation plus fade");
        require(clientEvents.contains("registerSpriteSet(GalaxyHymnRegister.GALAXY_HYMN_STARLINK.get()")
                        && clientEvents.contains("GALAXY_HYMN_SPARK.get()")
                        && clientEvents.contains("GALAXY_HYMN_GPU_STAR.get()")
                        && clientEvents.contains("GalaxyHymnGpuStarParticle.Provider::new")
                        && clientEvents.contains("GALAXY_HYMN_IMPACT_STARLINK.get()")
                        && clientEvents.contains("GalaxyHymnProjectileRenderer::new")
                        && clientEvents.contains("GalaxyHymnFieldRenderer::new")
                        && clientEvents.contains("GalaxyHymnHomingStarRenderer::new")
                        && !clientEvents.contains("NoopRenderer"),
                "Core, center afterimage and homing cross projectiles need distinct renderers");
        require(gpuParticle.contains("extends TextureSheetParticle")
                        && gpuParticle.contains("WILDFIRES_GALAXY_HYMN_GPU_STAR")
                        && gpuParticle.contains("DefaultVertexFormat.PARTICLE")
                        && gpuParticle.contains("GameRenderer::getParticleShader")
                        && gpuParticle.contains("DestFactor.ONE")
                        && gpuParticle.contains("RenderSystem.depthMask(false)")
                        && gpuParticle.contains("LIFETIME_TICKS = 3")
                        && gpuParticle.contains("xo = x - xSpeed")
                        && gpuParticle.contains("yo = y - ySpeed")
                        && gpuParticle.contains("zo = z - zSpeed")
                        && gpuParticle.contains("xd = 0.0D")
                        && gpuParticle.contains("yd = 0.0D")
                        && gpuParticle.contains("zd = 0.0D"),
                "Homing star bodies must use the authorized ParticleEngine GPU batch pattern");
    }

    private static void assertImpactVisualContract() throws IOException {
        String renderer = read(CLIENT_ROOT.resolve("GalaxyHymnProjectileRenderer.java"));
        String impact = read(CLIENT_ROOT.resolve("GalaxyHymnImpactVisuals.java"));
        String blackWorld = read(CLIENT_ROOT.resolve("GalaxyHymnBlackWorldShader.java"));
        String blackWorldJson = read(SHADER_ROOT.resolve("galaxy_hymn_black_world.json"));
        String packet = read(Path.of("src/main/java/first/wildfires/network/GalaxyHymnImpactVisualPacket.java"));
        String core = read(JAVA_ROOT.resolve("entity/GalaxyHymnCoreProjectile.java"));
        String field = read(JAVA_ROOT.resolve("entity/GalaxyHymnFieldEntity.java"));
        String fieldRenderer = read(CLIENT_ROOT.resolve("GalaxyHymnFieldRenderer.java"));
        String homingRenderer = read(CLIENT_ROOT.resolve("GalaxyHymnHomingStarRenderer.java"));
        String gpuParticle = read(CLIENT_ROOT.resolve("GalaxyHymnGpuStarParticle.java"));
        String bloom = read(CLIENT_ROOT.resolve("GalaxyHymnBloomGeometry.java"));
        String visualMath = read(JAVA_ROOT.resolve("GalaxyHymnVisualMath.java"));
        String clientEvents = read(Path.of("src/main/java/first/wildfires/event/forgeEvent/ClientForgeEvent.java"));
        String modEvents = read(Path.of("src/main/java/first/wildfires/event/forgeEvent/ClientModEvent.java"));

        require(renderer.contains("textures/particle/star/cosmic_0.png")
                        && renderer.contains("entityTranslucentEmissive")
                        && renderer.contains("animationTick < 7 ? 0 : animationTick - 6")
                        && renderer.contains("FULL_BRIGHT"),
                "Projectile body must use the authorized animated cosmic star as a full-bright core");
        require(packet.contains("SHAKE_RADIUS = 60.0D")
                        && packet.contains("BASE_SHAKE_INTENSITY = 5.0F")
                        && core.contains("1.0F - (float) (distance / radius)")
                        && core.contains("new GalaxyHymnImpactVisualPacket(center, intensity, getSeed(), true)")
                        && !field.contains("new GalaxyHymnImpactVisualPacket")
                        && packet.contains("buffer.writeBoolean(completeBurst)")
                        && impact.contains("if (completeBurst)")
                        && impact.contains("blackWorldEnabled = true")
                        && impact.contains("elevatedBurstCenter")
                        && impact.contains("GalaxyHymnFieldEntity.CENTER_STAR_HEIGHT")
                        && impact.contains("if (blackWorldEnabled && blackWorldAge <= 1.0F"),
                "One complete hit-frame packet must trigger dark flash, shards and nebula");
        require(!fieldRenderer.contains("void render(")
                        && !fieldRenderer.contains("GalaxyHymnBloomGeometry")
                        && !fieldRenderer.contains("CENTER_STAR_")
                        && !fieldRenderer.contains("renderSixPointStar")
                        && !bloom.contains("renderSixPointStar")
                        && !visualMath.contains("CENTER_STAR_")
                        && !visualMath.contains("centerStarEventScale")
                        && !fieldRenderer.contains("entityTranslucentEmissive")
                        && !fieldRenderer.contains("cosmic_0.png")
                        && homingRenderer.contains("renderTrajectoryRibbon")
                        && homingRenderer.contains("getClientTrailPositions")
                        && !homingRenderer.contains("renderCrossStar")
                        && !homingRenderer.contains("GalaxyHymnBloomGeometry")
                        && !homingRenderer.contains("entityTranslucentEmissive")
                        && !homingRenderer.contains("cosmic_0.png")
                        && !homingRenderer.contains("GALAXY_HYMN_STARLINK")
                        && renderer.contains("GalaxyHymnBloomGeometry.render")
                        && gpuParticle.contains("GPU_ADDITIVE_RENDER_TYPE")
                        && gpuParticle.contains("quadSize = baseSize")
                        && bloom.contains("alpha * 0.42F")
                        && bloom.contains("alpha * 0.68F")
                        && bloom.contains("for (int octave = 0; octave < 8; octave++)")
                        && bloom.contains("for (int axis = 0; axis < 2; axis++)")
                        && bloom.contains("addTaperedRay")
                        && bloom.contains("float tipWidth = Math.max(0.006F")
                        && bloom.contains("renderCore(poseStack, consumer"),
                "Removed center geometry, visible cross-star geometry or trajectory ribbon drifted");
        require(impact.contains("DURATION_TICKS = 60")
                        && impact.contains("BLACK_WORLD_DURATION_TICKS = DURATION_TICKS")
                        && impact.contains("age(event.getPartialTick()) / BLACK_WORLD_DURATION_TICKS")
                        && impact.contains("shakeIntensity = intensity")
                        && impact.contains("shakeDuration = DURATION_TICKS")
                        && impact.contains("shakeTotalDuration = DURATION_TICKS")
                        && impact.contains("shakeTickCounter = 0")
                        && !impact.contains("Math.max(shakeIntensity, intensity)")
                        && impact.contains("public static void tickShakeAtStart()")
                        && impact.contains("minecraft.player == null || minecraft.isPaused()")
                        && impact.contains("shakeTickCounter++")
                        && impact.contains("shakeDuration--")
                        && impact.contains("float progress = 1.0F - shakeDuration / (float) shakeTotalDuration")
                        && impact.contains("float fadeFactor = 1.0F - (float) Math.pow(1.0F - progress, 3.0D)")
                        && impact.contains("float currentIntensity = shakeIntensity * (1.0F - fadeFactor)")
                        && impact.contains("Random random = new Random()")
                        && count(impact, "random.nextDouble() - 0.5D") == 2
                        && impact.contains("event.setRoll(event.getRoll() + (float) z)")
                        && !impact.contains("shakeSeed")
                        && !impact.contains("shakeNoise")
                        && !impact.contains("Mth.lerp")
                        && !impact.contains("smoothFraction")
                        && impact.contains("RenderLevelStageEvent.Stage.AFTER_ENTITIES")
                        && clientEvents.contains("event.phase == TickEvent.Phase.START")
                        && clientEvents.contains("tickShakeAtStart()")
                        && clientEvents.contains("applyCameraShake(event)"),
                "Camera shake must exactly retain the source START-tick RANDOM impulse and cubic decay");
        require(blackWorld.contains("normalizedAge < 0.25F")
                        && blackWorld.contains("1.0F - (normalizedAge - 0.25F) / 0.75F")
                        && !blackWorld.contains("MAX_LIGHT_INTENSITY")
                        && !blackWorld.contains("MAX_EFFECT_ALPHA")
                        && !blackWorld.contains("lightIntensity *=")
                        && blackWorld.contains("DepthSampler")
                        && blackWorld.contains("projectilePosition().set((float) impactCenter.x")
                        && !blackWorld.contains("impactCenter.subtract(camera.getPosition())")
                        && modEvents.contains("GalaxyHymnBlackWorldShader.register(event)"),
                "Black World must use the source full-strength fade and absolute projectile position");
        for (String optimizedOutBinding : new String[]{"ColorSampler", "cameraPos", "\"time\"",
                "\"yaw\"", "\"pitch\""}) {
            require(!blackWorldJson.contains(optimizedOutBinding),
                    "Black World JSON must not register an upstream unused binding optimized out by GLSL: "
                            + optimizedOutBinding);
        }
        require(!blackWorld.contains("setSampler(\"ColorSampler\"")
                        && !blackWorld.contains("safeGetUniform(\"cameraPos\"")
                        && !blackWorld.contains("safeGetUniform(\"time\"")
                        && !blackWorld.contains("safeGetUniform(\"yaw\"")
                        && !blackWorld.contains("safeGetUniform(\"pitch\""),
                "Black World Java bindings must omit upstream declarations optimized out by GLSL");
        for (String extension : new String[]{".json", ".vsh", ".fsh"}) {
            require(Files.isRegularFile(SHADER_ROOT.resolve("galaxy_hymn_black_world" + extension)),
                    "Missing Galaxy Hymn Black World shader " + extension);
        }
    }

    private static void assertSpaceShardContract() throws IOException {
        require(GalaxyHymnSpaceShardMath.SHARD_COUNT == 40
                        && GalaxyHymnSpaceShardMath.TRAVEL_TICKS == 44
                        && GalaxyHymnSpaceShardMath.POST_TRAVEL_RAMP_TICKS == 12
                        && GalaxyHymnSpaceShardMath.COLLAPSE_START_TICKS == 120
                        && GalaxyHymnSpaceShardMath.COLLAPSE_DURATION_TICKS == 20,
                "Space-shard population or lifecycle drifted");
        double previousDistance = -1.0D;
        double previousSpeed = Double.POSITIVE_INFINITY;
        for (int tick = 0; tick <= GalaxyHymnSpaceShardMath.TRAVEL_TICKS; tick++) {
            double distance = GalaxyHymnSpaceShardMath.travelDistance(12.0D, tick);
            double speed = GalaxyHymnSpaceShardMath.travelSpeed(12.0D, tick);
            require(distance >= previousDistance && distance <= 12.0D,
                    "Shard radial distance must monotonically ease toward its stop radius");
            require(speed <= previousSpeed + 1.0E-12D && speed >= 0.0D,
                    "Shard speed must monotonically fall with distance");
            previousDistance = distance;
            previousSpeed = speed;
        }
        require(Math.abs(previousDistance - 12.0D) < 1.0E-12D
                        && GalaxyHymnSpaceShardMath.travelSpeed(12.0D,
                        GalaxyHymnSpaceShardMath.TRAVEL_TICKS) == 0.0D,
                "Shard explosive travel must reach its configured radius before residual motion");
        require(GalaxyHymnSpaceShardMath.MIN_TRAVEL_TICKS == 36
                        && GalaxyHymnSpaceShardMath.MAX_TRAVEL_TICKS == 54
                        && GalaxyHymnSpaceShardMath.travelSpeed(12.0D, 0.0F, 36)
                        != GalaxyHymnSpaceShardMath.travelSpeed(12.0D, 0.0F, 54)
                        && GalaxyHymnSpaceShardMath.travelSpeed(12.0D, 54.0F, 54) == 0.0D,
                "Each planar shard needs a different seeded initial explosive speed");
        for (int travelTicks : new int[]{GalaxyHymnSpaceShardMath.MIN_TRAVEL_TICKS,
                GalaxyHymnSpaceShardMath.MAX_TRAVEL_TICKS}) {
            float previousGrowth = 0.0F;
            for (int tick = 0; tick <= travelTicks * 2; tick++) {
                float growth = GalaxyHymnSpaceShardMath.growthScale(tick, travelTicks);
                require(growth >= previousGrowth && growth <= 2.0F,
                        "Both shard growth stages must remain monotonic and bounded");
                previousGrowth = growth;
            }
            require(GalaxyHymnSpaceShardMath.travelSpeed(12.0D, travelTicks, travelTicks) == 0.0D
                            && GalaxyHymnSpaceShardMath.growthScale(travelTicks, travelTicks) == 1.0F
                            && GalaxyHymnSpaceShardMath.growthScale(travelTicks * 2, travelTicks) == 2.0F,
                    "Each shard must reach 1x at zero travel speed and 2x one travel duration later");
        }
        double residualAtStart = GalaxyHymnSpaceShardMath.postTravelMotionTicks(44.0F, 44);
        double residualAfterOneTick = GalaxyHymnSpaceShardMath.postTravelMotionTicks(45.0F, 44);
        double residualAfterRamp = GalaxyHymnSpaceShardMath.postTravelMotionTicks(56.0F, 44);
        require(residualAtStart == 0.0D
                        && GalaxyHymnSpaceShardMath.postTravelSpeedFraction(44.0F, 44) == 0.0D
                        && residualAfterOneTick > 0.0D
                        && GalaxyHymnSpaceShardMath.postTravelSpeedFraction(45.0F, 44) > 0.0D
                        && Math.abs(residualAfterRamp - 6.0D) < 1.0E-12D
                        && GalaxyHymnSpaceShardMath.postTravelSpeedFraction(56.0F, 44) == 1.0D
                        && GalaxyHymnSpaceShardMath.postTravelMotionTicks(76.0F, 44)
                        > residualAfterRamp,
                "Shards must smoothly enter persistent slow drift/spin instead of freezing");
        require(GalaxyHymnSpaceShardMath.collapseScale(119.999F, 120) == 1.0F
                        && GalaxyHymnSpaceShardMath.collapseScale(120.0F, 120) == 1.0F
                        && GalaxyHymnSpaceShardMath.collapseScale(140.0F, 120) == 0.0F,
                "Shard must remain full through six seconds, then contract during its final 20 ticks");

        Vec3 center = new Vec3(2.0D, -3.0D, 7.0D);
        Vec3 basisU = new Vec3(0.8D, 0.6D, 0.0D).normalize();
        Vec3 normal = new Vec3(0.3D, -0.4D, 0.866025403784D).normalize();
        Vec3 basisV = normal.cross(basisU).normalize();
        basisU = basisV.cross(normal).normalize();
        for (int index = 0; index < 4; index++) {
            double angle = Math.PI * 2.0D * index / 4.0D;
            Vec3 point = GalaxyHymnSpaceShardMath.planarPoint(center, basisU, basisV,
                    Math.cos(angle), Math.sin(angle));
            require(Math.abs(point.subtract(center).dot(normal)) < 1.0E-10D,
                    "Every deformed triangle/quad vertex must remain in one plane");
        }

        String shards = read(CLIENT_ROOT.resolve("GalaxyHymnSpaceShardVisuals.java"));
        String windowShader = read(CLIENT_ROOT.resolve("GalaxyHymnSpaceWindowShader.java"));
        String shader = read(SHADER_ROOT.resolve("galaxy_hymn_space_window.fsh"));
        String vertexShader = read(SHADER_ROOT.resolve("galaxy_hymn_space_window.vsh"));
        String shaderJson = read(SHADER_ROOT.resolve("galaxy_hymn_space_window.json"));
        String packet = read(Path.of("src/main/java/first/wildfires/network/GalaxyHymnImpactVisualPacket.java"));
        require(shards.contains("random.nextBoolean() ? 3 : 4")
                        && shards.contains("new Vector3f(Mth.cos(localAngle) * radius")
                        && shards.contains("age * shard.morphSpeed()")
                        && shards.contains("shard.stopDistance(), age, shard.travelTicks()")
                        && shards.contains("growthScale(age, shard.travelTicks()) * collapse")
                        && shards.contains("OUTLINE_BASE_WIDTH = 0.014D")
                        && shards.contains("OUTLINE_SCALE_WIDTH = 0.018D")
                        && shards.contains("0.52F + random.nextFloat() * 1.18F, collapseStart")
                        && !shards.contains("SHARD_SIZE_MULTIPLIER")
                        && count(shards, "postTravelMotionTicks(") >= 2
                        && shards.contains("residualMotionTicks * shard.residualSpinRadiansPerTick()")
                        && shards.contains("shard.residualDriftPerTick()")
                        && shards.contains("MIN_TRAVEL_TICKS")
                        && shards.contains("MAX_TRAVEL_TICKS")
                        && !shards.contains("hoverSpin")
                        && !shards.contains("addSpark(builder")
                        && shards.contains("spawnLocalHitSparks(level, collapseCenter, shard.sparkSeed())")
                        && shards.contains("shardWorldCenter(shard, burstAge)")
                        && shards.contains("emittedCollapseBursts.add(index)")
                        && shards.contains("0.03F, 0.24F, 1.0F")
                        && shards.contains("RenderSystem.disableBlend()")
                        && shards.contains("RenderSystem.depthMask(true)")
                        && shards.contains("PoseStack modelViewStack = RenderSystem.getModelViewStack()")
                        && shards.contains("modelViewStack.setIdentity()")
                        && shards.contains("modelViewStack.mulPoseMatrix(event.getPoseStack().last().pose())")
                        && count(shards, "RenderSystem.applyModelViewMatrix()") == 2
                        && shards.indexOf("renderSpaceFaces(visible")
                        > shards.indexOf("modelViewStack.mulPoseMatrix(event.getPoseStack().last().pose())")
                        && shards.indexOf("renderEdgesAndStarlances(visible")
                        > shards.indexOf("modelViewStack.mulPoseMatrix(event.getPoseStack().last().pose())")
                        && shards.contains("modelViewStack.popPose()")
                        && shards.contains("GalaxyHymnSpaceWindowShader.prepare(camera)"),
                "Shard triangles/quads, persistent planar morph, eased travel or blue starlances drifted");
        require(shader.contains("#define iterations 17")
                        && shader.contains("#define volsteps 20")
                        && shader.contains("p = abs(p) / dot(p, p) - formuparam")
                        && shader.contains("in vec3 cameraRelativePos")
                        && shader.contains("vec4 clipPos = ProjMat * ModelViewMat")
                        && shader.contains("fragColor = vec4(finalColor, 1.0)")
                        && shader.contains("ScreenSize")
                        && shader.contains("CameraYaw")
                        && shader.contains("CameraPitch")
                        && shader.contains("portalCamera = mod(CameraPosition")
                        && shader.contains("vec3 from = portalCamera")
                        && !shader.contains("uniform float Time")
                        && !shader.contains("float times")
                        && !shader.contains("rotationMatrix")
                        && vertexShader.contains("cameraRelativePos = Position")
                        && windowShader.contains("safeGetUniform(\"CameraPosition\")")
                        && windowShader.contains("prepare(Camera camera)")
                        && !windowShader.contains("getGameTime()")
                        && !windowShader.contains("safeGetUniform(\"Time\")")
                        && windowShader.contains("cameraPosition.x")
                        && windowShader.contains("screenSize")
                        && windowShader.contains("cameraYaw")
                        && windowShader.contains("cameraPitch")
                        && !shaderJson.contains("\"blend\"")
                        && !shaderJson.contains("\"Time\"")
                        && shaderJson.contains("\"ScreenSize\"")
                        && shaderJson.contains("\"CameraPosition\"")
                        && shaderJson.contains("\"CameraYaw\"")
                        && shaderJson.contains("\"CameraPitch\""),
                "ArcaneVortex star-space window algorithm is no longer active inside the shards");
        require(packet.contains("int visualSeed") && packet.contains("buffer.writeInt(visualSeed)")
                        && packet.contains("boolean completeBurst")
                        && packet.contains("center, shakeIntensity, visualSeed, completeBurst)"),
                "All observers must receive the same deterministic impact-shard seed");
        for (String extension : new String[]{".json", ".vsh", ".fsh"}) {
            require(Files.isRegularFile(SHADER_ROOT.resolve("galaxy_hymn_space_window" + extension)),
                    "Missing Galaxy Hymn star-space window shader " + extension);
        }
    }

    private static void assertNebulaContract() throws IOException {
        String visuals = read(CLIENT_ROOT.resolve("GalaxyHymnNebulaVisuals.java"));
        String managedShader = read(CLIENT_ROOT.resolve("GalaxyHymnNebulaShader.java"));
        String fragment = read(SHADER_ROOT.resolve("galaxy_hymn_nebula.fsh"));
        String baseConfig = read(SHADER_ROOT.resolve("galaxy_hymn_nebula.json"));
        String glowConfig = read(SHADER_ROOT.resolve("galaxy_hymn_nebula_glow.json"));
        String impact = read(CLIENT_ROOT.resolve("GalaxyHymnImpactVisuals.java"));
        String modEvents = read(Path.of("src/main/java/first/wildfires/event/forgeEvent/ClientModEvent.java"));
        require(baseConfig.contains("\"srcrgb\": \"one\"")
                        && baseConfig.contains("\"dstrgb\": \"1-srcalpha\"")
                        && baseConfig.contains("\"srcalpha\": \"one\"")
                        && baseConfig.contains("\"dstalpha\": \"1-srcalpha\"")
                        && glowConfig.contains("\"srcrgb\": \"srcalpha\"")
                        && glowConfig.contains("\"dstrgb\": \"one\"")
                        && glowConfig.contains("\"srcalpha\": \"one\"")
                        && glowConfig.contains("\"dstalpha\": \"one\"")
                        && glowConfig.contains("\"vertex\": \"wildfires:galaxy_hymn_nebula\"")
                        && glowConfig.contains("\"fragment\": \"wildfires:galaxy_hymn_nebula\""),
                "Nebula base and glow passes must own their premultiplied/additive blend modes");
        require(visuals.contains("FULL_RADIUS = 14.0F")
                        && visuals.contains("Math.max(0.012F, expansion)")
                        && !visuals.contains("RenderSystem.blendFunc(")
                        && visuals.contains("RenderSystem.defaultBlendFunc()")
                        && visuals.contains("RenderLevelStageEvent.Stage.AFTER_WEATHER")
                        && !visuals.contains("RenderLevelStageEvent.Stage.AFTER_PARTICLES")
                        && !visuals.contains("RenderLevelStageEvent.Stage.AFTER_LEVEL")
                        && visuals.contains("RenderSystem.disableDepthTest()")
                        && visuals.contains("RenderSystem.enableDepthTest()")
                        && visuals.contains("visualSeed, 0.0F, relativeCenter, radius")
                        && visuals.contains("visualSeed, 1.0F, relativeCenter, radius")
                        && visuals.contains("center.subtract(camera.getPosition())")
                        && fragment.contains("for (int stepIndex = 0; stepIndex < 68; stepIndex++)")
                        && fragment.contains("* 0.03")
                        && fragment.contains("float S1 = pow(pow(efpos.x, 2.0) + pow(efpos.y, 2.0), 2.0)")
                        && fragment.contains("float shape_dis = S1 - 0.1 * 0.8")
                        && fragment.contains("float w_shape_dis = S1 - 0.4 * 0.8")
                        && fragment.contains("float strengthw0")
                        && fragment.contains("float strengthw1")
                        && fragment.contains("float strengthwa")
                        && fragment.contains("float strength0")
                        && fragment.contains("float strength1")
                        && fragment.contains("vec4(0.478, 0.196, 0.106, 0.0)")
                        && fragment.contains("vec4(0.3, 0.66, 0.66, 0.0)")
                        && fragment.contains("float Reddening = 0.6")
                        && fragment.contains("float Saturation = 0.3")
                        && fragment.contains("sourceToneMap")
                        && fragment.contains("worldToNebula")
                        && fragment.contains("float discriminant")
                        && fragment.contains("uniform vec3 CenterRelative")
                        && fragment.contains("uniform float Radius")
                        && fragment.contains("uniform vec2 ScreenSize")
                        && fragment.contains("if (GlowPass < 0.5)")
                        && fragment.contains("1.0 - smoothstep(0.88, 1.0, length(uv))")
                        && fragment.contains("inout float cloudEmission")
                        && fragment.contains("vec3 transportedCloud")
                        && fragment.contains("cloudEmission += dot(transportedCloud")
                        && fragment.contains("float cloudCoverage = 1.0 - exp(-cloudEmission * 1.6)")
                        && fragment.contains("WORLD_OPACITY_GAIN = 2.0")
                        && fragment.contains("WORLD_ALPHA_CAP = 0.84")
                        && fragment.contains("WORLD_SATURATION_BOOST = 1.35")
                        && fragment.contains("(gradedColor - vec3(preSaturationLuma)) * WORLD_SATURATION_BOOST")
                        && fragment.contains("* WORLD_OPACITY_GAIN")
                        && fragment.contains("0.0, WORLD_ALPHA_CAP")
                        && fragment.contains("vec4(gradedColor * contentAlpha, contentAlpha)")
                        && fragment.contains("if (contentAlpha <= 0.002)")
                        && !fragment.contains("vec3 stars(vec3 p)")
                        && !fragment.contains("hash43x")
                        && !fragment.contains("for (int layer = 0; layer < 5; layer++)")
                        && !fragment.contains("stars(rayDirection)")
                        && !fragment.contains("vec3 backgroundStars")
                        && !fragment.contains("sourceColor.r += 0.5")
                        && !fragment.contains("gradedColor - vec3(0.14)")
                        && fragment.contains("if (glowAlpha <= 0.002)")
                        && !fragment.contains("max(sourceColor.a")
                        && !fragment.contains("cyanLobes")
                        && !fragment.contains("sampleDensity")
                        && !fragment.contains("const int STEP_COUNT = 52")
                        && !fragment.contains("float envelope = (1.0 - smoothstep(0.18, 1.0, radial))")
                        && managedShader.contains("DefaultVertexFormat.POSITION_TEX")
                        && managedShader.contains("ShaderBindings baseBindings")
                        && managedShader.contains("ShaderBindings glowBindings")
                        && managedShader.contains("Wildfires.rl(\"galaxy_hymn_nebula_glow\")")
                        && managedShader.contains("glowPass < 0.5F ? baseBindings : glowBindings")
                        && managedShader.contains("shader.safeGetUniform(\"GlowPass\")")
                        && managedShader.contains("shader.safeGetUniform(\"CenterRelative\")")
                        && managedShader.contains("shader.safeGetUniform(\"Radius\")")
                        && managedShader.contains("shader.safeGetUniform(\"ScreenSize\")")
                        && impact.contains("GalaxyHymnNebulaVisuals.trigger")
                        && impact.contains("RenderLevelStageEvent.Stage.AFTER_PARTICLES")
                        && impact.contains("RenderLevelStageEvent.Stage.AFTER_WEATHER")
                        && !impact.contains("RenderLevelStageEvent.Stage.AFTER_LEVEL")
                        && impact.indexOf("GalaxyHymnSpaceShardVisuals.render(event)")
                        < impact.indexOf("GalaxyHymnNebulaVisuals.render(event)")
                        && modEvents.contains("GalaxyHymnNebulaShader.register(event)"),
                "The impact-frame point burst must directly adapt the supplied nebula equations");
        for (String extension : new String[]{".json", ".vsh", ".fsh"}) {
            require(Files.isRegularFile(SHADER_ROOT.resolve("galaxy_hymn_nebula" + extension)),
                    "Missing point-to-nebula shader " + extension);
        }
        require(Files.isRegularFile(SHADER_ROOT.resolve("galaxy_hymn_nebula_glow.json")),
                "Missing additive point-to-nebula glow shader config");
    }

    private static void assertExactParticleContract() throws IOException {
        String particle = read(CLIENT_ROOT.resolve("GalaxyHymnStarlinkParticle.java"));
        require(particle.contains("LINK_RANGE = 6.0D")
                        && particle.contains("MAX_CONNECTIONS_PER_PARTICLE = 3")
                        && particle.contains("CONNECTION_REFRESH_TICKS = 15")
                        && particle.contains("MIN_LIFETIME = 60")
                        && particle.contains("LIFETIME_VARIANTS = 40")
                        && particle.contains("IMPACT_STABLE_TICKS = 120")
                        && particle.contains("IMPACT_FADE_TICKS = 30")
                        && particle.contains("friction = linked ? 0.98F : 0.91F")
                        && particle.contains("0.4F + random.nextFloat() * 0.4F")
                        && particle.contains("linked ? 1.005F : 0.96F")
                        && particle.contains("connectionSearchCooldown = 3 + random.nextInt(5)")
                        && particle.contains("Collections.shuffle(candidates, new Random())")
                        && particle.contains("candidates.sort(Comparator.comparingDouble")
                        && particle.contains("age % CONNECTION_REFRESH_TICKS == 0")
                        && particle.contains("linked ? 0.7F : 0.45F")
                        && particle.contains("linked ? 0.96F : 0.92F")
                        && particle.contains("Math.pow(Math.max(0.0F, wave), 20.0D)")
                        && particle.contains("age <= IMPACT_STABLE_TICKS ? 1.0F")
                        && particle.contains("class ImpactProvider"),
                "Starlink lifetime, drift, growth, connection search or fade no longer matches Sky Ripper");
        require(particle.contains("DestFactor.ONE)")
                        && particle.contains("DefaultVertexFormat.PARTICLE")
                        && particle.contains("DestFactor.ONE_MINUS_SRC_ALPHA")
                        && particle.contains("DefaultVertexFormat.POSITION_COLOR")
                        && count(particle, "DestFactor.ONE);") >= 2
                        && count(particle, "RenderSystem.depthMask(false);") >= 2
                        && particle.contains("return 0x00F000F0")
                        && particle.contains("0.06F * distanceFactor + 0.015F")
                        && particle.contains("baseAlpha * distanceFactor * 1.85F")
                        && particle.contains("0.28F, 1.0F")
                        && particle.contains(".color(0.08F, 0.34F, 1.0F, lineAlpha)")
                        && particle.contains("class SparkProvider")
                        && !particle.contains("hslToRgb"),
                "Core constellation and unlinked motes must use the fixed deep-blue palette");
        for (String forbidden : new String[]{"DamageSource", "TrueDamage", "knockback", "pierce",
                "lightning", "Shockwave", "BlackHole"}) {
            require(!particle.contains(forbidden),
                    "The visual-only Starlink port contains forbidden attack/center geometry: " + forbidden);
        }
    }

    private static void assertExactSpriteAssets() throws IOException {
        String particleJson = read(RESOURCE_ROOT.resolve("particles/galaxy_hymn_starlink.json"));
        String impactParticleJson = read(RESOURCE_ROOT.resolve("particles/galaxy_hymn_impact_starlink.json"));
        String gpuParticleJson = read(RESOURCE_ROOT.resolve("particles/galaxy_hymn_gpu_star.json"));
        require(gpuParticleJson.contains("\"wildfires:star/cosmic_0\""),
                "GPU homing-star particle must use the transparent cross-star atlas sprite");
        for (int index = 0; index < 10; index++) {
            String baseName = "cosmic_" + index;
            require(particleJson.contains("\"wildfires:star/" + baseName + "\""),
                    "Particle atlas JSON is missing " + baseName);
            require(impactParticleJson.contains("\"wildfires:star/" + baseName + "\""),
                    "Impact constellation atlas JSON is missing " + baseName);
            for (String suffix : new String[]{".png", ".png.mcmeta"}) {
                Path runtime = STAR_TEXTURE_ROOT.resolve(baseName + suffix);
                Path evidence = EVIDENCE_TEXTURE_ROOT.resolve(baseName + suffix);
                require(Files.isRegularFile(runtime) && Files.isRegularFile(evidence),
                        "Missing runtime/evidence Starlink asset " + baseName + suffix);
                require(Files.mismatch(runtime, evidence) == -1L,
                        "Runtime Starlink asset must remain byte-identical to its authorized evidence "
                                + baseName + suffix);
            }
        }
    }

    private static void assertRemovedApproximationContract() throws IOException {
        require(!Files.exists(CLIENT_ROOT.resolve("GalaxyHymnShader.java"))
                        && !Files.exists(CLIENT_ROOT.resolve("GalaxyHymnCoreRenderer.java"))
                        && Files.isRegularFile(CLIENT_ROOT.resolve("GalaxyHymnFieldRenderer.java"))
                        && Files.isRegularFile(CLIENT_ROOT.resolve("GalaxyHymnHomingStarRenderer.java")),
                "The rejected fixed-VBO renderer approximation must stay deleted");
        for (String name : new String[]{"galaxy_hymn_star", "galaxy_hymn_link",
                "galaxy_hymn_sky_ripper"}) {
            for (String extension : new String[]{".json", ".vsh", ".fsh"}) {
                require(!Files.exists(SHADER_ROOT.resolve(name + extension)),
                        "Rejected Galaxy Hymn shader resource returned: " + name + extension);
            }
        }
        String runtimeVisual = read(CLIENT_ROOT.resolve("GalaxyHymnStarlinkParticle.java"))
                + read(CLIENT_ROOT.resolve("GalaxyHymnProjectileRenderer.java"))
                + read(CLIENT_ROOT.resolve("GalaxyHymnFieldRenderer.java"))
                + read(CLIENT_ROOT.resolve("GalaxyHymnHomingStarRenderer.java"))
                + read(CLIENT_ROOT.resolve("GalaxyHymnGpuStarParticle.java"))
                + read(CLIENT_ROOT.resolve("GalaxyHymnImpactVisuals.java"))
                + read(CLIENT_ROOT.resolve("GalaxyHymnBlackWorldShader.java"))
                + read(Path.of("src/main/java/first/wildfires/event/forgeEvent/ClientModEvent.java"));
        require(!runtimeVisual.contains("SkyRipperArrowRenderer")
                        && !runtimeVisual.contains("SkyRipperArrowDeadEffect1Renderer")
                        && !runtimeVisual.toLowerCase().contains("hypercube"),
                "Sky Ripper's center/impact tesseract renderer must remain excluded");
    }

    private static int count(String source, String token) {
        int total = 0;
        int from = 0;
        while ((from = source.indexOf(token, from)) >= 0) {
            total++;
            from += token.length();
        }
        return total;
    }

    private static String read(Path path) throws IOException {
        require(Files.isRegularFile(path), "Missing contract file: " + path);
        return Files.readString(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) <= 1.0E-6F;
    }
}

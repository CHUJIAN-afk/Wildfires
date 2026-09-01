package first.wildfires.compats.irons_spellbooks;

/*
 * Core-trail emission cadence adapted from ArcaneVortex 0.6.8 SkyRipperArrow
 * under the user's project-specific visual authorization. Impact and homing
 * stars use a separate Wildfires blue-light-band visual path.
 * Copyright ErChien. No upstream damage or attack behavior is included.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Core-only constellation emission plus unlinked blue hit sparks. */
public final class GalaxyHymnStarlinkSpawner {

    public static final double TRAIL_STEP = 0.3D;
    public static final int MAX_TRAIL_STEPS = 10;
    public static final int IMPACT_STAR_COUNT = 400;
    public static final double IMPACT_MIN_RADIUS = 10.0D;
    public static final double IMPACT_MAX_RADIUS = 26.0D;
    public static final int HIT_SPARK_COUNT = 32;
    public static final int VOLLEY_STARLINKS_PER_HOMING_STAR = 3;
    public static final double VOLLEY_STARLINK_MIN_SPEED = 0.07D;
    public static final double VOLLEY_STARLINK_MAX_SPEED = 0.24D;
    public static final double PACKET_RANGE_SQUARED = 65_536.0D;

    private GalaxyHymnStarlinkSpawner() {
    }

    /**
     * Mirrors Sky Ripper's 0.3-block stepping and {@code i % 10 == 0} gate. At
     * ordinary arrow speeds this emits exactly one particle at the real pre-move
     * position per client tick; it never synthesizes a sideways trail.
     */
    public static void spawnTrailParticles(Level level, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 0.01D) {
            return;
        }
        Vec3 step = direction.normalize().scale(TRAIL_STEP);
        Vec3 current = from;
        int steps = Math.min((int) (length / TRAIL_STEP), MAX_TRAIL_STEPS);
        for (int index = 0; index < steps; index++) {
            if (index % 10 == 0) {
                level.addParticle(GalaxyHymnRegister.GALAXY_HYMN_STARLINK.get(),
                        current.x, current.y, current.z,
                        (level.random.nextDouble() - 0.5D) * 0.05D,
                        (level.random.nextDouble() - 0.5D) * 0.05D,
                        (level.random.nextDouble() - 0.5D) * 0.05D);
            }
            current = current.add(step);
        }
    }

    public static void sendHitSparks(ServerLevel level, Vec3 center, int seed) {
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.x, center.y, center.z) < PACKET_RANGE_SQUARED) {
                nearbyPlayers.add(player);
            }
        }
        if (nearbyPlayers.isEmpty()) {
            return;
        }
        Random random = new Random(seed ^ 0x31A7B1E5);
        for (int index = 0; index < HIT_SPARK_COUNT; index++) {
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
            Vec3 direction = new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi),
                    Math.sin(phi) * Math.sin(theta));
            double speed = 0.08D + random.nextDouble() * 0.24D;
            Vec3 origin = center.add(direction.scale(random.nextDouble() * 0.18D));
            Vec3 velocity = direction.scale(speed);
            ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                    GalaxyHymnRegister.GALAXY_HYMN_SPARK.get(), true,
                    origin.x, origin.y, origin.z,
                    (float) velocity.x, (float) velocity.y, (float) velocity.z, 1.0F, 0);
            for (ServerPlayer player : nearbyPlayers) {
                player.connection.send(packet);
            }
        }
    }

    /** Client-local equivalent of {@link #sendHitSparks}; used by each collapsing space shard. */
    public static void spawnLocalHitSparks(Level level, Vec3 center, int seed) {
        Random random = new Random(seed ^ 0x31A7B1E5);
        for (int index = 0; index < HIT_SPARK_COUNT; index++) {
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
            Vec3 direction = new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi),
                    Math.sin(phi) * Math.sin(theta));
            double speed = 0.08D + random.nextDouble() * 0.24D;
            Vec3 origin = center.add(direction.scale(random.nextDouble() * 0.18D));
            Vec3 velocity = direction.scale(speed);
            level.addParticle(GalaxyHymnRegister.GALAXY_HYMN_SPARK.get(),
                    origin.x, origin.y, origin.z, velocity.x, velocity.y, velocity.z);
        }
    }

    /** Linked deep-blue nodes sprayed by each impact-frame partition at varied spherical speeds. */
    public static void sendVolleyConstellation(ServerLevel level, Vec3 center, int seed,
                                               int volleyIndex, int homingStarCount) {
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.x, center.y, center.z) < PACKET_RANGE_SQUARED) {
                nearbyPlayers.add(player);
            }
        }
        if (nearbyPlayers.isEmpty()) {
            return;
        }
        Random random = new Random(seed ^ volleyIndex * 0x6D2B79F5 ^ 0x51A7C0DE);
        int particleCount = homingStarCount * VOLLEY_STARLINKS_PER_HOMING_STAR;
        for (int index = 0; index < particleCount; index++) {
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
            Vec3 direction = new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi),
                    Math.sin(phi) * Math.sin(theta));
            double speed = VOLLEY_STARLINK_MIN_SPEED + random.nextDouble()
                    * (VOLLEY_STARLINK_MAX_SPEED - VOLLEY_STARLINK_MIN_SPEED);
            Vec3 origin = center.add(direction.scale(random.nextDouble() * 0.16D));
            Vec3 velocity = direction.scale(speed);
            ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                    GalaxyHymnRegister.GALAXY_HYMN_STARLINK.get(), true,
                    origin.x, origin.y, origin.z,
                    (float) velocity.x, (float) velocity.y, (float) velocity.z, 1.0F, 0);
            for (ServerPlayer player : nearbyPlayers) {
                player.connection.send(packet);
            }
        }
    }

    /** Large impact constellation retained separately from the blue cross-star projectiles. */
    public static void sendImpactShell(ServerLevel level, Vec3 center, int seed) {
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.x, center.y, center.z) < PACKET_RANGE_SQUARED) {
                nearbyPlayers.add(player);
            }
        }
        if (nearbyPlayers.isEmpty()) {
            return;
        }
        Random random = new Random(seed);
        for (int index = 0; index < IMPACT_STAR_COUNT; index++) {
            double radius = IMPACT_MIN_RADIUS
                    + random.nextDouble() * (IMPACT_MAX_RADIUS - IMPACT_MIN_RADIUS);
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
            double x = center.x + radius * Math.sin(phi) * Math.cos(theta);
            double y = center.y + radius * Math.cos(phi);
            double z = center.z + radius * Math.sin(phi) * Math.sin(theta);
            ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                    GalaxyHymnRegister.GALAXY_HYMN_IMPACT_STARLINK.get(), true,
                    x, y, z, 0.0F, 0.0F, 0.0F, 0.0F, 1);
            for (ServerPlayer player : nearbyPlayers) {
                player.connection.send(packet);
            }
        }
    }
}

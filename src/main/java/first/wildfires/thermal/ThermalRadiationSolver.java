package first.wildfires.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Receiver-driven line-of-sight surface radiation; no persistent radiation grid is created. */
final class ThermalRadiationSolver {

    private static final float MINIMUM_CONTRIBUTION = 0.1F;
    private static final long LOS_CACHE_TICKS = 10L;
    private static final long LOS_CACHE_RETENTION_TICKS = 200L;
    private static final int MAX_NEW_RAYS_PER_TICK = 64;
    private static final Map<ServerLevel, RadiationState> STATES = new WeakHashMap<>();

    private ThermalRadiationSolver() {
    }

    static float sample(ServerLevel level, ThermalWorldManager manager, ServerPlayer player,
                        BlockPos targetPosition, float airTemperature) {
        if (!ThermalConfig.radiationEnabled()) {
            return airTemperature;
        }
        Vec3 target = player == null
                ? Vec3.atCenterOf(targetPosition)
                : new Vec3(player.getX(), player.getBoundingBox().getCenter().y, player.getZ());
        double weightedRadiation = 0.0D;
        double totalWeight = 0.0D;

        for (ThermalWorldManager.RadiantPatch patch : manager.radiantPatchesNear(targetPosition)) {
            Float decay = patch.radiationDecayPerBlock();
            if (decay == null || patch.area() <= 0.0F) {
                continue;
            }
            Vec3 source = patch.closestPoint(target);
            Vec3 ray = target.subtract(source);
            double distance = ray.length();
            if (distance <= 0.001D) {
                continue;
            }
            float remaining = Math.max(0.0F,
                    Math.abs(patch.radiationTemperature()) - decay * (float) distance);
            if (remaining < MINIMUM_CONTRIBUTION) {
                continue;
            }
            Vec3 direction = ray.scale(1.0D / distance);
            double cosine = direction.x * patch.direction().getStepX()
                    + direction.y * patch.direction().getStepY()
                    + direction.z * patch.direction().getStepZ();
            if (cosine <= 0.0D) {
                continue;
            }
            LosKey key = new LosKey(patch.identity(), patch.direction().ordinal(), targetPosition.asLong());
            VisibilityLookup lookup = lookupVisibility(level, key);
            Boolean visible = lookup.cachedVisibility();
            if (lookup.traceRequired()) {
                visible = isVisible(level, player, source, target);
                storeVisibility(level, key, visible);
            }
            if (visible == null || !visible) {
                continue;
            }
            float radiatedTemperature = Math.copySign(remaining, patch.radiationTemperature());
            double weight = patch.area() * cosine / Math.max(1.0D, distance * distance);
            weightedRadiation += weight * radiatedTemperature;
            totalWeight += weight;
        }
        // Radiation temperatures are signed offsets from the natural background, just like the
        // air field. Saturate the radiation contribution independently before adding it to air.
        // Blending each source toward the already-heated air would make a positive radiant source
        // become a cooler whenever Tair exceeded Tr, which reverses the configured source sign.
        double effective = airTemperature + weightedRadiation / (1.0D + totalWeight);
        return Double.isFinite(effective) ? (float) effective : airTemperature;
    }

    static synchronized void invalidate(ServerLevel level) {
        RadiationState state = STATES.get(level);
        if (state != null) {
            state.cache.clear();
        }
    }

    static synchronized void clear(ServerLevel level) {
        STATES.remove(level);
    }

    static synchronized RadiationDiagnostics diagnostics(ServerLevel level) {
        RadiationState state = STATES.get(level);
        if (state == null) {
            return new RadiationDiagnostics(0, 0L, 0L, 0L, 0L);
        }
        state.beginTick(level.getGameTime());
        return new RadiationDiagnostics(state.raysThisTick, state.totalRays, state.cacheHits,
                state.staleCacheUses, state.deferredRays);
    }

    private static boolean isVisible(ServerLevel level, ServerPlayer player, Vec3 source, Vec3 target) {
        if (!traversedChunksLoaded(level, source, target)) {
            return false;
        }
        HitResult result = level.clip(new ClipContext(source, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.MISS
                || result.getLocation().distanceToSqr(target) < 0.04D;
    }

    /** Checks the ray corridor without asking ServerLevel for an unloaded chunk. */
    private static boolean traversedChunksLoaded(ServerLevel level, Vec3 source, Vec3 target) {
        double horizontalDistance = Math.max(Math.abs(target.x - source.x), Math.abs(target.z - source.z));
        int steps = Math.max(1, (int) Math.ceil(horizontalDistance / 8.0D));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            cursor.set((int) Math.floor(source.x + (target.x - source.x) * progress),
                    (int) Math.floor(source.y + (target.y - source.y) * progress),
                    (int) Math.floor(source.z + (target.z - source.z) * progress));
            if (level.isOutsideBuildHeight(cursor) || !level.hasChunkAt(cursor)) {
                return false;
            }
        }
        return true;
    }

    private static synchronized VisibilityLookup lookupVisibility(ServerLevel level, LosKey key) {
        RadiationState state = STATES.computeIfAbsent(level, ignored -> new RadiationState());
        state.beginTick(level.getGameTime());
        LosEntry entry = state.cache.get(key);
        long age = entry == null ? Long.MAX_VALUE : level.getGameTime() - entry.tick();
        if (entry != null && age > LOS_CACHE_RETENTION_TICKS) {
            state.cache.remove(key);
            entry = null;
        }
        if (entry != null && age <= LOS_CACHE_TICKS) {
            state.cacheHits++;
            return new VisibilityLookup(entry.visible(), false);
        }
        if (state.raysThisTick < MAX_NEW_RAYS_PER_TICK) {
            state.raysThisTick++;
            state.totalRays++;
            return new VisibilityLookup(entry == null ? null : entry.visible(), true);
        }
        if (entry != null) {
            state.staleCacheUses++;
            return new VisibilityLookup(entry.visible(), false);
        }
        state.deferredRays++;
        return new VisibilityLookup(null, false);
    }

    private static synchronized void storeVisibility(ServerLevel level, LosKey key, boolean visible) {
        STATES.computeIfAbsent(level, ignored -> new RadiationState()).cache
                .put(key, new LosEntry(visible, level.getGameTime()));
    }

    record RadiationDiagnostics(int raysThisTick, long totalRays, long cacheHits,
                                long staleCacheUses, long deferredRays) {
    }

    private record LosKey(long patchIdentity, int direction, long targetPosition) {
    }

    private record LosEntry(boolean visible, long tick) {
    }

    private record VisibilityLookup(Boolean cachedVisibility, boolean traceRequired) {
    }

    private static final class RadiationState {
        private final Map<LosKey, LosEntry> cache = new HashMap<>();
        private long budgetTick = Long.MIN_VALUE;
        private long lastPruneTick = Long.MIN_VALUE;
        private int raysThisTick;
        private long totalRays;
        private long cacheHits;
        private long staleCacheUses;
        private long deferredRays;

        private void beginTick(long tick) {
            if (budgetTick != tick) {
                budgetTick = tick;
                raysThisTick = 0;
            }
            if (lastPruneTick == Long.MIN_VALUE || tick - lastPruneTick >= LOS_CACHE_RETENTION_TICKS) {
                cache.entrySet().removeIf(entry -> tick - entry.getValue().tick() > LOS_CACHE_RETENTION_TICKS);
                lastPruneTick = tick;
            }
        }
    }
}

package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.api.celestial.CelestialState;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server-side, per-tick/per-chunk cache for position-dependent celestial gameplay queries. */
public final class CelestialGameplay {

    private static final Map<ServerLevel, LevelCache> CACHES = new WeakHashMap<>();

    private CelestialGameplay() {
    }

    public static double visibleBloodMoon(ServerLevel level, BlockPos position) {
        long calendarTick = Calendars.get(level).getCalendarTicks();
        CelestialRuntimeSettings settings = CelestialConfig.serverSettings();
        LevelCache cache = CACHES.computeIfAbsent(level, ignored -> new LevelCache());
        if (cache.calendarTick != calendarTick || cache.settings != settings) {
            cache.calendarTick = calendarTick;
            cache.settings = settings;
            cache.overworldFrame = level.dimension() == Level.OVERWORLD
                    ? OverworldCelestialProvider.context(level, 0.0F, settings) : null;
            cache.intensities.clear();
        }
        long chunkKey = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
        double cached = cache.intensities.get(chunkKey);
        if (cached != LevelCache.MISSING_INTENSITY) {
            return cached;
        }
        double intensity;
        if (cache.overworldFrame != null) {
            double observerZ = (ChunkPos.getZ(chunkKey) << 4) + 8.0D;
            intensity = cache.overworldFrame.visibleBloodMoonAt(observerZ);
        } else {
            double observerX = (ChunkPos.getX(chunkKey) << 4) + 8.0D;
            double observerZ = (ChunkPos.getZ(chunkKey) << 4) + 8.0D;
            Vec3 observer = new Vec3(observerX, position.getY(), observerZ);
            intensity = calculateVisibleBloodMoon(level, observer);
        }
        cache.intensities.put(chunkKey, intensity);
        return intensity;
    }

    private static double calculateVisibleBloodMoon(ServerLevel level, Vec3 observer) {
        CelestialState state = CelestialApi.state(level, observer, 0.0F).orElse(null);
        return state == null || !state.visibleBloodMoon() ? 0.0D : state.bloodMoon();
    }

    public static boolean allowsSurfaceMonster(EntityType<?> type) {
        CelestialConfig.SurfaceMonsterIdFilter filter = CelestialConfig.surfaceMonsterIdFilter();
        if (filter.unrestricted()) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return filter.allows(id);
    }

    private static final class LevelCache {
        private static final double MISSING_INTENSITY = -1.0D;
        private long calendarTick = Long.MIN_VALUE;
        private CelestialRuntimeSettings settings;
        private OverworldCelestialProvider.FrameContext overworldFrame;
        private final Long2DoubleOpenHashMap intensities = new Long2DoubleOpenHashMap();

        private LevelCache() {
            intensities.defaultReturnValue(MISSING_INTENSITY);
        }
    }
}

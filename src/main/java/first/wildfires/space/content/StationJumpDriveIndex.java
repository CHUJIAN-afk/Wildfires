package first.wildfires.space.content;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Loaded-only positional index for zero-consumption jump test engines. */
public final class StationJumpDriveIndex {

    private static final Map<ServerLevel, Map<UUID, Set<BlockPos>>> BY_LEVEL = new WeakHashMap<>();

    private StationJumpDriveIndex() {
    }

    public static synchronized void register(StationJumpTestEngineBlockEntity engine) {
        if (!(engine.getLevel() instanceof ServerLevel level) || level.dimension() != SpaceDimensions.ORBIT) {
            return;
        }
        BlockPos pos = engine.getBlockPos();
        SpaceSavedData.get(level.getServer()).stationAt(pos.getX(), pos.getZ())
                .filter(station -> station.region().containsBuildArea(pos))
                .ifPresent(station -> BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>())
                        .computeIfAbsent(station.stationId(), ignored -> new LinkedHashSet<>()).add(pos.immutable()));
    }

    public static synchronized void unregister(StationJumpTestEngineBlockEntity engine) {
        if (!(engine.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, Set<BlockPos>> stations = BY_LEVEL.get(level);
        if (stations == null) return;
        stations.values().forEach(positions -> positions.remove(engine.getBlockPos()));
        stations.values().removeIf(Set::isEmpty);
        if (stations.isEmpty()) BY_LEVEL.remove(level);
    }

    public static synchronized boolean hasLoadedEngine(ServerLevel level, StationRecord station) {
        if (level.dimension() != SpaceDimensions.ORBIT) return false;
        Map<UUID, Set<BlockPos>> stations = BY_LEVEL.get(level);
        Set<BlockPos> positions = stations == null ? null : stations.get(station.stationId());
        if (positions == null) return false;
        boolean found = false;
        Iterator<BlockPos> iterator = positions.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            boolean valid = station.region().containsBuildArea(pos) && level.hasChunkAt(pos)
                    && level.getBlockState(pos).is(SpaceContentRegister.STATION_JUMP_TEST_ENGINE.get())
                    && SpaceSavedData.get(level.getServer()).stationAt(pos.getX(), pos.getZ())
                    .map(current -> current.stationId().equals(station.stationId())).orElse(false);
            if (!valid) iterator.remove(); else found = true;
        }
        if (positions.isEmpty()) stations.remove(station.stationId());
        if (stations.isEmpty()) BY_LEVEL.remove(level);
        return found;
    }
}

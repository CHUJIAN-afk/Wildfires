package first.wildfires.space.content;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** NTM IPropulsion-style registry of normal engine objects owned by each loaded station. */
public final class StationDriveIndex {

    private static final Map<ServerLevel, Map<UUID, Set<StationPropulsion>>> BY_LEVEL = new WeakHashMap<>();

    private StationDriveIndex() {
    }

    /** Mirrors OrbitalStation.addPropulsion: the engine object registers itself after entering orbit. */
    public static synchronized boolean register(StationPropulsion propulsion) {
        BlockEntity engine = propulsion.blockEntity();
        if (!(engine.getLevel() instanceof ServerLevel level) || level.dimension() != SpaceDimensions.ORBIT) {
            return false;
        }
        BlockPos pos = engine.getBlockPos();
        return SpaceSavedData.get(level.getServer()).stationAt(pos.getX(), pos.getZ())
                .filter(station -> station.region().containsBuildArea(pos))
                .map(station -> {
                    BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>())
                            .computeIfAbsent(station.stationId(), ignored -> new LinkedHashSet<>())
                            .add(propulsion);
                    return true;
                }).orElse(false);
    }

    /** Mirrors OrbitalStation.removePropulsion and deliberately removes stale membership everywhere. */
    public static synchronized void unregister(StationPropulsion propulsion) {
        BY_LEVEL.values().forEach(stations -> {
            stations.values().forEach(propulsions -> propulsions.remove(propulsion));
            stations.values().removeIf(Set::isEmpty);
        });
        BY_LEVEL.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** NTM canTravel equivalent for the current route model's fixed reference station mass. */
    public static synchronized boolean hasLoadedEngine(ServerLevel level, StationRecord station) {
        if (level.dimension() != SpaceDimensions.ORBIT) {
            return false;
        }
        Map<UUID, Set<StationPropulsion>> stations = BY_LEVEL.get(level);
        Set<StationPropulsion> propulsions = stations == null ? null : stations.get(station.stationId());
        if (propulsions == null) {
            return false;
        }
        java.util.List<StationPropulsion> validPropulsions = new ArrayList<>();
        Iterator<StationPropulsion> iterator = propulsions.iterator();
        while (iterator.hasNext()) {
            StationPropulsion propulsion = iterator.next();
            if (!valid(level, station, propulsion)) {
                iterator.remove();
            } else {
                validPropulsions.add(propulsion);
            }
        }
        if (propulsions.isEmpty()) {
            stations.remove(station.stationId());
        }
        if (stations.isEmpty()) {
            BY_LEVEL.remove(level);
        }
        if (validPropulsions.isEmpty()) {
            return false;
        }
        float totalThrust = validPropulsions.stream().map(StationPropulsion::thrust)
                .reduce(0.0F, Float::sum);
        if (totalThrust <= 0.0F) {
            return false;
        }
        for (StationPropulsion propulsion : validPropulsions) {
            int assignedMass = Math.round(StationPropulsion.REFERENCE_SHIP_MASS
                    * propulsion.thrust() / totalThrust);
            if (!propulsion.canPerformBurn(assignedMass, 0.0D)) {
                return false;
            }
        }
        return true;
    }

    /** NTM getLeaveTime equivalent: begin every normal engine burn and retain the largest warm-up. */
    public static synchronized int startBurn(ServerLevel level, StationRecord station) {
        return burn(level, station, true);
    }

    /** NTM getArriveTime equivalent: end every normal engine burn and retain the largest cool-down. */
    public static synchronized int endBurn(ServerLevel level, StationRecord station) {
        return burn(level, station, false);
    }

    private static int burn(ServerLevel level, StationRecord station, boolean starting) {
        Map<UUID, Set<StationPropulsion>> stations = BY_LEVEL.get(level);
        Set<StationPropulsion> propulsions = stations == null ? null : stations.get(station.stationId());
        if (propulsions == null) {
            return 20;
        }
        int delay = 20;
        Iterator<StationPropulsion> iterator = propulsions.iterator();
        while (iterator.hasNext()) {
            StationPropulsion propulsion = iterator.next();
            if (!valid(level, station, propulsion)) {
                iterator.remove();
            } else {
                delay = Math.max(delay, starting ? propulsion.startBurn() : propulsion.endBurn());
            }
        }
        return delay;
    }

    private static boolean valid(ServerLevel level, StationRecord station, StationPropulsion propulsion) {
        BlockEntity engine = propulsion.blockEntity();
        BlockPos pos = engine.getBlockPos();
        return !engine.isRemoved() && engine.getLevel() == level && level.hasChunkAt(pos)
                && station.region().containsBuildArea(pos)
                && SpaceSavedData.get(level.getServer()).stationAt(pos.getX(), pos.getZ())
                .map(current -> current.stationId().equals(station.stationId())).orElse(false);
    }
}

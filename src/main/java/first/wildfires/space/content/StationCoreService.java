package first.wildfires.space.content;

import com.mojang.logging.LogUtils;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Idempotently materializes the authoritative core at every station's primary dock. */
public final class StationCoreService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<BlockPos> STRUCTURE_OFFSETS = createOffsets();

    private StationCoreService() {
    }

    public static int ensureAll(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        SpaceSavedData data = SpaceSavedData.get(server);
        if (!data.writable()) {
            LOGGER.error("Cannot reconcile station cores because space data is read-only: {}",
                    data.writeBlockReason().orElse("unknown reason"));
            return 0;
        }
        int ensured = 0;
        for (StationRecord station : data.stations().values()) {
            if (ensureCore(server, station)) {
                ensured++;
            }
        }
        return ensured;
    }

    public static boolean ensureCore(MinecraftServer server, StationRecord station) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(station, "station");
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) {
            LOGGER.error("Cannot materialize station core {}: wildfires:orbit is unavailable",
                    station.stationId());
            return false;
        }
        BlockPos position = station.primaryDock().position();
        orbit.getChunkAt(position);
        if (!orbit.getBlockState(position).is(SpaceContentRegister.STATION_CORE.get())
                && !orbit.setBlock(position, SpaceContentRegister.STATION_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL)) {
            LOGGER.error("Cannot materialize station core {} at {}", station.stationId(), position);
            return false;
        }
        if (!(orbit.getBlockEntity(position) instanceof StationCoreBlockEntity core)) {
            LOGGER.error("Station core {} at {} has no matching block entity", station.stationId(), position);
            return false;
        }
        core.bind(station.stationId());
        boolean complete = true;
        for (BlockPos offset : STRUCTURE_OFFSETS) {
            BlockPos proxy = position.offset(offset);
            orbit.getChunkAt(proxy);
            if (!orbit.getBlockState(proxy).is(SpaceContentRegister.STATION_STRUCTURE.get())
                    && !orbit.setBlock(proxy, SpaceContentRegister.STATION_STRUCTURE.get().defaultBlockState(),
                    Block.UPDATE_ALL)) {
                LOGGER.error("Cannot materialize station core proxy {} at {}", station.stationId(), proxy);
                complete = false;
            }
        }
        return complete;
    }

    /** Bottom-centre core plus these seventeen offsets form an exact 3x3x2 occupied volume. */
    public static List<BlockPos> structureOffsets() {
        return STRUCTURE_OFFSETS;
    }

    public static boolean isCoreStructureBlock(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(SpaceContentRegister.STATION_CORE.get())
                || state.is(SpaceContentRegister.STATION_STRUCTURE.get());
    }

    public static boolean isComplete(ServerLevel level, BlockPos corePosition) {
        if (!level.getBlockState(corePosition).is(SpaceContentRegister.STATION_CORE.get())) return false;
        return STRUCTURE_OFFSETS.stream().allMatch(offset -> level.getBlockState(
                corePosition.offset(offset)).is(SpaceContentRegister.STATION_STRUCTURE.get()));
    }

    private static List<BlockPos> createOffsets() {
        List<BlockPos> offsets = new ArrayList<>(17);
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) offsets.add(new BlockPos(x, y, z));
                }
            }
        }
        return Collections.unmodifiableList(offsets);
    }
}

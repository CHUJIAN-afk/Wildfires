package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import first.wildfires.thermal.ThermalWorldManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;
import java.util.Map;
import java.util.WeakHashMap;

/** Creative-only request for independently selectable visible and hidden thermal snapshots. */
public record ThermalDebugRequestPacket(boolean includeVisible, boolean includeHidden)
        implements ICustomPacketPayload {

    private static final int RADIUS = 12;
    private static final int MAX_CELLS = 1024;
    private static final int MAX_SOURCES = 256;
    private static final int MAX_SURFACES = 2048;
    private static final int REQUEST_COOLDOWN_TICKS = 5;
    private static final Map<ServerPlayer, Long> LAST_REQUEST_TICKS = new WeakHashMap<>();

    public ThermalDebugRequestPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(includeVisible);
        buffer.writeBoolean(includeHidden);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !player.getAbilities().instabuild || !allowRequest(player)) {
            return;
        }
        ThermalWorldManager manager = ThermalWorldManager.get(player.serverLevel());
        var cells = includeVisible
                ? limitCells(manager.snapshot(player.blockPosition(), RADIUS), player)
                : Map.<Long, Float>of();
        var hiddenCells = includeHidden
                ? limitCells(manager.hiddenSnapshot(player.blockPosition(), RADIUS), player)
                : Map.<Long, Float>of();
        var sources = includeVisible
                ? manager.sourceSnapshot(player.blockPosition(), RADIUS, MAX_SOURCES)
                : java.util.List.<ThermalWorldManager.SourceDebug>of();
        var surfaces = includeVisible
                ? manager.surfaceSnapshot(player.blockPosition(), RADIUS, MAX_SURFACES)
                : java.util.List.<ThermalWorldManager.SurfaceDebug>of();
        ThermalDebugSnapshotPacket response = new ThermalDebugSnapshotPacket(cells, hiddenCells, sources,
                surfaces, manager.diagnostics());
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), response);
    }

    private static Map<Long, Float> limitCells(Map<Long, Float> cells, ServerPlayer player) {
        if (cells.size() <= MAX_CELLS) {
            return cells;
        }
        var limited = new java.util.LinkedHashMap<Long, Float>();
        cells.entrySet().stream()
                .sorted(java.util.Comparator.comparingDouble(entry ->
                        net.minecraft.core.BlockPos.of(entry.getKey()).distSqr(player.blockPosition())))
                .limit(MAX_CELLS)
                .forEach(entry -> limited.put(entry.getKey(), entry.getValue()));
        return limited;
    }

    private static synchronized boolean allowRequest(ServerPlayer player) {
        long tick = player.serverLevel().getGameTime();
        Long previous = LAST_REQUEST_TICKS.get(player);
        if (previous != null && tick - previous < REQUEST_COOLDOWN_TICKS) {
            return false;
        }
        LAST_REQUEST_TICKS.put(player, tick);
        return true;
    }
}

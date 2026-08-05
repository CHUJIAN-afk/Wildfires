package first.wildfires.network;

import first.wildfires.client.ThermalDebugRenderer;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.thermal.ThermalWorldManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Bounded server-authoritative visible/hidden cells and source data for the client debugger. */
public record ThermalDebugSnapshotPacket(Map<Long, Float> cells,
                                         Map<Long, Float> hiddenCells,
                                         List<ThermalWorldManager.SourceDebug> sources,
                                         List<ThermalWorldManager.SurfaceDebug> surfaces,
                                         ThermalWorldManager.ThermalDiagnostics diagnostics)
        implements ICustomPacketPayload {

    private static final int MAX_STORED_CELLS = 8192;
    private static final int MAX_STORED_SOURCES = 256;
    private static final int MAX_STORED_SURFACES = 2048;
    private static final int MAX_WIRE_CELLS = 65_536;
    private static final int MAX_WIRE_SOURCES = 4096;
    private static final int MAX_WIRE_SURFACES = 16_384;

    public ThermalDebugSnapshotPacket {
        cells = Map.copyOf(cells);
        hiddenCells = Map.copyOf(hiddenCells);
        sources = List.copyOf(sources);
        surfaces = List.copyOf(surfaces);
    }

    public ThermalDebugSnapshotPacket(FriendlyByteBuf buffer) {
        this(readCells(buffer, "thermal cells"), readCells(buffer, "hidden thermal cells"),
                readSources(buffer), readSurfaces(buffer), readDiagnostics(buffer));
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(cells.size());
        for (Map.Entry<Long, Float> entry : cells.entrySet()) {
            buffer.writeLong(entry.getKey());
            buffer.writeFloat(entry.getValue());
        }
        buffer.writeVarInt(hiddenCells.size());
        for (Map.Entry<Long, Float> entry : hiddenCells.entrySet()) {
            buffer.writeLong(entry.getKey());
            buffer.writeFloat(entry.getValue());
        }
        buffer.writeVarInt(sources.size());
        for (ThermalWorldManager.SourceDebug source : sources) {
            buffer.writeLong(source.position());
            buffer.writeFloat(source.temperature());
        }
        buffer.writeVarInt(surfaces.size());
        for (ThermalWorldManager.SurfaceDebug surface : surfaces) {
            buffer.writeLong(surface.sourcePosition());
            buffer.writeLong(surface.targetPosition());
            buffer.writeByte(surface.direction());
            buffer.writeFloat(surface.temperature());
            buffer.writeFloat(surface.area());
        }
        buffer.writeVarInt(diagnostics.sectionCount());
        buffer.writeVarInt(diagnostics.sourceCount());
        buffer.writeVarInt(diagnostics.activeCellCount());
        buffer.writeVarInt(diagnostics.exposedFaceCount());
        buffer.writeVarInt(diagnostics.radiantPatchCount());
        buffer.writeVarInt(diagnostics.processedCellsLastTick());
        buffer.writeVarInt(diagnostics.deferredSectionsLastTick());
        buffer.writeVarInt(diagnostics.raysThisTick());
        buffer.writeVarLong(diagnostics.totalRays());
        buffer.writeVarLong(diagnostics.radiationCacheHits());
        buffer.writeVarLong(diagnostics.staleRadiationCacheUses());
        buffer.writeVarLong(diagnostics.deferredRays());
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ThermalDebugRenderer.acceptSnapshot(
                cells, hiddenCells, sources, surfaces, diagnostics));
    }

    private static Map<Long, Float> readCells(FriendlyByteBuf buffer, String label) {
        int count = readBoundedCount(buffer, MAX_WIRE_CELLS, label);
        Map<Long, Float> result = new LinkedHashMap<>(Math.min(count, MAX_STORED_CELLS));
        for (int index = 0; index < count; index++) {
            long position = buffer.readLong();
            float temperature = buffer.readFloat();
            if (index < MAX_STORED_CELLS) {
                result.put(position, temperature);
            }
        }
        return result;
    }

    private static List<ThermalWorldManager.SourceDebug> readSources(FriendlyByteBuf buffer) {
        int count = readBoundedCount(buffer, MAX_WIRE_SOURCES, "thermal sources");
        List<ThermalWorldManager.SourceDebug> result = new ArrayList<>(Math.min(count, MAX_STORED_SOURCES));
        for (int index = 0; index < count; index++) {
            long position = buffer.readLong();
            float temperature = buffer.readFloat();
            if (index < MAX_STORED_SOURCES) {
                result.add(new ThermalWorldManager.SourceDebug(position, temperature));
            }
        }
        return result;
    }

    private static List<ThermalWorldManager.SurfaceDebug> readSurfaces(FriendlyByteBuf buffer) {
        int count = readBoundedCount(buffer, MAX_WIRE_SURFACES, "thermal surfaces");
        List<ThermalWorldManager.SurfaceDebug> result = new ArrayList<>(Math.min(count, MAX_STORED_SURFACES));
        for (int index = 0; index < count; index++) {
            long source = buffer.readLong();
            long target = buffer.readLong();
            byte direction = buffer.readByte();
            float temperature = buffer.readFloat();
            float area = buffer.readFloat();
            if (index < MAX_STORED_SURFACES && direction >= 0 && direction < 6) {
                result.add(new ThermalWorldManager.SurfaceDebug(source, target, direction, temperature, area));
            }
        }
        return result;
    }

    private static ThermalWorldManager.ThermalDiagnostics readDiagnostics(FriendlyByteBuf buffer) {
        return new ThermalWorldManager.ThermalDiagnostics(
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong());
    }

    private static int readBoundedCount(FriendlyByteBuf buffer, int maximum, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
        return count;
    }
}

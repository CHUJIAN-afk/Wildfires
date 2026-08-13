package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.content.StationControlMenu;
import first.wildfires.space.content.StationDriveIndex;
import first.wildfires.space.route.StationRouteRuntime;
import first.wildfires.space.route.StationTravelRequest;
import first.wildfires.space.route.StationTravelResult;
import first.wildfires.space.route.StationTravelService;
import first.wildfires.space.route.StationTravelMode;
import first.wildfires.space.content.StationJumpDriveIndex;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.capsule.ReturnCapsuleService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** C2S control-computer intent. It cannot name dimensions, bodies or journey phases. */
public record RequestStationTravelPacket(StationTravelRequest request) implements ICustomPacketPayload {

    private static final int MAX_RESOURCE_ID_LENGTH = 256;
    private static final int MAX_MODE_LENGTH = 16;

    public RequestStationTravelPacket {
        Objects.requireNonNull(request, "request");
    }

    public RequestStationTravelPacket(FriendlyByteBuf buffer) {
        this(readRequest(buffer));
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(request.computerPos());
        buffer.writeUUID(request.stationId());
        buffer.writeVarLong(request.expectedRevision());
        buffer.writeUtf(request.routeId().toString(), MAX_RESOURCE_ID_LENGTH);
        buffer.writeUtf(request.mode().id(), MAX_MODE_LENGTH);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        StationTravelResult result = StationTravelService.start(
                SpaceSavedData.get(player.server), player.getUUID(), request,
                StationRouteRuntime.current(), CelestialRegistryRuntime.current(),
                new ServerValidation(player), player.server.overworld().getGameTime(), UUID.randomUUID());
        player.displayClientMessage(Component.translatable(result.status().translationKey()), true);
        if (result.successful()) {
            player.closeContainer();
        }
    }

    private static StationTravelRequest readRequest(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID stationId = buffer.readUUID();
        long revision = buffer.readVarLong();
        String value = buffer.readUtf(MAX_RESOURCE_ID_LENGTH);
        ResourceLocation routeId = ResourceLocation.tryParse(value);
        if (routeId == null) {
            throw new IllegalArgumentException("Invalid station route id: " + value);
        }
        StationTravelMode mode = StationTravelMode.fromId(buffer.readUtf(MAX_MODE_LENGTH))
                .orElseThrow(() -> new IllegalArgumentException("Invalid station travel mode"));
        return new StationTravelRequest(pos, stationId, revision, routeId, mode);
    }

    private record ServerValidation(ServerPlayer player) implements StationTravelService.ValidationContext {

        @Override
        public boolean allReturnCapsulesDocked(StationRecord station) {
            return ReturnCapsuleService.allDocked(player.server, station);
        }

        @Override
        public boolean validControlComputer(StationRecord station, StationTravelRequest request) {
            if (player.serverLevel().dimension() != SpaceDimensions.ORBIT
                    || !(player.containerMenu instanceof StationControlMenu menu)
                    || !menu.matches(request.computerPos(), request.stationId(), request.expectedRevision())
                    || !menu.stillValid(player)
                    || !player.serverLevel().getBlockState(request.computerPos())
                    .is(SpaceContentRegister.STATION_CONTROL_COMPUTER.get())) {
                return false;
            }
            return SpaceSavedData.get(player.server).stationAt(
                            request.computerPos().getX(), request.computerPos().getZ())
                    .filter(current -> current.stationId().equals(station.stationId()))
                    .filter(current -> current.region().containsBuildArea(request.computerPos()))
                    .isPresent();
        }

        @Override
        public boolean hasLoadedTestEngine(StationRecord station) {
            return StationDriveIndex.hasLoadedEngine(player.serverLevel(), station);
        }

        @Override
        public boolean hasLoadedJumpTestEngine(StationRecord station) {
            return StationJumpDriveIndex.hasLoadedEngine(player.serverLevel(), station);
        }
    }
}

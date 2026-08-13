package first.wildfires.space.route;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import first.wildfires.space.station.StationJourneyPhase;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable directed route data. Travel times are fixed server game-time durations. */
public record StationRouteDefinition(
        ResourceLocation id,
        ResourceLocation fromBody,
        ResourceLocation toBody,
        long departureTicks,
        long cruiseTicks,
        long arrivalTicks,
        boolean enabled) {

    /** Default NTM-style timings used for automatically available stable-orbit transfers. */
    public static final long FREE_TRANSFER_DEPARTURE_TICKS = 200L;
    public static final long FREE_TRANSFER_CRUISE_TICKS = 600L;
    public static final long FREE_TRANSFER_ARRIVAL_TICKS = 200L;
    private static final String FREE_TRANSFER_PATH = "orbital_transfer/";

    public static final Codec<StationRouteDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(StationRouteDefinition::id),
            ResourceLocation.CODEC.fieldOf("from_body").forGetter(StationRouteDefinition::fromBody),
            ResourceLocation.CODEC.fieldOf("to_body").forGetter(StationRouteDefinition::toBody),
            Codec.LONG.fieldOf("departure_ticks").forGetter(StationRouteDefinition::departureTicks),
            Codec.LONG.fieldOf("cruise_ticks").forGetter(StationRouteDefinition::cruiseTicks),
            Codec.LONG.fieldOf("arrival_ticks").forGetter(StationRouteDefinition::arrivalTicks),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(StationRouteDefinition::enabled)
    ).apply(instance, StationRouteDefinition::new));

    public StationRouteDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromBody, "fromBody");
        Objects.requireNonNull(toBody, "toBody");
        if (fromBody.equals(toBody)) {
            throw new IllegalArgumentException("A station route cannot target its origin: " + fromBody);
        }
        requireNonNegative(departureTicks, "departureTicks");
        requireNonNegative(cruiseTicks, "cruiseTicks");
        requireNonNegative(arrivalTicks, "arrivalTicks");
        addExact(addExact(departureTicks, cruiseTicks), arrivalTicks);
    }

    public long totalDurationTicks() {
        return addExact(addExact(departureTicks, cruiseTicks), arrivalTicks);
    }

    public long durationFor(StationJourneyPhase phase) {
        return switch (Objects.requireNonNull(phase, "phase")) {
            case DEPARTING -> departureTicks;
            case CRUISE -> cruiseTicks;
            case ARRIVING -> arrivalTicks;
            case JUMP_ACCELERATING, JUMP_CRUISING, JUMP_DECELERATING -> throw new IllegalArgumentException(
                    "Jump phase has a fixed relativistic duration: " + phase.id());
            case ORBITING, FAULTED -> throw new IllegalArgumentException(
                    "Phase has no route duration: " + phase.id());
        };
    }

    public boolean connects(ResourceLocation origin, ResourceLocation target) {
        return fromBody.equals(origin) && toBody.equals(target);
    }

    /**
     * Builds the deterministic direct route exposed whenever a station is stably orbiting a
     * non-stellar celestial. Data packs may still replace a directed pair with custom timing.
     */
    public static StationRouteDefinition freeTransfer(ResourceLocation fromBody,
                                                       ResourceLocation toBody) {
        return new StationRouteDefinition(freeTransferId(fromBody, toBody), fromBody, toBody,
                FREE_TRANSFER_DEPARTURE_TICKS, FREE_TRANSFER_CRUISE_TICKS,
                FREE_TRANSFER_ARRIVAL_TICKS, true);
    }

    public static ResourceLocation freeTransferId(ResourceLocation fromBody, ResourceLocation toBody) {
        Objects.requireNonNull(fromBody, "fromBody");
        Objects.requireNonNull(toBody, "toBody");
        if (fromBody.equals(toBody)) {
            throw new IllegalArgumentException("A free transfer cannot target its origin: " + fromBody);
        }
        return ResourceLocation.fromNamespaceAndPath("wildfires", FREE_TRANSFER_PATH
                + fromBody.getNamespace() + "/" + fromBody.getPath()
                + "_to/" + toBody.getNamespace() + "/" + toBody.getPath());
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    private static long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Station route duration overflows long", exception);
        }
    }
}

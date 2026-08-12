package first.wildfires.space.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable base pressure and gas composition for one celestial destination. */
public record CelestialEnvironment(
        double totalPressureKpa,
        Map<ResourceLocation, Double> gasesKpa,
        Set<ResourceLocation> hazards) {

    public static final double MAX_TOTAL_PRESSURE_KPA = 100_000.0D;
    public static final int MAX_GAS_COMPONENTS = 64;
    public static final int MAX_HAZARDS = 64;

    private static final Codec<CelestialEnvironment> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("total_pressure_kpa").forGetter(CelestialEnvironment::totalPressureKpa),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE)
                    .optionalFieldOf("gases_kpa", Map.of()).forGetter(CelestialEnvironment::gasesKpa),
            ResourceLocation.CODEC.listOf().optionalFieldOf("hazards", List.of())
                    .forGetter(environment -> List.copyOf(environment.hazards()))
    ).apply(instance, (pressure, gases, hazards) ->
            new CelestialEnvironment(pressure, gases, new LinkedHashSet<>(hazards))));

    public static final Codec<CelestialEnvironment> CODEC = RAW_CODEC.comapFlatMap(
            CelestialEnvironment::validated,
            environment -> environment);

    public CelestialEnvironment {
        gasesKpa = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(gasesKpa, "gasesKpa")));
        hazards = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(hazards, "hazards")));
        validate(totalPressureKpa, gasesKpa, hazards);
    }

    public boolean isVacuum() {
        return totalPressureKpa <= 1.0E-6D;
    }

    public double partialPressure(ResourceLocation gas) {
        return gasesKpa.getOrDefault(Objects.requireNonNull(gas, "gas"), 0.0D);
    }

    public double gasPressureSumKpa() {
        double sum = 0.0D;
        for (double partialPressure : gasesKpa.values()) {
            sum += partialPressure;
        }
        return sum;
    }

    public static CelestialEnvironment vacuum() {
        return new CelestialEnvironment(0.0D, Map.of(), Set.of());
    }

    private static DataResult<CelestialEnvironment> validated(CelestialEnvironment environment) {
        try {
            validate(environment.totalPressureKpa, environment.gasesKpa, environment.hazards);
            return DataResult.success(environment);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validate(double pressure, Map<ResourceLocation, Double> gases,
                                 Set<ResourceLocation> hazards) {
        if (!Double.isFinite(pressure) || pressure < 0.0D || pressure > MAX_TOTAL_PRESSURE_KPA) {
            throw new IllegalArgumentException("total_pressure_kpa must be finite and within 0.."
                    + MAX_TOTAL_PRESSURE_KPA + ": " + pressure);
        }
        if (gases.size() > MAX_GAS_COMPONENTS) {
            throw new IllegalArgumentException("Too many gas components: " + gases.size());
        }
        if (hazards.size() > MAX_HAZARDS) {
            throw new IllegalArgumentException("Too many environment hazards: " + hazards.size());
        }
        double gasSum = 0.0D;
        for (Map.Entry<ResourceLocation, Double> entry : gases.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "gas id");
            Double partialPressure = Objects.requireNonNull(entry.getValue(), "gas partial pressure");
            if (!Double.isFinite(partialPressure) || partialPressure < 0.0D
                    || partialPressure > MAX_TOTAL_PRESSURE_KPA) {
                throw new IllegalArgumentException("Gas partial pressure must be finite and non-negative for "
                        + entry.getKey() + ": " + partialPressure);
            }
            gasSum += partialPressure;
            if (!Double.isFinite(gasSum)) {
                throw new IllegalArgumentException("Gas partial pressure sum is not finite");
            }
        }
        double tolerance = Math.max(0.001D, pressure * 0.001D);
        if (gasSum > pressure + tolerance) {
            throw new IllegalArgumentException("Gas partial pressure sum exceeds total pressure: "
                    + gasSum + " > " + pressure);
        }
        for (ResourceLocation hazard : hazards) {
            Objects.requireNonNull(hazard, "hazard id");
        }
    }
}

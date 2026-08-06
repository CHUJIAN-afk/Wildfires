package first.wildfires.thermal;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/** Server-side controls for the world thermal solver. */
public final class ThermalConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec COMMON_SPEC;
    private static final ForgeConfigSpec SERVER_SPEC;
    private static final ThermalValues COMMON_DEFAULTS;
    private static final ThermalValues SERVER_VALUES;

    static {
        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON_DEFAULTS = defineThermalValues(commonBuilder,
                "Defaults copied into wildfires-server.toml when a new world is created.");
        COMMON_SPEC = commonBuilder.build();

        ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();
        SERVER_VALUES = defineThermalValues(serverBuilder,
                "World-specific thermal settings. These values are authoritative after world creation.");
        SERVER_SPEC = serverBuilder.build();
    }

    private static ThermalValues defineThermalValues(ForgeConfigSpec.Builder builder, String sectionComment) {
        builder.comment(sectionComment).push("thermal");
        ForgeConfigSpec.BooleanValue laplacianEnabled = builder
                .comment("Allow adjacent air cells to exchange heat through the Laplacian solver.")
                .define("laplacianEnabled", true);
        ForgeConfigSpec.DoubleValue laplacianCoefficient = builder
                .comment("Fraction by which air approaches the six-neighbor average per 0.125 second standard step.")
                .defineInRange("laplacianCoefficient", 0.6D, 0.0D, 1.0D);
        ForgeConfigSpec.DoubleValue buoyancyCoefficient = builder
                .comment("Fraction of a positive-temperature vertical difference transferred upward per standard solver step. Zero disables hot-air rise.")
                .defineInRange("buoyancyCoefficient", 0.05D, 0.0D, 1.0D);
        ForgeConfigSpec.DoubleValue coldSinkingCoefficient = builder
                .comment("Fraction of a negative-temperature vertical difference transferred downward per standard solver step. Zero disables cold-air sinking.")
                .defineInRange("coldSinkingCoefficient", 0.05D, 0.0D, 1.0D);
        ForgeConfigSpec.BooleanValue radiationEnabled = builder
                .comment("Allow exposed thermal-source faces to affect nearby players through line-of-sight radiation.")
                .define("radiationEnabled", true);
        ForgeConfigSpec.DoubleValue defaultSolidLoss = builder
                .comment("Fraction of the air/background temperature difference lost per full solid face and standard solver step.")
                .defineInRange("defaultSolidLoss", 0.001D, 0.0D, 1.0D);
        ForgeConfigSpec.DoubleValue airTemperatureCutoff = builder
                .comment("Absolute WTU needed for hidden air temperature to become visible to gameplay and diagnostics. Zero disables the visibility threshold.")
                .defineInRange("airTemperatureCutoff", 1.0D, 0.0D, 3276.7D);
        ForgeConfigSpec.DoubleValue hiddenTemperatureCutoff = builder
                .comment("Absolute WTU below which the hidden thermal solver discards numerical temperature. Lower values preserve a wider low-temperature tail at higher memory and solver cost.")
                .defineInRange("hiddenTemperatureCutoff", 0.05D, 0.01D, 1.0D);
        ForgeConfigSpec.IntValue cellBudgetPerTick = builder
                .comment("Soft target for air-cell substep work admitted to one asynchronous epoch. The scheduler may admit at least one Section per thermal worker so spare CPU cores remain usable.")
                .defineInRange("cellBudgetPerTick", 20000, 1000, 200000);
        builder.pop();
        return new ThermalValues(laplacianEnabled, laplacianCoefficient, buoyancyCoefficient,
                coldSinkingCoefficient, radiationEnabled, defaultSolidLoss, airTemperatureCutoff,
                hiddenTemperatureCutoff, cellBudgetPerTick);
    }

    private ThermalConfig() {
    }

    public static void register() {
        ModLoadingContext context = ModLoadingContext.get();
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "wildfires-common.toml");
        context.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "wildfires-server.toml");
    }

    /** Copies the global template into the newly created world's authoritative server config. */
    public static void applyCommonDefaultsToNewWorld() {
        SERVER_VALUES.laplacianEnabled().set(COMMON_DEFAULTS.laplacianEnabled().get());
        SERVER_VALUES.laplacianCoefficient().set(COMMON_DEFAULTS.laplacianCoefficient().get());
        SERVER_VALUES.buoyancyCoefficient().set(COMMON_DEFAULTS.buoyancyCoefficient().get());
        SERVER_VALUES.coldSinkingCoefficient().set(COMMON_DEFAULTS.coldSinkingCoefficient().get());
        SERVER_VALUES.radiationEnabled().set(COMMON_DEFAULTS.radiationEnabled().get());
        SERVER_VALUES.defaultSolidLoss().set(COMMON_DEFAULTS.defaultSolidLoss().get());
        SERVER_VALUES.airTemperatureCutoff().set(COMMON_DEFAULTS.airTemperatureCutoff().get());
        SERVER_VALUES.hiddenTemperatureCutoff().set(COMMON_DEFAULTS.hiddenTemperatureCutoff().get());
        SERVER_VALUES.cellBudgetPerTick().set(COMMON_DEFAULTS.cellBudgetPerTick().get());
        SERVER_SPEC.save();
        LOGGER.info("Applied global Wildfires thermal defaults to the new world's server config");
    }

    public static boolean laplacianEnabled() {
        return SERVER_VALUES.laplacianEnabled().get();
    }

    public static float laplacianCoefficient() {
        return SERVER_VALUES.laplacianCoefficient().get().floatValue();
    }

    public static float buoyancyCoefficient() {
        return SERVER_VALUES.buoyancyCoefficient().get().floatValue();
    }

    public static float coldSinkingCoefficient() {
        return SERVER_VALUES.coldSinkingCoefficient().get().floatValue();
    }

    public static boolean radiationEnabled() {
        return SERVER_VALUES.radiationEnabled().get();
    }

    public static float defaultSolidLoss() {
        return SERVER_VALUES.defaultSolidLoss().get().floatValue();
    }

    public static float airTemperatureCutoff() {
        return SERVER_VALUES.airTemperatureCutoff().get().floatValue();
    }

    public static float hiddenTemperatureCutoff() {
        return SERVER_VALUES.hiddenTemperatureCutoff().get().floatValue();
    }

    public static int cellBudgetPerTick() {
        return SERVER_VALUES.cellBudgetPerTick().get();
    }

    private record ThermalValues(ForgeConfigSpec.BooleanValue laplacianEnabled,
                                 ForgeConfigSpec.DoubleValue laplacianCoefficient,
                                 ForgeConfigSpec.DoubleValue buoyancyCoefficient,
                                 ForgeConfigSpec.DoubleValue coldSinkingCoefficient,
                                 ForgeConfigSpec.BooleanValue radiationEnabled,
                                 ForgeConfigSpec.DoubleValue defaultSolidLoss,
                                 ForgeConfigSpec.DoubleValue airTemperatureCutoff,
                                 ForgeConfigSpec.DoubleValue hiddenTemperatureCutoff,
                                 ForgeConfigSpec.IntValue cellBudgetPerTick) {
    }
}

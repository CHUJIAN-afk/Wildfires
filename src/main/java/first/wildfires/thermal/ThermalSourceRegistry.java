package first.wildfires.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures the static radiative properties of thermal source blocks.
 */
public final class ThermalSourceRegistry {

    private static final Map<Block, ThermalSourceDefinition> THERMAL_SOURCES = new HashMap<>();

    static {
        /*
         * 黑色陶瓦：标准复杂热源。
         * maximumTemperature = 100F：该热源参与叠加时，复杂热场最高不会超过 100F。
         * radiationTemperature = 10F：每个开放面的初始辐射温度。
         * radiationRadius = 24：热包最多向外传播 24 格。
         * readBlockEntityTemperature = false：不读取方块实体温度，始终使用固定辐射温度。
         * forceLoad = false：不会因为热源强制加载所在区块。
         * propagationLossPerBlock = 0.5F：热包每传播一格损失 0.5F，上升方向损失更少。
         * simpleHeatSource = false：使用复杂热包，支持墙面反射、向上浮升和热源叠加。
         */
        register(Blocks.BLACK_TERRACOTTA, new ThermalSourceDefinition(
                100.0F, // 热源允许的最高温度 / Maximum temperature allowed for this source
                10.0F,  // 初始辐射温度 / Initial radiated temperature
                24,     // 辐射半径（格） / Radiation radius in blocks
                false,  // 是否读取方块实体温度 / Read temperature from the block entity
                false,  // 是否强加载所在区块 / Keep the source chunk force-loaded
                0.5F,   // 传播损耗；null 为自动计算 / Propagation loss; null uses automatic calculation
                false   // 是否使用简易热源传播 / Use simple light-style thermal propagation
        ));
        /*
         * 红色陶瓦：高上限复杂热源，传播规则与黑色陶瓦相同。
         * maximumTemperature = 500F：只要该热源影响某空气格，复杂热量的总上限可提高到 500F。
         * radiationTemperature = 10F：单个红色陶瓦起始辐射仍为 10F，不会直接输出 500F。
         * radiationRadius = 24、propagationLossPerBlock = 0.5F：覆盖范围和衰减与黑色陶瓦一致。
         * simpleHeatSource = false：属于同一个复杂热源池，可与黑色陶瓦共同叠加、反射。
         */
        register(Blocks.RED_TERRACOTTA, new ThermalSourceDefinition(
                500.0F,
                10.0F,
                24,
                false,
                false,
                0.5F,
                false
        ));
        /*
         * 白色陶瓦：简易热源，使用类似原版方块光照的最大值传播。
         * maximumTemperature = 100F：单个热场的安全上限。
         * radiationTemperature = 10F，radiationRadius = 24：初始温度和最大传播距离。
         * propagationLossPerBlock = null：自动按“初始辐射温度 / 半径”计算每格损耗。
         * simpleHeatSource = true：同种白色陶瓦不累加，只取该格最强值；不计算反射。
         * 该模式带有 Section 缓存和集群采样，适合大规模铺设，性能开销远低于复杂热源。
         */
        register(Blocks.WHITE_TERRACOTTA, new ThermalSourceDefinition(
                100.0F, // 热源允许的最高温度 / Maximum temperature allowed for this source
                10.0F,  // 初始辐射温度 / Initial radiated temperature
                24,     // 辐射半径（格） / Radiation radius in blocks
                false,  // 是否读取方块实体温度 / Read temperature from the block entity
                false,  // 是否强加载所在区块 / Keep the source chunk force-loaded
                null,   // 传播损耗；null 为自动计算 / Propagation loss; null uses automatic calculation
                true    // 是否使用简易热源传播 / Use simple light-style thermal propagation
        ));
    }

    private ThermalSourceRegistry() {
    }

    public static void register(Block block, ThermalSourceDefinition definition) {
        THERMAL_SOURCES.put(block, definition);
    }

    public static ThermalSourceDefinition getDefinition(BlockState state) {
        return THERMAL_SOURCES.get(state.getBlock());
    }

    public static boolean isThermalSource(BlockState state) {
        return getDefinition(state) != null;
    }

    public static int getMaximumRadiationRadius() {
        return THERMAL_SOURCES.values().stream()
                .mapToInt(ThermalSourceDefinition::radiationRadius)
                .max()
                .orElse(0);
    }

    public static int getMaximumSimpleRadiationRadius() {
        return THERMAL_SOURCES.values().stream()
                .filter(ThermalSourceDefinition::simpleHeatSource)
                .mapToInt(ThermalSourceDefinition::radiationRadius)
                .max()
                .orElse(0);
    }

    public static int getMaximumComplexRadiationRadius() {
        return THERMAL_SOURCES.values().stream()
                .filter(definition -> !definition.simpleHeatSource())
                .mapToInt(ThermalSourceDefinition::radiationRadius)
                .max()
                .orElse(0);
    }

    public static float getRadiationTemperature(Level level, BlockPos position, BlockState state) {
        ThermalSourceDefinition definition = getDefinition(state);
        if (definition == null) {
            return 0.0F;
        }

        float temperature = definition.radiationTemperature();
        if (definition.readBlockEntityTemperature()) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof ThermalTemperatureProvider provider) {
                temperature = provider.getThermalTemperature();
            }
        }
        return clampToSourceLimit(temperature, definition.maximumTemperature());
    }

    private static float clampToSourceLimit(float temperature, float maximumTemperature) {
        float limit = Math.abs(maximumTemperature);
        return Math.max(-limit, Math.min(temperature, limit));
    }

    public record ThermalSourceDefinition(
            float maximumTemperature,
            float radiationTemperature,
            int radiationRadius,
            boolean readBlockEntityTemperature,
            boolean forceLoad,
            /**
             * 每格传播损耗；null 时自动使用当前辐射温度除以辐射半径。
             * Per-block propagation loss; null uses current radiation temperature divided by radius.
             */
            Float propagationLossPerBlock,
            boolean simpleHeatSource
    ) {
        public ThermalSourceDefinition {
            if (maximumTemperature == 0.0F || radiationRadius < 1) {
                throw new IllegalArgumentException("Thermal sources need a temperature limit and a positive radius");
            }
        }

        public float getAttenuation(float currentRadiationTemperature) {
            if (propagationLossPerBlock != null) {
                return Math.abs(propagationLossPerBlock);
            }
            return Math.abs(currentRadiationTemperature) / radiationRadius;
        }
    }
}

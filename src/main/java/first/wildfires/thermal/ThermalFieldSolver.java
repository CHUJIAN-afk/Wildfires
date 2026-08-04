package first.wildfires.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Disabled legacy solver. The active implementation uses ThermalGrid and the
 * section field caches. Keep this source commented until the old algorithm is
 * either removed permanently or reintroduced deliberately.
 */
/*
final class ThermalFieldSolver {

    private static final int MAX_SUPPORTED_RADIUS = 24;
    private static final int MAX_REFLECTION_PASSES = 3;
    private static final float WALL_REFLECTION = 0.6F;
    private static final float MINIMUM_TRANSFER_DIFFERENCE = 0.5F;
    private static final float OPEN_AIR_RETENTION = 0.55F;
    private static final float MAX_TEMPERATURE_OFFSET = 80.0F;

    private ThermalFieldSolver() {
    }

    static float solve(Level level, BlockPos origin) {
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> countedSources = new HashSet<>();
        queue.add(new SearchNode(origin.immutable(), 0));
        visited.add(origin.asLong());

        float positiveContribution = 0.0F;
        float negativeContribution = 0.0F;
        float positiveLimit = 0.0F;
        float negativeLimit = 0.0F;
        int wallContacts = 0;
        int maximumRadius = Math.min(MAX_SUPPORTED_RADIUS, ThermalSourceRegistry.getMaximumRadiationRadius());

        while (!queue.isEmpty()) {
            SearchNode current = queue.removeFirst();
            BlockPos currentPos = current.position();

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = currentPos.relative(direction);
                int distance = current.distance() + 1;
                if (distance > maximumRadius || !level.hasChunkAt(neighbor)) {
                    continue;
                }

                BlockState state = level.getBlockState(neighbor);
                ThermalSourceRegistry.ThermalSourceDefinition source = ThermalSourceRegistry.getDefinition(state);
                if (source != null) {
                    if (!countedSources.add(neighbor.asLong())) {
                        continue;
                    }

                    if (distance > source.radiationRadius()) {
                        continue;
                    }

                    float sourceTemperature = ThermalSourceRegistry.getRadiationTemperature(level, neighbor, state);
                    float transferredOffset = attenuate(sourceTemperature, source.getAttenuation(sourceTemperature), distance);
                    if (Math.abs(transferredOffset) > MINIMUM_TRANSFER_DIFFERENCE) {
                        if (transferredOffset > 0.0F) {
                            positiveContribution += transferredOffset;
                            positiveLimit = Math.max(positiveLimit, Math.abs(source.maximumTemperature()));
                        } else {
                            negativeContribution += Math.abs(transferredOffset);
                            negativeLimit = Math.max(negativeLimit, Math.abs(source.maximumTemperature()));
                        }
                    }
                    continue;
                }

                if (!isPassable(level, neighbor, state)) {
                    wallContacts++;
                    continue;
                }

                if (visited.add(neighbor.asLong())) {
                    queue.addLast(new SearchNode(neighbor.immutable(), distance));
                }
            }
        }

        float reflectionMultiplier = getReflectionMultiplier(wallContacts, visited.size());
        float heat = Math.min(positiveLimit, positiveContribution * reflectionMultiplier);
        float cold = Math.min(negativeLimit, negativeContribution * reflectionMultiplier);
        return Math.max(-MAX_TEMPERATURE_OFFSET, Math.min(heat - cold, MAX_TEMPERATURE_OFFSET));
    }

    private static float attenuate(float temperature, float attenuation, int distance) {
        if (temperature == 0.0F) {
            return 0.0F;
        }
        return temperature - Math.copySign(attenuation * distance, temperature);
    }

    private static float getReflectionMultiplier(int wallContacts, int visitedAirBlocks) {
        if (wallContacts == 0 || visitedAirBlocks == 0) {
            return 1.0F;
        }

        float wallCoverage = Math.min(1.0F, wallContacts / (float) (visitedAirBlocks * Direction.values().length));
        float reflectedEnergy = 0.0F;
        float passEnergy = WALL_REFLECTION * wallCoverage;
        for (int pass = 0; pass < MAX_REFLECTION_PASSES; pass++) {
            reflectedEnergy += passEnergy;
            passEnergy *= WALL_REFLECTION * wallCoverage;
        }
        return 1.0F + reflectedEnergy;
    }

    private static boolean isPassable(Level level, BlockPos position, BlockState state) {
        return state.isAir() || state.getCollisionShape(level, position).isEmpty();
    }

    private record SearchNode(BlockPos position, int distance) {
    }
}
*/

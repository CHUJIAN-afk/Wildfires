package first.wildfires.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sparse, virtual temperature cells. Values never modify world blocks.
 */
public final class ThermalGrid {

    private static final float EMPTY = 0.0F;
    private static final int MAX_REFLECTION_PASSES = 3;
    private static final float WALL_REFLECTION = 0.15F;
    private static final float MINIMUM_PACKET_TEMPERATURE = 0.5F;
    private static final float TEMPERATURE_PRECISION = 10.0F;
    private static final float BUOYANT_ATTENUATION_MULTIPLIER = 0.75F;
    private static final float OPPOSED_ATTENUATION_MULTIPLIER = 1.25F;
    private static final int FULL_DETAIL_SOURCE_LIMIT = 256;
    private static final int TARGET_DENSE_SOURCE_SEEDS = 512;
    private static final int CONNECTED_SOURCE_CELL_SIZE = 5;
    private static final int CONNECTED_SOURCE_CLUSTER_LIMIT = CONNECTED_SOURCE_CELL_SIZE
            * CONNECTED_SOURCE_CELL_SIZE * CONNECTED_SOURCE_CELL_SIZE;
    private static final int INDIVIDUAL_SOURCE_STACK_LIMIT = 64;
    private static final Map<FieldKey, Map<Long, Float>> CELLS = new HashMap<>();
    private static final Map<FieldKey, Map<Long, Float>> CELL_LIMITS = new HashMap<>();
    private static final Map<FieldKey, Map<Long, Float>> REFLECTED_CELLS = new HashMap<>();
    private static final Map<FieldKey, Map<Long, Long>> PRIMARY_CELL_SOURCES = new HashMap<>();

    private ThermalGrid() {
    }

    public static synchronized float get(Level level, BlockPos position) {
        return CELLS.getOrDefault(FieldKey.of(level), Map.of()).getOrDefault(position.asLong(), EMPTY);
    }

    public static synchronized void set(Level level, BlockPos position, float temperatureOffset) {
        Map<Long, Float> cells = CELLS.computeIfAbsent(FieldKey.of(level), ignored -> new HashMap<>());
        temperatureOffset = roundTemperature(temperatureOffset);
        if (Math.abs(temperatureOffset) <= 0.5F) {
            cells.remove(position.asLong());
        } else {
            cells.put(position.asLong(), temperatureOffset);
        }
    }

    public static synchronized void clear(Level level) {
        FieldKey key = FieldKey.of(level);
        CELLS.remove(key);
        CELL_LIMITS.remove(key);
        REFLECTED_CELLS.remove(key);
        PRIMARY_CELL_SOURCES.remove(key);
    }

    public static synchronized Map<Long, Float> snapshot(Level level) {
        return Map.copyOf(CELLS.getOrDefault(FieldKey.of(level), Map.of()));
    }

    public static synchronized Map<Long, Float> reflectionSnapshot(Level level) {
        return Map.copyOf(REFLECTED_CELLS.getOrDefault(FieldKey.of(level), Map.of()));
    }

    public static synchronized void rebuildAround(Level level, BlockPos center) {
        clear(level);
        ArrayDeque<Packet> queue = new ArrayDeque<>();
        Map<PacketLocation, StrongestPacket> strongestPackets = new HashMap<>();
        List<SourceCandidate> sources = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int activeRadius = ThermalSourceRegistry.getMaximumComplexRadiationRadius();
        for (int x = -activeRadius; x <= activeRadius; x++) {
            for (int y = -activeRadius; y <= activeRadius; y++) {
                for (int z = -activeRadius; z <= activeRadius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    ThermalSourceRegistry.ThermalSourceDefinition source = ThermalSourceRegistry.getDefinition(state);
                    if (source != null && !source.simpleHeatSource()) {
                        sources.add(new SourceCandidate(cursor.immutable(), state, source));
                    }
                }
            }
        }
        seedExposedSources(level, sources, queue, strongestPackets,
                sources.size() <= INDIVIDUAL_SOURCE_STACK_LIMIT);
        propagate(level, queue, strongestPackets);
    }

    private static void seedExposedSources(Level level, List<SourceCandidate> sources, ArrayDeque<Packet> queue,
                                           Map<PacketLocation, StrongestPacket> strongestPackets, boolean trackSourcesIndividually) {
        Map<Long, ClusterSampling> clusterSampling = buildClusterSampling(sources);
        float highestSourceLimit = 0.0F;
        for (SourceCandidate candidate : sources) {
            highestSourceLimit = Math.max(highestSourceLimit, getFieldTemperatureLimit(candidate.definition()));
        }
        int highestSourceCount = 0;
        for (SourceCandidate candidate : sources) {
            if (getFieldTemperatureLimit(candidate.definition()) == highestSourceLimit) {
                highestSourceCount++;
            }
        }
        boolean preserveHighestSources = !trackSourcesIndividually
                && highestSourceCount <= INDIVIDUAL_SOURCE_STACK_LIMIT;
        for (SourceCandidate candidate : sources) {
            ClusterSampling sampling = clusterSampling.get(candidate.position().asLong());
            float temperature = ThermalSourceRegistry.getRadiationTemperature(level, candidate.position(), candidate.state());
            List<Direction> openFaces = new ArrayList<>();
            int blockedFaces = 0;
            for (Direction direction : Direction.values()) {
                BlockPos target = candidate.position().relative(direction);
                if (!level.hasChunkAt(target)) {
                    continue;
                }
                BlockState targetState = level.getBlockState(target);
                if (ThermalSourceRegistry.isThermalSource(targetState)) {
                    continue;
                }
                if (!isPassable(level, target, targetState)) {
                    blockedFaces++;
                    continue;
                }
                boolean isHighestSource = getFieldTemperatureLimit(candidate.definition()) == highestSourceLimit;
                if (!shouldSeedFace(candidate.position(), direction, sampling)
                        && !(preserveHighestSources && isHighestSource)) {
                    continue;
                }
                openFaces.add(direction);
            }

            float emittedTemperature = temperature;
            PacketProfile profile = new PacketProfile(
                    getFieldTemperatureLimit(candidate.definition()),
                    candidate.definition().getAttenuation(emittedTemperature),
                    candidate.definition().radiationRadius(),
                    candidate.definition().simpleHeatSource(),
                    getSourceIdentity(candidate, trackSourcesIndividually),
                    !trackSourcesIndividually
            );
            for (Direction direction : openFaces) {
                BlockPos target = candidate.position().relative(direction);
                enqueuePacket(level, queue, strongestPackets, target.immutable(), 1,
                        attenuate(emittedTemperature, profile.attenuation(), direction), 0, profile, candidate.position().asLong());
            }

            // A face directly against a wall has no air cell in which to collide. Seed its
            // first reflected packet at the source so the reflected energy can leave through
            // the remaining open faces and add to their direct radiation.
            if (blockedFaces > 0 && !openFaces.isEmpty() && !profile.simpleHeatSource()) {
                enqueuePacket(level, queue, strongestPackets, candidate.position(), 0,
                        emittedTemperature * WALL_REFLECTION, 1, profile, candidate.position().asLong());
            }
        }
    }

    private static float getFieldTemperatureLimit(ThermalSourceRegistry.ThermalSourceDefinition definition) {
        return Math.abs(definition.maximumTemperature());
    }

    private static long getSourceIdentity(SourceCandidate candidate, boolean trackSourcesIndividually) {
        if (trackSourcesIndividually) {
            return candidate.position().asLong();
        }
        return Float.floatToIntBits(candidate.definition().maximumTemperature());
    }

    private static Map<Long, ClusterSampling> buildClusterSampling(List<SourceCandidate> sources) {
        Map<Long, SourceCandidate> byPosition = new HashMap<>();
        for (SourceCandidate source : sources) {
            byPosition.put(source.position().asLong(), source);
        }

        Map<Long, ClusterSampling> result = new HashMap<>();
        Set<Long> assigned = new HashSet<>();
        for (SourceCandidate source : sources) {
            if (!assigned.add(source.position().asLong())) {
                continue;
            }

            ArrayDeque<SourceCandidate> queue = new ArrayDeque<>();
            List<SourceCandidate> component = new ArrayList<>();
            queue.addLast(source);
            int minimumX = source.position().getX();
            int minimumY = source.position().getY();
            int minimumZ = source.position().getZ();
            while (!queue.isEmpty()) {
                SourceCandidate current = queue.removeFirst();
                component.add(current);
                minimumX = Math.min(minimumX, current.position().getX());
                minimumY = Math.min(minimumY, current.position().getY());
                minimumZ = Math.min(minimumZ, current.position().getZ());
                for (Direction direction : Direction.values()) {
                    SourceCandidate neighbor = byPosition.get(current.position().relative(direction).asLong());
                    if (neighbor != null && assigned.add(neighbor.position().asLong())) {
                        queue.addLast(neighbor);
                    }
                }
            }

            int stride = component.size() <= CONNECTED_SOURCE_CLUSTER_LIMIT ? 1
                    : Math.max(CONNECTED_SOURCE_CELL_SIZE, getDenseSourceStride(component.size()));
            ClusterSampling sampling = new ClusterSampling(new BlockPos(minimumX, minimumY, minimumZ), stride);
            for (SourceCandidate member : component) {
                result.put(member.position().asLong(), sampling);
            }
        }
        return result;
    }

    private static int getDenseSourceStride(int sourceCount) {
        if (sourceCount <= FULL_DETAIL_SOURCE_LIMIT) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(Math.cbrt(sourceCount / (double) TARGET_DENSE_SOURCE_SEEDS)));
    }

    private static boolean shouldSeedFace(BlockPos position, Direction direction, ClusterSampling sampling) {
        if (sampling.stride() == 1) {
            return true;
        }
        int stride = sampling.stride();
        BlockPos origin = sampling.origin();
        return switch (direction.getAxis()) {
            case X -> Math.floorMod(position.getY() - origin.getY(), stride) == 0
                    && Math.floorMod(position.getZ() - origin.getZ(), stride) == 0;
            case Y -> Math.floorMod(position.getX() - origin.getX(), stride) == 0
                    && Math.floorMod(position.getZ() - origin.getZ(), stride) == 0;
            case Z -> Math.floorMod(position.getX() - origin.getX(), stride) == 0
                    && Math.floorMod(position.getY() - origin.getY(), stride) == 0;
        };
    }

    private static boolean isPassable(Level level, BlockPos position, BlockState state) {
        return state.isAir() || state.getCollisionShape(level, position).isEmpty();
    }

    private static void propagate(Level level, ArrayDeque<Packet> queue, Map<PacketLocation, StrongestPacket> strongestPackets) {
        while (!queue.isEmpty()) {
            Packet current = queue.removeFirst();
            PacketLocation currentLocation = new PacketLocation(current.position().asLong(), current.reflectionPass(), current.profile());
            StrongestPacket strongestPacket = strongestPackets.get(currentLocation);
            if (strongestPacket != null && Math.abs(current.temperature()) < Math.abs(strongestPacket.temperature())) {
                addDiscardedContribution(level, current, strongestPacket.sourceOrigin());
                continue;
            }

            if (current.steps() > 0) {
                if (current.profile().simpleHeatSource()) {
                    addMaximumContribution(level, current.position(), current.temperature(), current.profile().sourceLimit());
                } else {
                    addCellTemperature(level, current.position(), current.temperature(), current.profile(),
                            current.reflectionPass() > 0, 1.0F);
                }
            }

            boolean emittedReflection = false;
            for (Direction direction : Direction.values()) {
                BlockPos next = current.position().relative(direction);
                int steps = current.steps() + 1;
                if (steps > current.profile().maximumSteps() || !level.hasChunkAt(next)) {
                    continue;
                }

                BlockState state = level.getBlockState(next);
                // A thermal source absorbs a returning packet instead of behaving like a wall.
                // Without this, a source reflects its own direct radiation even in open air.
                if (ThermalSourceRegistry.isThermalSource(state)) {
                    continue;
                }
                if (!state.isAir() && !state.getCollisionShape(level, next).isEmpty()) {
                    if (!current.profile().simpleHeatSource()
                            && !emittedReflection && current.reflectionPass() < MAX_REFLECTION_PASSES) {
                        enqueuePacket(level, queue, strongestPackets, current.position(), current.steps(),
                                current.temperature() * WALL_REFLECTION, current.reflectionPass() + 1, current.profile(), current.sourceOrigin());
                        emittedReflection = true;
                    }
                    continue;
                }

                // Do not read the accumulated cell temperature here. Reflections heat the cell,
                // but never feed their combined value back into later propagation.
                float nextTemperature = attenuate(current.temperature(), current.profile().attenuation(), direction);
                enqueuePacket(level, queue, strongestPackets, next.immutable(), steps, nextTemperature,
                        current.reflectionPass(), current.profile(), current.sourceOrigin());
            }
        }
    }

    private static void addDiscardedContribution(Level level, Packet packet, long strongestSourceOrigin) {
        if (packet.reflectionPass() > 0 && packet.steps() > 0 && !packet.profile().simpleHeatSource()) {
            addCellTemperature(level, packet.position(), packet.temperature(), packet.profile(), true,
                    packet.profile().aggregateSources() && packet.sourceOrigin() != strongestSourceOrigin ? 0.5F : 1.0F);
        } else if (packet.reflectionPass() == 0 && packet.steps() > 0 && packet.profile().aggregateSources()
                && packet.sourceOrigin() != strongestSourceOrigin) {
            addCellTemperature(level, packet.position(), packet.temperature(), packet.profile(), false, 0.5F);
        }
    }

    private static void enqueuePacket(Level level, ArrayDeque<Packet> queue, Map<PacketLocation, StrongestPacket> strongestPackets,
                                      BlockPos position, int steps, float temperature, int reflectionPass,
                                      PacketProfile profile, long sourceOrigin) {
        temperature = roundTemperature(temperature);
        if (steps > profile.maximumSteps() || Math.abs(temperature) <= MINIMUM_PACKET_TEMPERATURE) {
            return;
        }
        PacketLocation location = new PacketLocation(position.asLong(), reflectionPass, profile);
        StrongestPacket strongestPacket = strongestPackets.get(location);
        if (strongestPacket != null && Math.abs(temperature) <= Math.abs(strongestPacket.temperature())) {
            addDiscardedContribution(level, new Packet(position, steps, temperature, reflectionPass, profile, sourceOrigin),
                    strongestPacket.sourceOrigin());
            return;
        }
        strongestPackets.put(location, new StrongestPacket(temperature, sourceOrigin));
        queue.addLast(new Packet(position, steps, temperature, reflectionPass, profile, sourceOrigin));
    }

    private static void addCellTemperature(Level level, BlockPos position, float contribution, PacketProfile profile,
                                           boolean reflected, float contributionScale) {
        FieldKey fieldKey = FieldKey.of(level);
        Map<Long, Float> cells = CELLS.computeIfAbsent(fieldKey, ignored -> new HashMap<>());
        Map<Long, Float> limits = CELL_LIMITS.computeIfAbsent(fieldKey, ignored -> new HashMap<>());
        long key = position.asLong();
        contribution *= contributionScale;
        Map<Long, Long> primarySources = PRIMARY_CELL_SOURCES.computeIfAbsent(fieldKey, ignored -> new HashMap<>());
        long primarySource = primarySources.getOrDefault(key, profile.sourceIdentity());
        if (!primarySources.containsKey(key)) {
            primarySources.put(key, primarySource);
        } else if (primarySource != profile.sourceIdentity()) {
            contribution *= 0.5F;
        }
        float limit = Math.max(limits.getOrDefault(key, EMPTY), Math.abs(profile.sourceLimit()));
        limits.put(key, limit);
        float combined = roundTemperature(cells.getOrDefault(key, EMPTY) + contribution);
        if (Math.abs(combined) <= MINIMUM_PACKET_TEMPERATURE) {
            cells.remove(key);
        } else {
            cells.put(key, Math.max(-limit, Math.min(combined, limit)));
        }
        if (reflected) {
            Map<Long, Float> reflectedCells = REFLECTED_CELLS.computeIfAbsent(fieldKey, ignored -> new HashMap<>());
            float reflectedTotal = roundTemperature(reflectedCells.getOrDefault(key, EMPTY) + contribution);
            if (Math.abs(reflectedTotal) <= MINIMUM_PACKET_TEMPERATURE) {
                reflectedCells.remove(key);
            } else {
                reflectedCells.put(key, reflectedTotal);
            }
        }
    }

    private static void addMaximumContribution(Level level, BlockPos position, float contribution, float sourceLimit) {
        Map<Long, Float> cells = CELLS.computeIfAbsent(FieldKey.of(level), ignored -> new HashMap<>());
        long key = position.asLong();
        float clampedContribution = Math.max(-sourceLimit, Math.min(contribution, sourceLimit));
        if (Math.abs(clampedContribution) > Math.abs(cells.getOrDefault(key, EMPTY))) {
            cells.put(key, clampedContribution);
        }
    }

    private static float attenuate(float temperature, float attenuation, Direction direction) {
        float directionMultiplier = 1.0F;
        if (temperature > 0.0F) {
            directionMultiplier = direction == Direction.UP ? BUOYANT_ATTENUATION_MULTIPLIER
                    : direction == Direction.DOWN ? OPPOSED_ATTENUATION_MULTIPLIER : 1.0F;
        } else if (temperature < 0.0F) {
            directionMultiplier = direction == Direction.DOWN ? BUOYANT_ATTENUATION_MULTIPLIER
                    : direction == Direction.UP ? OPPOSED_ATTENUATION_MULTIPLIER : 1.0F;
        }
        float remainingMagnitude = Math.max(0.0F, Math.abs(temperature) - attenuation * directionMultiplier);
        return roundTemperature(Math.copySign(remainingMagnitude, temperature));
    }

    private static float roundTemperature(float temperature) {
        return Math.round(temperature * TEMPERATURE_PRECISION) / TEMPERATURE_PRECISION;
    }

    private record Packet(BlockPos position, int steps, float temperature, int reflectionPass, PacketProfile profile,
                          long sourceOrigin) {
    }

    private record FieldKey(ResourceKey<Level> dimension, boolean clientSide) {
        private static FieldKey of(Level level) {
            return new FieldKey(level.dimension(), level.isClientSide());
        }
    }

    private record PacketLocation(long position, int reflectionPass, PacketProfile profile) {
    }

    private record StrongestPacket(float temperature, long sourceOrigin) {
    }

    private record PacketProfile(float sourceLimit, float attenuation, int maximumSteps, boolean simpleHeatSource,
                                 long sourceIdentity, boolean aggregateSources) {
    }

    private record SourceCandidate(BlockPos position, BlockState state,
                                   ThermalSourceRegistry.ThermalSourceDefinition definition) {
    }

    private record ClusterSampling(BlockPos origin, int stride) {
    }
}

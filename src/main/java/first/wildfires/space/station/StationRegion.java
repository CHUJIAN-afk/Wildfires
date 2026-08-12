package first.wildfires.space.station;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/** Immutable grid coordinate for one fixed station region in the shared orbit level. */
public record StationRegion(int gridX, int gridZ) {

    public StationRegion {
        long centerX = (long) gridX * SpaceConstants.REGION_SIZE;
        long centerZ = (long) gridZ * SpaceConstants.REGION_SIZE;
        requireAxisWithinWorld(centerX, "gridX");
        requireAxisWithinWorld(centerZ, "gridZ");
    }

    public boolean reserved() {
        return gridX == 0 && gridZ == 0;
    }

    public int centerX() {
        return Math.multiplyExact(gridX, SpaceConstants.REGION_SIZE);
    }

    public int centerZ() {
        return Math.multiplyExact(gridZ, SpaceConstants.REGION_SIZE);
    }

    public int minX() {
        return centerX() - SpaceConstants.REGION_HALF_SIZE;
    }

    public int maxX() {
        return centerX() + SpaceConstants.REGION_HALF_SIZE - 1;
    }

    public int minZ() {
        return centerZ() - SpaceConstants.REGION_HALF_SIZE;
    }

    public int maxZ() {
        return centerZ() + SpaceConstants.REGION_HALF_SIZE - 1;
    }

    public boolean contains(int blockX, int blockZ) {
        return blockX >= minX() && blockX <= maxX() && blockZ >= minZ() && blockZ <= maxZ();
    }

    public boolean containsBuildArea(BlockPos position) {
        long dx = Math.abs((long) position.getX() - centerX());
        long dz = Math.abs((long) position.getZ() - centerZ());
        return dx <= SpaceConstants.BUILD_RADIUS && dz <= SpaceConstants.BUILD_RADIUS;
    }

    public BlockPos safePoint() {
        return new BlockPos(centerX(), SpaceConstants.STATION_SAFE_Y, centerZ());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("grid_x", gridX);
        tag.putInt("grid_z", gridZ);
        return tag;
    }

    public static StationRegion load(CompoundTag tag) {
        if (!tag.contains("grid_x", Tag.TAG_INT) || !tag.contains("grid_z", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Station region is missing grid coordinates");
        }
        return new StationRegion(tag.getInt("grid_x"), tag.getInt("grid_z"));
    }

    public static Optional<StationRegion> fromBlock(int blockX, int blockZ) {
        long gridX = Math.floorDiv((long) blockX + SpaceConstants.REGION_HALF_SIZE,
                SpaceConstants.REGION_SIZE);
        long gridZ = Math.floorDiv((long) blockZ + SpaceConstants.REGION_HALF_SIZE,
                SpaceConstants.REGION_SIZE);
        if (gridX < Integer.MIN_VALUE || gridX > Integer.MAX_VALUE
                || gridZ < Integer.MIN_VALUE || gridZ > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StationRegion((int) gridX, (int) gridZ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static void requireAxisWithinWorld(long center, String name) {
        long minimum = center - SpaceConstants.REGION_HALF_SIZE;
        long maximum = center + SpaceConstants.REGION_HALF_SIZE - 1L;
        if (minimum < -SpaceConstants.MAX_WORLD_COORDINATE
                || maximum > SpaceConstants.MAX_WORLD_COORDINATE) {
            throw new IllegalArgumentException(name + " places station region outside the Minecraft world: "
                    + center);
        }
    }
}

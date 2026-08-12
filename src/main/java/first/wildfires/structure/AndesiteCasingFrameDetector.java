package first.wildfires.structure;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Optional;

/** Detects a vertical 3x3 andesite casing frame with a hollow center. */
public final class AndesiteCasingFrameDetector {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ANDESITE_CASING_ID = "create:andesite_casing";
    private static final int FRAME_RADIUS = 1;
    private static final int MAX_HEIGHT = 32;

    private AndesiteCasingFrameDetector() {
    }

    public static void checkAfterPlacement(Level level, BlockPos placedPos) {
        if (level.isClientSide() || !isAndesiteCasing(level.getBlockState(placedPos))) {
            return;
        }

        find(level, placedPos).ifPresent(frame -> LOGGER.debug(
                "Detected vertical andesite casing frame at {} with size 3x{}x3",
                frame.center(), frame.height()));
    }

    public static void checkAfterBreak(Level level, BlockPos brokenPos) {
        if (level.isClientSide()) {
            return;
        }

        boolean frameRemains = false;
        for (BlockPos center : candidateCenters(brokenPos)) {
            if (hasRing(level, center, brokenPos)) {
                frameRemains = true;
                break;
            }
        }
        if (!frameRemains) {
            LOGGER.debug("Andesite casing frame changed near {}", brokenPos);
        }
    }

    public static Optional<Frame> find(Level level, BlockPos near) {
        for (BlockPos center : candidateCenters(near)) {
            if (hasRing(level, center, null)) {
                return Optional.of(findBounds(level, center));
            }
        }
        return Optional.empty();
    }

    private static Frame findBounds(Level level, BlockPos center) {
        int minY = center.getY();
        int maxY = center.getY();

        while (minY > center.getY() - MAX_HEIGHT && hasRing(level, center.atY(minY - 1), null)) {
            minY--;
        }
        while (maxY < center.getY() + MAX_HEIGHT - 1 && hasRing(level, center.atY(maxY + 1), null)) {
            maxY++;
        }

        return new Frame(new BlockPos(center.getX(), minY, center.getZ()), maxY - minY + 1);
    }

    private static boolean hasRing(Level level, BlockPos center, BlockPos ignored) {
        if (!isAir(level, center, ignored)) {
            return false;
        }

        for (int x = -FRAME_RADIUS; x <= FRAME_RADIUS; x++) {
            for (int z = -FRAME_RADIUS; z <= FRAME_RADIUS; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                BlockPos casingPos = center.offset(x, 0, z);
                if (casingPos.equals(ignored) || !isAndesiteCasing(level.getBlockState(casingPos))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isAir(Level level, BlockPos pos, BlockPos ignored) {
        return !pos.equals(ignored) && level.getBlockState(pos).isAir();
    }

    private static boolean isAndesiteCasing(BlockState state) {
        return ForgeRegistries.BLOCKS.getKey(state.getBlock()) != null
                && ANDESITE_CASING_ID.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
    }

    private static Iterable<BlockPos> candidateCenters(BlockPos casingPos) {
        return () -> new java.util.Iterator<>() {
            private int x = -FRAME_RADIUS;
            private int z = -FRAME_RADIUS;

            @Override
            public boolean hasNext() {
                return x <= FRAME_RADIUS;
            }

            @Override
            public BlockPos next() {
                BlockPos center = casingPos.offset(-x, 0, -z);
                z++;
                if (z > FRAME_RADIUS) {
                    z = -FRAME_RADIUS;
                    x++;
                }
                return center;
            }
        };
    }

    public record Frame(BlockPos base, int height) {
        public BlockPos center() {
            return base.above(height / 2);
        }
    }
}

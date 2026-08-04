package first.wildfires.client;

import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.dries007.tfc.common.blocks.wood.ILeavesBlock;

public final class TfcLeavesCulling {
    private static final String EMBEDDIUM_LEAVES_QUALITY = readEmbeddiumLeavesQuality();

    private TfcLeavesCulling() {
    }

    private static String readEmbeddiumLeavesQuality() {
        try {
            Class<?> sodiumClientMod = Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
            Class<?> optionsClass = Class.forName("me.jellysquid.mods.sodium.client.gui.SodiumGameOptions");
            Class<?> qualityClass = Class.forName("me.jellysquid.mods.sodium.client.gui.SodiumGameOptions$QualitySettings");
            Object options = sodiumClientMod.getMethod("options").invoke(null);
            Object quality = optionsClass.getField("quality").get(options);
            Object leavesQuality = qualityClass.getField("leavesQuality").get(quality);
            return String.valueOf(leavesQuality);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static boolean useFastLeaves() {
        if ("FAST".equals(EMBEDDIUM_LEAVES_QUALITY)) {
            return true;
        }
        if ("FANCY".equals(EMBEDDIUM_LEAVES_QUALITY)) {
            return false;
        }
        return Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
    }

    /**
     * Hides a leaf face only when the two blocks beyond that face are also
     * leaves. This leaves the outer two layers visible in fast mode.
     */
    public static boolean shouldCullLeafSide(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!useFastLeaves() || !(state.getBlock() instanceof ILeavesBlock)) {
            return false;
        }

        BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        for (int distance = 1; distance <= 2; distance++) {
            scratch.move(direction);
            if (!(level.getBlockState(scratch).getBlock() instanceof ILeavesBlock)) {
                return false;
            }
        }
        return true;
    }
}

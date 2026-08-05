package first.wildfires.thermal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ID and tag overrides for passive solid-boundary heat loss. */
public final class ThermalBoundaryRegistry {

    private static final Map<Block, Float> BLOCK_LOSSES = new HashMap<>();
    private static final List<TagLoss> TAG_LOSSES = new ArrayList<>();

    private ThermalBoundaryRegistry() {
    }

    public static synchronized void registerBlock(Block block, float loss) {
        BLOCK_LOSSES.put(block, validateLoss(loss));
    }

    public static synchronized void registerTag(ResourceLocation tagId, float loss) {
        TAG_LOSSES.add(new TagLoss(TagKey.create(Registries.BLOCK, tagId), validateLoss(loss)));
    }

    public static synchronized void clearOverrides() {
        BLOCK_LOSSES.clear();
        TAG_LOSSES.clear();
    }

    public static synchronized float getLoss(BlockState state) {
        Float blockLoss = BLOCK_LOSSES.get(state.getBlock());
        if (blockLoss != null) {
            return blockLoss;
        }
        for (int index = TAG_LOSSES.size() - 1; index >= 0; index--) {
            TagLoss rule = TAG_LOSSES.get(index);
            if (state.is(rule.tag())) {
                return rule.loss();
            }
        }
        return ThermalConfig.defaultSolidLoss();
    }

    private static float validateLoss(float loss) {
        if (!Float.isFinite(loss) || loss < 0.0F || loss > 1.0F) {
            throw new IllegalArgumentException("Solid heat loss must be finite and between 0 and 1: " + loss);
        }
        return loss;
    }

    private record TagLoss(TagKey<Block> tag, float loss) {
    }
}

package first.wildfires.mixin.aquaculture;

import com.teammetallurgy.aquaculture.api.fish.FishData;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = FishData.class, remap = false)
public abstract class FishDataMixin {

    @Shadow
    @Final
    private ConcurrentHashMap<Item, Double> WEIGHT_MIN;

    @Shadow
    @Final
    private ConcurrentHashMap<Item, Double> WEIGHT_MAX;

    @Shadow
    @Final
    private ConcurrentHashMap<Item, Integer> FILLET_AMOUNT;

    /**
     * @author Fix NullPointerException
     * @reason Fix NullPointerException when fish is not registered
     */
    @Overwrite
    public double getMinWeight(Item fish) {
        return WEIGHT_MIN.getOrDefault(fish, 0.0);
    }

    /**
     * @author Fix NullPointerException
     * @reason Fix NullPointerException when fish is not registered
     */
    @Overwrite
    public double getMaxWeight(Item fish) {
        return WEIGHT_MAX.getOrDefault(fish, 0.0);
    }

    /**
     * @author Fix NullPointerException
     * @reason Fix NullPointerException when fish is not registered
     */
    @Overwrite
    public int getFilletAmount(Item fish) {
        return FILLET_AMOUNT.getOrDefault(fish, 0);
    }
}

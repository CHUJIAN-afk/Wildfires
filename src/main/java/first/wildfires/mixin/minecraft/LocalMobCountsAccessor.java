package first.wildfires.mixin.minecraft;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minimal accessor used to enforce a finite, intensity-scaled local mob cap. */
@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public interface LocalMobCountsAccessor {

    @Accessor("counts")
    Object2IntMap<MobCategory> wildfires$getCounts();
}

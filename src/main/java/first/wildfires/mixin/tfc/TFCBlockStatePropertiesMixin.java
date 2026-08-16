package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.blocks.TFCBlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends TFC's shared charcoal-forge heat property with Wildfires' eighth tier. */
@Mixin(value = TFCBlockStateProperties.class, remap = false)
public abstract class TFCBlockStatePropertiesMixin {

    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/properties/IntegerProperty;create(Ljava/lang/String;II)Lnet/minecraft/world/level/block/state/properties/IntegerProperty;"
            ),
            remap = true
    )
    private static IntegerProperty wildfires$extendHeatLevel(String name, int min, int max) {
        return IntegerProperty.create(name, min, "heat_level".equals(name) ? 8 : max);
    }
}

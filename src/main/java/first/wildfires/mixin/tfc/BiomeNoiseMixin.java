package first.wildfires.mixin.tfc;

import first.wildfires.api.NoiseData;
import net.dries007.tfc.world.biome.BiomeNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(BiomeNoise.class)
public class BiomeNoiseMixin {

    @ModifyArgs(
            method = "sharpHills",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/world/noise/Noise2D;scaled(DDDD)Lnet/dries007/tfc/world/noise/Noise2D;"
            )
    )
    private static void scaled(Args args) {
        args.set(2, NoiseData.HIGHLANDS.getDepthMax());
        args.set(3, NoiseData.HIGHLANDS.getDepthMin());
    }

}

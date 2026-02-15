package first.wildfires.mixin.tfc;

import first.wildfires.api.NoiseData;
import net.dries007.tfc.world.biome.BiomeNoise;
import net.dries007.tfc.world.biome.TFCBiomes;
import net.dries007.tfc.world.noise.Noise2D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.LongFunction;

@Mixin(value = TFCBiomes.class, remap = false)
public class TFCBiomesMixin {

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/world/biome/BiomeBuilder;heightmap(Ljava/util/function/LongFunction;)Lnet/dries007/tfc/world/biome/BiomeBuilder;",
                    ordinal = 0
            )
    )
    private static LongFunction<Noise2D> biomeBuilder0(LongFunction<Noise2D> heightNoiseFactory) {
        NoiseData noiseData = NoiseData.OCEAN;
        return seed -> BiomeNoise.ocean(seed, noiseData.getHeightMin(), noiseData.getHeightMax());
    }

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/world/biome/BiomeBuilder;heightmap(Ljava/util/function/LongFunction;)Lnet/dries007/tfc/world/biome/BiomeBuilder;",
                    ordinal = 0
            )
    )
    private static LongFunction<Noise2D> biomeBuilder1(LongFunction<Noise2D> heightNoiseFactory) {
        NoiseData noiseData = NoiseData.OCEAN_REEF;
        return seed -> BiomeNoise.ocean(seed, noiseData.getHeightMin(), noiseData.getHeightMax());
    }

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/world/biome/BiomeBuilder;heightmap(Ljava/util/function/LongFunction;)Lnet/dries007/tfc/world/biome/BiomeBuilder;",
                    ordinal = 2
            )
    )
    private static LongFunction<Noise2D> biomeBuilder2(LongFunction<Noise2D> heightNoiseFactory) {
        NoiseData noiseData = NoiseData.DEEP_OCEAN;
        return seed -> BiomeNoise.ocean(seed, noiseData.getHeightMin(), noiseData.getHeightMax());
    }

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/world/biome/BiomeBuilder;heightmap(Ljava/util/function/LongFunction;)Lnet/dries007/tfc/world/biome/BiomeBuilder;",
                    ordinal = 3
            )
    )
    private static LongFunction<Noise2D> biomeBuilder3(LongFunction<Noise2D> heightNoiseFactory) {
        NoiseData noiseData = NoiseData.DEEP_OCEAN_TRENCH;
        return seed -> BiomeNoise.ocean(seed, noiseData.getHeightMin(), noiseData.getHeightMax());
    }

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/dries007/tfc/world/biome/BiomeBuilder;heightmap(Ljava/util/function/LongFunction;)Lnet/dries007/tfc/world/biome/BiomeBuilder;",
                    ordinal = 15
            )
    )
    private static LongFunction<Noise2D> biomeBuilder15(LongFunction<Noise2D> heightNoiseFactory) {
        NoiseData noiseData = NoiseData.MOUNTAINS;
        return seed -> BiomeNoise.mountains(seed, noiseData.getHeightMin(), noiseData.getHeightMax());
    }

}

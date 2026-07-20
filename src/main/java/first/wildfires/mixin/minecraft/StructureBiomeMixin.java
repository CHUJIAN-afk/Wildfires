package first.wildfires.mixin.minecraft;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Structure.class, priority = 2000)
public class StructureBiomeMixin {

    @Shadow
    @Final
    private Structure.StructureSettings settings;

    @Inject(method = "biomes", at = @At("HEAD"), cancellable = true)
    private void wildfires$useVanillaBiomesOnClient(CallbackInfoReturnable<HolderSet<Biome>> cir) {
        // Structure biome replacement is server-side worldgen. Avoid client-only TFE lookups.
        cir.setReturnValue(settings.biomes());
    }
}

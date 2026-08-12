package first.wildfires.mixin.minecraft;

import first.wildfires.client.space.OrbitClientIllumination;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Removes vanilla's five-percent sky floor while applying station-local NTM sunlight. */
@Mixin(LightTexture.class)
public abstract class OrbitLightTextureMixin {

    @Shadow @Final private Minecraft minecraft;

    @ModifyVariable(
            method = "updateLightTexture",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;dimensionType()Lnet/minecraft/world/level/dimension/DimensionType;",
                    ordinal = 0),
            index = 4
    )
    private float wildfires$exactOrbitSkyMultiplier(float original, float partialTick) {
        if (minecraft.level == null) {
            return original;
        }
        return OrbitClientIllumination.resolve(minecraft.level,
                        minecraft.gameRenderer.getMainCamera().getPosition(), partialTick)
                .map(illumination -> (float) illumination.sunlight())
                .orElse(original);
    }
}

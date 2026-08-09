package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import first.wildfires.client.celestial.CelestialClientTime;
import first.wildfires.client.celestial.CelestialClientStateCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Uses local apparent solar time inside client-only sky, cloud, star and lightmap calculations. */
@Mixin(ClientLevel.class)
public abstract class CelestialClientLevelMixin {

    @ModifyExpressionValue(
            method = "getSkyColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private float wildfires$localSkyColorTime(float original, Vec3 observer, float partialTick) {
        ClientLevel level = (ClientLevel) (Object) this;
        return CelestialClientStateCache.state(level, observer, partialTick)
                .map(state -> CelestialClientTime.visualCelestialAngle(
                        state.daylight().apparentDayTime(), state.solarEclipse(), original)).orElse(original);
    }

    @ModifyExpressionValue(
            method = "getCloudColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private float wildfires$localCloudTime(float original, float partialTick) {
        return wildfires$localCameraTime(original, partialTick);
    }

    @ModifyExpressionValue(
            method = "getStarBrightness",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private float wildfires$localStarTime(float original, float partialTick) {
        return wildfires$localCameraTime(original, partialTick);
    }

    @ModifyExpressionValue(
            method = "getSkyDarken",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F")
    )
    private float wildfires$localLightmapTime(float original, float partialTick) {
        return wildfires$localCameraTime(original, partialTick);
    }

    private float wildfires$localCameraTime(float original, float partialTick) {
        ClientLevel level = (ClientLevel) (Object) this;
        Vec3 observer = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return CelestialClientStateCache.state(level, observer, partialTick)
                .map(state -> CelestialClientTime.visualCelestialAngle(
                        state.daylight().apparentDayTime(), state.solarEclipse(), original)).orElse(original);
    }
}

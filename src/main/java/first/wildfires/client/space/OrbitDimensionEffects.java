package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.client.space.render.OrbitSkyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Dedicated orbit effect; it never applies to a surface dimension. */
public final class OrbitDimensionEffects extends DimensionSpecialEffects {

    public OrbitDimensionEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        // NTM Space WorldProviderOrbit returns (0,0,0) for both fog and sky in vacuum.
        return Vec3.ZERO;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy,
                             Runnable setupFog) {
        if (level.dimension() != SpaceDimensions.ORBIT) {
            return false;
        }
        OrbitSkyRenderer.render(level, partialTick, poseStack, camera, projectionMatrix, isFoggy, setupFog);
        return true;
    }
}

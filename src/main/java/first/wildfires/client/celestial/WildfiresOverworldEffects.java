package first.wildfires.client.celestial;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/** Public Forge sky entry. It only takes ownership of the actual overworld dimension. */
public final class WildfiresOverworldEffects extends DimensionSpecialEffects.OverworldEffects {

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        CelestialRenderer.render(level, ticks, partialTick, poseStack, camera, projectionMatrix, isFoggy, setupFog);
        return true;
    }
}

package first.wildfires.client.celestial;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.celestial.CelestialDiscGeometry;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** Draws the moving Minecraft-style square terrestrial shadow from unified eclipse geometry. */
public final class LunarEclipseRenderer {

    private static ShaderInstance shader;

    private LunarEclipseRenderer() {}

    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Wildfires.rl("lunar_eclipse"),
                DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
    }

    static void render(CelestialState state, PoseStack poseStack, float moonBodyHalfSize,
                       Vec3 skyColor, Vec3 moonTint, float moonAlpha) {
        if (shader == null || !(moonBodyHalfSize > 0.0F)
                || !Float.isFinite(moonBodyHalfSize) || skyColor == null || moonTint == null
                || !(moonAlpha > 0.0F) || !Float.isFinite(moonAlpha)) {
            return;
        }
        CelestialVisualRules.LunarShadow shadow = CelestialVisualRules.lunarShadow(
                state.lunarEclipseRegion());
        if (!shadow.visible()) {
            return;
        }

        CelestialVector moonDirection = state.moon().observerDirection();
        CelestialVisualRules.DiscBasis apiBasis = CelestialVisualRules.stableDiscBasis(
                moonDirection, state.celestialNorth());
        Vec3 direction = CelestialRenderer.worldDirection(moonDirection);
        double layerScale = CelestialDiscGeometry.LUNAR_ECLIPSE_LAYER_RADIUS
                / CelestialDiscGeometry.SKY_SPHERE_RADIUS;
        Vec3 right = CelestialRenderer.worldDirection(apiBasis.right()).scale(moonBodyHalfSize * layerScale);
        Vec3 up = CelestialRenderer.worldDirection(apiBasis.up()).scale(moonBodyHalfSize * layerScale);
        Vec3 center = direction.scale(CelestialDiscGeometry.LUNAR_ECLIPSE_LAYER_RADIUS);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(() -> shader);
        shader.safeGetUniform("ShadowCenter").set((float) shadow.centerX(), (float) shadow.centerY());
        shader.safeGetUniform("ShadowRadius").set((float) shadow.radius());
        shader.safeGetUniform("EclipseIntensity").set((float)
                state.lunarEclipseRegion().umbraCoverage());
        shader.safeGetUniform("PenumbraIntensity").set((float)
                state.lunarEclipseRegion().penumbraCoverage());
        shader.safeGetUniform("SkyColor").set((float) skyColor.x, (float) skyColor.y,
                (float) skyColor.z);
        shader.safeGetUniform("MoonTint").set((float) moonTint.x, (float) moonTint.y,
                (float) moonTint.z);
        shader.safeGetUniform("MoonAlpha").set(moonAlpha);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = poseStack.last().pose();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, matrix, center.subtract(right).subtract(up), 0.0F, 1.0F);
        vertex(builder, matrix, center.add(right).subtract(up), 1.0F, 1.0F);
        vertex(builder, matrix, center.add(right).add(up), 1.0F, 0.0F);
        vertex(builder, matrix, center.subtract(right).add(up), 0.0F, 0.0F);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 point, float u, float v) {
        builder.vertex(matrix, (float) point.x, (float) point.y, (float) point.z).uv(u, v).endVertex();
    }
}

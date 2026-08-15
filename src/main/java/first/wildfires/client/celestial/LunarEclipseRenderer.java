package first.wildfires.client.celestial;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.CelestialDiscGeometry;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** Draws the moving Minecraft-style square terrestrial shadow from unified eclipse geometry. */
public final class LunarEclipseRenderer {

    private static ShaderBindings bindings;

    private LunarEclipseRenderer() {}

    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Wildfires.rl("lunar_eclipse"),
                DefaultVertexFormat.POSITION_TEX), loaded -> bindings = ShaderBindings.create(loaded));
    }

    static void render(CelestialState state, CelestialRenderer.DiscFrame moonFrame,
                       PoseStack poseStack, float moonBodyHalfSize,
                       Vec3 skyColor, CelestialVisualRules.MoonTint moonTint, float moonAlpha) {
        ShaderBindings active = bindings;
        if (active == null || !(moonBodyHalfSize > 0.0F)
                || !Float.isFinite(moonBodyHalfSize) || skyColor == null || moonTint == null
                || !(moonAlpha > 0.0F) || !Float.isFinite(moonAlpha)) {
            return;
        }
        CelestialVisualRules.LunarShadow shadow = CelestialVisualRules.lunarShadow(
                state.lunarEclipseRegion());
        if (!shadow.visible()) {
            return;
        }

        double layerScale = CelestialDiscGeometry.LUNAR_ECLIPSE_LAYER_RADIUS
                / CelestialDiscGeometry.SKY_SPHERE_RADIUS;
        Vec3 right = moonFrame.right().scale(moonBodyHalfSize * layerScale);
        Vec3 up = moonFrame.up().scale(moonBodyHalfSize * layerScale);
        Vec3 center = moonFrame.direction().scale(CelestialDiscGeometry.LUNAR_ECLIPSE_LAYER_RADIUS);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(active);
        active.shadowCenter().set((float) shadow.centerX(), (float) shadow.centerY());
        active.shadowRadius().set((float) shadow.radius());
        active.eclipseIntensity().set((float)
                state.lunarEclipseRegion().umbraCoverage());
        active.penumbraIntensity().set((float)
                state.lunarEclipseRegion().penumbraCoverage());
        active.skyColor().set((float) skyColor.x, (float) skyColor.y,
                (float) skyColor.z);
        active.moonTint().set((float) moonTint.red(), (float) moonTint.green(),
                (float) moonTint.blue());
        active.moonAlpha().set(moonAlpha);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = poseStack.last().pose();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, matrix, (center.x - right.x) - up.x,
                (center.y - right.y) - up.y, (center.z - right.z) - up.z, 0.0F, 1.0F);
        vertex(builder, matrix, (center.x + right.x) - up.x,
                (center.y + right.y) - up.y, (center.z + right.z) - up.z, 1.0F, 1.0F);
        vertex(builder, matrix, (center.x + right.x) + up.x,
                (center.y + right.y) + up.y, (center.z + right.z) + up.z, 1.0F, 0.0F);
        vertex(builder, matrix, (center.x - right.x) + up.x,
                (center.y - right.y) + up.y, (center.z - right.z) + up.z, 0.0F, 0.0F);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               double x, double y, double z, float u, float v) {
        builder.vertex(matrix, (float) x, (float) y, (float) z).uv(u, v).endVertex();
    }

    private record ShaderBindings(ShaderInstance shader,
                                  AbstractUniform shadowCenter,
                                  AbstractUniform shadowRadius,
                                  AbstractUniform eclipseIntensity,
                                  AbstractUniform penumbraIntensity,
                                  AbstractUniform skyColor,
                                  AbstractUniform moonTint,
                                  AbstractUniform moonAlpha)
            implements Supplier<ShaderInstance> {

        private static ShaderBindings create(ShaderInstance shader) {
            return new ShaderBindings(shader,
                    shader.safeGetUniform("ShadowCenter"),
                    shader.safeGetUniform("ShadowRadius"),
                    shader.safeGetUniform("EclipseIntensity"),
                    shader.safeGetUniform("PenumbraIntensity"),
                    shader.safeGetUniform("SkyColor"),
                    shader.safeGetUniform("MoonTint"),
                    shader.safeGetUniform("MoonAlpha"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }
}

package first.wildfires.client.celestial;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.GlStateManager;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.CelestialConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** TFCCaelum-style post-rain rainbow rendered opposite the local sun. */
final class RainbowRenderer {

    private static final ResourceLocation RAINBOW = Wildfires.rl("textures/sky/rainbow.png");
    private static final ResourceLocation OUTER = Wildfires.rl("textures/sky/rainbow_outer.png");
    private static long remainingTicks;
    private static long lastGameTime = Long.MIN_VALUE;

    private RainbowRenderer() {}

    static void render(ClientLevel level, CelestialState state, float partialTick, PoseStack poseStack) {
        if (!CelestialConfig.rainbow()) {
            reset();
            return;
        }
        float rain = level.getRainLevel(partialTick);
        float rainBefore = level.getRainLevel(partialTick - 100.0F);
        float rainAfter = level.getRainLevel(partialTick + 100.0F);
        double dayTime = state.daylight().apparentDayTime();
        long gameTime = level.getGameTime();
        boolean started = false;
        if (remainingTicks <= 0L
                && CelestialVisualRules.startsRainbow(rainBefore, rain, rainAfter, dayTime,
                state.sun().altitudeRadians())) {
            remainingTicks = CelestialVisualRules.RAINBOW_DURATION_TICKS;
            started = true;
        }
        boolean visible = CelestialVisualRules.rainbowVisible(remainingTicks, dayTime,
                state.sun().altitudeRadians());
        if (!started && visible && lastGameTime != Long.MIN_VALUE && gameTime > lastGameTime) {
            remainingTicks = CelestialVisualRules.advanceRainbowTimer(remainingTicks,
                    gameTime - lastGameTime, true);
            visible = CelestialVisualRules.rainbowVisible(remainingTicks, dayTime,
                    state.sun().altitudeRadians());
        }
        lastGameTime = gameTime;
        if (!visible) {
            return;
        }
        float fade = (float) CelestialVisualRules.rainbowAlpha(remainingTicks, state.solarEclipse());
        if (fade <= 0.001F) {
            return;
        }
        Vec3 sun = CelestialRenderer.worldDirection(state.sun().observerDirection());
        CelestialVisualRules.RainbowDirection rainbow = CelestialVisualRules.rainbowDirection(sun.x, sun.z);
        Vec3 direction = new Vec3(rainbow.x(), rainbow.y(), rainbow.z());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        drawLayer(poseStack.last().pose(), direction, 201.0F, 140.0F, 0.6F * fade, RAINBOW);
        drawLayer(poseStack.last().pose(), direction, 202.0F, 280.0F, 0.05F * fade, OUTER);
        RenderSystem.defaultBlendFunc();
    }

    static void reset() {
        remainingTicks = 0L;
        lastGameTime = Long.MIN_VALUE;
    }

    private static void drawLayer(Matrix4f matrix, Vec3 direction, float distance, float size, float alpha,
                                  ResourceLocation texture) {
        if (alpha <= 0.001F) return;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(0.5F, 0.5F, 0.5F, alpha);
        Vec3 center = direction.scale(distance);
        Vec3 reference = Math.abs(direction.y) > 0.98D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = direction.cross(reference).normalize().scale(size);
        Vec3 up = right.cross(direction).normalize().scale(size);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
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

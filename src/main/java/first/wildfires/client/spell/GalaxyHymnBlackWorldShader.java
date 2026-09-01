package first.wildfires.client.spell;

/*
 * Adapted from ArcaneVortex 0.6.8 SkyRipperArrowDeadEffect0Renderer under the
 * user's project-specific visual authorization. Wildfires keeps the original
 * Black World timing/shader and removes all attack, shockwave-entity and
 * tesseract behavior. Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import first.wildfires.Wildfires;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** Full-screen Sky Ripper Black World burst pass without its center tesseract. */
public final class GalaxyHymnBlackWorldShader {

    private static ShaderBindings bindings;

    private GalaxyHymnBlackWorldShader() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("galaxy_hymn_black_world"), DefaultVertexFormat.POSITION),
                loaded -> bindings = ShaderBindings.create(loaded));
    }

    public static void render(Vec3 impactCenter, float normalizedAge, PoseStack levelPoseStack) {
        ShaderBindings active = bindings;
        Minecraft minecraft = Minecraft.getInstance();
        if (active == null || minecraft.level == null || minecraft.player == null
                || impactCenter == null || levelPoseStack == null
                || !Float.isFinite(normalizedAge) || normalizedAge < 0.0F || normalizedAge > 1.0F) {
            return;
        }

        float lightIntensity = normalizedAge < 0.25F
                ? 1.0F - normalizedAge / 0.25F : 0.0F;
        float effectAlpha = normalizedAge < 0.25F
                ? 1.0F : 1.0F - (normalizedAge - 0.25F) / 0.75F;
        effectAlpha = Math.max(0.0F, Math.min(1.0F, effectAlpha));

        Matrix4f inverseProjection = new Matrix4f(RenderSystem.getProjectionMatrix()).invert();
        // The source entity renderer cancels its entity-relative translation before inversion.
        // AFTER_ENTITIES already exposes the resulting level-view pose directly.
        Matrix4f inverseModelView = new Matrix4f(levelPoseStack.last().pose()).invert();

        RenderSystem.setShader(active);
        active.shader().setSampler("DepthSampler", minecraft.getMainRenderTarget().getDepthTextureId());
        active.projectionMatrix().set(inverseProjection);
        active.modelViewMatrix().set(inverseModelView);
        active.projectilePosition().set((float) impactCenter.x, (float) impactCenter.y,
                (float) impactCenter.z);
        active.screenSize().set((float) minecraft.getMainRenderTarget().width,
                (float) minecraft.getMainRenderTarget().height);
        active.lightIntensity().set(lightIntensity);
        active.effectAlpha().set(effectAlpha);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        try {
            drawFullscreenQuad();
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    private static void drawFullscreenQuad() {
        RenderSystem.backupProjectionMatrix();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(
                    -1.0F, 1.0F, -1.0F, 1.0F, -1.0F, 1.0F), VertexSorting.ORTHOGRAPHIC_Z);
            modelViewStack.setIdentity();
            RenderSystem.applyModelViewMatrix();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.vertex(-1.0D, -1.0D, 0.0D).endVertex();
            builder.vertex(1.0D, -1.0D, 0.0D).endVertex();
            builder.vertex(1.0D, 1.0D, 0.0D).endVertex();
            builder.vertex(-1.0D, 1.0D, 0.0D).endVertex();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private record ShaderBindings(ShaderInstance shader,
                                  AbstractUniform projectionMatrix,
                                  AbstractUniform modelViewMatrix,
                                  AbstractUniform projectilePosition,
                                  AbstractUniform screenSize,
                                  AbstractUniform lightIntensity,
                                  AbstractUniform effectAlpha)
            implements Supplier<ShaderInstance> {

        private static ShaderBindings create(ShaderInstance shader) {
            return new ShaderBindings(shader,
                    shader.safeGetUniform("projectionMatrix"),
                    shader.safeGetUniform("modelViewMatrix"),
                    shader.safeGetUniform("projectilePos"),
                    shader.safeGetUniform("screenSize"),
                    shader.safeGetUniform("lightIntensity"),
                    shader.safeGetUniform("effectAlpha"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }
}

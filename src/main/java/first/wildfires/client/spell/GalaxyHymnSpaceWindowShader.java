package first.wildfires.client.spell;

/*
 * Star-space algorithm adapted from ArcaneVortex 0.6.8 star_sky.* and
 * StarShaderBlockEntityRender under the user's project-specific visual authorization.
 * Wildfires replaces the source cube with transient planar impact polygons.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import first.wildfires.Wildfires;
import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

/** Forge-managed ArcaneVortex star-space window used inside the planar impact shards. */
public final class GalaxyHymnSpaceWindowShader {

    private static ShaderBindings bindings;

    private GalaxyHymnSpaceWindowShader() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("galaxy_hymn_space_window"), DefaultVertexFormat.POSITION),
                loaded -> bindings = ShaderBindings.create(loaded));
    }

    public static Supplier<ShaderInstance> prepare(Camera camera) {
        ShaderBindings active = bindings;
        Minecraft minecraft = Minecraft.getInstance();
        if (active == null || minecraft.level == null || camera == null) {
            return null;
        }
        RenderSystem.setShader(active);
        var target = minecraft.getMainRenderTarget();
        active.screenSize().set((float) target.width, (float) target.height);
        var cameraPosition = camera.getPosition();
        active.cameraPosition().set((float) cameraPosition.x,
                (float) cameraPosition.y, (float) cameraPosition.z);
        active.cameraYaw().set(-camera.getYRot() * ((float) Math.PI / 180.0F));
        active.cameraPitch().set(camera.getXRot() * ((float) Math.PI / 180.0F));
        return active;
    }

    private record ShaderBindings(ShaderInstance shader,
                                  AbstractUniform screenSize,
                                  AbstractUniform cameraPosition,
                                  AbstractUniform cameraYaw,
                                  AbstractUniform cameraPitch)
            implements Supplier<ShaderInstance> {

        private static ShaderBindings create(ShaderInstance shader) {
            return new ShaderBindings(shader,
                    shader.safeGetUniform("ScreenSize"),
                    shader.safeGetUniform("CameraPosition"),
                    shader.safeGetUniform("CameraYaw"),
                    shader.safeGetUniform("CameraPitch"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }
}

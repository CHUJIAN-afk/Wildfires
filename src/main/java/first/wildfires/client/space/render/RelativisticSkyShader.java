package first.wildfires.client.space.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import first.wildfires.Wildfires;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** Managed shader handle for rigid-sky atlas sliding and per-direction jump colour. */
public final class RelativisticSkyShader {

    private static ShaderInstance shader;

    private RelativisticSkyShader() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Wildfires.rl("relativistic_sky"),
                DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
    }

    public static ShaderInstance get() {
        return shader;
    }
}

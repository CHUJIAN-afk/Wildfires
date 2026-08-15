package first.wildfires.client.space.render;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import first.wildfires.Wildfires;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.function.Supplier;

/** Managed shader handle for rigid-sky atlas sliding and per-direction jump colour. */
public final class RelativisticSkyShader {

    private static Bindings bindings;

    private RelativisticSkyShader() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Wildfires.rl("relativistic_sky"),
                DefaultVertexFormat.POSITION_TEX), loaded -> bindings = new Bindings(loaded));
    }

    public static ShaderInstance get() {
        return bindings == null ? null : bindings.shader();
    }

    public static Bindings bindings() {
        return bindings;
    }

    public static final class Bindings implements Supplier<ShaderInstance> {
        private final ShaderInstance shader;
        private final AbstractUniform velocity;
        private final AbstractUniform beta;
        private final AbstractUniform aberrationBeta;
        private final AbstractUniform starTrailStrength;

        private Bindings(ShaderInstance shader) {
            this.shader = shader;
            this.velocity = shader.safeGetUniform("Velocity");
            this.beta = shader.safeGetUniform("Beta");
            this.aberrationBeta = shader.safeGetUniform("AberrationBeta");
            this.starTrailStrength = shader.safeGetUniform("StarTrailStrength");
        }

        public ShaderInstance shader() {
            return shader;
        }

        public AbstractUniform velocity() {
            return velocity;
        }

        public AbstractUniform beta() {
            return beta;
        }

        public AbstractUniform aberrationBeta() {
            return aberrationBeta;
        }

        public AbstractUniform starTrailStrength() {
            return starTrailStrength;
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }
}

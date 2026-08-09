package first.wildfires.client.celestial;

import first.wildfires.Wildfires;
import java.io.IOException;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client MOD-bus registration for the unified sky, stars, and managed shaders. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CelestialClientEvents {

    private CelestialClientEvents() {}

    @SubscribeEvent
    public static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new WildfiresOverworldEffects());
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(StarDataManager.INSTANCE);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        AuroraRenderer.registerShader(event);
    }
}

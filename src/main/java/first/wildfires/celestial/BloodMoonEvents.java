package first.wildfires.celestial;

import first.wildfires.Wildfires;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies the retained TFCCaelum Unluck effect from the server-authoritative local moon state. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BloodMoonEvents {

    private BloodMoonEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 100 != 0) {
            return;
        }
        double intensity = CelestialGameplay.visibleBloodMoon(player.serverLevel(), player.blockPosition());
        if (intensity > 0.0D && player.serverLevel().canSeeSky(player.blockPosition())) {
            player.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 600,
                    CelestialGameplayRules.unluckAmplifier(intensity)));
        }
    }
}

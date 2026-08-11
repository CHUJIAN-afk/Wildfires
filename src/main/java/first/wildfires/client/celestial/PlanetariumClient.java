package first.wildfires.client.celestial;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Client entry points for opening the planetarium without affecting world time. */
public final class PlanetariumClient {

    private PlanetariumClient() {
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            if (minecraft.level.dimension() == Level.OVERWORLD) {
                minecraft.setScreen(new PlanetariumScreen());
            } else {
                minecraft.player.displayClientMessage(Component.translatable(
                        "item.wildfires.planetarium.overworld_only"), true);
            }
        }
    }
}

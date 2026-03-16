package first.wildfires.compats.kubejs;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

public class AdvancementBindings {
    public boolean checkAdvancement(ServerPlayer player, String advancementName) {
        if(player.getServer() == null)
            return false;

        ServerAdvancementManager manager = player.getServer().getAdvancements();

        Advancement advancement = manager.getAdvancement(ResourceLocation.parse(advancementName));

        if (advancement == null) {
            return false;
        }

        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
package first.wildfires.api.customEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * 玩家目标温度修改事件,获取玩家目标温度时触发此事件，尝试修改玩家目标温度（传说生存单位）
 */
public class PlayerTargetTemperatureModifyEvent extends Event {

    private final Player player;
    private final float oldTemperature;
    private float newTemperature;

    public PlayerTargetTemperatureModifyEvent(Player player, float oldTemperature) {
        this.player = player;
        this.oldTemperature = oldTemperature;
        this.newTemperature = oldTemperature;
    }

    public Player getPlayer() {
        return player;
    }

    public float getOldTemperature() {
        return oldTemperature;
    }

    public float getNewTemperature() {
        return newTemperature;
    }

    public void setNewTemperature(float newTemperature) {
        this.newTemperature = newTemperature;
    }

}

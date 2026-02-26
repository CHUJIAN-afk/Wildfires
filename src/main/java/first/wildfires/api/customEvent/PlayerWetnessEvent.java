package first.wildfires.api.customEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.wetness.WetnessCapability;

/**
 * 玩家湿度事件,玩家湿度会触发此事件，分为降雨增加和流体增加两种事件
 */
@Cancelable
public abstract class PlayerWetnessEvent extends Event {

    private final Player player;
    private final Level level;
    private int wetness;

    public PlayerWetnessEvent(Player player, Level level, int wetness) {
        this.player = player;
        this.level = level;
        this.wetness = wetness;
    }

    public int getWetness() {
        return wetness;
    }

    public void setWetness(int wetness) {
        this.wetness = wetness;
    }

    public Level getLevel() {
        return level;
    }

    public Player getPlayer() {
        return player;
    }

    public static class RainIncrease extends PlayerWetnessEvent{

        public RainIncrease(Player player, Level level, int wetness) {
            super(player, level, wetness);
        }

    }

    public static class FluidIncrease extends PlayerWetnessEvent{

        public FluidIncrease(Player player, Level level, int wetness) {
            super(player, level, wetness);
        }

    }

}

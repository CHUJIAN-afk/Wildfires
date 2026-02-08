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
    private final WetnessCapability wetnessCapability;
    private int wetness;

    public PlayerWetnessEvent(Player player, WetnessCapability instance, Level level, int wetness) {
        this.player = player;
        this.level = level;
        this.wetnessCapability = instance;
        this.wetness = wetness;
    }

    public int getWetness() {
        return wetness;
    }

    public void setWetness(int wetness) {
        this.wetness = wetness;
    }

    public WetnessCapability getWetnessCapability() {
        return wetnessCapability;
    }

    public Level getLevel() {
        return level;
    }

    public Player getPlayer() {
        return player;
    }

    public static class RainIncrease extends PlayerWetnessEvent{

        public RainIncrease(Player player, WetnessCapability instance, Level level, int wetness) {
            super(player, instance, level, wetness);
        }

    }

    public static class FluidIncrease extends PlayerWetnessEvent{

        public FluidIncrease(Player player, WetnessCapability instance, Level level, int wetness) {
            super(player, instance, level, wetness);
        }

    }

}

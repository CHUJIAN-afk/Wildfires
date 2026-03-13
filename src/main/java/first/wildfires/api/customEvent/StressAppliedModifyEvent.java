package first.wildfires.api.customEvent;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.Event;

public class StressAppliedModifyEvent extends Event {

    public final Block block;
    public float stressApplied;

    public StressAppliedModifyEvent(Block block, float stressApplied){
        this.block = block;
        this.stressApplied = stressApplied;
    }

    public float getStressApplied() {
        return stressApplied;
    }

    public void setStressApplied(float stressApplied) {
        this.stressApplied = stressApplied;
    }

    public Block getBlock() {
        return block;
    }

}

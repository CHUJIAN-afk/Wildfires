package first.wildfires.api.customEvent;

import first.wildfires.utils.WildfiresUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

public class StructureStageModifyEvent extends Event {

    public void addStage(String stage, List<ResourceLocation> structureList) {
        WildfiresUtil.StructureStageMap.put(stage, structureList);
    }

}

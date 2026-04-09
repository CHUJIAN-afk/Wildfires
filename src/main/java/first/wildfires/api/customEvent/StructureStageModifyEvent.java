package first.wildfires.api.customEvent;

import first.wildfires.utils.WildfiresUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StructureStageModifyEvent extends Event {

    public void add(String stage, List<ResourceLocation> structureList) {
        WildfiresUtil.StructureStageMap.put(stage, structureList);
    }

    public void add(String stage, ResourceLocation structure) {
        WildfiresUtil.StructureStageMap.computeIfAbsent(stage, k -> new ArrayList<>()).add(structure);
    }

    public void addList(String stage, String[] structures) {
        Arrays.stream(structures)
                .map(ResourceLocation::parse)
                .forEach(location -> add(stage, location));
    }

}




package first.wildfires.api.customEvent;

import first.wildfires.utils.WildfiresUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

import java.util.*;

public class StructureStageModifyEvent extends Event {

    Map<String, List<ResourceLocation>> StructureStageMap = new HashMap<>();

    public void add(String stage, List<ResourceLocation> structureList) {
        StructureStageMap.put(stage, structureList);
    }

    public void add(String stage, ResourceLocation structure) {
        StructureStageMap.computeIfAbsent(stage, k -> new ArrayList<>()).add(structure);
    }

    public void addList(String stage, String[] structures) {
        Arrays.stream(structures)
                .map(ResourceLocation::parse)
                .forEach(location -> add(stage, location));
    }

    public Map<String, List<ResourceLocation>> getStructureStageMap() {
        return StructureStageMap;
    }
}




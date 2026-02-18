package first.wildfires.api.customEvent;

import first.wildfires.api.MobPoopData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class MobPoopDataModifyEvent extends Event {

    private final List<MobPoopData> mobPoopDataList;

    public MobPoopDataModifyEvent(){
        this.mobPoopDataList = new ArrayList<>();
    }

    public List<MobPoopData> getMobPoopDataList() {
        return mobPoopDataList;
    }

    public void addMobPoopData(MobPoopData mobPoopData) {
        mobPoopDataList.add(mobPoopData);
    }

    public void addMobPoopData(String type, int ticks, List<ItemStack> list) {
        EntityType<?> value = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(type));
        if (value != null) {
            mobPoopDataList.add(new MobPoopData(value, ticks, list));
        }
    }

}

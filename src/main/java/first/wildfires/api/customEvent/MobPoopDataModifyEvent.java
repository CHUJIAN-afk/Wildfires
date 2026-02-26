package first.wildfires.api.customEvent;

import first.wildfires.api.MobPoopData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.Event;

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

    public void addMobPoopData(EntityType<?> type, int ticks, ItemStack itemStack, Block block) {
        mobPoopDataList.add(new MobPoopData(type, ticks, itemStack, block));
    }

}

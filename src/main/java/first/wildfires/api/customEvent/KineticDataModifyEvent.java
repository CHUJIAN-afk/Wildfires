package first.wildfires.api.customEvent;

import first.wildfires.api.KineticData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 对机器性能设置上限
 */
public class KineticDataModifyEvent extends Event {

    private final List<KineticData> kineticData;

    public KineticDataModifyEvent() {
        this.kineticData = new ArrayList<>();
    }

    public void addKineticData(Block block, Integer maxSpeed, Integer maxNetStress, List<ItemStack> list) {
        kineticData.add(new KineticData(block, maxSpeed, maxNetStress, list));
    }

    public List<KineticData> getKineticData() {
        return kineticData;
    }

}

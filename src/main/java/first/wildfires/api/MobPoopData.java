package first.wildfires.api;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record MobPoopData(EntityType<?> type, int ticks, List<ItemStack> list) {
}

package first.wildfires.api;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public record MobPoopData(EntityType<?> type, int ticks, ItemStack itemStack, Block block) {
}

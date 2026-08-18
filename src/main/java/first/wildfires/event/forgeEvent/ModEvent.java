package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.mixin.minecraft.BlockEntityTypeAccessor;
import first.wildfires.register.BlockRegister;
import first.wildfires.register.AttributeRegister;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvent {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void modLoading(FMLCommonSetupEvent event) {
        ModList modList = ModList.get();
        Wildfires.TFCLoaded = modList.isLoaded("tfc");
        Wildfires.CurioLoaded = modList.isLoaded("curios");
        Wildfires.LSOLoaded = modList.isLoaded("legendarysurvivaloverhaul");
    }

    @SubscribeEvent
    public static void EntityAttributeModificationEvent(EntityAttributeModificationEvent event) {
        event.getTypes().forEach(entityType -> event.add(entityType, AttributeRegister.ArmorPenetration.get()));
        event.add(EntityType.PLAYER, AttributeRegister.Rainproof.get());
        event.add(EntityType.PLAYER, AttributeRegister.Waterproof.get());
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        if (Wildfires.TFCLoaded) {
            event.enqueueWork(() -> {
                // Add UnrestrictedCharcoalForge to CHARCOAL_FORGE BlockEntity
                BlockEntityTypeAccessor forgeAccessor = (BlockEntityTypeAccessor) (Object) TFCBlockEntities.CHARCOAL_FORGE.get();
                Set<Block> forgeValidBlocks = new HashSet<>(forgeAccessor.wildfires$getValidBlocks());
                forgeValidBlocks.add(BlockRegister.UnrestrictedCharcoalForge.get());
                forgeAccessor.wildfires$setValidBlocks(Set.copyOf(forgeValidBlocks));

                // Add custom Crucible to CRUCIBLE BlockEntity
                BlockEntityTypeAccessor crucibleAccessor = (BlockEntityTypeAccessor) (Object) TFCBlockEntities.CRUCIBLE.get();
                Set<Block> crucibleValidBlocks = new HashSet<>(crucibleAccessor.wildfires$getValidBlocks());
                crucibleValidBlocks.add(BlockRegister.Crucible.get());
                crucibleAccessor.wildfires$setValidBlocks(Set.copyOf(crucibleValidBlocks));
            });
        }

    }

}

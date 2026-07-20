package first.wildfires.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderScenes;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.simibubi.create.infrastructure.ponder.scenes.ProcessingScenes;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import first.wildfires.Wildfires;
import first.wildfires.register.BlockRegister;
import fr.lucreeper74.createmetallurgy.ponders.CMPonders;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Ponder 场景注册
 * 用于在游戏中展示织布机的使用方式
 */
public class WildfiresPonderPlugin implements PonderPlugin {

    private static WildfiresPonderPlugin plugin = null;
    public boolean loaded = false;

    private WildfiresPonderPlugin() {

    }

    public static WildfiresPonderPlugin getPlugin() {
        if (plugin == null) {
            plugin = new WildfiresPonderPlugin();
        }
        return plugin;
    }

    @Override
    public @NotNull String getModId() {
        return Wildfires.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<RegistryEntry<?>> HELPER = helper.withKeyFunction(RegistryEntry::getId);
        HELPER.forComponents(new ItemProviderEntry[]{BlockRegister.LoomControlBlock})
                .addStoryBoard("loom/loom", LoomPonder::weave, AllCreatePonderTags.REDSTONE);
        HELPER.forComponents(new ItemProviderEntry[]{
                        BlockRegister.StoneMillgranite, BlockRegister.StoneMilldiorite,
                        BlockRegister.StoneMillgabbro, BlockRegister.StoneMillrhyolite,
                        BlockRegister.StoneMillbasalt, BlockRegister.StoneMilldacite})
                .addStoryBoard("millstone", ProcessingScenes::millstone);
        HELPER.forComponents(new ItemProviderEntry[]{
                        BlockRegister.StoneCrushingWheelGranite, BlockRegister.StoneCrushingWheelDiorite,
                        BlockRegister.StoneCrushingWheelGabbro, BlockRegister.StoneCrushingWheelRhyolite,
                        BlockRegister.StoneCrushingWheelBasalt, BlockRegister.StoneCrushingWheelDacite})
                .addStoryBoard("crushing_wheel", ProcessingScenes::crushingWheels);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<RegistryEntry<?>> registrationHelper = helper.withKeyFunction(RegistryEntry::getId);
        registrationHelper.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
                .add(BlockRegister.LoomControlBlock)
                .add(BlockRegister.StoneMillgranite)
                .add(BlockRegister.StoneMilldiorite)
                .add(BlockRegister.StoneMillgabbro)
                .add(BlockRegister.StoneMillrhyolite)
                .add(BlockRegister.StoneMillbasalt)
                .add(BlockRegister.StoneMilldacite)
                .add(BlockRegister.StoneCrushingWheelGranite)
                .add(BlockRegister.StoneCrushingWheelDiorite)
                .add(BlockRegister.StoneCrushingWheelGabbro)
                .add(BlockRegister.StoneCrushingWheelRhyolite)
                .add(BlockRegister.StoneCrushingWheelBasalt)
                .add(BlockRegister.StoneCrushingWheelDacite);
    }
}

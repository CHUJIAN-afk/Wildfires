package first.wildfires.event.forgeEvent;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.utility.CreateLang;
import com.tterrag.registrate.util.entry.BlockEntry;
import first.wildfires.Wildfires;
import first.wildfires.api.customEvent.CreativeTabBuildEvent;
import first.wildfires.kinetic.loom.LoomControlBlock;
import first.wildfires.network.PlayerInputPacket;
import first.wildfires.ponder.WildfiresPonderPlugin;
import first.wildfires.register.BlockRegister;
import first.wildfires.register.CreativeModeTabRegister;
import first.wildfires.register.ItemRegister;
import first.wildfires.utils.CuriosUtil;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvent {

    private static final float LOOM_STRESS_IMPACT = 4.0f;


    @SubscribeEvent
    public static void inputEvent(MovementInputUpdateEvent event) {
        if (event.getInput().jumping) {
            PlayerInputPacket packet = new PlayerInputPacket();
            packet.sendToServer();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        List<Component> toolTip = event.getToolTip();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Reinforcement")) {
            int reinforcement = tag.getInt("Reinforcement");
            String statusKey = reinforcement >= 6 ? "wildfires.tooltip.reinforcement.firm" : reinforcement >= 4 ? "wildfires.tooltip.reinforcement.bound" : "wildfires.tooltip.reinforcement.loose";
            ChatFormatting statusColor = reinforcement >= 8 ? ChatFormatting.BLUE : reinforcement >= 6 ? ChatFormatting.WHITE : reinforcement >= 2 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY;
            int reinforcementTooltipIndex = Math.min(1, toolTip.size());
            toolTip.add(reinforcementTooltipIndex, Component.translatable("wildfires.tooltip.reinforcement.normal", Component.translatable(statusKey).withStyle(statusColor)));
            if (player != null && player.getAbilities().instabuild) {
                int noDurabilityDamageChance = reinforcement * 5 - 20;
                toolTip.add(reinforcementTooltipIndex + 1, Component.translatable("wildfires.tooltip.reinforcement", reinforcement, noDurabilityDamageChance).withStyle(ChatFormatting.GRAY));
            }
        }
        BlockEntry<LoomControlBlock> loomControlBlock = BlockRegister.LoomControlBlock;
        if (stack.getItem() == loomControlBlock.asItem()) {
            WildfiresPonderPlugin plugin = WildfiresPonderPlugin.getPlugin();
            if (!plugin.loaded) {
                plugin.loaded = true;
                PonderIndex.addPlugin(plugin);
                PonderIndex.reload();
            }
            CreateLang.builder()
                    .space()
                    .addTo(toolTip);
            CreateLang.translate("tooltip.stressImpact")
                    .style(ChatFormatting.GRAY).addTo(toolTip);
            String string = player != null && (player.getItemBySlot(EquipmentSlot.HEAD).getItem() == AllItems.GOGGLES.get() || CuriosUtil.isEquipped(player, AllItems.GOGGLES.get())) ? " 4x RPM" : " 中";
            CreateLang.text(" ██▒" + string)
                    .style(ChatFormatting.GOLD)
                    .addTo(toolTip);
        }
    }
}

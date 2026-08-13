package first.wildfires.event.forgeEvent;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.utility.CreateLang;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.tterrag.registrate.util.entry.BlockEntry;
import first.wildfires.Wildfires;
import first.wildfires.client.ThermalDebugRenderer;
import first.wildfires.client.space.OrbitVisualDebugClock;
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
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvent {

    private static final float LOOM_STRESS_IMPACT = 4.0f;
    private static boolean jumpWasDown;


    @SubscribeEvent
    public static void inputEvent(MovementInputUpdateEvent event) {
        boolean jumping = event.getInput().jumping;
        if (jumping && !jumpWasDown) {
            PlayerInputPacket packet = new PlayerInputPacket();
            packet.sendToServer();
        }
        jumpWasDown = jumping;
    }

    @SubscribeEvent
    public static void renderThermalDebug(RenderLevelStageEvent event) {
        ThermalDebugRenderer.render(event);
    }

    @SubscribeEvent
    public static void tickThermalDebug(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ThermalDebugRenderer.tick();
        }
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        var thermalDebug = Commands.literal("thermaldebug")
                .executes(context -> {
                    boolean enabled = ThermalDebugRenderer.toggle();
                    int sources = enabled ? ThermalDebugRenderer.refreshAndGetSourceCount() : 0;
                    context.getSource().sendSuccess(() -> Component.literal(enabled
                            ? "热调试已开启，热源数量：" + sources
                            : "热调试已关闭"), false);
                    return 1;
                })
                .then(Commands.literal("hidden").executes(context -> {
                    boolean enabled = ThermalDebugRenderer.toggleHidden();
                    context.getSource().sendSuccess(() -> Component.literal(enabled
                            ? "隐藏热场调试已开启（绿色）"
                            : "隐藏热场调试已关闭"), false);
                    return 1;
                }))
                .then(Commands.literal("stats").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            ThermalDebugRenderer.diagnosticsSummary()), false);
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("wildfires")
                .then(thermalDebug));
        if (!FMLEnvironment.production) {
            var orbitVisualTime = Commands.literal("orbitvisualtime")
                    .then(Commands.literal("set")
                            .then(Commands.argument("game_time", DoubleArgumentType.doubleArg(0.0D))
                                    .then(Commands.argument("calendar_ticks", DoubleArgumentType.doubleArg(0.0D))
                                            .executes(context -> {
                                                double gameTime = DoubleArgumentType.getDouble(context, "game_time");
                                                double calendarTicks = DoubleArgumentType.getDouble(context,
                                                        "calendar_ticks");
                                                OrbitVisualDebugClock.set(gameTime, calendarTicks);
                                                context.getSource().sendSuccess(() -> Component.literal(
                                                        "Orbit visual time frozen: game=" + gameTime
                                                                + ", calendar=" + calendarTicks), false);
                                                return 1;
                                            }))))
                    .then(Commands.literal("clear").executes(context -> {
                        OrbitVisualDebugClock.clear();
                        context.getSource().sendSuccess(() -> Component.literal(
                                "Orbit visual time returned to synchronized clocks"), false);
                        return 1;
                    }));
            event.getDispatcher().register(Commands.literal("wildfires")
                    .then(orbitVisualTime));
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

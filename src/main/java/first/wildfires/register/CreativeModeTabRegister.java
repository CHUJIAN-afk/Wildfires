package first.wildfires.register;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.Wildfires;
import first.wildfires.api.customEvent.CreativeTabBuildEvent;
import first.wildfires.client.AnimInfo;
import first.wildfires.event.forgeEvent.ForgeEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CreativeModeTabRegister {

    private static final DeferredRegister<CreativeModeTab> Register = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Wildfires.MODID);

    public static final RegistryObject<CreativeModeTab> WildfiresTab = Register.register("wildfires_tab", () -> CreativeModeTab.builder().icon(() -> Blocks.CAMPFIRE.asItem().getDefaultInstance())
            .title(Component.translatable("itemGroup.wildfire_tab"))
            .icon(Items.CAMPFIRE::getDefaultInstance)
            .build()
    );

    public static void creativeTabBuild(CreativeModeTabRegister.Registers registers) {
        registers.push(new CreativeModeTabRegister.TabSection(0, Wildfires.rl("textures/item/banner/banner.png"), new AnimInfo(18, 3, 8)));
        registers.put(ItemRegister.CopperBoltItem.get());
        registers.put(ItemRegister.AirCushion.get());
        registers.pop();
        registers.push(new CreativeModeTabRegister.TabSection(1, Wildfires.rl("textures/item/banner/default_banner.png"), new AnimInfo(18, 1, 1)));
        //没有分类的全部丢进默认分组
        ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> {
                    ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
                    return location != null && location.getNamespace().equals(Wildfires.MODID);
                })
                .forEach(registers::put);
        registers.pop();
    }

    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

    public static void renderBanners(final CreativeModeInventoryScreen screen, final GuiGraphics graphics, int mouseX, int mouseY, float scrollOffs) {
        Registers instance = Registers.getRegisters();
        List<TabSection> sections = instance.sortedEntries();
        LinkedHashMap<TabSection, HashSet<Item>> map = instance.map;

        int totalRows = 0;
        for (TabSection section : sections) {
            totalRows += 1;
            totalRows += (map.get(section).size() + 8) / 9;
        }

        int scrollRow = Math.round(scrollOffs * Math.max(0, totalRows - 5));
        int left = screen.getGuiLeft() + 8;
        int top = screen.getGuiTop() + 17;

        int currentRow = 0;
        for (TabSection section : sections) {
            int bannerRow = currentRow;
            int itemRows = (map.get(section).size() + 8) / 9;
            currentRow += 1 + itemRows;
            ResourceLocation texture = section.texture();
            AnimInfo animInfo = section.animInfo;
            int visibleRow = bannerRow - scrollRow;
            if (visibleRow < 0 || visibleRow >= 5) continue;
            int bannerY = top + visibleRow * 18;
            AnimInfo.blitAnimated(graphics, texture, animInfo, left, bannerY, 162, mouseX, mouseY, true);
        }
    }

    public static void processItems(Consumer<ItemStack> displayItems, Consumer<ItemStack> searchItems) {
        Registers instance = Registers.getRegisters();
        creativeTabBuild(instance);
        LinkedHashMap<TabSection, HashSet<Item>> map = instance.map;
        List<TabSection> sortedKeys = instance.sortedEntries();
        for (TabSection key : sortedKeys) {
            HashSet<Item> items = map.get(key);
            List<ItemStack> stacks = new ArrayList<>(items.stream()
                    .map(Item::getDefaultInstance)
                    .toList());
            for (int i = 0; i < 9; i++) {
                stacks.add(0, ItemStack.EMPTY);
            }
            while (stacks.size() % 9 != 0) {
                stacks.add(ItemStack.EMPTY);
            }
            for (ItemStack stack : stacks) {
                displayItems.accept(stack);
                if (!stack.isEmpty()) {
                    searchItems.accept(stack);
                }
            }
        }
    }

    public static class Registers {

        private final static Registers registers = new Registers();
        private final LinkedHashMap<TabSection, HashSet<Item>> map = new LinkedHashMap<>();
        private TabSection tabSection = null;

        private Registers() {
        }

        public static Registers getRegisters() {
            return registers;
        }

        public void push(TabSection tabSection) {
            this.tabSection = tabSection;
        }

        public void put(Item... items) {
            for (Item item : items) {
                map.computeIfAbsent(tabSection, k -> new HashSet<>()).add(item);
            }
        }

        public void pop(){
            this.tabSection = null;
        }

        public List<TabSection> sortedEntries() {
            List<TabSection> sortedKeys = new ArrayList<>(map.keySet());
            sortedKeys.sort(Comparator.comparingInt(TabSection::integer));
            return sortedKeys;
        }
    }

    public record TabSection(Integer integer, ResourceLocation texture, AnimInfo animInfo) {
    }
}

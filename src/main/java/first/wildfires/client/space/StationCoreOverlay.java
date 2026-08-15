/*
 * Adapted from NTM: Space BlockOrbitalStation.printHook / ILookOverlay behavior.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires changes: renders synchronized UUID-based station, port, pod, tape and water state on
 * Forge 1.20.1 without NTM's global overlay dispatcher or per-tick proxy scans.
 */
package first.wildfires.client.space;

import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.content.StationCoreBlockEntity;
import first.wildfires.space.content.StationCoreService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/** NTM-style crosshair information for the root or the sole top-centre interaction proxy. */
public final class StationCoreOverlay {

    private StationCoreOverlay() {
    }

    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult hit)) return;
        var level = minecraft.level;
        var hitState = level.getBlockState(hit.getBlockPos());
        var corePosition = hitState.is(SpaceContentRegister.STATION_CORE.get())
                ? hit.getBlockPos().immutable()
                : StationCoreService.coreForTopCenterBlock(level, hit.getBlockPos()).orElse(null);
        if (corePosition == null
                || !(level.getBlockEntity(corePosition) instanceof StationCoreBlockEntity core)) return;

        ReusableReturnCapsuleEntity capsule = core.dockedCapsuleId().flatMap(id ->
                level.getEntitiesOfClass(ReusableReturnCapsuleEntity.class,
                                new AABB(corePosition).inflate(3.0D, 5.0D, 3.0D),
                                candidate -> candidate.getUUID().equals(id)).stream().findFirst())
                .orElse(null);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("space.wildfires.station_core.overlay.station",
                core.stationId().map(Object::toString).orElse("-")).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("space.wildfires.station_core.overlay.dock",
                core.primary() ? Component.translatable("space.wildfires.station_core.overlay.primary")
                        : Component.translatable("space.wildfires.station_core.overlay.secondary"),
                core.dockId().map(Object::toString).orElse("-")).withStyle(ChatFormatting.GRAY));
        if (capsule == null) {
            lines.add(Component.translatable("space.wildfires.station_core.overlay.no_capsule")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            lines.add(Component.translatable("space.wildfires.station_core.overlay.capsule",
                    Component.literal(capsule.capsuleState().name()),
                    capsule.getPassengers().isEmpty()
                            ? Component.translatable("space.wildfires.station_core.overlay.empty")
                            : Component.translatable("space.wildfires.station_core.overlay.occupied"))
                    .withStyle(capsule.getPassengers().isEmpty()
                            ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            lines.add(Component.translatable("space.wildfires.station_core.overlay.tape",
                    capsule.navigationTape().isEmpty()
                            ? Component.translatable("space.wildfires.station_core.overlay.no_tape")
                            : capsule.navigationTape().getHoverName()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("space.wildfires.station_core.overlay.water",
                    capsule.fuelMb(), first.wildfires.space.capsule.ReturnCapsuleFuelTank.CAPACITY_MB)
                    .withStyle(ChatFormatting.BLUE));
            lines.add(Component.translatable(minecraft.player != null
                            && minecraft.player.isShiftKeyDown()
                            ? "space.wildfires.station_core.overlay.retrieve_hint"
                            : "space.wildfires.station_core.overlay.enter_hint")
                    .withStyle(ChatFormatting.WHITE));
        }
        draw(event, Component.translatable("block.wildfires.station_core")
                .withStyle(ChatFormatting.GOLD), lines);
    }

    private static void draw(RenderGuiOverlayEvent.Post event, Component title,
                             List<Component> lines) {
        Minecraft minecraft = Minecraft.getInstance();
        int x = event.getWindow().getGuiScaledWidth() / 2 + 10;
        int y = event.getWindow().getGuiScaledHeight() / 2 + 12;
        int width = minecraft.font.width(title);
        for (Component line : lines) width = Math.max(width, minecraft.font.width(line));
        event.getGuiGraphics().fill(x - 3, y - 3, x + width + 4,
                y + (lines.size() + 1) * 10 + 2, 0x90000000);
        event.getGuiGraphics().drawString(minecraft.font, title, x, y, 0xFFFFFFFF, true);
        for (int index = 0; index < lines.size(); index++) {
            event.getGuiGraphics().drawString(minecraft.font, lines.get(index), x,
                    y + 10 + index * 10, 0xFFFFFFFF, true);
        }
    }
}

package first.wildfires.client.space;

import first.wildfires.network.RequestStationTravelPacket;
import first.wildfires.space.celestial.CelestialDefinitionRegistry;
import first.wildfires.space.celestial.CelestialKind;
import first.wildfires.space.content.StationControlMenu;
import first.wildfires.space.route.StationRouteDefinition;
import first.wildfires.space.route.StationRouteRegistry;
import first.wildfires.space.route.StationTravelRequest;
import first.wildfires.space.route.StationTravelMode;
import first.wildfires.space.route.StationTransferTopology;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Compact paged route selector; the server remains authoritative for every displayed choice. */
public final class StationControlScreen extends AbstractContainerScreen<StationControlMenu> {

    private static final int ROUTES_PER_PAGE = 5;
    private List<StationRouteDefinition> routes = List.of();
    private int page;

    public StationControlScreen(StationControlMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 220;
        imageHeight = 176;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        rebuildRoutes();
        rebuildButtons();
    }

    private void rebuildRoutes() {
        if (minecraft == null || minecraft.level == null) {
            routes = List.of();
            return;
        }
        ResourceLocation currentBody = SpaceClientState.current()
                .filter(context -> context.stationId().equals(menu.stationId()))
                .map(context -> context.currentBody()).orElse(null);
        if (currentBody == null) {
            routes = List.of();
            return;
        }
        Set<ResourceLocation> travelBodies = new LinkedHashSet<>();
        CelestialDefinitionRegistry.get(minecraft.level.registryAccess()).entrySet().stream()
                .filter(entry -> entry.getValue().kind() != CelestialKind.STAR)
                .map(entry -> entry.getKey().location())
                .forEach(travelBodies::add);
        if (!travelBodies.contains(currentBody)) {
            routes = List.of();
            return;
        }
        java.util.Map<ResourceLocation, StationRouteDefinition> choices = new LinkedHashMap<>();
        StationRouteRegistry.get(minecraft.level.registryAccess()).entrySet().stream()
                .filter(entry -> entry.getKey().location().equals(entry.getValue().id()))
                .map(java.util.Map.Entry::getValue)
                .filter(StationRouteDefinition::enabled)
                .filter(route -> route.fromBody().equals(currentBody))
                .filter(route -> travelBodies.contains(route.fromBody()) && travelBodies.contains(route.toBody()))
                .forEach(route -> choices.putIfAbsent(route.toBody(), route));
        travelBodies.stream()
                .filter(target -> !target.equals(currentBody))
                .forEach(target -> choices.putIfAbsent(target,
                        StationRouteDefinition.freeTransfer(currentBody, target)));
        routes = choices.values().stream()
                .sorted(Comparator.comparing(route -> route.toBody().toString())).toList();
        page = Math.min(page, maxPage());
    }

    private void rebuildButtons() {
        clearWidgets();
        int start = page * ROUTES_PER_PAGE;
        int end = Math.min(routes.size(), start + ROUTES_PER_PAGE);
        for (int index = start; index < end; index++) {
            StationRouteDefinition route = routes.get(index);
            int y = topPos + 38 + (index - start) * 23;
            addRenderableWidget(Button.builder(Component.translatable(
                            "screen.wildfires.station_control.travel", route.toBody().toString(),
                            route.totalDurationTicks()), button -> request(route.id()))
                    .bounds(leftPos + 15, y, 128, 20).build());
            if (jumpEligible(route)) {
                addRenderableWidget(Button.builder(Component.translatable(
                                "screen.wildfires.station_control.jump"), button ->
                        request(route.id(), StationTravelMode.JUMP))
                        .bounds(leftPos + 147, y, 58, 20).build());
            }
        }
        if (page > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page--;
                rebuildButtons();
            }).bounds(leftPos + 15, topPos + 153, 20, 18).build());
        }
        if (page < maxPage()) {
            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page++;
                rebuildButtons();
            }).bounds(leftPos + 185, topPos + 153, 20, 18).build());
        }
    }

    private int maxPage() {
        return routes.isEmpty() ? 0 : (routes.size() - 1) / ROUTES_PER_PAGE;
    }

    private void request(ResourceLocation routeId) {
        request(routeId, StationTravelMode.NORMAL);
    }

    private void request(ResourceLocation routeId, StationTravelMode mode) {
        new RequestStationTravelPacket(new StationTravelRequest(menu.computerPos(), menu.stationId(),
                menu.expectedRevision(), routeId, mode)).sendToServer();
    }

    /** Mirrors the shared parent-system rule only for UI visibility; the server revalidates it. */
    private boolean jumpEligible(StationRouteDefinition route) {
        if (minecraft == null || minecraft.level == null) return false;
        var registry = CelestialDefinitionRegistry.get(minecraft.level.registryAccess());
        var from = registry.get(route.fromBody());
        var to = registry.get(route.toBody());
        if (from == null || to == null) return false;
        return StationTransferTopology.classify(route.fromBody(), from, route.toBody(), to)
                .isJumpEligible();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101724);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + 29, 0xFF24364F);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 10, 0xFFFFFF, false);
        Component status = routes.isEmpty()
                ? Component.translatable("screen.wildfires.station_control.no_routes")
                : Component.translatable("screen.wildfires.station_control.page", page + 1, maxPage() + 1);
        graphics.drawString(font, status, 15, 28, 0xA9C8EA, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}

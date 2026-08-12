package first.wildfires.space.route;

import com.mojang.logging.LogUtils;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Server runtime holder for the current validated station-route generation. */
public final class StationRouteRuntime {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object LOCK = new Object();
    private static StationRouteSnapshot current = StationRouteSnapshot.empty();
    private static long nextGeneration;
    private static boolean forgeRegistered;

    private StationRouteRuntime() {
    }

    public static void register(IEventBus modBus) {
        StationRouteRegistry.register(modBus);
        synchronized (LOCK) {
            if (forgeRegistered) return;
            forgeRegistered = true;
        }
        MinecraftForge.EVENT_BUS.addListener(StationRouteRuntime::onReload);
        MinecraftForge.EVENT_BUS.addListener(StationRouteRuntime::onStopped);
    }

    public static StationRouteSnapshot current() {
        synchronized (LOCK) {
            return current;
        }
    }

    private static void onReload(AddReloadListenerEvent event) {
        RegistryAccess access = event.getRegistryAccess();
        event.addListener((barrier, resources, prep, reload, background, game) ->
                CompletableFuture.supplyAsync(() -> Boolean.TRUE, background)
                        .thenCompose(barrier::wait).thenRunAsync(() -> refresh(access), game));
    }

    private static void refresh(RegistryAccess access) {
        Map<ResourceLocation, StationRouteDefinition> routes = new LinkedHashMap<>();
        StationRouteRegistry.get(access).entrySet().forEach(entry ->
                routes.put(entry.getKey().location(), entry.getValue()));
        StationRouteSnapshot snapshot;
        synchronized (LOCK) {
            if (nextGeneration == Long.MAX_VALUE) throw new IllegalStateException("Route generation exhausted");
            snapshot = StationRouteSnapshot.validate(++nextGeneration, routes,
                    CelestialRegistryRuntime.current());
            current = snapshot;
        }
        snapshot.rejected().forEach((id, reason) -> LOGGER.error("Rejected station route {}: {}", id, reason));
        LOGGER.info("Loaded {} Wildfires station routes as generation {} ({} rejected)",
                snapshot.definitions().size(), snapshot.generation(), snapshot.rejected().size());
    }

    private static void onStopped(ServerStoppedEvent event) {
        synchronized (LOCK) {
            current = StationRouteSnapshot.empty();
            nextGeneration = 0L;
        }
    }
}

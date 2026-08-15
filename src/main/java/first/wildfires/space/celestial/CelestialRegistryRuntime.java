package first.wildfires.space.celestial;

import com.mojang.logging.LogUtils;
import first.wildfires.space.route.StationRouteRuntime;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Server-side holder for the current validated synchronized celestial registry generation. */
public final class CelestialRegistryRuntime {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object LOCK = new Object();

    private static CelestialRegistrySnapshot current = CelestialRegistrySnapshot.empty();
    private static long nextGeneration;
    private static boolean registered;

    private CelestialRegistryRuntime() {
    }

    public static void register() {
        synchronized (LOCK) {
            if (registered) {
                return;
            }
            registered = true;
        }
        MinecraftForge.EVENT_BUS.addListener(CelestialRegistryRuntime::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(CelestialRegistryRuntime::onServerStopped);
    }

    public static CelestialRegistrySnapshot current() {
        synchronized (LOCK) {
            return current;
        }
    }

    private static void onAddReloadListener(AddReloadListenerEvent event) {
        RegistryAccess registryAccess = event.getRegistryAccess();
        event.addListener((barrier, resourceManager, preparationsProfiler, reloadProfiler,
                           backgroundExecutor, gameExecutor) ->
                CompletableFuture.supplyAsync(() -> Boolean.TRUE, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenRunAsync(() -> refresh(registryAccess), gameExecutor));
    }

    private static void refresh(RegistryAccess registryAccess) {
        Registry<CelestialDefinition> registry = CelestialDefinitionRegistry.get(registryAccess);
        Map<ResourceLocation, CelestialDefinition> definitions = new LinkedHashMap<>();
        registry.entrySet().forEach(entry -> definitions.put(entry.getKey().location(), entry.getValue()));
        Set<ResourceLocation> existingDimensions = existingDimensions(registryAccess);

        CelestialRegistrySnapshot snapshot;
        synchronized (LOCK) {
            if (nextGeneration == Long.MAX_VALUE) {
                throw new IllegalStateException("Celestial registry generation exhausted");
            }
            snapshot = CelestialRegistrySnapshot.reload(current, ++nextGeneration,
                    definitions, existingDimensions,
                    resource -> true); // Client asset existence is validated on the rendering side.
            current = snapshot;
        }

        // Routes are validated against this exact published celestial generation. Keeping this in
        // one reload continuation prevents initial-load ordering from producing an empty route map.
        StationRouteRuntime.refreshAfterCelestials(registryAccess);

        for (CelestialBindingValidator.Issue issue : snapshot.validation().issues()) {
            if (issue.severity() == CelestialBindingValidator.Severity.ERROR) {
                LOGGER.error("Wildfires celestial definition error: {}", issue.message());
            } else {
                LOGGER.warn("Wildfires celestial definition warning: {}", issue.message());
            }
        }
        LOGGER.info("Loaded {} Wildfires celestial definitions as generation {} ({} removed)",
                snapshot.validation().definitions().size(), snapshot.generation(),
                snapshot.removedDefinitions().size());
    }

    private static Set<ResourceLocation> existingDimensions(RegistryAccess registryAccess) {
        Set<ResourceLocation> dimensions = new LinkedHashSet<>(
                registryAccess.registryOrThrow(Registries.DIMENSION).keySet());
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Forge's integrated-server /reload can present an empty dynamic dimension registry
            // here while the real ServerLevels remain alive. Preserve the live server as the
            // authority so Earth does not lose minecraft:overworld until the next restart.
            server.getAllLevels().forEach(level -> dimensions.add(level.dimension().location()));
        }
        return Set.copyOf(dimensions);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        RegistryAccess registryAccess = event.getServer().registryAccess();
        synchronized (LOCK) {
            if (nextGeneration == Long.MAX_VALUE) {
                current = CelestialRegistrySnapshot.empty();
                nextGeneration = 0L;
                return;
            }
            CelestialRegistrySnapshot empty = CelestialRegistrySnapshot.reload(current, ++nextGeneration,
                    Map.of(), Set.of(), resource -> true);
            current = empty;
        }
        // Keep the paired route snapshot in the same terminal empty generation rather than
        // leaving stale routes available during a server shutdown/restart boundary.
        StationRouteRuntime.refreshAfterCelestials(registryAccess);
    }
}

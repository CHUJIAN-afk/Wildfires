package first.wildfires.diagnostics;

import first.wildfires.Wildfires;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientStartupReloadDiagnostics {

    private ClientStartupReloadDiagnostics() {
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier, resourceManager, preparationProfiler, reloadProfiler, backgroundExecutor, gameExecutor) -> {
            long startedAt = StartupDiagnostics.now();
            StartupDiagnostics.clientMark("resource reload started (" + resourceManager.getNamespaces().size() + " namespaces)");
            return CompletableFuture.completedFuture(null)
                    .thenCompose(barrier::wait)
                    .thenRunAsync(() -> StartupDiagnostics.clientCompleted("resource reload", startedAt), gameExecutor);
        });
    }
}

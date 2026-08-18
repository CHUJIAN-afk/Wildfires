package first.wildfires.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.util.LazyOptional;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public abstract class CuriosUtil {

    /*
     * Entity identity is intentional: a respawned/reconnected LocalPlayer must never inherit a
     * result captured from its predecessor. Weak keys also let discarded client entities leave
     * without an explicit lifecycle sweep.
     */
    private static final Map<LivingEntity, Map<Item, Boolean>> CACHE = new WeakHashMap<>();

    public static synchronized boolean isEquipped(LivingEntity livingEntity, Item item) {
        return CACHE.computeIfAbsent(livingEntity, ignored -> new HashMap<>()).computeIfAbsent(item, ignored -> {
            LazyOptional<ICuriosItemHandler> lazyOptional = CuriosApi.getCuriosInventory(livingEntity);
            if (lazyOptional.isPresent()) {
                ICuriosItemHandler handler = lazyOptional.resolve().orElse(null);
                return handler != null && handler.isEquipped(item);
            }
            return false;
        });
    }

    /** Invalidates one entity after a Curios inventory mutation or client synchronization. */
    public static synchronized void invalidate(LivingEntity livingEntity) {
        CACHE.remove(livingEntity);
    }

    /** Releases all client-side cached entity identities when leaving a connection. */
    public static synchronized void clear() {
        CACHE.clear();
    }

}

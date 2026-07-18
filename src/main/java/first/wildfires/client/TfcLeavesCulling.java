package first.wildfires.client;

import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;

public final class TfcLeavesCulling {
    private static final String EMBEDDIUM_LEAVES_QUALITY = readEmbeddiumLeavesQuality();

    private TfcLeavesCulling() {
    }

    private static String readEmbeddiumLeavesQuality() {
        try {
            Class<?> sodiumClientMod = Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
            Class<?> optionsClass = Class.forName("me.jellysquid.mods.sodium.client.gui.SodiumGameOptions");
            Class<?> qualityClass = Class.forName("me.jellysquid.mods.sodium.client.gui.SodiumGameOptions$QualitySettings");
            Object options = sodiumClientMod.getMethod("options").invoke(null);
            Object quality = optionsClass.getField("quality").get(options);
            Object leavesQuality = qualityClass.getField("leavesQuality").get(quality);
            return String.valueOf(leavesQuality);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static boolean useFastLeaves() {
        if ("FAST".equals(EMBEDDIUM_LEAVES_QUALITY)) {
            return true;
        }
        if ("FANCY".equals(EMBEDDIUM_LEAVES_QUALITY)) {
            return false;
        }
        return Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
    }
}

package first.wildfires.client.spell;

/*
 * Random camera impulse and 60-tick fade adapted from ArcaneVortex 0.6.8
 * ScreenShakeHelper and SkyRipperArrowDeadEffect0 under the user's
 * project-specific visual authorization. No upstream attack logic is used.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import first.wildfires.compats.irons_spellbooks.GalaxyHymnSpaceShardMath;
import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnFieldEntity;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;

/** Client-local state for the synchronized Galaxy Hymn impact flash and camera impulse. */
public final class GalaxyHymnImpactVisuals {

    public static final double SHAKE_RADIUS = 60.0D;
    public static final float BASE_SHAKE_INTENSITY = 5.0F;
    public static final int DURATION_TICKS = 60;
    /** Sky Ripper drives the Black World envelope and camera impulse from the same 60-tick clock. */
    public static final int BLACK_WORLD_DURATION_TICKS = DURATION_TICKS;

    private static ClientLevel activeLevel;
    private static Vec3 impactCenter;
    private static long startGameTime;
    private static float shakeIntensity;
    private static int shakeDuration;
    private static int shakeTotalDuration;
    private static int shakeTickCounter;
    private static boolean blackWorldEnabled;

    private GalaxyHymnImpactVisuals() {
    }

    public static void trigger(Vec3 center, float intensity, int visualSeed, boolean completeBurst) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || center == null || !Float.isFinite(intensity) || intensity <= 0.0F) {
            return;
        }
        activeLevel = minecraft.level;
        impactCenter = center;
        startGameTime = minecraft.level.getGameTime();
        // The hit frame owns the only impact pulse; there is no delayed finale packet.
        shakeIntensity = intensity;
        shakeDuration = DURATION_TICKS;
        shakeTotalDuration = DURATION_TICKS;
        shakeTickCounter = 0;
        blackWorldEnabled = true;
        if (completeBurst) {
            Vec3 elevatedBurstCenter = center.add(0.0D, GalaxyHymnFieldEntity.CENTER_STAR_HEIGHT, 0.0D);
            GalaxyHymnSpaceShardVisuals.trigger(minecraft.level, elevatedBurstCenter, visualSeed);
            GalaxyHymnNebulaVisuals.trigger(minecraft.level, elevatedBurstCenter, visualSeed);
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (activeLevel == null) {
            return;
        }
        if (minecraft.level != activeLevel
                || age(0.0F) >= GalaxyHymnSpaceShardMath.TOTAL_DURATION_TICKS) {
            reset();
        }
    }

    /** Mirrors ArcaneVortex ScreenShakeHelper.ClientHandler's START-phase state update. */
    public static void tickShakeAtStart() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        if (shakeDuration > 0) {
            shakeTickCounter++;
            shakeDuration--;
            if (shakeDuration <= 0) {
                shakeIntensity = 0.0F;
                shakeDuration = 0;
                shakeTotalDuration = 0;
                shakeTickCounter = 0;
            }
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            float blackWorldAge = age(event.getPartialTick()) / BLACK_WORLD_DURATION_TICKS;
            if (blackWorldEnabled && blackWorldAge <= 1.0F && hasImpactContext()) {
                GalaxyHymnBlackWorldShader.render(impactCenter, blackWorldAge, event.getPoseStack());
            }
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            GalaxyHymnSpaceShardVisuals.render(event);
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            // Forge renders vanilla clouds between AFTER_PARTICLES and AFTER_WEATHER.
            // Keep the world-space nebula after clouds without using the invalid
            // AFTER_LEVEL matrix state that previously made the proxy disappear.
            GalaxyHymnNebulaVisuals.render(event);
        }
    }

    public static void applyCameraShake(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || shakeDuration <= 0 || shakeTotalDuration <= 0) {
            return;
        }
        Random random = new Random();
        float progress = 1.0F - shakeDuration / (float) shakeTotalDuration;
        float fadeFactor = 1.0F - (float) Math.pow(1.0F - progress, 3.0D);
        float currentIntensity = shakeIntensity * (1.0F - fadeFactor);
        double x = (random.nextDouble() - 0.5D) * currentIntensity * 2.0D;
        double y = (random.nextDouble() - 0.5D) * currentIntensity * 2.0D;
        double z = 0.0D;
        event.setYaw(event.getYaw() + (float) x);
        event.setPitch(event.getPitch() + (float) y);
        event.setRoll(event.getRoll() + (float) z);
    }

    public static void reset() {
        activeLevel = null;
        impactCenter = null;
        startGameTime = 0L;
        shakeIntensity = 0.0F;
        shakeDuration = 0;
        shakeTotalDuration = 0;
        shakeTickCounter = 0;
        blackWorldEnabled = false;
        GalaxyHymnNebulaVisuals.reset();
        GalaxyHymnSpaceShardVisuals.reset();
    }

    private static boolean hasImpactContext() {
        Minecraft minecraft = Minecraft.getInstance();
        return activeLevel != null && impactCenter != null && minecraft.level == activeLevel;
    }

    private static float age(float partialTick) {
        return activeLevel == null ? DURATION_TICKS
                : Math.max(0.0F, activeLevel.getGameTime() - startGameTime + partialTick);
    }
}

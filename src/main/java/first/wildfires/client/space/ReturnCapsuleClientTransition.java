/*
 * Adapted from VS: Genesis TransitionFrame/TransitionState.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236.
 * SPDX-License-Identifier: Apache-2.0
 * Wildfires changes: bounded reusable-capsule transaction state and captured departure frame.
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import first.wildfires.network.ReturnCapsuleTransitionArmedPacket;
import first.wildfires.network.ReturnCapsuleTrackingAckPacket;
import first.wildfires.network.ReturnCapsuleTrackingReadyPacket;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.util.UUID;

/** Client-only bridge that preserves the last rendered flight frame while chunks change worlds. */
public final class ReturnCapsuleClientTransition {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static RenderTarget captured;
    private static UUID ticketId;
    private static UUID capsuleId;
    private static ResourceLocation targetDimension;
    private static boolean toStation;
    private static long armedAtMillis;
    private static boolean acknowledgementSent;
    private static boolean serverConfirmed;
    private static boolean transferCommitted;
    private static long lastArmedAcknowledgementMillis;
    private static long lastAcknowledgementMillis;
    private static long lastReadyMillis;
    private static int stableTargetTicks;
    private static int renderedTargetFrames;
    private static boolean orbitContextReady;
    private static boolean orbitCelestialReady;
    private static boolean orbitBodyRendered;
    private static int lastReadinessBits = -1;
    private static String passengerRepairStatus = "idle";
    private static String lastReportedRepairStatus;

    private static final long TIMEOUT_MILLIS = 300_000L;
    private static final long ACK_RETRY_MILLIS = 500L;
    private static final int REQUIRED_STABLE_TICKS = 2;
    private static final int REQUIRED_RENDERED_FRAMES = 2;

    private ReturnCapsuleClientTransition() {
    }

    public static void arm(UUID ticket, UUID capsule, ResourceLocation target, boolean stationDirection) {
        if (ticket.equals(ticketId) && capsule.equals(capsuleId) && target.equals(targetDimension)) {
            installReceivingScreenIfNeeded();
            sendArmedAcknowledgementIfDue(true);
            return;
        }
        ticketId = ticket;
        capsuleId = capsule;
        targetDimension = target;
        toStation = stationDirection;
        armedAtMillis = System.currentTimeMillis();
        acknowledgementSent = false;
        serverConfirmed = false;
        transferCommitted = false;
        lastArmedAcknowledgementMillis = 0L;
        lastAcknowledgementMillis = 0L;
        lastReadyMillis = 0L;
        stableTargetTicks = 0;
        renderedTargetFrames = 0;
        orbitContextReady = false;
        orbitCelestialReady = false;
        orbitBodyRendered = false;
        lastReadinessBits = -1;
        passengerRepairStatus = "idle";
        lastReportedRepairStatus = null;
        captureFrame();
        installReceivingScreenIfNeeded();
        ResourceLocation source = Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.dimension().location();
        LOGGER.info("[Wildfires return capsule/client] armed ticket={} capsule={} source={} target={} toStation={}",
                ticketId, capsuleId, source, targetDimension, toStation);
        // This runs on Minecraft's client thread. The server must not send Respawn until this
        // acknowledgement proves that ticket identity and the departure framebuffer are armed.
        sendArmedAcknowledgementIfDue(true);
    }

    private static void installReceivingScreenIfNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        if ((minecraft.screen instanceof ReceivingLevelScreen
                || minecraft.screen instanceof ProgressScreen)
                && !(minecraft.screen instanceof ReturnCapsuleReceivingScreen)) {
            minecraft.setScreen(new ReturnCapsuleReceivingScreen());
        }
    }

    public static boolean armed() {
        return ticketId != null;
    }

    public static boolean timedOut() {
        return armed() && System.currentTimeMillis() - armedAtMillis >= TIMEOUT_MILLIS;
    }

    public static boolean toStation() {
        return toStation;
    }

    public static RenderTarget captured() {
        return captured;
    }

    public static void complete() {
        ticketId = null;
        capsuleId = null;
        targetDimension = null;
        armedAtMillis = 0L;
        acknowledgementSent = false;
        serverConfirmed = false;
        transferCommitted = false;
        lastArmedAcknowledgementMillis = 0L;
        lastAcknowledgementMillis = 0L;
        lastReadyMillis = 0L;
        stableTargetTicks = 0;
        renderedTargetFrames = 0;
        orbitContextReady = false;
        orbitCelestialReady = false;
        orbitBodyRendered = false;
        lastReadinessBits = -1;
        passengerRepairStatus = "idle";
        lastReportedRepairStatus = null;
    }

    /** Ends the client world session and releases the reusable departure framebuffer. */
    public static void shutdown() {
        complete();
        releaseCapturedFrame();
    }

    /** A recovery packet may close only the exact transition it was issued for. */
    public static void abort(UUID ticket, UUID capsule) {
        if (!ticket.equals(ticketId) || !capsule.equals(capsuleId)) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean closeReceivingScreen = minecraft.screen instanceof ReturnCapsuleReceivingScreen;
        // Clear first: ReturnCapsuleMinecraftMixin intentionally rejects setScreen(null) while a
        // capsule transition is armed, so reversing this order would preserve the stuck screen.
        complete();
        releaseCapturedFrame();
        if (closeReceivingScreen) minecraft.setScreen(null);
    }

    /** Server-side proof that this exact tracking acknowledgement was accepted. */
    public static void confirm(UUID ticket, UUID capsule, ResourceLocation target) {
        if (!armed() || !ticket.equals(ticketId) || !capsule.equals(capsuleId)
                || !target.equals(targetDimension)) return;
        if (!serverConfirmed) {
            LOGGER.info("[Wildfires return capsule/client] server confirmed target tracking ticket={} capsule={}",
                    ticket, capsule);
        }
        serverConfirmed = true;
    }

    /** Final server proof that the transfer barrier has advanced to its released state. */
    public static void commit(UUID ticket, UUID capsule, ResourceLocation target) {
        if (!armed() || !ticket.equals(ticketId) || !capsule.equals(capsuleId)
                || !target.equals(targetDimension)) return;
        serverConfirmed = true;
        if (!transferCommitted) {
            LOGGER.info("[Wildfires return capsule/client] server released target scene ticket={} capsule={}",
                    ticket, capsule);
        }
        transferCommitted = true;
    }

    /** Called after a non-orbit destination level has completed a render pass. */
    public static void markTargetFrameRendered() {
        if (!toStation && serverConfirmed && targetGraphReady()) {
            renderedTargetFrames = Math.min(REQUIRED_RENDERED_FRAMES, renderedTargetFrames + 1);
        }
    }

    /**
     * The orbit receiving barrier counts only frames that actually rendered the current body's
     * Genesis cube. A black vacuum fallback is never evidence that the target scene is ready.
     */
    public static void markOrbitSceneRendered(boolean contextReady, boolean celestialReady,
                                               boolean currentBodyRendered) {
        if (!armed() || !toStation) return;
        orbitContextReady = contextReady;
        orbitCelestialReady = celestialReady;
        orbitBodyRendered = currentBodyRendered;
        reportReadiness("orbit-render");
        if (orbitSceneReady() && serverConfirmed && targetGraphReady()) {
            renderedTargetFrames = Math.min(REQUIRED_RENDERED_FRAMES, renderedTargetFrames + 1);
        } else {
            renderedTargetFrames = 0;
        }
    }

    /**
     * NTM remounts at the destination before flight continues. Modern clients acknowledge the
     * equivalent only after the level, entity tracker and local passenger graph all agree.
     */
    public static void tick() {
        if (ticketId == null) return;
        if (!armed() || timedOut()) return;
        sendArmedAcknowledgementIfDue(false);
        holdAndRepairTargetPassengerGraph();
        reportReadiness("client-tick");
        if (!targetGraphReady()) {
            stableTargetTicks = 0;
            renderedTargetFrames = 0;
            return;
        }
        if (toStation && !orbitSceneReady()) {
            renderedTargetFrames = 0;
        }
        stableTargetTicks = Math.min(REQUIRED_STABLE_TICKS, stableTargetTicks + 1);
        long now = System.currentTimeMillis();
        if (!serverConfirmed && (!acknowledgementSent
                || now - lastAcknowledgementMillis >= ACK_RETRY_MILLIS)) {
            acknowledgementSent = true;
            lastAcknowledgementMillis = now;
            new ReturnCapsuleTrackingAckPacket(ticketId, capsuleId).sendToServer();
        }
        if (serverConfirmed && !transferCommitted
                && stableTargetTicks >= REQUIRED_STABLE_TICKS
                && renderedTargetFrames >= REQUIRED_RENDERED_FRAMES
                && (lastReadyMillis == 0L || now - lastReadyMillis >= ACK_RETRY_MILLIS)) {
            lastReadyMillis = now;
            new ReturnCapsuleTrackingReadyPacket(ticketId, capsuleId).sendToServer();
        }
        if (transferCommitted && stableTargetTicks >= REQUIRED_STABLE_TICKS
                && renderedTargetFrames >= REQUIRED_RENDERED_FRAMES) {
            finishClientTransition();
        }
    }

    private static void sendArmedAcknowledgementIfDue(boolean immediate) {
        if (!armed()) return;
        long now = System.currentTimeMillis();
        if (!immediate && lastArmedAcknowledgementMillis != 0L
                && now - lastArmedAcknowledgementMillis < ACK_RETRY_MILLIS) return;
        lastArmedAcknowledgementMillis = now;
        new ReturnCapsuleTransitionArmedPacket(ticketId, capsuleId).sendToServer();
    }

    private static boolean targetGraphReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || targetDimension == null
                || !minecraft.level.dimension().location().equals(targetDimension)) return false;
        ReusableReturnCapsuleEntity tracked = findTrackedTargetCapsule(minecraft);
        return tracked != null && minecraft.player.getVehicle() == tracked
                && tracked.hasPassenger(minecraft.player);
    }

    private static boolean orbitSceneReady() {
        return orbitContextReady && orbitCelestialReady && orbitBodyRendered;
    }

    /** Lets the receiving screen reveal only a live target frame already proven safe to publish. */
    public static boolean targetSceneReadyForPreview() {
        if (!armed() || !targetGraphReady()) return false;
        return toStation ? orbitSceneReady() : renderedTargetFrames > 0;
    }

    private static void reportReadiness(String source) {
        if (!armed()) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean targetLevel = minecraft.level != null && targetDimension != null
                && minecraft.level.dimension().location().equals(targetDimension);
        boolean exactMounted = targetGraphReady();
        boolean exactTracked = findTrackedTargetCapsule(minecraft) != null;
        int bits = (targetLevel ? 1 : 0)
                | (exactTracked ? 2 : 0)
                | (exactMounted ? 4 : 0)
                | (serverConfirmed ? 8 : 0)
                | (transferCommitted ? 16 : 0)
                | (orbitContextReady ? 32 : 0)
                | (orbitCelestialReady ? 64 : 0)
                | (orbitBodyRendered ? 128 : 0);
        if (bits == lastReadinessBits
                && passengerRepairStatus.equals(lastReportedRepairStatus)) return;
        lastReadinessBits = bits;
        lastReportedRepairStatus = passengerRepairStatus;
        Entity vehicle = minecraft.player == null ? null : minecraft.player.getVehicle();
        ReusableReturnCapsuleEntity tracked = findTrackedTargetCapsule(minecraft);
        LOGGER.info("[Wildfires return capsule/client] readiness source={} ticket={} capsule={} "
                        + "level={} tracked={} mounted={} confirmed={} released={} context={} celestial={} cube={} "
                        + "frames={} repair={} vehicle={} targetPassengers={} targetHasPlayer={}",
                source, ticketId, capsuleId, targetLevel, exactTracked, exactMounted,
                serverConfirmed, transferCommitted, orbitContextReady, orbitCelestialReady,
                orbitBodyRendered, renderedTargetFrames, passengerRepairStatus,
                describeEntity(vehicle), tracked == null ? -1 : tracked.getPassengers().size(),
                tracked != null && minecraft.player != null && tracked.hasPassenger(minecraft.player));
    }

    private static String describeEntity(Entity entity) {
        if (entity == null) return "none";
        return entity.getType() + "#" + entity.getId() + "/" + entity.getUUID()
                + " removed=" + entity.isRemoved()
                + " level=" + entity.level().dimension().location();
    }

    private static ReusableReturnCapsuleEntity findTrackedTargetCapsule(Minecraft minecraft) {
        if (minecraft.level == null || targetDimension == null
                || !minecraft.level.dimension().location().equals(targetDimension)) return null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof ReusableReturnCapsuleEntity capsule
                    && capsule.getUUID().equals(capsuleId)) return capsule;
        }
        return null;
    }

    /**
     * Modern Respawn can install the target level before the passenger packet that refers to the
     * newly tracked capsule. NTM's server is already authoritative that this exact player rides
     * this exact pod; repair only that same UUID graph locally and keep the prediction from falling
     * into the orbit void while the normal server packet is retried.
     */
    private static void holdAndRepairTargetPassengerGraph() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || targetDimension == null
                || !minecraft.level.dimension().location().equals(targetDimension)) return;

        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.fallDistance = 0.0F;
        ReusableReturnCapsuleEntity tracked = findTrackedTargetCapsule(minecraft);
        if (tracked == null) {
            passengerRepairStatus = "target-untracked";
            return;
        }
        Entity mounted = minecraft.player.getVehicle();
        if (mounted == tracked && tracked.hasPassenger(minecraft.player)) {
            passengerRepairStatus = "already-mounted";
            tracked.positionRider(minecraft.player);
            return;
        }

        // Respawn replaces LocalPlayer while the source and target capsules intentionally share a
        // UUID. Only a removed/wrong-level capsule with that exact ticket UUID is safe to detach;
        // an unrelated live vehicle remains untouched.
        if (mounted != null) {
            boolean staleExactCapsule = mounted instanceof ReusableReturnCapsuleEntity capsule
                    && capsule.getUUID().equals(capsuleId)
                    && (capsule != tracked || capsule.isRemoved()
                    || capsule.level() != minecraft.level);
            if (!staleExactCapsule) {
                passengerRepairStatus = "blocked-live-foreign-vehicle";
                return;
            }
            minecraft.player.stopRiding();
            if (minecraft.player.getVehicle() != null) {
                passengerRepairStatus = "stale-vehicle-dismount-rejected";
                return;
            }
        }

        // SetPassengers uses the same eject-then-rebuild contract. Clearing the exact ticket pod
        // here also removes a pre-Respawn LocalPlayer ghost if the first vanilla packet raced the
        // replacement player. The server continues replaying the authoritative graph every 5 ticks.
        if (!tracked.getPassengers().isEmpty()) tracked.ejectPassengers();
        if (minecraft.player.startRiding(tracked, true)) {
            passengerRepairStatus = "mounted";
            tracked.positionRider(minecraft.player);
            minecraft.player.setDeltaMovement(Vec3.ZERO);
            minecraft.player.fallDistance = 0.0F;
        } else {
            passengerRepairStatus = "mount-rejected";
        }
    }

    private static void finishClientTransition() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean closeReceivingScreen = minecraft.screen instanceof ReturnCapsuleReceivingScreen;
        LOGGER.info("[Wildfires return capsule/client] completed ticket={} capsule={} target={} frames={}",
                ticketId, capsuleId, targetDimension, renderedTargetFrames);
        complete();
        if (closeReceivingScreen) minecraft.setScreen(null);
    }

    private static void captureFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (captured == null || captured.width != main.width || captured.height != main.height) {
            if (captured != null) captured.destroyBuffers();
            captured = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            captured.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captured.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, captured.width,
                captured.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
    }

    /**
     * Normal completed trips retain one same-size target for the next trip. Abort and logout are
     * lifecycle endpoints, so they must relinquish its colour/depth attachments without risking a
     * later render-thread callback destroying a newly allocated capture.
     */
    private static void releaseCapturedFrame() {
        RenderTarget frame = captured;
        captured = null;
        if (frame == null) return;
        if (RenderSystem.isOnRenderThread()) {
            frame.destroyBuffers();
        } else {
            RenderSystem.recordRenderCall(frame::destroyBuffers);
        }
    }
}

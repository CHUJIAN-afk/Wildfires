package first.wildfires.space;

import first.wildfires.space.capsule.ReturnCapsuleFuelLedger;
import first.wildfires.space.capsule.ReturnCapsuleState;
import first.wildfires.space.capsule.ReturnCapsuleTransitionTicket;
import first.wildfires.space.capsule.ReturnCapsuleService;
import first.wildfires.network.ReturnCapsuleTransitionAbortPacket;
import first.wildfires.network.PlayerInputPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Pure transaction and persistence checks before dimension-changing GameTests. */
public final class ReturnCapsuleSelfTest {

    private ReturnCapsuleSelfTest() {
    }

    public static void main(String[] args) {
        reservationsRemainIdempotent();
        committedTicketTombstoneMakesReplayIdempotent();
        ticketsAndStableStatesRoundTrip();
        abortPacketRoundTripsExactTransactionIdentity();
        playerInputPacketPreservesPressAndReleaseEdges();
        actionResultsAndPhaseProgressAreStable();
        bottomDockGeometryAndVisualCurvesAreStable();
        System.out.println("ReturnCapsuleSelfTest: all checks passed");
    }

    private static void abortPacketRoundTripsExactTransactionIdentity() {
        UUID ticket = UUID.randomUUID();
        UUID capsule = UUID.randomUUID();
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        new ReturnCapsuleTransitionAbortPacket(ticket, capsule).encode(encoded);
        ReturnCapsuleTransitionAbortPacket decoded = new ReturnCapsuleTransitionAbortPacket(encoded);
        assertEquals(ticket, decoded.ticketId(), "abort packet ticket identity");
        assertEquals(capsule, decoded.capsuleId(), "abort packet capsule identity");
    }

    private static void playerInputPacketPreservesPressAndReleaseEdges() {
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        new PlayerInputPacket(true).encode(encoded);
        new PlayerInputPacket(false).encode(encoded);
        assertTrue(new PlayerInputPacket(encoded).pressed(), "jump press packet edge");
        assertTrue(!new PlayerInputPacket(encoded).pressed(), "jump release packet edge");
    }

    private static void committedTicketTombstoneMakesReplayIdempotent() {
        ReturnCapsuleFuelLedger ledger = new ReturnCapsuleFuelLedger();
        UUID ticket = UUID.randomUUID();
        assertTrue(ledger.reserve(ticket, 4_000), "trip reserves one bucket");
        ledger.markCommitted(ticket);
        assertTrue(!ledger.matches(ticket) && ledger.wasCommitted(ticket),
                "commit clears reservation and retains its tombstone");
        ReturnCapsuleFuelLedger restored = new ReturnCapsuleFuelLedger();
        restored.restoreLastCommitted(ledger.lastCommitted().orElseThrow());
        assertTrue(restored.wasCommitted(ticket), "committed tombstone restores by UUID");
        UUID next = UUID.randomUUID();
        assertTrue(restored.reserve(next, 3_000), "a later trip may reserve normally");
        assertTrue(restored.release(next), "later reservation may roll back");
        assertTrue(restored.wasCommitted(ticket), "rollback does not erase the committed tombstone");
    }

    private static void reservationsRemainIdempotent() {
        ReturnCapsuleFuelLedger ledger = new ReturnCapsuleFuelLedger();
        UUID first = UUID.randomUUID();
        assertTrue(ledger.reserve(first, 1_000), "first trip reserves");
        assertTrue(ledger.reserve(first, 0), "same ticket reserve is idempotent");
        assertTrue(ledger.matches(first), "matching ticket can commit");
        assertTrue(ledger.release(first), "matching ticket releases once");
        assertTrue(!ledger.release(first), "duplicate release is inert");
        UUID restored = UUID.randomUUID();
        ledger.restore(restored, 1_000, 1_000);
        assertEquals(1_000, ledger.reservedMb(), "restored reservation amount");
        assertTrue(ledger.matches(restored), "restored ticket identity");
    }

    private static void ticketsAndStableStatesRoundTrip() {
        for (ReturnCapsuleState state : ReturnCapsuleState.values()) {
            assertEquals(state, ReturnCapsuleState.fromStableId(state.stableId()).orElseThrow(),
                    "state stable id round trip");
        }
        ResourceLocation body = ResourceLocation.fromNamespaceAndPath("example", "ceres");
        ResourceLocation surfaceDimension = ResourceLocation.fromNamespaceAndPath(
                "example", "ceres_surface");
        ReturnCapsuleTransitionTicket ticket = new ReturnCapsuleTransitionTicket(UUID.randomUUID(),
                UUID.randomUUID(), ReturnCapsuleTransitionTicket.Direction.TO_STATION,
                body, surfaceDimension,
                new BlockPos(12, 70, -9), id("orbit"), new BlockPos(2048, 128, 2048),
                UUID.randomUUID(), 17L, 300L, ReturnCapsuleTransitionTicket.Stage.PREPARED);
        CompoundTag tag = ticket.save();
        assertEquals(ticket, ReturnCapsuleTransitionTicket.load(tag), "ticket NBT round trip");
        assertEquals(ReturnCapsuleTransitionTicket.Stage.TRANSFERRED,
                ticket.withStage(ReturnCapsuleTransitionTicket.Stage.TRANSFERRED).stage(),
                "ticket stage advances without changing identity");
        assertEquals(ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING,
                ticket.withStage(ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING).stage(),
                "modern transfer persists the destination remount acknowledgement gate");
        assertEquals(ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED,
                ticket.withStage(ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED).stage(),
                "source client acknowledgement is a persisted pre-Respawn gate");
        assertEquals(body, ticket.bodyId(), "ticket preserves a non-Earth departure body");
        assertEquals(surfaceDimension,
                ticket.surfaceDimension(), "surface dimension is direction-independent");
        assertEquals(new BlockPos(12, 70, -9), ticket.surfacePosition(),
                "surface position is direction-independent");
        ReturnCapsuleTransitionTicket shifted = ticket.withSurfacePosition(new BlockPos(17, 71, -9));
        assertEquals(new BlockPos(17, 71, -9), shifted.sourcePosition(),
                "to-station surface endpoint shifts in source position");
        assertEquals(ticket.targetPosition(), shifted.targetPosition(),
                "surface shift preserves orbit endpoint");
        ReturnCapsuleTransitionTicket returnTicket = new ReturnCapsuleTransitionTicket(UUID.randomUUID(),
                UUID.randomUUID(), ReturnCapsuleTransitionTicket.Direction.TO_SURFACE,
                body, id("orbit"), new BlockPos(2048, 128, 2048), surfaceDimension,
                new BlockPos(12, 70, -9), UUID.randomUUID(), 22L, 500L,
                ReturnCapsuleTransitionTicket.Stage.PREPARED);
        ReturnCapsuleTransitionTicket shiftedReturn = returnTicket.withSurfacePosition(
                new BlockPos(12, 68, -14));
        assertEquals(new BlockPos(12, 68, -14), shiftedReturn.targetPosition(),
                "to-surface endpoint shifts in target position");
        assertEquals(returnTicket.sourcePosition(), shiftedReturn.sourcePosition(),
                "return surface shift preserves orbit endpoint");
        assertTrue(ticket.hasKnownPassenger(), "new tickets retain passenger evidence");
    }

    private static void actionResultsAndPhaseProgressAreStable() {
        for (ReturnCapsuleService.ActionResult result : ReturnCapsuleService.ActionResult.values()) {
            assertTrue(result.translationKey().startsWith("space.wildfires.return_capsule.action."),
                    "capsule result has a stable translation key: " + result);
        }
        assertTrue(ReturnCapsuleService.ActionResult.STARTED.successful()
                        && ReturnCapsuleService.ActionResult.RECOVERED.successful(),
                "only start and recovery are successful capsule actions");
        assertTrue(!ReturnCapsuleService.ActionResult.NO_FUEL.successful()
                        && !ReturnCapsuleService.ActionResult.RECOVERY_REQUIRED.successful(),
                "failure actions remain diagnostic failures");
        assertEquals(360, ReturnCapsuleService.LAUNCH_TICKS, "altitude-driven launch presentation scale");
        assertEquals(45, ReturnCapsuleService.DOOR_CLOSE_TICKS, "door closes before ignition");
        assertEquals(20, ReturnCapsuleService.INSERTION_TICKS, "NTM docking load wait");
        assertEquals(80, ReturnCapsuleService.APPROACH_TICKS,
                "twenty-tick NTM port wait plus fourfold bounded final approach");
        assertEquals(60, ReturnCapsuleService.UNDOCK_TICKS,
                "fourfold NTM-speed undock presentation scale");
        assertEquals(800, ReturnCapsuleService.REENTRY_TICKS, "NTM Y800 surface-entry scale");
        assertEquals(900, ReturnCapsuleService.LANDING_TICKS, "NTM terrain-distance landing scale");
        assertEquals(95, ReturnCapsuleService.TIPPING_EXPLOSION_TICKS,
                "NTM liquid tipping reaches ninety degrees before lava explosion");
        assertEquals(Double.valueOf(4.0D), Double.valueOf(ReturnCapsuleService.CAPSULE_HEIGHT),
                "NTM rp_pod_20 logical entity height");
        assertEquals(Double.valueOf(1.0D), Double.valueOf(ReturnCapsuleService.CAPSULE_HALF_WIDTH),
                "NTM EntityRideableRocket logical half-width");
        assertEquals(Double.valueOf(4.0D), Double.valueOf(ReturnCapsuleService.NTM_MOTION_MULTIPLIER),
                "NTM EntityRideableRocket inherited motion multiplier");
    }

    private static void bottomDockGeometryAndVisualCurvesAreStable() {
        BlockPos core = new BlockPos(40, 128, -22);
        Vec3 dock = ReturnCapsuleService.stationDockedPosition(core);
        assertEquals(Double.valueOf(core.getX() + 0.5D), Double.valueOf(dock.x), "dock x centre");
        assertEquals(Double.valueOf(core.getY() + 1.5D - ReturnCapsuleService.CAPSULE_HEIGHT),
                Double.valueOf(dock.y), "NTM spawnRocket docking base");
        assertEquals(Double.valueOf(core.getY() + 1.5D),
                Double.valueOf(dock.y + ReturnCapsuleService.CAPSULE_HEIGHT),
                "pod top reaches NTM coreY + 1.5 docking plane");
        assertEquals(Double.valueOf(core.getY() - 2.5D), Double.valueOf(dock.y),
                "height-four NTM pod hangs at coreY - 2.5");
        Vec3 insertion = ReturnCapsuleService.stationInsertionStart(core);
        Vec3 approach = ReturnCapsuleService.stationApproachStart(core);
        assertEquals(approach, insertion,
                "the rendered target scene releases directly into the final approach");
        assertEquals(Double.valueOf(24.0D), Double.valueOf(dock.y - insertion.y),
                "final approach is bounded to twenty-four blocks below the real port");
        assertEquals(Double.valueOf(dock.x), Double.valueOf(insertion.x),
                "final approach never snaps horizontally after the loading wait");
        assertEquals(Double.valueOf(dock.z), Double.valueOf(insertion.z),
                "final approach never snaps horizontally after the loading wait");
        assertEquals(Double.valueOf(24.0D),
                Double.valueOf(dock.y - ReturnCapsuleService.stationUndockEnd(core).y),
                "undocking keeps a bounded twenty-four-block visible departure");
        var open = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.SURFACE_LANDED, 45.0D, Vec3.ZERO, false);
        var closed = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.SURFACE_CLOSING, 45.0D, Vec3.ZERO, false);
        var lockedInFlight = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.SURFACE_LAUNCHING, 1.0D, Vec3.ZERO, false);
        var braking = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.REENTRY, 50.0D, new Vec3(0.0D, -1.0D, 0.0D), false);
        var finalDescent = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.REENTRY, 20.0D, new Vec3(0.0D, -0.2D, 0.0D), false);
        var terminalBraking = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.SURFACE_LANDING, 50.0D, new Vec3(0.0D, -1.0D, 0.0D), false);
        var terminalPowered = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.SURFACE_LANDING, 50.0D, new Vec3(0.0D, -0.2D, 0.0D), false);
        var orbitRenderAll = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.STATION_DOCKED, 50.0D, Vec3.ZERO, true);
        var tipping = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.SURFACE_TIPPING, 50.0D, Vec3.ZERO, false);
        var tipped = first.wildfires.space.capsule.ReturnCapsuleVisuals.snapshot(
                ReturnCapsuleState.SURFACE_TIPPING, 95.0D, Vec3.ZERO, false);
        assertEquals(Float.valueOf(90.0F), Float.valueOf(open.doorDegrees()), "landed door endpoint");
        assertEquals(Float.valueOf(0.0F), Float.valueOf(closed.doorDegrees()), "launch door endpoint");
        assertEquals(Float.valueOf(0.0F), Float.valueOf(lockedInFlight.doorDegrees()),
                "flight always keeps the door locked shut");
        assertEquals(Float.valueOf(65.0F), Float.valueOf(braking.airbrakeDegrees()),
                "reentry airbrake endpoint");
        assertEquals(Float.valueOf(0.0F), Float.valueOf(finalDescent.airbrakeDegrees()),
                "NTM airbrakes close after descent slows above -0.4");
        assertEquals(Float.valueOf(1.0F), Float.valueOf(braking.legExtension()),
                "NTM landing retains fully extended legs");
        assertEquals(Float.valueOf(65.0F), Float.valueOf(terminalBraking.airbrakeDegrees()),
                "terminal landing keeps the same NTM airbrake contract");
        assertTrue(terminalPowered.mainEngine(),
                "terminal landing keeps the same NTM low-speed four-nozzle contract");
        assertEquals(Float.valueOf(0.0F), Float.valueOf(orbitRenderAll.legExtension()),
                "NTM orbit renderAll keeps the OBJ default leg transform");
        assertTrue(orbitRenderAll.orbitRenderAll() && !open.orbitRenderAll(),
                "actual dimension selects NTM renderAll versus surface HeatShield exclusion");
        assertEquals(Float.valueOf(25.0F), Float.valueOf(tipping.pitchDegrees()),
                "NTM tipping pitch follows squared timer");
        assertEquals(Float.valueOf(90.0F), Float.valueOf(tipped.pitchDegrees()),
                "NTM tipping pitch clamps at ninety degrees");
        assertTrue(Float.isFinite(open.doorDegrees()) && Float.isFinite(braking.airbrakeDegrees())
                        && braking.legExtension() >= 0.0F && braking.legExtension() <= 1.0F,
                "visual curves remain finite and bounded");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("wildfires", path);
    }

    private static void assertTrue(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }
}

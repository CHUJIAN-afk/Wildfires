package first.wildfires.space;

import first.wildfires.space.capsule.ReturnCapsuleFuelLedger;
import first.wildfires.space.capsule.ReturnCapsuleState;
import first.wildfires.space.capsule.ReturnCapsuleTransitionTicket;
import first.wildfires.space.capsule.ReturnCapsuleService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Pure transaction and persistence checks before dimension-changing GameTests. */
public final class ReturnCapsuleSelfTest {

    private ReturnCapsuleSelfTest() {
    }

    public static void main(String[] args) {
        reservationsRemainIdempotent();
        committedTicketTombstoneMakesReplayIdempotent();
        ticketsAndStableStatesRoundTrip();
        actionResultsAndPhaseProgressAreStable();
        System.out.println("ReturnCapsuleSelfTest: all checks passed");
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
        ReturnCapsuleTransitionTicket ticket = new ReturnCapsuleTransitionTicket(UUID.randomUUID(),
                UUID.randomUUID(), ReturnCapsuleTransitionTicket.Direction.TO_STATION,
                id("earth"), ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(12, 70, -9), id("orbit"), new BlockPos(2048, 128, 2048),
                UUID.randomUUID(), 17L, 300L, ReturnCapsuleTransitionTicket.Stage.PREPARED);
        CompoundTag tag = ticket.save();
        assertEquals(ticket, ReturnCapsuleTransitionTicket.load(tag), "ticket NBT round trip");
        assertEquals(ReturnCapsuleTransitionTicket.Stage.TRANSFERRED,
                ticket.withStage(ReturnCapsuleTransitionTicket.Stage.TRANSFERRED).stage(),
                "ticket stage advances without changing identity");
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                ticket.surfaceDimension(), "surface dimension is direction-independent");
        assertEquals(new BlockPos(12, 70, -9), ticket.surfacePosition(),
                "surface position is direction-independent");
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
        assertEquals(100, ReturnCapsuleService.LAUNCH_TICKS, "launch visual duration");
        assertEquals(60, ReturnCapsuleService.INSERTION_TICKS, "insertion visual duration");
        assertEquals(100, ReturnCapsuleService.APPROACH_TICKS, "approach visual duration");
        assertEquals(40, ReturnCapsuleService.UNDOCK_TICKS, "undock visual duration");
        assertEquals(100, ReturnCapsuleService.REENTRY_TICKS, "reentry visual duration");
        assertEquals(100, ReturnCapsuleService.LANDING_TICKS, "landing visual duration");
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

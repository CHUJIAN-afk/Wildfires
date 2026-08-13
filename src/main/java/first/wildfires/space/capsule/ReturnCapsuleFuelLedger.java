package first.wildfires.space.capsule;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Registry-independent exactly-once reservation ledger used by the Forge water tank. */
public final class ReturnCapsuleFuelLedger {

    private UUID reservation;
    private int reservedMb;
    private UUID lastCommitted;

    public int reservedMb() {
        return reservedMb;
    }

    public Optional<UUID> reservation() {
        return Optional.ofNullable(reservation);
    }

    /** Persisted tombstone for the most recently completed trip. */
    public Optional<UUID> lastCommitted() {
        return Optional.ofNullable(lastCommitted);
    }

    public boolean reserve(UUID ticketId, int availableMb) {
        Objects.requireNonNull(ticketId, "ticketId");
        if (ticketId.equals(reservation)) return true;
        if (reservation != null || availableMb < ReturnCapsuleFuelTank.TRIP_COST_MB) return false;
        reservation = ticketId;
        reservedMb = ReturnCapsuleFuelTank.TRIP_COST_MB;
        return true;
    }

    public boolean matches(UUID ticketId) {
        return Objects.equals(ticketId, reservation)
                && reservedMb == ReturnCapsuleFuelTank.TRIP_COST_MB;
    }

    public boolean wasCommitted(UUID ticketId) {
        return Objects.equals(ticketId, lastCommitted);
    }

    public void markCommitted(UUID ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        if (!matches(ticketId)) {
            throw new IllegalStateException("Cannot commit an unreserved return-capsule ticket");
        }
        lastCommitted = ticketId;
        clearReservation();
    }

    public boolean release(UUID ticketId) {
        if (!matches(ticketId)) return false;
        clearReservation();
        return true;
    }

    public void restore(UUID ticketId, int amount, int storedMb) {
        Objects.requireNonNull(ticketId, "ticketId");
        if (amount != ReturnCapsuleFuelTank.TRIP_COST_MB || storedMb < amount) {
            throw new IllegalArgumentException("Invalid return capsule fuel reservation amount: " + amount);
        }
        reservation = ticketId;
        reservedMb = amount;
    }

    public void restoreLastCommitted(UUID ticketId) {
        lastCommitted = Objects.requireNonNull(ticketId, "ticketId");
    }

    public void clear() {
        clearReservation();
        lastCommitted = null;
    }

    private void clearReservation() {
        reservation = null;
        reservedMb = 0;
    }
}

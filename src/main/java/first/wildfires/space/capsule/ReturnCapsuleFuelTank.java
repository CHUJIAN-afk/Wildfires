package first.wildfires.space.capsule;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Forge-fluid water tank with an exactly-once reservation/commit boundary. */
public final class ReturnCapsuleFuelTank implements IFluidHandler {

    public static final int CAPACITY_MB = 4_000;
    public static final int TRIP_COST_MB = 1_000;
    private static final String TANK = "tank";
    private static final String RESERVATION = "reservation";
    private static final String RESERVED_MB = "reserved_mb";
    private static final String LAST_COMMITTED = "last_committed";

    private final FluidTank tank;
    private final ReturnCapsuleFuelLedger ledger = new ReturnCapsuleFuelLedger();

    public ReturnCapsuleFuelTank() {
        this(() -> { });
    }

    public ReturnCapsuleFuelTank(Runnable contentsChanged) {
        Objects.requireNonNull(contentsChanged, "contentsChanged");
        tank = new FluidTank(CAPACITY_MB,
                fluid -> !fluid.isEmpty() && fluid.getFluid() == Fluids.WATER) {
            @Override
            protected void onContentsChanged() {
                contentsChanged.run();
            }
        };
    }

    public int storedMb() {
        return tank.getFluidAmount();
    }

    public int reservedMb() {
        return ledger.reservedMb();
    }

    public int availableMb() {
        return storedMb() - ledger.reservedMb();
    }

    public Optional<UUID> reservation() {
        return ledger.reservation();
    }

    public Optional<UUID> lastCommitted() {
        return ledger.lastCommitted();
    }

    public boolean reserveTrip(UUID ticketId) {
        return ledger.reserve(ticketId, availableMb());
    }

    public boolean commit(UUID ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        if (ledger.wasCommitted(ticketId)) {
            return true;
        }
        if (!ledger.matches(ticketId)) {
            return false;
        }
        FluidStack drained = tank.drain(ledger.reservedMb(), FluidAction.EXECUTE);
        if (drained.getAmount() != ledger.reservedMb() || drained.getFluid() != Fluids.WATER) {
            throw new IllegalStateException("Reserved return-capsule water disappeared before commit");
        }
        ledger.markCommitted(ticketId);
        return true;
    }

    public boolean rollback(UUID ticketId) {
        return ledger.release(ticketId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put(TANK, tank.writeToNBT(new CompoundTag()));
        ledger.reservation().ifPresent(value -> {
            tag.putUUID(RESERVATION, value);
            tag.putInt(RESERVED_MB, ledger.reservedMb());
        });
        ledger.lastCommitted().ifPresent(value -> tag.putUUID(LAST_COMMITTED, value));
        return tag;
    }

    public void load(CompoundTag tag) {
        tank.setFluid(FluidStack.EMPTY);
        ledger.clear();
        if (tag.get(TANK) instanceof CompoundTag tankTag) {
            tank.readFromNBT(tankTag);
        }
        if (!tank.getFluid().isEmpty() && tank.getFluid().getFluid() != Fluids.WATER) {
            throw new IllegalArgumentException("Return capsule tank contains a non-water fluid");
        }
        if (tag.contains(RESERVATION, Tag.TAG_INT_ARRAY)
                || tag.contains(RESERVED_MB, Tag.TAG_INT)) {
            if (!tag.contains(RESERVATION, Tag.TAG_INT_ARRAY)
                    || !tag.contains(RESERVED_MB, Tag.TAG_INT)) {
                throw new IllegalArgumentException("Incomplete return capsule fuel reservation");
            }
            int amount = tag.getInt(RESERVED_MB);
            ledger.restore(tag.getUUID(RESERVATION), amount, tank.getFluidAmount());
        }
        if (tag.contains(LAST_COMMITTED, Tag.TAG_INT_ARRAY)) {
            UUID committed = tag.getUUID(LAST_COMMITTED);
            if (ledger.reservation().filter(committed::equals).isPresent()) {
                throw new IllegalArgumentException("Committed return-capsule ticket is still reserved");
            }
            ledger.restoreLastCommitted(committed);
        }
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tankIndex) {
        return tankIndex == 0 ? tank.getFluid().copy() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return tankIndex == 0 ? CAPACITY_MB : 0;
    }

    @Override
    public boolean isFluidValid(int tankIndex, FluidStack stack) {
        return tankIndex == 0 && !stack.isEmpty() && stack.getFluid() == Fluids.WATER;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return tank.fill(resource, action);
    }

    /** External automation may never extract propulsion water. */
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    /** External automation may never extract propulsion water. */
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}

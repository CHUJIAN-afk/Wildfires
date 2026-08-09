package first.wildfires.mixin.tfc;

import first.wildfires.tfc.calendar.TfcCalendarRateController;
import net.dries007.tfc.client.ClientCalendar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps TFC's client calendar moving smoothly between its normal server update packets. */
@Mixin(value = ClientCalendar.class, remap = false)
public abstract class ClientCalendarRateMixin {

    @Unique
    private long wildfires$calendarTicksBefore;

    @Inject(method = "onClientTick", at = @At("HEAD"))
    private void wildfires$captureCalendarTicks(CallbackInfo callback) {
        wildfires$calendarTicksBefore = ((CalendarAccessor) this).wildfires$getCalendarTicks();
    }

    @Inject(method = "onClientTick", at = @At("TAIL"))
    private void wildfires$applyCalendarRate(CallbackInfo callback) {
        CalendarAccessor calendar = (CalendarAccessor) this;
        long current = calendar.wildfires$getCalendarTicks();
        if (current - wildfires$calendarTicksBefore != 1L) {
            return;
        }
        long desiredAdvance = TfcCalendarRateController.clientCalendarTicksForBaseAdvance(true);
        long correction = desiredAdvance - 1L;
        if (correction != 0L) {
            calendar.wildfires$setCalendarTicks(current + correction);
        }
    }
}

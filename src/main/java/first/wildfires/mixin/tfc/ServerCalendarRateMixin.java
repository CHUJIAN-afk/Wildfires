package first.wildfires.mixin.tfc;

import first.wildfires.celestial.TfcCalendarEventAcceleration;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import net.dries007.tfc.util.calendar.ServerCalendar;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the configured rate after TFC confirms that its calendar advanced normally. */
@Mixin(value = ServerCalendar.class, remap = false)
public abstract class ServerCalendarRateMixin {

    @Unique
    private long wildfires$calendarTicksBefore;

    /** Cancels an active target before TFC commands or another mod directly jump the calendar. */
    @Inject(method = "setTimeFromCalendarTime", at = @At("HEAD"))
    private void wildfires$cancelTargetBeforeDirectTimeJump(long targetTick, CallbackInfo callback) {
        long current = ((CalendarAccessor) this).wildfires$getCalendarTicks();
        if (current != targetTick) {
            TfcCalendarEventAcceleration.onExternalCalendarJump(
                    ServerLifecycleHooks.getCurrentServer(), current, targetTick);
        }
    }

    @Inject(method = "onOverworldTick", at = @At("HEAD"))
    private void wildfires$captureCalendarTicks(ServerLevel level, CallbackInfo callback) {
        wildfires$calendarTicksBefore = ((CalendarAccessor) this).wildfires$getCalendarTicks();
    }

    @Inject(method = "onOverworldTick", at = @At("TAIL"))
    private void wildfires$applyCalendarRate(ServerLevel level, CallbackInfo callback) {
        CalendarAccessor calendar = (CalendarAccessor) this;
        long current = calendar.wildfires$getCalendarTicks();
        if (current - wildfires$calendarTicksBefore != 1L) {
            TfcCalendarEventAcceleration.onExternalCalendarJump(level.getServer(),
                    wildfires$calendarTicksBefore, current);
            return;
        }
        long desiredAdvance = TfcCalendarRateController.serverCalendarTicksForBaseAdvance(true);
        desiredAdvance = TfcCalendarEventAcceleration.limitAdvance(level,
                wildfires$calendarTicksBefore, desiredAdvance);
        long correction = desiredAdvance - 1L;
        if (correction != 0L) {
            calendar.wildfires$setCalendarTicks(current + correction);
            level.setDayTime(level.getDayTime() + correction);
        }
        TfcCalendarEventAcceleration.afterAdvance(level,
                wildfires$calendarTicksBefore + desiredAdvance);
    }
}

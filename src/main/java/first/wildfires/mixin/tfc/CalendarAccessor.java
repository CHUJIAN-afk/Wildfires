package first.wildfires.mixin.tfc;

import net.dries007.tfc.util.calendar.Calendar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minimal access to TFC 3.2.20's authoritative calendar tick field. */
@Mixin(value = Calendar.class, remap = false)
public interface CalendarAccessor {

    @Accessor("calendarTicks")
    long wildfires$getCalendarTicks();

    @Accessor("calendarTicks")
    void wildfires$setCalendarTicks(long calendarTicks);
}

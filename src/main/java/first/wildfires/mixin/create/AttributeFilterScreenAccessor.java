package first.wildfires.mixin.create;

import com.simibubi.create.content.logistics.filter.AttributeFilterScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AttributeFilterScreen.class, remap = false)
public interface AttributeFilterScreenAccessor {

    @Accessor("whitelistDis")
    IconButton wildfires$getWhitelistDis();

    @Accessor("whitelistCon")
    IconButton wildfires$getWhitelistCon();

    @Accessor("blacklist")
    IconButton wildfires$getBlacklist();
}

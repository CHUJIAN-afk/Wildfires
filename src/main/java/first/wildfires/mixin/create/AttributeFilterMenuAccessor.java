package first.wildfires.mixin.create;

import com.simibubi.create.content.logistics.filter.AttributeFilterMenu;
import com.simibubi.create.content.logistics.filter.AttributeFilterMenu.WhitelistMode;
import net.createmod.catnip.data.Pair;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = AttributeFilterMenu.class, remap = false)
public interface AttributeFilterMenuAccessor {

    @Accessor("whitelistMode")
    WhitelistMode wildfires$getWhitelistMode();

    @Mutable
    @Accessor("whitelistMode")
    void wildfires$setWhitelistMode(WhitelistMode mode);

    @Accessor("selectedAttributes")
    List<Pair<ItemAttribute, Boolean>> wildfires$getSelectedAttributes();
}

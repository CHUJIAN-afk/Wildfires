package first.wildfires.mixin.create;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AllGuiTextures.class, remap = false)
public interface AllGuiTexturesAccessor {

    @Mutable
    @Accessor("location")
    void wildfires$setLocation(ResourceLocation location);
}

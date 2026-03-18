package first.wildfires.compats.kubejs.spartan;

import com.oblivioussp.spartanweaponry.api.WeaponMaterial;
import com.oblivioussp.spartanweaponry.api.WeaponTraits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;

public class SpartanBindings {
    public WeaponMaterial createWeaponMaterial(String name, int durability, float speed, float baseDamage, int enchantability, int primaryColor, int secondaryColor, ResourceLocation itemTag, ResourceLocation traitTag) {
        var tag = ItemTags.create(itemTag);

        var trait = TagKey.create(WeaponTraits.REGISTRY_KEY, traitTag);

        return new WeaponMaterial(name, "kubejs", primaryColor, secondaryColor, durability, speed, baseDamage, enchantability, tag, trait);
    }
}

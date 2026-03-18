package first.wildfires.client.renderer.entity;

import first.wildfires.Wildfires;
import first.wildfires.item.GeckoSimpleArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class GeckoSimpleArmorItemRenderer extends GeoArmorRenderer<GeckoSimpleArmorItem> {

    private final ArmorItem.Type _type;

    public GeckoSimpleArmorItemRenderer(String path, ArmorItem.Type armorType) {
        super(
            new DefaultedItemGeoModel<GeckoSimpleArmorItem>(getResourceLocation(path, armorType))
            .withAltTexture(ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, String.format("armor/%s", path)))
        );

        _type =  armorType;
    }

    public static ResourceLocation getResourceLocation(String path, ArmorItem.Type armorType) {
        var fullPath = String.format("armor/%s", path) + (armorType == ArmorItem.Type.LEGGINGS ? "_leggings" : "");

        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, fullPath);
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
        if (_type == ArmorItem.Type.LEGGINGS)
            setAllVisible(true);
        else
            super.applyBoneVisibilityBySlot(currentSlot);
    }
}

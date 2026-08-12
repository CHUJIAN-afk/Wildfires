package first.wildfires.client.renderer.entity;

import first.wildfires.Wildfires;
import first.wildfires.item.GeckoSimpleArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class GeckoSimpleArmorItemRenderer extends GeoArmorRenderer<GeckoSimpleArmorItem> {

    private static final String SHARED_ARMOR_MODEL = "wrought_iron_guard_expanded";
    private static final String[] SHARED_ARMOR_BONES = {
            "armorBody", "armorRightArm", "armorLeftArm",
            "armorRightLeg", "armorLeftLeg", "armorRightBoot", "armorLeftBoot",
            "leggingsBody"
    };

    private final ArmorItem.Type _type;
    private final boolean _sharedArmorModel;

    public GeckoSimpleArmorItemRenderer(String modelPath, String texturePath, ArmorItem.Type armorType) {
        super(
            new DefaultedItemGeoModel<GeckoSimpleArmorItem>(getResourceLocation(modelPath, armorType))
            .withAltTexture(ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, String.format("armor/%s", texturePath)))
        );

        _type =  armorType;
        _sharedArmorModel = SHARED_ARMOR_MODEL.equals(modelPath);
    }

    public static ResourceLocation getResourceLocation(String path, ArmorItem.Type armorType) {
        var fullPath = String.format("armor/%s", path)
                + (armorType == ArmorItem.Type.LEGGINGS && !SHARED_ARMOR_MODEL.equals(path) ? "_leggings" : "");

        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, fullPath);
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
        if (_sharedArmorModel) {
            applySharedArmorVisibility();
            return;
        }

        if (_type == ArmorItem.Type.LEGGINGS)
            setAllVisible(true);
        else
            super.applyBoneVisibilityBySlot(currentSlot);
    }

    private void applySharedArmorVisibility() {
        setAllVisible(false);
        for (String boneName : SHARED_ARMOR_BONES)
            setSharedBoneVisible(boneName, false);

        switch (_type) {
            case CHESTPLATE -> {
                setSharedBoneVisible("armorBody", true);
                setSharedBoneVisible("armorRightArm", true);
                setSharedBoneVisible("armorLeftArm", true);
            }
            case LEGGINGS -> {
                setSharedBoneVisible("armorRightLeg", true);
                setSharedBoneVisible("armorLeftLeg", true);
                setSharedBoneVisible("leggingsBody", true);
            }
            case BOOTS -> {
                setSharedBoneVisible("armorRightBoot", true);
                setSharedBoneVisible("armorLeftBoot", true);
            }
            default -> {
            }
        }
    }

    private void setSharedBoneVisible(String boneName, boolean visible) {
        getGeoModel().getBone(boneName).ifPresent(bone -> bone.setHidden(!visible));
    }
}

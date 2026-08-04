package first.wildfires.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class HeatResistantArmorItem extends ArmorItem {
    private static final ResourceLocation HEAT_RESISTANCE = ResourceLocation.fromNamespaceAndPath("legendarysurvivaloverhaul", "heat_resistance");
    private final EquipmentSlot equipmentSlot;

    public HeatResistantArmorItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
        equipmentSlot = type.getSlot();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != equipmentSlot) {
            return super.getDefaultAttributeModifiers(slot);
        }

        Attribute heatResistance = ForgeRegistries.ATTRIBUTES.getValue(HEAT_RESISTANCE);
        if (heatResistance == null) {
            return super.getDefaultAttributeModifiers(slot);
        }

        return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .putAll(super.getDefaultAttributeModifiers(slot))
                .put(heatResistance, new AttributeModifier(heatResistanceUuid(), "Wildfires armor heat resistance", 8.0D, AttributeModifier.Operation.ADDITION))
                .build();
    }

    private UUID heatResistanceUuid() {
        String key = BuiltInRegistries.ITEM.getKey(this).toString();
        return UUID.nameUUIDFromBytes((key + ":heat_resistance").getBytes(StandardCharsets.UTF_8));
    }
}

package first.wildfires.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import first.wildfires.client.renderer.entity.GeckoSimpleArmorItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;
import java.util.function.Consumer;

public class GeckoSimpleArmorItem extends ArmorItem implements GeoItem {
    private static final ResourceLocation HEAT_RESISTANCE = ResourceLocation.fromNamespaceAndPath("legendarysurvivaloverhaul", "heat_resistance");
    private static final UUID HEAT_RESISTANCE_UUID = UUID.fromString("0c8d2f18-a0e6-427a-8b1e-e2df7e0cbe24");

    private AnimatableInstanceCache _cache;
    private final String modelPath;
    private final String texturePath;
    private final double heatResistance;
    private final EquipmentSlot equipmentSlot;

    public GeckoSimpleArmorItem(ArmorMaterial material, Type type, Properties properties) {
        this(material, type, properties, material.getName().split(":")[1], material.getName().split(":")[1], 0D);
    }

    public GeckoSimpleArmorItem(ArmorMaterial material, Type type, Properties properties, String modelPath) {
        this(material, type, properties, modelPath, material.getName().split(":")[1], 0D);
    }

    public GeckoSimpleArmorItem(ArmorMaterial material, Type type, Properties properties, String modelPath, String texturePath, double heatResistance) {
        super(material, type, properties);

        _cache = GeckoLibUtil.createInstanceCache(this);;
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.heatResistance = heatResistance;
        this.equipmentSlot = type.getSlot();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {

    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeckoSimpleArmorItemRenderer renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = new GeckoSimpleArmorItemRenderer(modelPath, texturePath, type);
                }

                renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);

                return renderer;
            }
        });
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return _cache;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != equipmentSlot || heatResistance == 0D) {
            return super.getDefaultAttributeModifiers(slot);
        }

        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(HEAT_RESISTANCE);
        if (attribute == null) {
            return super.getDefaultAttributeModifiers(slot);
        }

        return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .putAll(super.getDefaultAttributeModifiers(slot))
                .put(attribute, new AttributeModifier(HEAT_RESISTANCE_UUID, "Conical hat heat resistance", heatResistance, AttributeModifier.Operation.ADDITION))
                .build();
    }
}

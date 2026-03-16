package first.wildfires.compats.kubejs;

import com.oblivioussp.spartanweaponry.api.WeaponTraits;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import first.wildfires.compats.kubejs.event.TFCFluidEvents;
import first.wildfires.compats.kubejs.spartan.SpartanBindings;
import first.wildfires.compats.kubejs.spartan.builders.*;

public class WildfiresPlugin extends KubeJSPlugin  {

    public static final RegistryInfo<?> WEAPON_TRAIT = RegistryInfo.of(WeaponTraits.REGISTRY_KEY);

    @Override
    public void init()
    {
        RegistryInfo.ITEM.addType("heal_item", HealingItem.Builder.class, HealingItem.Builder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:quarterstaff", QuarterstaffBuilder.class, QuarterstaffBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:dagger", DaggerBuilder.class, DaggerBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:parrying_dagger", ParryingDaggerBuilder.class, ParryingDaggerBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:longsword", LongswordBuilder.class, LongswordBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:greatsword", GreatswordBuilder.class, GreatswordBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:saber", SaberBuilder.class, SaberBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:rapier", RapierBuilder.class, RapierBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:katana", KatanaBuilder.class, KatanaBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:battleaxe", BattleaxeBuilder.class, BattleaxeBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:battle_hammer", BattleHammerBuilder.class, BattleHammerBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:warhammer", WarhammerBuilder.class, WarhammerBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:javelin", JavelinBuilder.class, JavelinBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:spear", SpearBuilder.class, SpearBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:pike", PikeBuilder.class, PikeBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:lance", LanceBuilder.class, LanceBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:glaive", GlaiveBuilder.class, GlaiveBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:halberd", HalberdBuilder.class, HalberdBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:throwing_knife", ThrowingKnifeBuilder.class, ThrowingKnifeBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:tomahawk", TomahawkBuilder.class, TomahawkBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:scythe", ScytheBuilder.class, ScytheBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:longbow", LongbowBuilder.class, LongbowBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:boomerang", BoomerangBuilder.class, BoomerangBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:flanged_mace", FlangedMaceBuilder.class, FlangedMaceBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:heavy_crossbow", HeavyCrossbowBuilder.class, HeavyCrossbowBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:cestus", CestusBuilder.class, CestusBuilder::new);

        RegistryInfo.ITEM.addType("spartanweaponry:club", ClubBuilder.class, ClubBuilder::new);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("WildfiresAdvancement", new AdvancementBindings());

        event.add("Spartan", new SpartanBindings());
    }

    @Override
    public void registerEvents() {
        TFCFluidEvents.GROUP.register();
    }
}

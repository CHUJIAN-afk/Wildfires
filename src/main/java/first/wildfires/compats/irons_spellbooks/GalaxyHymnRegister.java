package first.wildfires.compats.irons_spellbooks;

import first.wildfires.Wildfires;
import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnCoreProjectile;
import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnFieldEntity;
import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnHomingStar;
import first.wildfires.compats.irons_spellbooks.spell.GalaxyHymnSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registrations owned by the Iron's Spells 'n Spellbooks integration. */
public final class GalaxyHymnRegister {

    private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(
            SpellRegistry.SPELL_REGISTRY_KEY, Wildfires.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            ForgeRegistries.ENTITY_TYPES, Wildfires.MODID);
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Wildfires.MODID);

    public static final RegistryObject<GalaxyHymnSpell> GALAXY_HYMN = SPELLS.register(
            "galaxy_hymn", GalaxyHymnSpell::new);

    public static final RegistryObject<EntityType<GalaxyHymnCoreProjectile>> GALAXY_HYMN_CORE =
            ENTITIES.register("galaxy_hymn_core", () -> EntityType.Builder
                    .<GalaxyHymnCoreProjectile>of(GalaxyHymnCoreProjectile::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(Wildfires.rl("galaxy_hymn_core").toString()));

    public static final RegistryObject<EntityType<GalaxyHymnFieldEntity>> GALAXY_HYMN_FIELD =
            ENTITIES.register("galaxy_hymn_field", () -> EntityType.Builder
                    .<GalaxyHymnFieldEntity>of(GalaxyHymnFieldEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(Wildfires.rl("galaxy_hymn_field").toString()));

    public static final RegistryObject<EntityType<GalaxyHymnHomingStar>> GALAXY_HYMN_HOMING_STAR =
            ENTITIES.register("galaxy_hymn_homing_star", () -> EntityType.Builder
                    .<GalaxyHymnHomingStar>of(GalaxyHymnHomingStar::new, MobCategory.MISC)
                    .sized(0.45F, 0.45F)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(Wildfires.rl("galaxy_hymn_homing_star").toString()));

    /** Exact Starlink-style sprite particle used by the authorized Sky Ripper visual adaptation. */
    public static final RegistryObject<SimpleParticleType> GALAXY_HYMN_STARLINK = PARTICLES.register(
            "galaxy_hymn_starlink", () -> new SimpleParticleType(false));

    /** Impact constellation held through the center afterimage, then faded. */
    public static final RegistryObject<SimpleParticleType> GALAXY_HYMN_IMPACT_STARLINK = PARTICLES.register(
            "galaxy_hymn_impact_starlink", () -> new SimpleParticleType(false));

    /** Unlinked blue motes used around hovering stars and on homing-star impact. */
    public static final RegistryObject<SimpleParticleType> GALAXY_HYMN_SPARK = PARTICLES.register(
            "galaxy_hymn_spark", () -> new SimpleParticleType(false));

    /** ParticleEngine-batched cross-star body for the real homing projectile entity. */
    public static final RegistryObject<SimpleParticleType> GALAXY_HYMN_GPU_STAR = PARTICLES.register(
            "galaxy_hymn_gpu_star", () -> new SimpleParticleType(false));

    private GalaxyHymnRegister() {
    }

    public static void register(IEventBus bus) {
        SPELLS.register(bus);
        ENTITIES.register(bus);
        PARTICLES.register(bus);
    }
}

package first.wildfires.compats.irons_spellbooks.spell;

import first.wildfires.Wildfires;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnRegister;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnVisualMath;
import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnCoreProjectile;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Legendary ender spell whose damage is deliberately fixed by its gameplay contract. */
@AutoSpellConfig
public final class GalaxyHymnSpell extends AbstractSpell {

    public static final float CORE_EXPLOSION_DAMAGE = 200.0F;
    public static final float HOMING_STAR_DAMAGE = 20.0F;
    /** Earliest global hit-sequence tick at which the impact volley may begin tracking. */
    public static final int CONSTELLATION_DELAY_TICKS = GalaxyHymnVisualMath.TRACKING_START_TICK;
    public static final float EXPLOSION_RADIUS = 6.0F;
    public static final float TARGET_RANGE = 18.0F;

    private static final ResourceLocation SPELL_ID = Wildfires.rl("galaxy_hymn");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(45)
            .build();

    public GalaxyHymnSpell() {
        baseManaCost = 300;
        manaCostPerLevel = 0;
        baseSpellPower = 1;
        spellPowerPerLevel = 0;
        castTime = 40;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.BLACK_HOLE_CHARGE.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundRegistry.ENDER_CAST.get());
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.FINISH_ANIMATION;
    }

    @Override
    public boolean stopSoundOnCancel() {
        return true;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.wildfires.galaxy_hymn.core_damage", (int) CORE_EXPLOSION_DAMAGE),
                Component.translatable("ui.wildfires.galaxy_hymn.tracking_damage", (int) HOMING_STAR_DAMAGE),
                Component.translatable("ui.wildfires.galaxy_hymn.delay", CONSTELLATION_DELAY_TICKS / 20.0F)
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource,
                       MagicData playerMagicData) {
        if (!level.isClientSide) {
            Vec3 look = caster.getLookAngle().normalize();
            GalaxyHymnCoreProjectile core = new GalaxyHymnCoreProjectile(
                    GalaxyHymnRegister.GALAXY_HYMN_CORE.get(), level, caster);
            Vec3 origin = caster.getEyePosition().add(look.scale(0.9D));
            core.setPos(origin.x, origin.y - core.getBbHeight() * 0.5D, origin.z);
            core.setLaunchOrigin(core.position());
            core.setSeed(caster.getRandom().nextInt());
            core.shoot(look);
            level.addFreshEntity(core);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}

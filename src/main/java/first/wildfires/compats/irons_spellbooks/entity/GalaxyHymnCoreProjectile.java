package first.wildfires.compats.irons_spellbooks.entity;

/*
 * Visual trail sampling adapted from ArcaneVortex 0.6.8 SkyRipperArrow under
 * the user's project-specific rendering authorization. Wildfires changes the
 * spell/entity contract and does not use ArcaneVortex attack-damage mechanics.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import first.wildfires.compats.irons_spellbooks.GalaxyHymnRegister;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnStarlinkSpawner;
import first.wildfires.compats.irons_spellbooks.spell.GalaxyHymnSpell;
import first.wildfires.network.GalaxyHymnImpactVisualPacket;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;

/** Server-authoritative core projectile emitting the authorized Sky Ripper Starlink trail. */
public final class GalaxyHymnCoreProjectile extends Projectile {

    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(
            GalaxyHymnCoreProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LAUNCH_X = SynchedEntityData.defineId(
            GalaxyHymnCoreProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LAUNCH_Y = SynchedEntityData.defineId(
            GalaxyHymnCoreProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LAUNCH_Z = SynchedEntityData.defineId(
            GalaxyHymnCoreProjectile.class, EntityDataSerializers.FLOAT);

    private static final float SPEED = 1.65F;
    private static final int MAX_LIFETIME = 80;
    private boolean detonated;

    public GalaxyHymnCoreProjectile(EntityType<? extends GalaxyHymnCoreProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public GalaxyHymnCoreProjectile(EntityType<? extends GalaxyHymnCoreProjectile> type, Level level,
                                    LivingEntity owner) {
        this(type, level);
        setOwner(owner);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SEED, 0);
        entityData.define(LAUNCH_X, 0.0F);
        entityData.define(LAUNCH_Y, 0.0F);
        entityData.define(LAUNCH_Z, 0.0F);
    }

    public void shoot(Vec3 direction) {
        setDeltaMovement(direction.normalize().scale(SPEED));
    }

    public void setSeed(int seed) {
        entityData.set(SEED, seed);
    }

    public int getSeed() {
        return entityData.get(SEED);
    }

    public void setLaunchOrigin(Vec3 origin) {
        entityData.set(LAUNCH_X, (float) origin.x);
        entityData.set(LAUNCH_Y, (float) origin.y);
        entityData.set(LAUNCH_Z, (float) origin.z);
    }

    public Vec3 getLaunchOrigin() {
        return new Vec3(entityData.get(LAUNCH_X), entityData.get(LAUNCH_Y), entityData.get(LAUNCH_Z));
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        return target instanceof LivingEntity && target != owner
                && (owner == null || !DamageSources.isFriendlyFireBetween(owner, target))
                && super.canHitEntity(target);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 currentPosition = position();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
                setPos(hit.getLocation());
                detonate();
                return;
            }
            if (tickCount >= MAX_LIFETIME) {
                detonate();
                return;
            }
        }
        Vec3 nextPosition = currentPosition.add(getDeltaMovement());
        setPos(nextPosition);
        if (level().isClientSide) {
            GalaxyHymnStarlinkSpawner.spawnTrailParticles(level(), currentPosition, nextPosition);
        }
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
    }

    private void detonate() {
        if (detonated || level().isClientSide) {
            return;
        }
        detonated = true;
        Entity owner = getOwner();
        AABB damageArea = getBoundingBox().inflate(GalaxyHymnSpell.EXPLOSION_RADIUS);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, damageArea, LivingEntity::isAlive)) {
            if (distanceToSqr(target) > GalaxyHymnSpell.EXPLOSION_RADIUS * GalaxyHymnSpell.EXPLOSION_RADIUS
                    || target == owner || (owner != null && DamageSources.isFriendlyFireBetween(owner, target))) {
                continue;
            }
            DamageSources.applyDamage(target, GalaxyHymnSpell.CORE_EXPLOSION_DAMAGE,
                    GalaxyHymnRegister.GALAXY_HYMN.get().getDamageSource(this, owner == null ? this : owner));
        }

        GalaxyHymnFieldEntity field = new GalaxyHymnFieldEntity(
                GalaxyHymnRegister.GALAXY_HYMN_FIELD.get(), level());
        field.setPos(position());
        field.setSeed(getSeed());
        field.setMode(GalaxyHymnFieldEntity.Mode.DETONATION);
        field.setStartGameTime(level().getGameTime());
        field.setOwner(owner);
        level().addFreshEntity(field);
        field.releaseImpactVolleys();
        sendImpactVisual((ServerLevel) level());
        GalaxyHymnStarlinkSpawner.sendImpactShell((ServerLevel) level(), position(), getSeed());
        discard();
    }

    private void sendImpactVisual(ServerLevel serverLevel) {
        Vec3 center = position();
        double radius = GalaxyHymnImpactVisualPacket.SHAKE_RADIUS;
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            double distance = player.position().distanceTo(center);
            if (distance > radius) {
                continue;
            }
            float intensity = GalaxyHymnImpactVisualPacket.BASE_SHAKE_INTENSITY
                    * (1.0F - (float) (distance / radius));
            if (intensity > 0.1F) {
                new GalaxyHymnImpactVisualPacket(center, intensity, getSeed(), true).sendTo(player);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Seed", getSeed());
        Vec3 origin = getLaunchOrigin();
        tag.putDouble("LaunchX", origin.x);
        tag.putDouble("LaunchY", origin.y);
        tag.putDouble("LaunchZ", origin.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSeed(tag.getInt("Seed"));
        setLaunchOrigin(new Vec3(tag.getDouble("LaunchX"), tag.getDouble("LaunchY"), tag.getDouble("LaunchZ")));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

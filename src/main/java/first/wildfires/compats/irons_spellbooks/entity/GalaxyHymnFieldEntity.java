package first.wildfires.compats.irons_spellbooks.entity;

/*
 * The visual density and star-link lifetime are adapted from ArcaneVortex 0.6.8
 * SkyRipper's block-impact Starlink particles under the user's project-specific
 * visual authorization. Gameplay, state and damage remain Wildfires-owned.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import first.wildfires.compats.irons_spellbooks.GalaxyHymnRegister;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnStarlinkSpawner;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnVisualMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/** Timed impact controller retaining the center-star afterimage and constellation lifetime. */
public final class GalaxyHymnFieldEntity extends Entity {

    public enum Mode {
        DETONATION,
        CONSTELLATION,
        SPARK_BURST
    }

    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(
            GalaxyHymnFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(
            GalaxyHymnFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START_GAME_TIME = SynchedEntityData.defineId(
            GalaxyHymnFieldEntity.class, EntityDataSerializers.LONG);
    public static final int DETONATION_TICKS = 12;
    /** Keeps Galaxy Hymn's linked constellations and residual stars visible after the Starlink burst. */
    private static final int CONSTELLATION_LIFETIME = 150;
    private static final int SPARK_LIFETIME = 24;
    public static final int HOMING_STAR_COUNT = GalaxyHymnVisualMath.HOMING_STAR_COUNT;
    public static final double CENTER_STAR_HEIGHT = 6.0D;

    @Nullable
    private UUID ownerUuid;
    private int releasedVolleyMask;

    public GalaxyHymnFieldEntity(EntityType<? extends GalaxyHymnFieldEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SEED, 0);
        entityData.define(MODE, Mode.DETONATION.ordinal());
        entityData.define(START_GAME_TIME, -1L);
    }

    public int getSeed() {
        return entityData.get(SEED);
    }

    public void setSeed(int seed) {
        entityData.set(SEED, seed);
    }

    public Mode getMode() {
        int value = entityData.get(MODE);
        return Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, value))];
    }

    public void setMode(Mode mode) {
        entityData.set(MODE, mode.ordinal());
    }

    public void setStartGameTime(long gameTime) {
        entityData.set(START_GAME_TIME, gameTime);
    }

    public long getStartGameTime() {
        return entityData.get(START_GAME_TIME);
    }

    public float getSequenceAge(float partialTick) {
        long start = getStartGameTime();
        return start >= 0L
                ? Math.max(0.0F, (float) (level().getGameTime() - start) + partialTick)
                : tickCount + partialTick;
    }

    public void setOwner(@Nullable Entity owner) {
        ownerUuid = owner == null ? null : owner.getUUID();
    }

    /** Called by the core impact so the unified sixty-four-star volley exists in the hit frame. */
    public void releaseImpactVolleys() {
        if (!level().isClientSide) {
            for (int volley = 0; volley < GalaxyHymnVisualMath.VOLLEY_COUNT; volley++) {
                releaseHomingStarVolley(volley);
            }
        }
    }

    @Nullable
    private Entity resolveOwner() {
        return ownerUuid != null && level() instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(ownerUuid) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getStartGameTime() < 0L) {
            setStartGameTime(level().getGameTime());
        }
        long sequenceAge = getStartGameTime() >= 0L
                ? Math.max(0L, level().getGameTime() - getStartGameTime()) : tickCount;
        Mode mode = getMode();
        if (!level().isClientSide && mode == Mode.DETONATION && sequenceAge >= DETONATION_TICKS) {
            setMode(Mode.CONSTELLATION);
            mode = Mode.CONSTELLATION;
        }
        if (mode == Mode.DETONATION || mode == Mode.CONSTELLATION) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide) {
                for (int volley = 0; volley < GalaxyHymnVisualMath.VOLLEY_COUNT; volley++) {
                    if (sequenceAge >= GalaxyHymnVisualMath.volleyAge(volley)
                            && (releasedVolleyMask & 1 << volley) == 0) {
                        releaseHomingStarVolley(volley);
                    }
                }
            }
        }
        int lifetime = mode == Mode.SPARK_BURST ? SPARK_LIFETIME : CONSTELLATION_LIFETIME;
        if (!level().isClientSide && sequenceAge >= lifetime) {
            discard();
        }
    }

    private void releaseHomingStarVolley(int volley) {
        if (level().isClientSide || (releasedVolleyMask & 1 << volley) != 0) {
            return;
        }
        releasedVolleyMask |= 1 << volley;
        Entity owner = resolveOwner();
        Vec3 burstCenter = position().add(0.0D, CENTER_STAR_HEIGHT, 0.0D);
        GalaxyHymnStarlinkSpawner.sendVolleyConstellation((ServerLevel) level(), burstCenter,
                getSeed(), volley, GalaxyHymnVisualMath.volleySize(volley));
        for (int index = 0; index < HOMING_STAR_COUNT; index++) {
            if (GalaxyHymnVisualMath.volleyIndex(index) != volley) {
                continue;
            }
            Vec3 offset = GalaxyHymnVisualMath.homingReleaseOffset(getSeed(), index);
            GalaxyHymnHomingStar star = new GalaxyHymnHomingStar(
                    GalaxyHymnRegister.GALAXY_HYMN_HOMING_STAR.get(), level());
            star.setOwner(owner);
            star.setSeed(getSeed() + index * 0x45d9f3b);
            star.setBurst(burstCenter, offset, index);
            star.setPos(burstCenter);
            star.setDeltaMovement(Vec3.ZERO);
            level().addFreshEntity(star);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Seed", getSeed());
        tag.putInt("Mode", getMode().ordinal());
        tag.putLong("StartGameTime", getStartGameTime());
        tag.putInt("ReleasedVolleyMask", releasedVolleyMask);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setSeed(tag.getInt("Seed"));
        setMode(Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, tag.getInt("Mode")))]);
        setStartGameTime(tag.contains("StartGameTime") ? tag.getLong("StartGameTime") : -1L);
        releasedVolleyMask = tag.contains("ReleasedVolleyMask") ? tag.getInt("ReleasedVolleyMask")
                : (tag.getBoolean("Released") ? (1 << GalaxyHymnVisualMath.VOLLEY_COUNT) - 1 : 0);
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

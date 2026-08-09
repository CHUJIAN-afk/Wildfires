package first.wildfires.celestial;

import com.mojang.authlib.GameProfile;
import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import net.dries007.tfc.ForgeEventHandler;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real ServerLevel acceptance tests for the authoritative celestial API and blood-moon integrations. */
@GameTestHolder("wildfires")
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = "wildfires", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CelestialServerGameTests {

    private static final ResourceLocation EMPTY_TEMPLATE =
            ResourceLocation.fromNamespaceAndPath("wildfires", "celestial_empty");

    private CelestialServerGameTests() {
    }

    /** Registers an in-memory template before the game-test server creates its test structures. */
    @SubscribeEvent
    public static void prepareTemplate(ServerStartingEvent event) {
        if (!ForgeGameTestHooks.isGametestServer()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        level.getStructureManager().getOrCreate(EMPTY_TEMPLATE).fillFromWorld(level,
                new BlockPos(0, 200, 0), new Vec3i(3, 3, 3), false, Blocks.STRUCTURE_VOID);
    }

    @GameTest(template = "celestial_empty", timeoutTicks = 200)
    public static void authoritativeServerBehavior(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        long originalCalendarTicks = Calendars.SERVER.getCalendarTicks();
        try {
            assertTrue(level.getServer().getCommands().getDispatcher().getRoot()
                            .getChild("wildfires").getChild("tfctime").getChild("clear") != null,
                    "Wildfires TFC calendar clear command was not registered");
            var untilBranch = level.getServer().getCommands().getDispatcher().getRoot()
                    .getChild("wildfires").getChild("tfctime").getChild("until");
            for (CelestialEventType event : CelestialEventType.values()) {
                assertTrue(untilBranch.getChild(event.commandName()) != null
                                && untilBranch.getChild(event.commandName()).getChild("speed") != null,
                        "Wildfires event acceleration command branch was not registered: "
                                + event.commandName());
            }
            assertTrue(level.getServer().getCommands().getDispatcher().getRoot()
                            .getChild("time").getChild("set").getChild("dayLength") != null,
                    "TFC 1.21 dayLength command branch was not registered");
            level.setBlockAndUpdate(position.below(), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(position.east(), Blocks.GLOWSTONE.defaultBlockState());

            verifyApiAndClimateIsolation(level, position);

            Calendars.SERVER.setTimeFromCalendarTime(0L);
            assertTrue(CelestialGameplay.visibleBloodMoon(level, position) == 0.0D,
                    "calendar time zero unexpectedly began as a visible blood moon");
            assertTrue(!Monster.isDarkEnoughToSpawn(level, position, RandomSource.create(1L)),
                    "bright control position passed the vanilla darkness check without a blood moon");
            MobSpawnEvent.FinalizeSpawn controlSpawn = zombieSpawnEvent(level, position);
            ForgeEventHandler.onLivingSpawnCheck(controlSpawn);
            assertTrue(controlSpawn.isCanceled() && controlSpawn.isSpawnCancelled(),
                    "TFC surface control spawn was not denied outside a blood moon");

            long bloodMoonTicks = findVisibleBloodMoonTicks(level, position);
            Calendars.SERVER.setTimeFromCalendarTime(bloodMoonTicks);
            CelestialState bloodState = CelestialApi.state(level, position.getCenter(), 0.0F).orElseThrow();
            double intensity = CelestialGameplay.visibleBloodMoon(level, position);
            assertTrue(intensity > CelestialGameplayRules.ACTIVE_THRESHOLD
                            && bloodState.moon().altitudeRadians() > 0.0D,
                    "real provider did not expose the searched visible blood moon");
            assertTrue(Monster.isDarkEnoughToSpawn(level, position, RandomSource.create(1L)),
                    "Monster darkness Mixin did not accept the visible blood moon");

            MobSpawnEvent.FinalizeSpawn bloodSpawn = zombieSpawnEvent(level, position);
            ForgeEventHandler.onLivingSpawnCheck(bloodSpawn);
            assertTrue(!bloodSpawn.isCanceled() && !bloodSpawn.isSpawnCancelled(),
                    "TFC surface expression Mixin did not preserve the tagged blood-moon spawn");

            ServerPlayer player = makePacketlessServerPlayer(level);
            player.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
            verifyLocalMobCap(level, position, player, intensity);
            verifyExposedUnluck(level, player, intensity);
            verifyOppositePoleIsInactive(level, position);

            player.removeEffectNoUpdate(MobEffects.UNLUCK);
            setRoof(level, position, Blocks.STONE);
            long sunriseTicks = findNextSunriseTicks(level, position, bloodMoonTicks);
            long eventStartTicks = sunriseTicks - 10L;
            Calendars.SERVER.setTimeFromCalendarTime(eventStartTicks);
            int commandResult = level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withLevel(level)
                            .withPosition(position.getCenter()).withPermission(4),
                    "wildfires tfctime until sunrise 20");
            assertTrue(commandResult > 0 && TfcCalendarEventAcceleration.status().isPresent()
                            && TfcCalendarRateController.serverMultiplier() == 20.0D,
                    "natural-event acceleration command did not start at 20x");
            helper.runAfterDelay(2, () -> {
                try {
                    long reachedTicks = Calendars.SERVER.getCalendarTicks();
                    assertTrue(reachedTicks >= sunriseTicks && reachedTicks <= sunriseTicks + 2L,
                            "event acceleration did not stop at the first natural sunrise tick: "
                                    + reachedTicks + " vs " + sunriseTicks);
                    assertTrue(TfcCalendarRateController.serverMultiplier() == 1.0D
                                    && TfcCalendarEventAcceleration.status().isEmpty(),
                            "event acceleration did not automatically restore 1x");

                    Calendars.SERVER.setTimeFromCalendarTime(bloodMoonTicks);
                    long rateStartCalendar = Calendars.SERVER.getCalendarTicks();
                    long rateStartPlayer = Calendars.SERVER.getTicks();
                    long rateStartDayTime = level.getDayTime();
                    int rateStartServerTick = level.getServer().getTickCount();
                    TfcCalendarRateController.setServerMultiplier(20.0D);
                    helper.runAfterDelay(10, () -> {
                        try {
                            long calendarDelta = Calendars.SERVER.getCalendarTicks() - rateStartCalendar;
                            long playerDelta = Calendars.SERVER.getTicks() - rateStartPlayer;
                            long dayTimeDelta = level.getDayTime() - rateStartDayTime;
                            long serverTickDelta = level.getServer().getTickCount() - rateStartServerTick;
                            assertTrue(playerDelta == serverTickDelta,
                                    "TFC player ticks diverged from the unchanged server tick count");
                            assertTrue(calendarDelta == playerDelta * 20L,
                                    "20x rate did not advance the authoritative TFC calendar exactly");
                            assertTrue(dayTimeDelta == calendarDelta,
                                    "TFC calendar and its normal world dayTime mirror diverged under acceleration");
                            assertTrue(!level.canSeeSky(player.blockPosition()),
                                    "covered player still had maximum sky light after roof propagation");
                            BloodMoonEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                            assertTrue(player.getEffect(MobEffects.UNLUCK) == null,
                                    "covered player received blood-moon Unluck without sky exposure");
                            helper.succeed();
                        } finally {
                            TfcCalendarEventAcceleration.resetSession();
                            TfcCalendarRateController.resetServer();
                            setRoof(level, position, Blocks.AIR);
                            player.removeEffectNoUpdate(MobEffects.UNLUCK);
                            Calendars.SERVER.setTimeFromCalendarTime(originalCalendarTicks);
                        }
                    });
                } catch (RuntimeException | Error exception) {
                    TfcCalendarEventAcceleration.resetSession();
                    TfcCalendarRateController.resetServer();
                    Calendars.SERVER.setTimeFromCalendarTime(originalCalendarTicks);
                    throw exception;
                }
            });
        } catch (RuntimeException | Error exception) {
            TfcCalendarEventAcceleration.resetSession();
            TfcCalendarRateController.resetServer();
            Calendars.SERVER.setTimeFromCalendarTime(originalCalendarTicks);
            throw exception;
        }
    }

    private static void verifyApiAndClimateIsolation(ServerLevel level, BlockPos position) {
        float temperature = Climate.getTemperature(level, position);
        float rainfall = Climate.getRainfall(level, position);
        int skyLight = level.getBrightness(LightLayer.SKY, position);
        int blockLight = level.getBrightness(LightLayer.BLOCK, position);
        long dayTime = level.getDayTime();
        long calendarTicks = Calendars.SERVER.getCalendarTicks();
        float tfeHemisphereScale = authoritativeTfeHemisphereScale(level);

        assertTrue(CelestialApi.state(level, position.getCenter(), 0.0F).isPresent(),
                "overworld celestial provider was not registered");
        assertTrue(CelestialApi.daylight(level, position).isPresent(),
                "overworld daylight API returned empty");
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        assertTrue(nether != null && CelestialApi.state(nether, position.getCenter(), 0.0F).isEmpty(),
                "an unregistered dimension inherited the overworld celestial model");

        assertTrue(Float.floatToIntBits(temperature) == Float.floatToIntBits(Climate.getTemperature(level, position))
                        && Float.floatToIntBits(rainfall) == Float.floatToIntBits(Climate.getRainfall(level, position)),
                "celestial API query changed TFE/TFC temperature or rainfall");
        assertTrue(Float.floatToIntBits(tfeHemisphereScale)
                        == Float.floatToIntBits(TfeHemisphereScale.get(level)),
                "overworld provider did not use TerraFirmaEarth's authoritative hemisphere scale");
        assertTrue(skyLight == level.getBrightness(LightLayer.SKY, position)
                        && blockLight == level.getBrightness(LightLayer.BLOCK, position)
                        && dayTime == level.getDayTime() && calendarTicks == Calendars.SERVER.getCalendarTicks(),
                "celestial API query changed actual light or global time");
    }

    private static float authoritativeTfeHemisphereScale(ServerLevel level) {
        try {
            Class<?> helper = Class.forName("com.newterraearth.tfe.client.NTEClimateRenderHelpers");
            Object value = helper.getMethod("getHemisphereScale", net.minecraft.world.level.Level.class)
                    .invoke(null, level);
            if (value instanceof Float scale && Float.isFinite(scale) && scale != 0.0F) {
                return scale;
            }
            throw new AssertionError("TerraFirmaEarth returned an invalid hemisphere scale: " + value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("TerraFirmaEarth 1.1.3 hemisphere helper was unavailable", exception);
        }
    }

    private static void verifyLocalMobCap(ServerLevel level, BlockPos position,
                                          ServerPlayer player, double intensity) {
        ChunkPos chunk = new ChunkPos(position);
        int vanillaLimit = MobCategory.MONSTER.getMaxInstancesPerChunk();
        LocalMobCapCalculator vanilla = new LocalMobCapCalculator(level.getChunkSource().chunkMap);
        cacheNearbyPlayer(vanilla, chunk, player);
        for (int i = 0; i < vanillaLimit; i++) {
            vanilla.addMob(chunk, MobCategory.MONSTER);
        }
        assertTrue(vanilla.canSpawn(MobCategory.MONSTER, chunk),
                "visible blood moon did not extend the exhausted vanilla local cap");

        int bloodLimit = CelestialGameplayRules.localMobCapLimit(vanillaLimit, intensity,
                CelestialConfig.serverSettings().bloodMoonSpawnMultiplier());
        LocalMobCapCalculator finite = new LocalMobCapCalculator(level.getChunkSource().chunkMap);
        cacheNearbyPlayer(finite, chunk, player);
        for (int i = 0; i < bloodLimit; i++) {
            finite.addMob(chunk, MobCategory.MONSTER);
        }
        assertTrue(!finite.canSpawn(MobCategory.MONSTER, chunk),
                "blood-moon local cap was not finite at its configured limit");
    }

    private static ServerPlayer makePacketlessServerPlayer(ServerLevel level) {
        return new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "wildfires-gametest-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            protected void onEffectAdded(MobEffectInstance effect, Entity source) {
            }

            @Override
            protected void onEffectUpdated(MobEffectInstance effect, boolean forced, Entity source) {
            }

            @Override
            protected void onEffectRemoved(MobEffectInstance effect) {
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static void cacheNearbyPlayer(LocalMobCapCalculator calculator, ChunkPos chunk, ServerPlayer player) {
        try {
            Field field = LocalMobCapCalculator.class.getDeclaredField("playersNearChunk");
            field.setAccessible(true);
            Long2ObjectMap<List<ServerPlayer>> players =
                    (Long2ObjectMap<List<ServerPlayer>>) field.get(calculator);
            players.put(chunk.toLong(), List.of(player));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("unable to seed the real LocalMobCapCalculator player cache", exception);
        }
    }

    private static void verifyExposedUnluck(ServerLevel level, ServerPlayer player, double intensity) {
        player.tickCount = 100;
        player.removeEffectNoUpdate(MobEffects.UNLUCK);
        BloodMoonEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        MobEffectInstance effect = player.getEffect(MobEffects.UNLUCK);
        assertTrue(effect != null && effect.getDuration() == 600
                        && effect.getAmplifier() == CelestialGameplayRules.unluckAmplifier(intensity),
                "exposed player did not receive the authoritative 600-tick Unluck effect");
    }

    private static void setRoof(ServerLevel level, BlockPos position, net.minecraft.world.level.block.Block block) {
        BlockPos roofCenter = position.above(2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(roofCenter.offset(x, 0, z), block.defaultBlockState());
            }
        }
    }

    private static void verifyOppositePoleIsInactive(ServerLevel level, BlockPos reference) {
        double scale = TfeHemisphereScale.get(level);
        BlockPos north = new BlockPos(reference.getX(), reference.getY(), (int) Math.round(-0.5D * scale));
        BlockPos south = new BlockPos(reference.getX(), reference.getY(), (int) Math.round(1.5D * scale));
        CelestialState northState = CelestialApi.state(level, north.getCenter(), 0.0F).orElseThrow();
        CelestialState southState = CelestialApi.state(level, south.getCenter(), 0.0F).orElseThrow();
        BlockPos hidden = northState.moon().altitudeRadians() <= southState.moon().altitudeRadians() ? north : south;
        CelestialState hiddenState = hidden == north ? northState : southState;
        assertTrue(hiddenState.moon().altitudeRadians() < 0.0D
                        && CelestialGameplay.visibleBloodMoon(level, hidden) == 0.0D,
                "blood-moon gameplay remained active below the local horizon");
    }

    private static MobSpawnEvent.FinalizeSpawn zombieSpawnEvent(ServerLevel level, BlockPos position) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new AssertionError("unable to create zombie for TFC spawn test");
        }
        zombie.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        return new MobSpawnEvent.FinalizeSpawn(zombie, level, zombie.getX(), zombie.getY(), zombie.getZ(),
                level.getCurrentDifficultyAt(position), MobSpawnType.NATURAL, null, null, null);
    }

    private static long findVisibleBloodMoonTicks(ServerLevel level, BlockPos position) {
        int daysInMonth = Calendars.SERVER.getCalendarDaysInMonth();
        CelestialRuntimeSettings settings = CelestialConfig.serverSettings();
        double maximumDays = CelestialMath.daysInYear(daysInMonth) * settings.nodalYears();
        double scale = TfeHemisphereScale.get(level);
        for (double day = 0.0D; day < maximumDays; day += 0.005D) {
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(position.getZ(), scale,
                    day * CelestialMath.TICKS_IN_DAY, daysInMonth,
                    settings.resolvedSynodicDays(daysInMonth), settings.resolvedAnomalisticDays(daysInMonth),
                    settings.nodalYears(), settings.lunarInclinationRadians()));
            if (result.bloodMoon() > 0.2D && result.moonElevation() > 0.1D) {
                return Math.round(day * CelestialMath.TICKS_IN_DAY);
            }
        }
        throw new AssertionError("no visible blood moon found across one configured nodal cycle");
    }

    private static long findNextSunriseTicks(ServerLevel level, BlockPos position, long startTick) {
        int daysInMonth = Calendars.SERVER.getCalendarDaysInMonth();
        CelestialRuntimeSettings settings = CelestialConfig.serverSettings();
        double scale = TfeHemisphereScale.get(level);
        boolean previous = solarElevationAt(position, scale, startTick, daysInMonth, settings) > 0.0D;
        for (long offset = 1L; offset <= (long) CelestialMath.TICKS_IN_DAY
                * Math.max(2, daysInMonth * 12); offset++) {
            long tick = startTick + offset;
            boolean current = solarElevationAt(position, scale, tick, daysInMonth, settings) > 0.0D;
            if (!previous && current) {
                return tick;
            }
            previous = current;
        }
        throw new AssertionError("no natural sunrise found across one configured year");
    }

    private static double solarElevationAt(BlockPos position, double scale, long calendarTick,
                                            int daysInMonth, CelestialRuntimeSettings settings) {
        return CelestialMath.calculate(new CelestialMath.Input(position.getZ(), scale, calendarTick,
                daysInMonth, settings.resolvedSynodicDays(daysInMonth),
                settings.resolvedAnomalisticDays(daysInMonth), settings.nodalYears(),
                settings.lunarInclinationRadians())).solarElevation();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

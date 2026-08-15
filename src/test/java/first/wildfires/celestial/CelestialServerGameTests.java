package first.wildfires.celestial;

import com.mojang.authlib.GameProfile;
import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import first.wildfires.space.celestial.CelestialSurfaceBindingResolver;
import first.wildfires.space.celestial.ExistingCelestialEphemeris;
import first.wildfires.space.celestial.ObservationContextResolver;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.route.StationRouteRuntime;
import first.wildfires.space.route.StationTravelRequest;
import first.wildfires.space.route.StationTravelResult;
import first.wildfires.space.route.StationTravelService;
import first.wildfires.space.route.StationTravelMode;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.content.StationDriveIndex;
import first.wildfires.space.content.StationJumpDriveIndex;
import first.wildfires.space.content.StationCoreBlockEntity;
import first.wildfires.space.content.StationCoreService;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import first.wildfires.space.capsule.ReturnCapsuleFuelTank;
import first.wildfires.space.content.StationIdTapeItem;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationService;
import first.wildfires.space.station.StationStatus;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import net.dries007.tfc.ForgeEventHandler;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        prepareTemplate(event.getServer().overworld());
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
            var skipToBranch = level.getServer().getCommands().getDispatcher().getRoot()
                    .getChild("wildfires").getChild("tfctime").getChild("skipto");
            for (CelestialEventType event : CelestialEventType.values()) {
                assertTrue(untilBranch.getChild(event.commandName()) != null
                                && untilBranch.getChild(event.commandName()).getChild("speed") != null,
                        "Wildfires event acceleration command branch was not registered: "
                                + event.commandName());
                assertTrue(skipToBranch.getChild(event.commandName()) != null,
                        "Wildfires direct event jump command branch was not registered: "
                                + event.commandName());
            }
            assertTrue(level.getServer().getCommands().getDispatcher().getRoot()
                            .getChild("time").getChild("set").getChild("dayLength") != null,
                    "TFC 1.21 dayLength command branch was not registered");
            level.setBlockAndUpdate(position.below(), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(position.east(), Blocks.GLOWSTONE.defaultBlockState());

            verifyExistingEphemerisAdapter(level, position);
            verifyApiAndClimateIsolation(level, position);

            long controlTicks = findInactiveBloodMoonTicks(level, position,
                    Math.max(0L, originalCalendarTicks));
            Calendars.SERVER.setTimeFromCalendarTime(controlTicks);
            assertTrue(CelestialGameplay.visibleBloodMoon(level, position) == 0.0D,
                    "forward control time unexpectedly began as a visible blood moon");
            assertTrue(!Monster.isDarkEnoughToSpawn(level, position, RandomSource.create(1L)),
                    "bright control position passed the vanilla darkness check without a blood moon");
            MobSpawnEvent.FinalizeSpawn controlSpawn = zombieSpawnEvent(level, position);
            ForgeEventHandler.onLivingSpawnCheck(controlSpawn);
            assertTrue(controlSpawn.isCanceled() && controlSpawn.isSpawnCancelled(),
                    "TFC surface control spawn was not denied outside a blood moon");

            long penumbralTicks = findVisiblePenumbralLunarEclipseTicks(level, position, controlTicks);
            Calendars.SERVER.setTimeFromCalendarTime(penumbralTicks);
            CelestialState penumbralState = CelestialApi.state(level, position.getCenter(), 0.0F).orElseThrow();
            var penumbralEvents = CelestialApi.events(level, position).orElseThrow();
            assertTrue(penumbralState.lunarEclipseRegion().penumbralOnly()
                            && penumbralState.lunarEclipse() == 0.0D
                            && penumbralEvents.lunarEclipseCoverage() == 0.0D
                            && penumbralEvents.lunarPenumbraCoverage() > 0.0D
                            && penumbralEvents.lunarEclipseVisible()
                            && !penumbralEvents.bloodMoonVisible(),
                    "public event API did not expose the visible penumbral-only lunar eclipse");
            var penumbralCurrent = EclipsePredictionService.currentEvents(level,
                    position.getCenter());
            assertTrue(penumbralCurrent.events().stream().anyMatch(event -> event.type()
                            == CelestialEventType.LUNAR_ECLIPSE
                            && event.endCalendarTicks() > penumbralTicks)
                            && penumbralCurrent.events().stream().noneMatch(event -> event.type()
                            == CelestialEventType.BLOOD_MOON),
                    "planetarium current-event end prediction diverged from the public API "
                            + "penumbral lunar-eclipse state");

            long bloodMoonTicks = findVisibleBloodMoonTicks(level, position, penumbralTicks);
            Calendars.SERVER.setTimeFromCalendarTime(bloodMoonTicks);
            CelestialState bloodState = CelestialApi.state(level, position.getCenter(), 0.0F).orElseThrow();
            double intensity = CelestialGameplay.visibleBloodMoon(level, position);
            assertTrue(intensity > CelestialGameplayRules.ACTIVE_THRESHOLD
                            && bloodState.moon().altitudeRadians() > 0.0D
                            && bloodState.lunarEclipse() == bloodState.lunarEclipseRegion().umbraCoverage()
                            && bloodState.lunarEclipseRegion().penumbraCoverage()
                            >= bloodState.lunarEclipseRegion().umbraCoverage(),
                    "real provider did not expose the searched visible blood moon");
            var bloodEvents = CelestialApi.events(level, position).orElseThrow();
            var bloodCurrent = EclipsePredictionService.currentEvents(level,
                    position.getCenter());
            assertTrue(bloodEvents.bloodMoonVisible()
                            && bloodCurrent.events().stream().anyMatch(event -> event.type()
                            == CelestialEventType.BLOOD_MOON
                            && event.endCalendarTicks() > bloodMoonTicks)
                            && bloodCurrent.events().stream().noneMatch(event -> event.type()
                            == CelestialEventType.LUNAR_ECLIPSE),
                    "planetarium blood-moon row did not preserve public API authority or replace "
                            + "the synonymous lunar-eclipse row");
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
            long jumpStartPlayerTicks = Calendars.SERVER.getTicks();
            long jumpStartDayTime = level.getDayTime();
            int jumpResult = level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withLevel(level)
                            .withPosition(position.getCenter()).withPermission(4),
                    "wildfires tfctime skipto sunrise");
            long jumpedCalendarTicks = Calendars.SERVER.getCalendarTicks();
            long skippedTicks = jumpedCalendarTicks - eventStartTicks;
            assertTrue(jumpResult > 0 && jumpedCalendarTicks == sunriseTicks,
                    "direct event jump did not land on the first sunrise tick: "
                            + jumpedCalendarTicks + " vs " + sunriseTicks);
            assertTrue(skippedTicks == 10L
                            && Calendars.SERVER.getTicks() - jumpStartPlayerTicks == skippedTicks
                            && level.getDayTime() - jumpStartDayTime == skippedTicks,
                    "direct event jump did not advance TFC playerTicks/dayTime by the reported delta");
            assertTrue(TfcCalendarRateController.serverMultiplier() == 1.0D
                            && TfcCalendarEventAcceleration.status().isEmpty(),
                    "direct event jump did not clear tracking and restore 1x");

            long beforeRejectedRainbow = Calendars.SERVER.getCalendarTicks();
            int rejectedRainbow = level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withLevel(level)
                            .withPosition(position.getCenter()).withPermission(4),
                    "wildfires tfctime skipto rainbow");
            assertTrue(rejectedRainbow == 0
                            && Calendars.SERVER.getCalendarTicks() == beforeRejectedRainbow,
                    "direct rainbow jump changed time without a real rain transition");

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
                                    "TFC player ticks diverged from the unchanged server tick count: player="
                                            + playerDelta + ", server=" + serverTickDelta
                                            + ", calendar=" + calendarDelta + ", dayTime=" + dayTimeDelta);
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

    @GameTest(template = "celestial_empty", batch = "zz_space_registry_reload", timeoutTicks = 400)
    public static void synchronizedCelestialRegistryReloads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long initialGeneration = CelestialRegistryRuntime.current().generation();
        assertTrue(initialGeneration > 0L,
                "celestial registry runtime was not populated during the initial data load");
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withLevel(level).withPermission(4),
                "reload");
        CelestialRegistrySnapshot snapshot = CelestialRegistryRuntime.current();
        assertTrue(snapshot.generation() > initialGeneration,
                "celestial registry generation did not advance after /reload completed");
        verifyOnlyEarthBindsOverworld(snapshot);
        ResourceLocation earth = ResourceLocation.fromNamespaceAndPath("wildfires", "earth");
        CelestialRegistrySnapshot transientlyPoisoned = CelestialRegistrySnapshot.reload(
                CelestialRegistrySnapshot.empty(), 1L,
                snapshot.validation().definitions(), java.util.Set.of(), resource -> true);
        assertTrue(!transientlyPoisoned.validation().get(earth).orElseThrow().landingAvailable(),
                "regression fixture did not reproduce the transient missing-dimension snapshot");
        var recoveredSurface = CelestialSurfaceBindingResolver.resolve(
                level.getServer(), transientlyPoisoned, earth).orElse(null);
        assertTrue(recoveredSurface != null
                        && recoveredSurface.level() == level.getServer().overworld()
                        && recoveredSurface.dimension().equals(Level.OVERWORLD.location()),
                "live overworld did not override a transient reload-time dimension diagnostic");
        verifyCommandCreatedStationTapeBindsInOverworld(helper, level);
        prepareTemplate(level);
        helper.succeed();
    }

    private static void verifyCommandCreatedStationTapeBindsInOverworld(GameTestHelper helper,
                                                                         ServerLevel level) {
        var server = level.getServer();
        UUID owner = UUID.fromString("a4fc259e-ccdb-45e0-b6dd-2c88e12e3db2");
        var player = FakePlayerFactory.get(server.overworld(),
                new GameProfile(owner, "wildfires-command-station-owner"));
        server.overworld().addNewPlayer(player);
        String name = "Command Surface Binding Station";
        int created = server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4),
                "wildfires space station create " + name);
        StationRecord station = SpaceSavedData.get(server).stations().values().stream()
                .filter(candidate -> candidate.owner().equals(owner) && candidate.name().equals(name))
                .findFirst().orElse(null);
        assertTrue(created > 0 && station != null
                        && station.currentBody().equals(ResourceLocation.fromNamespaceAndPath(
                        "wildfires", "earth")),
                "production station create command did not persist an Earth station");
        int before = countStationTapes(player);
        int issued = server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4),
                "wildfires space station tape " + station.stationId());
        ItemStack tape = player.getInventory().items.stream()
                .filter(stack -> StationIdTapeItem.stationId(stack)
                        .filter(station.stationId()::equals).isPresent())
                .findFirst().orElse(ItemStack.EMPTY);
        assertTrue(issued > 0 && !tape.isEmpty() && countStationTapes(player) == before + 1,
                "production station tape command did not issue the command-created station tape");
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        ReusableReturnCapsuleEntity capsule = new ReusableReturnCapsuleEntity(server.overworld(),
                base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D, owner);
        assertTrue(server.overworld().addFreshEntity(capsule),
                "command-created station tape test capsule did not spawn");
        player.setItemInHand(InteractionHand.MAIN_HAND, tape);
        InteractionResult applied = first.wildfires.space.capsule.ReturnCapsuleService.applyStationTape(
                player, capsule, InteractionHand.MAIN_HAND, tape);
        assertTrue(applied.consumesAction()
                        && capsule.stationId().filter(station.stationId()::equals).isPresent()
                        && capsule.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_CLOSING,
                "command-created station tape was rejected by an overworld reusable capsule");
        capsule.initializeFuelForTesting(1_000);
        assertTrue(player.startRiding(capsule, true),
                "command-created station capsule could not be boarded for its real input edge");
        assertTrue(first.wildfires.space.capsule.ReturnCapsuleService
                        .handlePrimaryActionInput(player, capsule, false).isEmpty()
                        && first.wildfires.space.capsule.ReturnCapsuleService
                        .handlePrimaryActionInput(player, capsule, true)
                        .filter(first.wildfires.space.capsule.ReturnCapsuleService.ActionResult.STARTED::equals)
                        .isPresent()
                        && capsule.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LAUNCHING
                        && capsule.transitionTicket().isPresent(),
                "real release/press input edge did not launch toward the command-created station");
        assertTrue(first.wildfires.space.capsule.ReturnCapsuleService
                        .recoverTransaction(capsule).successful(),
                "command-created station input test could not roll back its prepared launch");
        capsule.discard();
        server.overworld().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "celestial_empty", batch = "space_station_persistence", timeoutTicks = 200)
    public static void globalStationDataPersistsFromOverworld(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        CelestialEphemerisSavedData directEphemeris = server.overworld().getDataStorage().computeIfAbsent(
                CelestialEphemerisSavedData::load, CelestialEphemerisSavedData::new,
                CelestialEphemerisSavedData.FILE_ID);
        CelestialOrbitalPhases startupPhases = directEphemeris.phases();
        CelestialEphemerisSavedData ephemeris = CelestialEphemerisSavedData.get(server);
        assertTrue(ephemeris == directEphemeris,
                "creation ephemeris was not initialized at server startup in overworld DataStorage");
        CelestialOrbitalPhases phases = ephemeris.phases();
        assertTrue(phases.equals(startupPhases) && phases.equals(CelestialEphemerisSavedData.load(
                        ephemeris.save(new CompoundTag())).phases())
                        && phases.minimumInitialHeliocentricGap(
                        CelestialConfig.serverSettings().planetSettings())
                        >= 0.6D / 11.0D - 1.0E-12D,
                "creation ephemeris did not persist or allowed an initial planetary alignment");
        SpaceSavedData data = SpaceSavedData.get(server);
        SpaceSavedData directOverworldData = server.overworld().getDataStorage().computeIfAbsent(
                SpaceSavedData::load, SpaceSavedData::new, SpaceSavedData.FILE_ID);
        assertTrue(data == directOverworldData,
                "global station authority was not stored in the overworld DataStorage");
        assertTrue(data.writable(), "global station data unexpectedly opened read-only");

        var root = server.getCommands().getDispatcher().getRoot();
        var wildfires = root.getChild("wildfires");
        var space = wildfires == null ? null : wildfires.getChild("space");
        var stationBranch = space == null ? null : space.getChild("station");
        assertTrue(stationBranch != null
                        && stationBranch.getChild("create") != null
                        && stationBranch.getChild("list") != null
                        && stationBranch.getChild("info") != null
                        && stationBranch.getChild("teleport") != null
                        && stationBranch.getChild("recover") != null,
                "station debug command branches were not registered");

        UUID stationId = UUID.fromString("d650b0ee-85d2-4b9a-9e73-ad4aa30d6610");
        UUID owner = UUID.fromString("38cf1b16-c614-4558-8808-d7af921c0d1b");
        ResourceLocation earth = ResourceLocation.fromNamespaceAndPath("wildfires", "earth");
        if (data.station(stationId).isEmpty()) {
            StationService.OperationResult created = StationService.create(data, stationId,
                    "GameTest Station", owner, earth, CelestialRegistryRuntime.current(),
                    server.overworld().getGameTime());
            assertTrue(created.status() == StationService.OperationStatus.SUCCESS,
                    "fixed GameTest station could not be created: " + created.status()
                            + " " + created.message());
        }

        StationRecord station = data.station(stationId).orElseThrow();
        assertTrue(station.owner().equals(owner) && station.currentBody().equals(earth)
                        && station.status() == StationStatus.ACTIVE && station.revision() >= 1L,
                "fixed GameTest station did not retain its authoritative identity or Earth state");
        BlockPos safePoint = StationService.safePoint(data, stationId).orElseThrow();
        assertTrue(safePoint.equals(station.primaryDock().position().above().south(3))
                        && data.stationAt(safePoint.getX(), safePoint.getZ())
                        .map(value -> value.stationId().equals(stationId)).orElse(false),
                "station safe point did not resolve back to its allocated region");

        SpaceSavedData decoded = SpaceSavedData.load(data.save(new CompoundTag()));
        StationRecord reloaded = decoded.station(stationId).orElseThrow();
        assertTrue(reloaded.equals(station)
                        && StationService.safePoint(decoded, stationId).orElseThrow().equals(safePoint)
                        && reloaded.revision() == station.revision(),
                "station NBT save/load changed the record, safe point or revision");
        helper.succeed();
    }

    @GameTest(template = "celestial_empty", batch = "space_orbit_context", timeoutTicks = 200)
    public static void orbitDimensionAndStationContextsAreIsolated(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        assertTrue(orbit != null && orbit.dimension() == SpaceDimensions.ORBIT,
                "the required wildfires:orbit dimension was not loaded");
        ResourceLocation earth = ResourceLocation.fromNamespaceAndPath("wildfires", "earth");
        ResourceLocation mars = ResourceLocation.fromNamespaceAndPath("wildfires", "mars");
        ResourceLocation moon = ResourceLocation.fromNamespaceAndPath("wildfires", "moon");
        var routes = StationRouteRuntime.current();
        assertTrue(routes.definitions().size() == 306 && routes.rejected().isEmpty()
                        && routes.route(ResourceLocation.fromNamespaceAndPath("wildfires", "earth_to_mars"))
                        .isPresent()
                        && routes.route(first.wildfires.space.route.StationRouteDefinition
                        .freeTransferId(earth, moon)).isPresent()
                        && routes.route(first.wildfires.space.route.StationRouteDefinition
                        .freeTransferId(mars, moon)).isPresent(),
                "the 18-body stable-orbit transfer graph was not loaded and validated");
        assertTrue(!orbit.dimensionType().bedWorks() && !orbit.dimensionType().respawnAnchorWorks()
                        && !orbit.dimensionType().natural(),
                "orbit dimension unexpectedly allowed beds, anchors or natural-world semantics");

        SpaceSavedData data = SpaceSavedData.get(server);
        UUID firstId = UUID.fromString("d650b0ee-85d2-4b9a-9e73-ad4aa30d6610");
        UUID firstOwner = UUID.fromString("38cf1b16-c614-4558-8808-d7af921c0d1b");
        if (data.station(firstId).isEmpty()) {
            StationService.OperationResult created = StationService.create(data, firstId,
                    "GameTest Station", firstOwner, earth, CelestialRegistryRuntime.current(),
                    server.overworld().getGameTime());
            assertTrue(created.successful(), "first orbit GameTest station could not be created");
        }
        UUID secondId = UUID.fromString("e54121d1-7573-4ba3-ae30-6b8e02006b25");
        if (data.station(secondId).isEmpty()) {
            StationService.OperationResult created = StationService.create(data, secondId,
                    "Second Context Station", UUID.fromString("48c1feef-d155-4382-867a-a04cbd493758"),
                    mars, CelestialRegistryRuntime.current(), server.overworld().getGameTime());
            assertTrue(created.successful(), "second orbit GameTest station could not be created");
        }

        StationRecord first = data.station(firstId).orElseThrow();
        StationRecord second = data.station(secondId).orElseThrow();
        BlockPos firstPoint = first.region().safePoint();
        BlockPos secondPoint = second.region().safePoint();
        var firstContext = ObservationContextResolver.resolve(orbit, firstPoint.getCenter()).orElseThrow();
        var secondContext = ObservationContextResolver.resolve(orbit, secondPoint.getCenter()).orElseThrow();
        assertTrue(firstContext.stationId().equals(firstId)
                        && secondContext.stationId().equals(secondId)
                        && firstContext.currentBody().equals(earth)
                        && secondContext.currentBody().equals(mars)
                        && !firstContext.stationId().equals(secondContext.stationId())
                        && firstContext.region().contains(firstPoint.getX(), firstPoint.getZ())
                        && secondContext.region().contains(secondPoint.getX(), secondPoint.getZ()),
                "two stations in the shared orbit Level leaked or aliased observation context");
        assertTrue(ObservationContextResolver.resolve(orbit, new net.minecraft.world.phys.Vec3(0.5D, 128.0D, 0.5D))
                        .isEmpty()
                        && CelestialApi.state(orbit, new net.minecraft.world.phys.Vec3(0.5D, 128.0D, 0.5D), 0.0F)
                        .isEmpty(),
                "reserved station-free orbit region borrowed the last station context");
        CelestialState firstSky = CelestialApi.state(orbit, firstPoint.getCenter(), 0.0F).orElseThrow();
        CelestialState secondSky = CelestialApi.state(orbit, secondPoint.getCenter(), 0.0F).orElseThrow();
        assertTrue(firstSky.orbitingBodies().stream().anyMatch(body -> body.id().equals(mars))
                        && secondSky.orbitingBodies().stream().anyMatch(body -> body.id().equals(mars))
                        && firstSky.equals(secondSky),
                "orbit CelestialApi provider did not expose the same complete ephemeris to both contexts");

        BlockPos persistedBlock = firstPoint.below();
        orbit.setBlockAndUpdate(persistedBlock, Blocks.GOLD_BLOCK.defaultBlockState());
        assertTrue(orbit.getBlockState(persistedBlock).is(Blocks.GOLD_BLOCK)
                        && orbit.getBlockState(firstPoint.above(100)).isAir(),
                "orbit dimension did not preserve an ordinary block or was not void-generated");
        helper.succeed();
    }

    @GameTest(template = "celestial_empty", batch = "space_orbit_lighting", timeoutTicks = 240)
    public static void orbitBlockAndSkyLightPropagateAndClear(GameTestHelper helper) {
        ServerLevel orbit = helper.getLevel().getServer().getLevel(SpaceDimensions.ORBIT);
        assertTrue(orbit != null, "orbit level missing for lighting test");
        long runOffset = Math.floorMod(orbit.getGameTime(), 10_000L) * 32L;
        BlockPos source = new BlockPos(96 + (int) runOffset, 160, 96);
        BlockPos neighbor = source.east();
        BlockPos roof = source.above(8);
        BlockPos roofSample = roof.below();
        ChunkPos testChunk = new ChunkPos(source);
        orbit.setChunkForced(testChunk.x, testChunk.z, true);
        orbit.getChunkAt(source);
        orbit.setBlockAndUpdate(source, Blocks.GLOWSTONE.defaultBlockState());
        orbit.setBlockAndUpdate(roof, Blocks.GOLD_BLOCK.defaultBlockState());
        helper.runAfterDelay(40, () -> {
            assertTrue(orbit.getBrightness(LightLayer.BLOCK, source) == 15
                            && orbit.getBrightness(LightLayer.BLOCK, neighbor) > 0,
                    "orbit glowstone did not propagate block light");
            assertTrue(orbit.getBrightness(LightLayer.SKY, roofSample) < 15,
                    "orbit roof did not obstruct propagated sky light");
            orbit.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
            orbit.setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(60, () -> {
                assertTrue(orbit.getBrightness(LightLayer.BLOCK, source) == 0
                                && orbit.getBrightness(LightLayer.BLOCK, neighbor) == 0,
                        "orbit retained block light after glowstone removal");
                assertTrue(orbit.getBrightness(LightLayer.SKY, roofSample) == 15,
                        "orbit sky light did not recover after roof removal");
                orbit.setChunkForced(testChunk.x, testChunk.z, false);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "celestial_empty", batch = "space_station_travel", timeoutTicks = 2600)
    public static void testEngineGatesAndCompletesStationTravel(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        assertTrue(orbit != null, "orbit level missing for P5 travel test");
        SpaceSavedData data = SpaceSavedData.get(server);
        UUID stationId = UUID.fromString("5cf53992-f72f-45cb-b696-41753e011a75");
        UUID owner = UUID.fromString("09c848f5-b203-47c8-89ae-6438a16ce76a");
        if (data.station(stationId).isEmpty()) {
            StationService.OperationResult created = StationService.create(data, stationId,
                    "P5 Travel Station", owner,
                    ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                    CelestialRegistryRuntime.current(), server.overworld().getGameTime());
            assertTrue(created.successful(), "P5 travel station could not be created");
        }
        awaitTravelReady(helper, orbit, stationId, owner, 0);
    }

    @GameTest(template = "celestial_empty", batch = "space_capsule_core", timeoutTicks = 240)
    public static void stationCoreSelfHealsAndCapsuleUsesForgeWater(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        assertTrue(orbit != null, "orbit level missing for capsule/core acceptance");
        SpaceSavedData data = SpaceSavedData.get(server);
        UUID stationId = UUID.fromString("16f42077-81da-4d4c-a950-d06af305ce56");
        UUID owner = UUID.fromString("0bda69bf-aa55-4a14-bf05-984743cb83ca");
        if (data.station(stationId).isEmpty()) {
            StationService.OperationResult created = StationService.create(data, stationId,
                    "Capsule Core Station", owner,
                    ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                    CelestialRegistryRuntime.current(), server.overworld().getGameTime());
            assertTrue(created.successful(), "capsule/core station could not be created");
        }
        StationRecord station = data.station(stationId).orElseThrow();
        BlockPos corePos = station.primaryDock().position();
        ChunkPos chunk = new ChunkPos(corePos);
        orbit.setChunkForced(chunk.x, chunk.z, true);
        orbit.getChunkAt(corePos);
        assertTrue(StationCoreService.ensureCore(server, station), "station core did not materialize");
        assertTrue(orbit.getBlockState(corePos).is(SpaceContentRegister.STATION_CORE.get())
                        && orbit.getBlockEntity(corePos) instanceof StationCoreBlockEntity core
                        && core.stationId().filter(stationId::equals).isPresent(),
                "station core was not bound to its authoritative station");
        assertTrue(StationCoreService.isComplete(orbit, corePos)
                        && StationCoreService.structureOffsets().size() == 49,
                "station core is not the exact 1+49 five-by-five-by-two structure");
        for (BlockPos offset : StationCoreService.structureOffsets()) {
            assertTrue(orbit.getBlockState(corePos.offset(offset))
                            .is(SpaceContentRegister.STATION_STRUCTURE.get()),
                    "station core proxy missing at " + offset);
        }
        assertTrue(orbit.getBlockState(corePos.east(3)).isAir()
                        && orbit.getBlockState(corePos.west(3)).isAir()
                        && orbit.getBlockState(corePos.north(3)).isAir()
                        && orbit.getBlockState(corePos.south(3)).isAir(),
                "initial station contains blocks outside its sole core structure");

        var coreBlock = SpaceContentRegister.STATION_CORE.get();
        var coreState = orbit.getBlockState(corePos);
        var corePlayer = FakePlayerFactory.get(orbit,
                new GameProfile(UUID.fromString("9013bd94-9244-4dad-9ed7-08ecf03029c8"),
                        "wildfires-core-breaker"));
        assertTrue(coreBlock.getDestroyProgress(coreState, corePlayer, orbit, corePos) == 0.0F,
                "station core exposed finite mining progress");
        assertTrue(!coreBlock.onDestroyedByPlayer(coreState, orbit, corePos, corePlayer,
                        false, Fluids.EMPTY.defaultFluidState())
                        && orbit.getBlockState(corePos).is(coreBlock),
                "station core allowed player destruction");
        assertTrue(!coreBlock.canEntityDestroy(coreState, orbit, corePos, corePlayer),
                "station core allowed entity destruction");
        assertTrue(coreBlock.getPistonPushReaction(coreState)
                        == net.minecraft.world.level.material.PushReaction.BLOCK,
                "station core can be moved by a piston");
        orbit.explode(null, corePos.getX() + 0.5D, corePos.getY() + 0.5D,
                corePos.getZ() + 0.5D, 8.0F, Level.ExplosionInteraction.BLOCK);
        assertTrue(orbit.getBlockState(corePos).is(coreBlock)
                        && !coreBlock.dropFromExplosion(new Explosion(orbit, null,
                        corePos.getX() + 0.5D, corePos.getY() + 0.5D,
                        corePos.getZ() + 0.5D, 4.0F, false, Explosion.BlockInteraction.DESTROY)),
                "station core was destroyed or configured to drop from an explosion");

        // The same block item creates removable NTM-style secondary ports, while the initial
        // station core above remains immutable. Placement must leave both the 5x5x2 shell and the
        // capsule volume below it clear.
        var ownerPlayer = FakePlayerFactory.get(orbit,
                new GameProfile(owner, "wildfires-secondary-dock-owner"));
        ownerPlayer.getAbilities().instabuild = true;
        BlockPos secondaryCore = corePos.east(8);
        BlockPos constructionAnchor = secondaryCore.below(5);
        orbit.setBlockAndUpdate(constructionAnchor, Blocks.STONE.defaultBlockState());
        ItemStack secondaryItem = new ItemStack(SpaceContentRegister.STATION_CORE_ITEM.get());
        ownerPlayer.setItemInHand(InteractionHand.MAIN_HAND, secondaryItem);
        InteractionResult placedSecondary = secondaryItem.getItem().useOn(new UseOnContext(
                orbit, ownerPlayer, InteractionHand.MAIN_HAND, secondaryItem,
                new BlockHitResult(Vec3.atCenterOf(constructionAnchor).add(0.0D, 0.5D, 0.0D),
                        Direction.UP, constructionAnchor, false)));
        assertTrue(placedSecondary.consumesAction()
                        && StationCoreService.isComplete(orbit, secondaryCore)
                        && orbit.getBlockEntity(secondaryCore) instanceof StationCoreBlockEntity secondary
                        && !secondary.primary()
                        && data.station(stationId).orElseThrow().docks().values().stream()
                        .anyMatch(dock -> dock.position().equals(secondaryCore)),
                "secondary station core did not place as a registered 5x5x2 docking point");
        StationCoreBlockEntity secondary = (StationCoreBlockEntity) orbit.getBlockEntity(secondaryCore);
        UUID reservedCapsule = UUID.fromString("fcf188ef-a8f7-49b1-b19c-69d084359411");
        assertTrue(secondary.reserveDock(reservedCapsule)
                        && secondary.reservedCapsuleId().filter(reservedCapsule::equals).isPresent()
                        && secondary.dockedCapsuleId().isEmpty()
                        && !StationCoreService.removeSecondary(orbit, secondaryCore, owner, false)
                        && StationCoreService.isComplete(orbit, secondaryCore),
                "occupied secondary docking point could be dismantled");
        assertTrue(secondary.releaseDockLock(reservedCapsule)
                        && orbit.getBlockState(secondaryCore).onDestroyedByPlayer(
                        orbit, secondaryCore, ownerPlayer, false, Fluids.EMPTY.defaultFluidState())
                        && orbit.getBlockState(secondaryCore).isAir()
                        && data.station(stationId).orElseThrow().docks().values().stream()
                        .noneMatch(dock -> dock.position().equals(secondaryCore)),
                "empty secondary docking point was not atomically dismantled and unregistered");

        orbit.setBlockAndUpdate(corePos, Blocks.AIR.defaultBlockState());
        ReusableReturnCapsuleEntity capsule = new ReusableReturnCapsuleEntity(orbit,
                corePos.getX() + 4.5D, corePos.getY() + 1.0D, corePos.getZ() + 0.5D, owner);
        assertTrue(orbit.addFreshEntity(capsule), "return capsule entity did not spawn");
        IFluidHandler fluids = capsule.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER)
                .orElseThrow(() -> new AssertionError("return capsule exposed no Forge fluid handler"));
        assertTrue(fluids.fill(new FluidStack(Fluids.LAVA, 1_000), IFluidHandler.FluidAction.EXECUTE) == 0,
                "return capsule accepted non-water fluid");
        assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 4_000), IFluidHandler.FluidAction.EXECUTE) == 4_000
                        && fluids.getFluidInTank(0).getAmount() == ReturnCapsuleFuelTank.CAPACITY_MB,
                "return capsule did not accept its full Forge water capacity");
        assertTrue(fluids.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty()
                        && fluids.getFluidInTank(0).getAmount() == ReturnCapsuleFuelTank.CAPACITY_MB,
                "external Forge automation extracted protected propulsion water");

        var bucketPlayer = FakePlayerFactory.get(orbit,
                new GameProfile(UUID.fromString("c489d41c-1bf1-40a0-b1d5-0757ac8acf9c"),
                        "wildfires-bucket-pilot"));
        bucketPlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
        capsule.interact(bucketPlayer, InteractionHand.OFF_HAND);
        assertTrue(bucketPlayer.getOffhandItem().is(Items.WATER_BUCKET)
                        && capsule.fuelMb() == ReturnCapsuleFuelTank.CAPACITY_MB,
                "full capsule consumed an offhand water bucket");
        assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 1_000),
                        IFluidHandler.FluidAction.SIMULATE) == 0
                        && capsule.fuelMb() == ReturnCapsuleFuelTank.CAPACITY_MB,
                "full capsule Forge simulation accepted water or mutated the tank");
        bucketPlayer.getAbilities().instabuild = true;
        fluids.drain(1_000, IFluidHandler.FluidAction.EXECUTE);
        // External drain is forbidden, so create a separate empty entity for the creative-hand contract.
        ReusableReturnCapsuleEntity creativeCapsule = new ReusableReturnCapsuleEntity(orbit,
                corePos.getX() + 7.5D, corePos.getY() + 1.0D, corePos.getZ() + 0.5D, owner);
        assertTrue(orbit.addFreshEntity(creativeCapsule), "creative bucket capsule did not spawn");
        bucketPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        creativeCapsule.interact(bucketPlayer, InteractionHand.MAIN_HAND);
        assertTrue(creativeCapsule.fuelMb() == 1_000
                        && bucketPlayer.getMainHandItem().is(Items.WATER_BUCKET),
                "creative water bucket did not fill exactly 1000 mB while remaining intact");

        CompoundTag saved = new CompoundTag();
        capsule.save(saved);
        ReusableReturnCapsuleEntity restored = new ReusableReturnCapsuleEntity(
                SpaceContentRegister.REUSABLE_RETURN_CAPSULE.get(), orbit);
        restored.load(saved);
        assertTrue(restored.fuelMb() == ReturnCapsuleFuelTank.CAPACITY_MB
                        && restored.ownerPlayer().filter(owner::equals).isPresent(),
                "return capsule entity NBT did not preserve fuel or owner");

        helper.runAfterDelay(45, () -> {
            assertTrue(orbit.getBlockState(corePos).isAir(),
                    "station proxies were polled and rebuilt by a periodic tick scan");
            for (BlockPos offset : StationCoreService.structureOffsets()) {
                assertTrue(orbit.getBlockState(corePos.offset(offset))
                                .is(SpaceContentRegister.STATION_STRUCTURE.get()),
                        "isolated station proxy was polled and cleared at " + offset);
            }
            assertTrue(StationCoreService.ensureCore(server, station),
                    "explicit station recovery boundary did not rebuild the core");
            assertTrue(orbit.getBlockState(corePos).is(SpaceContentRegister.STATION_CORE.get())
                            && orbit.getBlockEntity(corePos) instanceof StationCoreBlockEntity core
                            && core.stationId().filter(stationId::equals).isPresent(),
                    "loaded station core was not restored after forced replacement");
            capsule.discard();
            creativeCapsule.discard();
            orbit.setChunkForced(chunk.x, chunk.z, false);
            helper.succeed();
        });
    }

    @GameTest(template = "celestial_empty", batch = "space_capsule_manufacturing", timeoutTicks = 200)
    public static void reusableCapsuleRecipeAndDeploymentArePlayerUsable(GameTestHelper helper) {
        ServerLevel surface = helper.getLevel().getServer().overworld();
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                "wildfires", "reusable_return_capsule");
        var recipe = surface.getRecipeManager().byKey(recipeId).orElse(null);
        assertTrue(recipe instanceof CraftingRecipe,
                "reusable return capsule crafting recipe was not loaded");

        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }
        };
        TransientCraftingContainer grid = new TransientCraftingContainer(menu, 3, 3);
        grid.setItem(1, new ItemStack(Items.IRON_INGOT));
        grid.setItem(3, new ItemStack(Items.IRON_INGOT));
        grid.setItem(4, new ItemStack(Items.GLASS));
        grid.setItem(5, new ItemStack(Items.IRON_INGOT));
        grid.setItem(6, new ItemStack(Items.IRON_INGOT));
        grid.setItem(7, new ItemStack(Items.IRON_INGOT));
        grid.setItem(8, new ItemStack(Items.IRON_INGOT));
        CraftingRecipe crafting = (CraftingRecipe) recipe;
        assertTrue(crafting.matches(grid, surface),
                "documented reusable return capsule recipe does not match its 3x3 ingredients");
        ItemStack manufactured = crafting.assemble(grid, surface.registryAccess());
        assertTrue(manufactured.is(SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get())
                        && manufactured.getCount() == 1,
                "reusable return capsule recipe did not produce exactly one deployable capsule");
        grid.setItem(4, new ItemStack(Items.DIRT));
        assertTrue(!crafting.matches(grid, surface),
                "reusable return capsule recipe accepted a non-glass cabin");

        UUID owner = UUID.fromString("7de4f4db-a1ec-4ae9-a7f3-9d79abcbbf5e");
        var player = FakePlayerFactory.get(surface,
                new GameProfile(owner, "wildfires-capsule-builder"));
        BlockPos support = helper.absolutePos(new BlockPos(5, 2, 5));
        surface.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        player.moveTo(support.getX() + 3.5D, support.getY() + 1.0D,
                support.getZ() + 0.5D, 90.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, manufactured.copy());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D),
                Direction.UP, support, false);
        InteractionResult deployed = manufactured.getItem().useOn(new UseOnContext(
                surface, player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit));
        AABB deploymentArea = new AABB(support.above()).inflate(2.0D, 1.0D, 2.0D);
        List<ReusableReturnCapsuleEntity> capsules = surface.getEntitiesOfClass(
                ReusableReturnCapsuleEntity.class, deploymentArea);
        assertTrue(deployed.consumesAction() && capsules.size() == 1
                        && capsules.get(0).ownerPlayer().filter(owner::equals).isPresent()
                        && player.getMainHandItem().isEmpty(),
                "manufactured capsule did not deploy one owner-bound entity and consume the item");
        ReusableReturnCapsuleEntity deployedCapsule = capsules.get(0);
        deployedCapsule.initializeFuelForTesting(2_000);
        UUID internalTapeStation = UUID.fromString("5f134f91-9341-4958-b865-3ad65d9c3d85");
        deployedCapsule.setNavigationTape(StationIdTapeItem.createMigrated(internalTapeStation));
        assertTrue(deployedCapsule.hurt(surface.damageSources().playerAttack(player), 1.0F)
                        && deployedCapsule.isRemoved(),
                "stable unoccupied return capsule could not be broken for recovery");
        List<ItemEntity> recoveredDrops = surface.getEntitiesOfClass(ItemEntity.class,
                deploymentArea, drop -> drop.getItem().is(
                        SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get()));
        assertTrue(recoveredDrops.size() == 1, "capsule recovery did not create exactly one item");
        ItemStack recoveredStack = recoveredDrops.get(0).getItem().copy();
        List<ItemEntity> recoveredTapes = surface.getEntitiesOfClass(ItemEntity.class,
                deploymentArea, drop -> drop.getItem().is(SpaceContentRegister.STATION_ID_TAPE.get()));
        assertTrue(recoveredTapes.size() == 1
                        && StationIdTapeItem.stationId(recoveredTapes.get(0).getItem())
                        .filter(internalTapeStation::equals).isPresent()
                        && (recoveredStack.getTag() == null || !recoveredStack.getTag()
                        .contains("wildfires_capsule_navigation_tape")),
                "broken capsule did not drop its internal station tape as a separate NTM drive item");
        recoveredTapes.get(0).discard();
        recoveredDrops.get(0).discard();
        player.setItemInHand(InteractionHand.MAIN_HAND, recoveredStack);
        InteractionResult redeployed = recoveredStack.getItem().useOn(new UseOnContext(
                surface, player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit));
        List<ReusableReturnCapsuleEntity> restoredCapsules = surface.getEntitiesOfClass(
                ReusableReturnCapsuleEntity.class, deploymentArea);
        assertTrue(redeployed.consumesAction() && restoredCapsules.size() == 1
                        && restoredCapsules.get(0).fuelMb() == 2_000,
                "recovered capsule did not redeploy once with its preserved water fuel");

        BlockPos blockedSupport = support.offset(6, 0, 0);
        surface.setBlockAndUpdate(blockedSupport, Blocks.STONE.defaultBlockState());
        surface.setBlockAndUpdate(blockedSupport.above(), Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get()));
        BlockHitResult blockedHit = new BlockHitResult(
                Vec3.atCenterOf(blockedSupport).add(0.0D, 0.5D, 0.0D),
                Direction.UP, blockedSupport, false);
        InteractionResult blocked = player.getMainHandItem().getItem().useOn(new UseOnContext(
                surface, player, InteractionHand.MAIN_HAND, player.getMainHandItem(), blockedHit));
        assertTrue(blocked == InteractionResult.FAIL && player.getMainHandItem().getCount() == 1
                        && surface.getEntitiesOfClass(ReusableReturnCapsuleEntity.class,
                        new AABB(blockedSupport.above()).inflate(2.0D, 1.0D, 2.0D)).isEmpty(),
                "blocked capsule deployment consumed the item or spawned an intersecting entity");

        restoredCapsules.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "celestial_empty", batch = "space_capsule_round_trip", timeoutTicks = 6400)
    public static void reusableCapsuleCompletesAuthoritativeRoundTrip(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel surface = server.overworld();
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        assertTrue(orbit != null, "orbit level missing for reusable-capsule round trip");
        SpaceSavedData data = SpaceSavedData.get(server);
        UUID stationId = UUID.fromString("36ae061d-c903-48c3-a399-16196c7a9b50");
        UUID owner = UUID.fromString("5158865b-234e-4b59-93e9-420ae975b05d");
        if (data.station(stationId).isEmpty()) {
            var created = StationService.create(data, stationId, "Round Trip Station", owner,
                    ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                    CelestialRegistryRuntime.current(), server.overworld().getGameTime());
            assertTrue(created.successful(), "round-trip station could not be created");
        }
        StationRecord station = data.station(stationId).orElseThrow();
        for (UUID staleCapsule : List.copyOf(station.ownedReturnCapsules())) {
            StationService.setReturnCapsule(data, stationId, staleCapsule, false,
                    server.overworld().getGameTime());
        }
        station = data.station(stationId).orElseThrow();
        assertTrue(station.currentBody().equals(ResourceLocation.fromNamespaceAndPath("wildfires", "earth"))
                        && station.journey().isEmpty(),
                "round-trip station is not in stable Earth orbit");
        BlockPos corePos = station.primaryDock().position();
        ChunkPos orbitChunk = new ChunkPos(corePos);
        orbit.setChunkForced(orbitChunk.x, orbitChunk.z, true);
        assertTrue(StationCoreService.ensureCore(server, station), "round-trip station core is unavailable");

        BlockPos landingBase = helper.absolutePos(new BlockPos(1, 2, 1));
        surface.setBlockAndUpdate(landingBase.below(), Blocks.STONE.defaultBlockState());
        var player = FakePlayerFactory.get(surface, new GameProfile(owner, "wildfires-capsule-pilot"));
        surface.addNewPlayer(player);
        UUID unknownStation = UUID.fromString("506dcd97-a8bc-4710-b5b4-7ec3f3d4ff19");
        int beforeTapeCount = countStationTapes(player);
        String tapePrefix = "wildfires space station tape ";
        var parsedTapeCommand = server.getCommands().getDispatcher().parse(
                tapePrefix, player.createCommandSourceStack().withPermission(4));
        var tapeSuggestions = server.getCommands().getDispatcher()
                .getCompletionSuggestions(parsedTapeCommand, tapePrefix.length()).join();
        assertTrue(tapeSuggestions.getList().stream()
                        .anyMatch(suggestion -> suggestion.getText().equals(stationId.toString())),
                "station tape command did not suggest the existing station UUID");
        int rejectedTapeCommand = server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4),
                "wildfires space station tape " + unknownStation);
        assertTrue(rejectedTapeCommand == 0 && countStationTapes(player) == beforeTapeCount,
                "unknown station command issued a forged tape");
        int issuedTapeCommand = server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4),
                "wildfires space station tape " + stationId);
        ItemStack issuedTape = player.getInventory().items.stream()
                .filter(stack -> StationIdTapeItem.stationId(stack).filter(stationId::equals).isPresent())
                .findFirst().orElse(ItemStack.EMPTY);
        assertTrue(issuedTapeCommand > 0 && !issuedTape.isEmpty()
                        && countStationTapes(player) == beforeTapeCount + 1,
                "station tape command did not issue exactly one canonical tape");
        player.getInventory().removeItem(issuedTape);
        ItemStack manufactured = manufactureReusableCapsule(surface);
        player.setItemInHand(InteractionHand.MAIN_HAND, manufactured);
        BlockPos deploymentSupport = landingBase.below();
        BlockHitResult deploymentHit = new BlockHitResult(Vec3.atCenterOf(deploymentSupport)
                .add(0.0D, 0.5D, 0.0D), Direction.UP, deploymentSupport, false);
        InteractionResult deployment = manufactured.getItem().useOn(new UseOnContext(
                surface, player, InteractionHand.MAIN_HAND, player.getMainHandItem(), deploymentHit));
        List<ReusableReturnCapsuleEntity> deployed = surface.getEntitiesOfClass(
                ReusableReturnCapsuleEntity.class, new AABB(landingBase).inflate(2.0D, 1.0D, 2.0D));
        assertTrue(deployment.consumesAction() && deployed.size() == 1
                        && deployed.get(0).ownerPlayer().filter(owner::equals).isPresent()
                        && player.getMainHandItem().isEmpty(),
                "manufactured return capsule did not deploy through its production item entry point");
        ReusableReturnCapsuleEntity capsule = deployed.get(0);
        UUID capsuleId = capsule.getUUID();
        var tapePlayer = FakePlayerFactory.get(orbit,
                new GameProfile(owner, "wildfires-station-tape-programmer"));
        ItemStack stationTape = new ItemStack(SpaceContentRegister.STATION_ID_TAPE.get());
        tapePlayer.setItemInHand(InteractionHand.MAIN_HAND, stationTape);
        InteractionResult programmed = orbit.getBlockState(corePos).use(orbit, tapePlayer,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(corePos),
                Direction.UP, corePos, false));
        assertTrue(programmed.consumesAction()
                        && first.wildfires.space.content.StationIdTapeItem.stationId(stationTape)
                        .filter(stationId::equals).isPresent(),
                "station core root did not program the station-ID tape");
        BlockPos missingProxy = corePos.offset(StationCoreService.structureOffsets().get(0));
        BlockPos remainingProxy = StationCoreService.topCenter(corePos);
        orbit.setBlockAndUpdate(missingProxy, Blocks.AIR.defaultBlockState());
        ItemStack incompleteTape = new ItemStack(SpaceContentRegister.STATION_ID_TAPE.get());
        tapePlayer.setItemInHand(InteractionHand.MAIN_HAND, incompleteTape);
        InteractionResult incomplete = orbit.getBlockState(remainingProxy).use(orbit, tapePlayer,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(remainingProxy),
                Direction.UP, remainingProxy, false));
        assertTrue(incomplete == InteractionResult.FAIL
                        && first.wildfires.space.content.StationIdTapeItem.stationId(incompleteTape).isEmpty(),
                "incomplete station core structure still programmed a station-ID tape");
        orbit.setBlockAndUpdate(missingProxy, SpaceContentRegister.STATION_STRUCTURE.get()
                .defaultBlockState());
        BlockPos isolatedProxy = corePos.east(4);
        orbit.setBlockAndUpdate(isolatedProxy, SpaceContentRegister.STATION_STRUCTURE.get()
                .defaultBlockState());
        ItemStack rejectedTape = new ItemStack(SpaceContentRegister.STATION_ID_TAPE.get());
        tapePlayer.setItemInHand(InteractionHand.MAIN_HAND, rejectedTape);
        InteractionResult rejected = orbit.getBlockState(isolatedProxy).use(orbit, tapePlayer,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(isolatedProxy),
                Direction.UP, isolatedProxy, false));
        assertTrue(rejected == InteractionResult.FAIL
                        && first.wildfires.space.content.StationIdTapeItem.stationId(rejectedTape).isEmpty(),
                "isolated station structure proxy guessed a nearby core");
        orbit.setBlockAndUpdate(isolatedProxy, Blocks.AIR.defaultBlockState());
        player.setItemInHand(InteractionHand.OFF_HAND, stationTape.copy());
        capsule.interact(player, InteractionHand.OFF_HAND);
        assertTrue(capsule.stationId().filter(stationId::equals).isPresent(),
                "station-ID tape did not bind the surface capsule to the target station");
        IFluidHandler fluids = capsule.getCapability(
                net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER)
                .orElseThrow(() -> new AssertionError("return capsule has no Forge fluid capability"));
        assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 3_000),
                        IFluidHandler.FluidAction.EXECUTE) == 3_000,
                "Forge water did not prepare the first three capsule buckets");

        player.moveTo(capsule.getX(), capsule.getY() + 1.0D, capsule.getZ(), 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        capsule.interact(player, InteractionHand.MAIN_HAND);
        assertTrue(capsule.fuelMb() == 4_000 && player.getMainHandItem().is(Items.BUCKET),
                "water-bucket interaction was not an atomic 1000 mB fill");
        assertTrue(player.startRiding(capsule, true), "test pilot could not board the capsule");
        double launchY = capsule.getY();
        UUID staleDockLock = UUID.fromString("677fe7cd-7da8-4346-a54d-2ffcb44a6bb0");
        assertTrue(orbit.getBlockEntity(corePos)
                        instanceof first.wildfires.space.content.StationCoreBlockEntity core
                        && clearFixtureDockClaim(core)
                        && core.reserveDock(staleDockLock),
                "round-trip fixture could not reproduce an orphaned persisted dock lock");
        BlockPos launchMissingProxy = corePos.offset(StationCoreService.structureOffsets().get(2));
        orbit.setBlockAndUpdate(launchMissingProxy, Blocks.AIR.defaultBlockState());
        assertTrue(first.wildfires.space.capsule.ReturnCapsuleService
                        .requestPrimaryAction(player, capsule)
                        == first.wildfires.space.capsule.ReturnCapsuleService.ActionResult.NO_DOCK
                        && capsule.fuelMb() == 4_000
                        && capsule.fuelTank().reservation().isEmpty()
                        && capsule.transitionTicket().isEmpty()
                        && capsule.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_CLOSING,
                "launch without a complete free docking point mutated fuel or flight state");
        orbit.setBlockAndUpdate(launchMissingProxy, SpaceContentRegister.STATION_STRUCTURE.get()
                .defaultBlockState());
        var launchResult = first.wildfires.space.capsule.ReturnCapsuleService
                .requestPrimaryAction(player, capsule);
        assertTrue(launchResult.successful(),
                "surface launch request was rejected: " + launchResult + "; state="
                        + capsule.capsuleState() + "; armed=" + capsule.primaryActionArmed()
                        + "; pressed=" + capsule.primaryActionPressed());

        helper.runAfterDelay(95, () -> {
            Entity stillSurface = surface.getEntity(capsuleId);
            assertTrue(stillSurface instanceof ReusableReturnCapsuleEntity rising
                            && rising.capsuleState() == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LAUNCHING
                            && rising.getY() > launchY + 220.0D && rising.getY() < launchY + 270.0D,
                    "surface launch did not follow its continuous ascent trajectory");
        });
        helper.runAfterDelay(240, () -> awaitCapsuleDocking(helper, surface, orbit, data,
                stationId, capsuleId, player, landingBase, orbitChunk, new NtmCapsuleMotionAudit(), 0));
    }

    @GameTest(template = "celestial_empty", batch = "space_capsule_docked_deployment", timeoutTicks = 240)
    public static void reusableCapsuleDeploysOnceBelowCompleteCore(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        assertTrue(orbit != null, "orbit level missing for docked capsule deployment");
        SpaceSavedData data = SpaceSavedData.get(server);
        UUID stationId = UUID.fromString("bc66271a-7ab5-477d-8b0a-3ea87618f369");
        UUID owner = UUID.fromString("28fc4571-f544-409c-bea0-02c6706f63c2");
        if (data.station(stationId).isEmpty()) {
            assertTrue(StationService.create(data, stationId, "Docked Deployment Station", owner,
                    ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                    CelestialRegistryRuntime.current(), server.overworld().getGameTime()).successful(),
                    "docked deployment station could not be created");
        }
        StationRecord station = data.station(stationId).orElseThrow();
        for (UUID stale : List.copyOf(station.ownedReturnCapsules())) {
            Entity entity = orbit.getEntity(stale);
            if (entity != null) entity.discard();
            StationService.setReturnCapsule(data, stationId, stale, false,
                    server.overworld().getGameTime());
        }
        station = data.station(stationId).orElseThrow();
        assertTrue(StationCoreService.ensureCore(server, station), "complete docking core unavailable");
        first.wildfires.space.capsule.ReturnCapsuleService.reconcileDockLocks(server, station);
        BlockPos core = station.primaryDock().position();
        var player = FakePlayerFactory.get(orbit,
                new GameProfile(owner, "wildfires-docked-capsule-installer"));
        orbit.addNewPlayer(player);
        ItemStack capsuleItem = new ItemStack(SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, capsuleItem);
        BlockPos proxy = core.offset(StationCoreService.structureOffsets().get(0));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(proxy), Direction.DOWN, proxy, false);
        InteractionResult deployed = capsuleItem.getItem().useOn(new UseOnContext(
                orbit, player, InteractionHand.MAIN_HAND, capsuleItem, hit));
        Vec3 dock = first.wildfires.space.capsule.ReturnCapsuleService.stationDockedPosition(core);
        List<ReusableReturnCapsuleEntity> capsules = orbit.getEntitiesOfClass(
                ReusableReturnCapsuleEntity.class,
                first.wildfires.space.capsule.ReturnCapsuleService.capsuleBoundsAt(dock).inflate(0.1D));
        assertTrue(deployed.consumesAction() && player.getMainHandItem().isEmpty()
                        && capsules.size() == 1
                        && capsules.get(0).capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.STATION_DOCKED
                        && capsules.get(0).stationId().filter(stationId::equals).isPresent()
                        && capsules.get(0).position().distanceTo(dock) < 0.01D
                        && data.station(stationId).orElseThrow().ownedReturnCapsules()
                        .contains(capsules.get(0).getUUID()),
                "capsule item did not atomically deploy into the bottom docking port");
        ReusableReturnCapsuleEntity docked = capsules.get(0);
        docked.setNavigationTape(ItemStack.EMPTY);
        ItemStack commandTape = StationIdTapeItem.createProgrammed(
                data.station(stationId).orElseThrow());
        player.setItemInHand(InteractionHand.OFF_HAND, commandTape);
        BlockPos topCenter = StationCoreService.topCenter(core);
        BlockHitResult topHit = new BlockHitResult(Vec3.atCenterOf(topCenter).add(0.0D, 0.5D, 0.0D),
                Direction.UP, topCenter, false);
        InteractionResult installedTape = orbit.getBlockState(topCenter).use(
                orbit, player, InteractionHand.OFF_HAND, topHit);
        assertTrue(installedTape.consumesAction()
                        && docked.stationId().filter(stationId::equals).isPresent()
                        && player.getOffhandItem().isEmpty(),
                "top-centre core proxy rejected the first correct station-ID tape");
        IFluidHandler coreFluids = orbit.getBlockEntity(core)
                .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER)
                .orElseThrow(() -> new AssertionError("station core exposed no Forge fluid input"));
        assertTrue(StationCoreService.fluidPortOffsets().size() == 12,
                "station core does not expose exactly twelve NTM fluid interfaces");
        for (BlockPos offset : StationCoreService.fluidPortOffsets()) {
            BlockPos portPosition = core.offset(offset);
            assertTrue(orbit.getBlockEntity(portPosition)
                            instanceof first.wildfires.space.content.StationFluidPortBlockEntity port
                            && core.equals(port.corePosition()),
                    "station fluid interface is missing its constant-time core owner at " + portPosition);
            IFluidHandler portFluids = orbit.getBlockEntity(portPosition)
                    .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER)
                    .orElseThrow(() -> new AssertionError("station interface exposed no Forge fluid input"));
            assertTrue(portFluids.fill(new FluidStack(Fluids.WATER, 1),
                            IFluidHandler.FluidAction.SIMULATE) == 1
                            && portFluids.fill(new FluidStack(Fluids.LAVA, 1),
                            IFluidHandler.FluidAction.SIMULATE) == 0
                            && portFluids.drain(1, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                    "station interface did not remain water-input-only at " + portPosition);
        }
        IFluidHandler firstPort = orbit.getBlockEntity(core.offset(
                        StationCoreService.fluidPortOffsets().get(0)))
                .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER)
                .orElseThrow(() -> new AssertionError("first station interface has no fluid handler"));
        assertTrue(coreFluids.fill(new FluidStack(Fluids.LAVA, 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 0
                        && firstPort.fill(new FluidStack(Fluids.WATER, 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000
                        && docked.fuelMb() == 1_000,
                "one of the twelve station interfaces did not forward Forge water into its physically docked capsule");
        ItemStack duplicate = new ItemStack(SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, duplicate);
        InteractionResult duplicateResult = duplicate.getItem().useOn(new UseOnContext(
                orbit, player, InteractionHand.MAIN_HAND, duplicate, hit));
        assertTrue(duplicateResult == InteractionResult.FAIL && duplicate.getCount() == 1
                        && orbit.getEntitiesOfClass(ReusableReturnCapsuleEntity.class,
                        first.wildfires.space.capsule.ReturnCapsuleService.capsuleBoundsAt(dock)
                                .inflate(0.1D)).size() == 1,
                "occupied docking port consumed or duplicated a second capsule");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult boarded = orbit.getBlockState(topCenter).use(
                orbit, player, InteractionHand.MAIN_HAND, topHit);
        assertTrue(boarded.consumesAction() && player.getVehicle() == docked,
                "empty-hand top-centre interaction did not board the docked capsule");
        Vec3 expectedDismount = first.wildfires.space.capsule.ReturnCapsuleService
                .stationDismountPosition(core);
        assertTrue(docked.getDismountLocationForPassenger(player).distanceToSqr(expectedDismount)
                        < 1.0E-8D
                        && Math.abs(expectedDismount.y - (core.getY() + 2.0D)) < 1.0E-8D,
                "station dismount was not placed on the exposed top-centre surface");
        BlockPos missingProxy = core.offset(StationCoreService.structureOffsets().get(1));
        assertTrue(orbit.setBlock(missingProxy, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL), "test could not remove one core proxy");
        assertTrue(first.wildfires.space.capsule.ReturnCapsuleService.requestPrimaryAction(player, docked)
                        == first.wildfires.space.capsule.ReturnCapsuleService.ActionResult.NO_CORE,
                "return capsule departed from an incomplete station core");
        assertTrue(docked.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.STATION_DOCKED
                        && docked.fuelMb() == 1000 && docked.transitionTicket().isEmpty(),
                "rejected incomplete-core departure mutated capsule state or fuel");
        assertTrue(StationCoreService.ensureCore(server, data.station(stationId).orElseThrow()),
                "test could not restore complete docking core");
        player.stopRiding();
        capsules.get(0).discard();
        StationService.setReturnCapsule(data, stationId, capsules.get(0).getUUID(), false,
                server.overworld().getGameTime());
        helper.succeed();
    }

    private static int countStationTapes(ServerPlayer player) {
        return player.getInventory().items.stream()
                .filter(stack -> stack.is(SpaceContentRegister.STATION_ID_TAPE.get()))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static ItemStack manufactureReusableCapsule(ServerLevel level) {
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                "wildfires", "reusable_return_capsule");
        var recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
        assertTrue(recipe instanceof CraftingRecipe,
                "reusable return capsule recipe was not loaded for the round trip");
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }
        };
        TransientCraftingContainer grid = new TransientCraftingContainer(menu, 3, 3);
        grid.setItem(1, new ItemStack(Items.IRON_INGOT));
        grid.setItem(3, new ItemStack(Items.IRON_INGOT));
        grid.setItem(4, new ItemStack(Items.GLASS));
        grid.setItem(5, new ItemStack(Items.IRON_INGOT));
        grid.setItem(6, new ItemStack(Items.IRON_INGOT));
        grid.setItem(7, new ItemStack(Items.IRON_INGOT));
        grid.setItem(8, new ItemStack(Items.IRON_INGOT));
        CraftingRecipe crafting = (CraftingRecipe) recipe;
        assertTrue(crafting.matches(grid, level),
                "reusable return capsule recipe did not match during the round trip");
        ItemStack result = crafting.assemble(grid, level.registryAccess());
        assertTrue(result.is(SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get())
                        && result.getCount() == 1,
                "round-trip manufacturing did not produce exactly one return capsule");
        return result;
    }

    @GameTest(template = "celestial_empty", batch = "space_capsule_recovery", timeoutTicks = 240)
    public static void reusableCapsuleRecoversPreparedFuelTransactionExactlyOnce(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel surface = server.overworld();
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        assertTrue(orbit != null, "orbit level missing for reusable-capsule recovery");
        SpaceSavedData data = SpaceSavedData.get(server);
        UUID stationId = UUID.fromString("dd1c42ab-4b85-4494-a7cc-b4761bde0ad2");
        UUID owner = UUID.fromString("4ea3dd80-1347-43cc-8b2f-734b5b422443");
        if (data.station(stationId).isEmpty()) {
            var created = StationService.create(data, stationId, "Recovery Station", owner,
                    ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                    CelestialRegistryRuntime.current(), server.overworld().getGameTime());
            assertTrue(created.successful(), "recovery station could not be created");
        }
        StationRecord station = data.station(stationId).orElseThrow();
        // This GameTest world is intentionally persistent across local runs. Earlier versions
        // discarded fixture entities without removing their station ownership, eventually filling
        // MAX_RETURN_CAPSULES and making an otherwise valid recovery transaction fail before it
        // was exercised. Clear only this test station's fixture ownership and re-read its revision.
        for (UUID staleCapsule : List.copyOf(station.ownedReturnCapsules())) {
            var removed = StationService.setReturnCapsule(data, stationId, staleCapsule, false,
                    server.overworld().getGameTime());
            assertTrue(removed.successful(),
                    "stale recovery fixture ownership could not be cleared: "
                            + removed.status() + " " + removed.message());
        }
        station = data.station(stationId).orElseThrow();
        BlockPos corePos = station.primaryDock().position();
        ChunkPos orbitChunk = new ChunkPos(corePos);
        orbit.setChunkForced(orbitChunk.x, orbitChunk.z, true);
        orbit.getChunkAt(corePos);
        assertTrue(StationCoreService.ensureCore(server, station),
                "recovery station core is unavailable");
        first.wildfires.space.capsule.ReturnCapsuleService.reconcileDockLocks(server, station);

        BlockPos landing = helper.absolutePos(new BlockPos(1, 2, 1));
        ReusableReturnCapsuleEntity sourceCapsule = new ReusableReturnCapsuleEntity(surface,
                landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D, owner);
        assertTrue(surface.addFreshEntity(sourceCapsule), "source recovery capsule did not spawn");
        assertTrue(StationService.setReturnCapsule(data, stationId, sourceCapsule.getUUID(), true,
                server.overworld().getGameTime()).successful(),
                "source recovery capsule ownership could not be restored");
        sourceCapsule.initializeFuelForTesting(ReturnCapsuleFuelTank.CAPACITY_MB);
        sourceCapsule.bindStation(stationId);
        sourceCapsule.setHomeSurface(Level.OVERWORLD.location(), landing);
        UUID rollbackId = UUID.fromString("af813330-d0d8-4897-889b-b3fb5c4a9883");
        assertTrue(sourceCapsule.reserveFuelTrip(rollbackId),
                "source recovery transaction did not reserve one bucket");
        var rollbackTicket = new first.wildfires.space.capsule.ReturnCapsuleTransitionTicket(
                rollbackId, stationId,
                first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Direction.TO_STATION,
                ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                Level.OVERWORLD.location(), landing, SpaceDimensions.ORBIT.location(), corePos,
                owner, sourceCapsule.revision(), server.overworld().getGameTime(),
                first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.PREPARED);
        sourceCapsule.setTransitionTicket(rollbackTicket);
        sourceCapsule.setCapsuleState(
                first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LAUNCHING);
        assertTrue(first.wildfires.space.capsule.ReturnCapsuleService
                        .recoverTransaction(sourceCapsule)
                        == first.wildfires.space.capsule.ReturnCapsuleService.ActionResult.RECOVERED,
                "source-side PREPARED recovery was rejected");
        assertTrue(sourceCapsule.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LANDED
                        && sourceCapsule.fuelMb() == ReturnCapsuleFuelTank.CAPACITY_MB
                        && sourceCapsule.fuelTank().reservation().isEmpty()
                        && sourceCapsule.fuelTank().lastCommitted().isEmpty()
                        && sourceCapsule.transitionTicket().isEmpty(),
                "source recovery did not roll back without consuming water");

        ReusableReturnCapsuleEntity targetCapsule = new ReusableReturnCapsuleEntity(orbit,
                corePos.getX() + 4.5D, corePos.getY() + 1.0D, corePos.getZ() + 0.5D, owner);
        assertTrue(orbit.addFreshEntity(targetCapsule), "target recovery capsule did not spawn");
        assertTrue(StationService.setReturnCapsule(data, stationId, targetCapsule.getUUID(), true,
                server.overworld().getGameTime()).successful(),
                "target recovery capsule ownership could not be restored");
        targetCapsule.initializeFuelForTesting(ReturnCapsuleFuelTank.CAPACITY_MB);
        targetCapsule.bindStation(stationId);
        targetCapsule.setHomeSurface(Level.OVERWORLD.location(), landing);
        UUID commitId = UUID.fromString("2a832780-a27e-4cc8-98d0-6f7b3c188995");
        assertTrue(targetCapsule.reserveFuelTrip(commitId),
                "target recovery transaction did not reserve one bucket");
        var commitTicket = new first.wildfires.space.capsule.ReturnCapsuleTransitionTicket(
                commitId, stationId,
                first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Direction.TO_STATION,
                ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                Level.OVERWORLD.location(), landing, SpaceDimensions.ORBIT.location(), corePos,
                owner, targetCapsule.revision(), server.overworld().getGameTime(),
                first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.PREPARED);
        targetCapsule.setTransitionTicket(commitTicket);
        targetCapsule.setCapsuleState(
                first.wildfires.space.capsule.ReturnCapsuleState.ORBIT_INSERTION);
        assertTrue(first.wildfires.space.capsule.ReturnCapsuleService
                        .recoverTransaction(targetCapsule)
                        == first.wildfires.space.capsule.ReturnCapsuleService.ActionResult.RECOVERED,
                "target-side PREPARED recovery was rejected");
        int committedFuel = targetCapsule.fuelMb();
        assertTrue(targetCapsule.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.STATION_DOCKED
                        && committedFuel == ReturnCapsuleFuelTank.CAPACITY_MB
                        - ReturnCapsuleFuelTank.TRIP_COST_MB
                        && targetCapsule.fuelTank().reservation().isEmpty()
                        && targetCapsule.fuelTank().lastCommitted().filter(commitId::equals).isPresent()
                        && targetCapsule.transitionTicket().map(ticket -> ticket.stage()
                        == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.COMMITTED)
                        .orElse(false),
                "target recovery did not commit exactly one bucket and retain its replay fence");
        assertTrue(first.wildfires.space.capsule.ReturnCapsuleService
                        .recoverTransaction(targetCapsule)
                        == first.wildfires.space.capsule.ReturnCapsuleService.ActionResult.RECOVERED
                        && targetCapsule.fuelMb() == committedFuel
                        && targetCapsule.fuelTank().reservation().isEmpty(),
                "replayed destination recovery consumed a second bucket");

        assertTrue(StationService.setReturnCapsule(data, stationId, sourceCapsule.getUUID(), false,
                        server.overworld().getGameTime()).successful()
                        && StationService.setReturnCapsule(data, stationId, targetCapsule.getUUID(), false,
                        server.overworld().getGameTime()).successful(),
                "recovery fixtures could not release station ownership");
        sourceCapsule.discard();
        targetCapsule.discard();
        orbit.setChunkForced(orbitChunk.x, orbitChunk.z, false);
        helper.succeed();
    }

    @GameTest(template = "celestial_empty", batch = "space_capsule_passenger_fuse", timeoutTicks = 240)
    public static void reusableCapsuleNeverTeleportsASeparatedPassengerBack(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel surface = server.overworld();
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        assertTrue(orbit != null, "orbit level missing for passenger-fuse test");
        SpaceSavedData data = SpaceSavedData.get(server);
        UUID stationId = UUID.fromString("586bd01c-20d4-4986-97b6-f53e7a01fab8");
        UUID pilotId = UUID.fromString("a3068960-c5b1-4b91-882a-cfc17d32e556");
        if (data.station(stationId).isEmpty()) {
            assertTrue(StationService.create(data, stationId, "Passenger Fuse Station", pilotId,
                    ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                    CelestialRegistryRuntime.current(), server.overworld().getGameTime()).successful(),
                    "passenger-fuse station could not be created");
        }
        StationRecord station = data.station(stationId).orElseThrow();
        BlockPos core = station.primaryDock().position();
        orbit.getChunkAt(core);
        assertTrue(StationCoreService.ensureCore(server, station),
                "passenger-fuse station core is unavailable");
        var separatedPilot = FakePlayerFactory.get(orbit,
                new GameProfile(pilotId, "wildfires-separated-capsule-pilot"));
        orbit.addNewPlayer(separatedPilot);
        separatedPilot.moveTo(core.getX() + 8.5D, core.getY(), core.getZ() + 0.5D, 0.0F, 0.0F);
        Vec3 pilotPosition = separatedPilot.position();

        BlockPos landing = helper.absolutePos(new BlockPos(1, 2, 1));
        ReusableReturnCapsuleEntity capsule = new ReusableReturnCapsuleEntity(surface,
                landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D, pilotId);
        assertTrue(surface.addFreshEntity(capsule), "passenger-fuse capsule did not spawn");
        capsule.initializeFuelForTesting(ReturnCapsuleFuelTank.CAPACITY_MB);
        capsule.bindStation(stationId);
        capsule.setHomeSurface(Level.OVERWORLD.location(), landing);
        UUID ticketId = UUID.fromString("3faf692a-791d-4144-a760-431b045f20a1");
        assertTrue(capsule.reserveFuelTrip(ticketId), "passenger-fuse fuel was not reserved");
        capsule.setTransitionTicket(new first.wildfires.space.capsule.ReturnCapsuleTransitionTicket(
                ticketId, stationId,
                first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Direction.TO_STATION,
                ResourceLocation.fromNamespaceAndPath("wildfires", "earth"),
                Level.OVERWORLD.location(), landing, SpaceDimensions.ORBIT.location(), core,
                pilotId, capsule.revision(), server.overworld().getGameTime(),
                first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.PREPARED));
        capsule.setCapsuleState(first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LAUNCHING);

        first.wildfires.space.capsule.ReturnCapsuleService.tick(capsule);
        long fusedRevision = capsule.revision();
        first.wildfires.space.capsule.ReturnCapsuleService.tick(capsule);
        first.wildfires.space.capsule.ReturnCapsuleService.tick(capsule);
        assertTrue(capsule.capsuleState()
                        == first.wildfires.space.capsule.ReturnCapsuleState.RECOVERY_REQUIRED
                        && capsule.transitionTicket().filter(ticket -> ticket.stage()
                        == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.RECOVERY).isPresent()
                        && capsule.revision() == fusedRevision
                        && capsule.fuelMb() == ReturnCapsuleFuelTank.CAPACITY_MB
                        && capsule.fuelTank().reservation().filter(ticketId::equals).isPresent(),
                "separated-passenger transaction did not enter one terminal recovery fuse");
        assertTrue(separatedPilot.serverLevel() == orbit && separatedPilot.getVehicle() == null
                        && separatedPilot.position().distanceToSqr(pilotPosition) < 0.0001D,
                "recovery searched another dimension and teleported/remounted the separated pilot");
        capsule.discard();
        orbit.removePlayerImmediately(separatedPilot, Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    private static void awaitCapsuleDocking(GameTestHelper helper, ServerLevel surface,
                                            ServerLevel orbit, SpaceSavedData data, UUID stationId,
                                            UUID capsuleId, ServerPlayer player, BlockPos landingBase,
                                            ChunkPos orbitChunk, NtmCapsuleMotionAudit motionAudit,
                                            int attempts) {
        armWaitingSourceTransfer(surface, capsuleId, player,
                first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Direction.TO_STATION);
        Entity arrivedEntity = orbit.getEntity(capsuleId);
        if (arrivedEntity instanceof ReusableReturnCapsuleEntity observed) {
            motionAudit.observeApproach(observed);
        }
        if (arrivedEntity instanceof ReusableReturnCapsuleEntity transfer
                && transfer.transitionTicket().filter(ticket -> ticket.stage()
                == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING)
                .isPresent() && player.getVehicle() == transfer) {
            var ticket = transfer.transitionTicket().orElseThrow();
            first.wildfires.space.capsule.ReturnCapsuleService.confirmClientTracking(
                    player, ticket.ticketId(), transfer.getUUID());
            assertTrue(transfer.transitionTicket().filter(current -> current.stage()
                            == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.TRACKING_CONFIRMED)
                            .isPresent() && transfer.getDeltaMovement().lengthSqr() == 0.0D,
                    "tracking ACK alone released the destination flight before client-ready proof");
            first.wildfires.space.capsule.ReturnCapsuleService.confirmClientReady(
                    player, ticket.ticketId(), transfer.getUUID());
        }
        if (!(arrivedEntity instanceof ReusableReturnCapsuleEntity arrived)
                || arrived.capsuleState() != first.wildfires.space.capsule.ReturnCapsuleState.STATION_DOCKED) {
            // This budget covers NTM's altitude-driven surface launch, the client-ready barrier
            // and the 20-tick wait plus 0.4-block/tick inherited-motion final approach.
            if (attempts < 2200) {
                helper.runAfterDelay(1, () -> awaitCapsuleDocking(helper, surface, orbit, data,
                        stationId, capsuleId, player, landingBase, orbitChunk, motionAudit,
                        attempts + 1));
                return;
            }
            assertTrue(arrivedEntity instanceof ReusableReturnCapsuleEntity,
                    "capsule UUID was not preserved into orbit; orbitEntity=" + entityIdentity(arrivedEntity)
                            + "; surfaceEntity=" + entityIdentity(surface.getEntity(capsuleId))
                            + "; pilotLevel=" + player.serverLevel().dimension().location()
                            + "; pilotVehicle=" + entityIdentity(player.getVehicle()));
            ReusableReturnCapsuleEntity arrived = (ReusableReturnCapsuleEntity) arrivedEntity;
            throw new AssertionError(capsuleProgress("orbit", arrived, orbit,
                    first.wildfires.space.capsule.ReturnCapsuleState.STATION_DOCKED));
        }
        assertTrue(arrived.fuelMb() == 3_000,
                "orbit capsule fuel=" + arrived.fuelMb() + " mB, expected 3000 mB; ticket="
                        + arrived.transitionTicket());
        assertTrue(arrived.fuelTank().reservation().isEmpty(),
                "orbit capsule retained fuel reservation=" + arrived.fuelTank().reservation()
                        + "; ticket=" + arrived.transitionTicket());
        assertTrue(player.serverLevel() == orbit,
                "pilot level=" + player.serverLevel().dimension().location()
                        + ", expected " + orbit.dimension().location()
                        + "; vehicle=" + entityIdentity(player.getVehicle()));
        assertTrue(player.getVehicle() == arrived,
                "pilot vehicle=" + entityIdentity(player.getVehicle())
                        + ", expected capsule=" + entityIdentity(arrived)
                        + "; firstPassenger=" + entityIdentity(arrived.getFirstPassenger()));
        assertTrue(arrived.getFirstPassenger() == player,
                "orbit capsule firstPassenger=" + entityIdentity(arrived.getFirstPassenger())
                        + ", expected pilot=" + entityIdentity(player)
                        + "; pilotVehicle=" + entityIdentity(player.getVehicle()));
        motionAudit.assertApproachObserved();
        StationRecord dockedStation = data.station(stationId).orElseThrow();
        Vec3 expectedDock = first.wildfires.space.capsule.ReturnCapsuleService
                .stationDockedPosition(dockedStation.primaryDock().position());
        assertTrue(arrived.position().distanceTo(expectedDock) < 0.01D
                        && Math.abs(arrived.getBoundingBox().maxY
                        - (dockedStation.primaryDock().position().getY() + 1.5D)) < 0.001D,
                "return capsule did not use NTM's coreY + 1.5 - entityHeight docking anchor");
        assertTrue(dockedStation.ownedReturnCapsules().contains(capsuleId)
                        && first.wildfires.space.capsule.ReturnCapsuleService.allDocked(
                        orbit.getServer(), dockedStation),
                "docked capsule did not satisfy station departure interlock");
        var dockCore = orbit.getBlockEntity(dockedStation.primaryDock().position());
        assertTrue(dockCore instanceof first.wildfires.space.content.StationCoreBlockEntity core
                        && core.dockedCapsuleId().filter(capsuleId::equals).isPresent()
                        && arrived.dockLocked(),
                "NTM-style core/entity bidirectional dock lock was not committed");
        helper.runAfterDelay(2, () -> {
            Entity stableEntity = orbit.getEntity(capsuleId);
            assertTrue(stableEntity instanceof ReusableReturnCapsuleEntity stable
                            && stable.transitionTicket().isEmpty()
                            && first.wildfires.space.capsule.ReturnCapsuleService
                            .requestPrimaryAction(player, stable)
                            == first.wildfires.space.capsule.ReturnCapsuleService.ActionResult.BUSY
                            && stable.fuelMb() == 3_000
                            && stable.fuelTank().reservation().isEmpty()
                            && stable.capsuleState()
                            == first.wildfires.space.capsule.ReturnCapsuleState.STATION_DOCKED,
                    "arrival press replay automatically started the reverse trip");
            ReusableReturnCapsuleEntity stable = (ReusableReturnCapsuleEntity) stableEntity;
            first.wildfires.space.capsule.ReturnCapsuleService.releasePrimaryAction(player, stable);
            helper.runAfterDelay(2, () -> {
                Entity armedEntity = orbit.getEntity(capsuleId);
                assertTrue(armedEntity instanceof ReusableReturnCapsuleEntity,
                        "docked return capsule disappeared before the requested station undock");
                ReusableReturnCapsuleEntity armed = (ReusableReturnCapsuleEntity) armedEntity;
                assertTrue(first.wildfires.space.capsule.ReturnCapsuleService
                                .requestPrimaryAction(player, armed).successful(),
                        "release/fresh spacebar press did not start the requested station undock");
                double beforeUndockY = armed.getY();
                first.wildfires.space.capsule.ReturnCapsuleService.tick(armed);
                assertTrue(armed.capsuleState()
                                == first.wildfires.space.capsule.ReturnCapsuleState.STATION_UNDOCKING
                                && armed.phaseTicks() == 1
                                && Math.abs(armed.getY() - (beforeUndockY - 0.4D)) < 1.0E-9D
                                && Math.abs(armed.flightVelocity() + 0.1D) < 1.0E-6D,
                        "one authoritative undock tick did not apply NTM -0.1 velocity times motionMult 4");
                helper.runAfterDelay(20, () -> {
                    Entity departingEntity = orbit.getEntity(capsuleId);
                    assertTrue(departingEntity instanceof ReusableReturnCapsuleEntity departing
                                    && departing.getY() < expectedDock.y,
                            "station undocking did not move downward from the bottom port");
                });
                helper.runAfterDelay(800, () -> awaitCapsuleLanding(helper, surface, orbit, data,
                        stationId, capsuleId, player, landingBase, orbitChunk, motionAudit, 0));
            });
        });
    }

    private static void awaitCapsuleLanding(GameTestHelper helper, ServerLevel surface,
                                            ServerLevel orbit, SpaceSavedData data, UUID stationId,
                                            UUID capsuleId, ServerPlayer player, BlockPos landingBase,
                                            ChunkPos orbitChunk, NtmCapsuleMotionAudit motionAudit,
                                            int attempts) {
        armWaitingSourceTransfer(orbit, capsuleId, player,
                first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Direction.TO_SURFACE);
        Entity landedEntity = surface.getEntity(capsuleId);
        if (landedEntity instanceof ReusableReturnCapsuleEntity transfer
                && transfer.transitionTicket().filter(ticket -> ticket.stage()
                == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING)
                .isPresent() && player.getVehicle() == transfer) {
            var ticket = transfer.transitionTicket().orElseThrow();
            first.wildfires.space.capsule.ReturnCapsuleService.confirmClientTracking(
                    player, ticket.ticketId(), transfer.getUUID());
            assertTrue(transfer.transitionTicket().filter(current -> current.stage()
                            == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.TRACKING_CONFIRMED)
                            .isPresent() && transfer.getDeltaMovement().lengthSqr() == 0.0D,
                    "tracking ACK alone released reentry before client-ready proof");
            first.wildfires.space.capsule.ReturnCapsuleService.confirmClientReady(
                    player, ticket.ticketId(), transfer.getUUID());
        }
        if (landedEntity instanceof ReusableReturnCapsuleEntity observed) {
            motionAudit.observeLanding(observed, surface, landingBase);
        }
        if (!(landedEntity instanceof ReusableReturnCapsuleEntity landed)
                || landed.capsuleState()
                != first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LANDED) {
            // The reusable-pod law preserves NTM's velocity curve and fourfold inherited motion:
            // -1 block/tick aloft, then a long proportional flare with a -0.005 minimum sink rate.
            if (attempts < 1700) {
                helper.runAfterDelay(1, () -> awaitCapsuleLanding(helper, surface, orbit, data,
                        stationId, capsuleId, player, landingBase, orbitChunk, motionAudit,
                        attempts + 1));
                return;
            }
            assertTrue(landedEntity instanceof ReusableReturnCapsuleEntity,
                    "capsule UUID was not preserved back to the surface; entity=" + entityIdentity(landedEntity));
            ReusableReturnCapsuleEntity landed = (ReusableReturnCapsuleEntity) landedEntity;
            throw new AssertionError(capsuleProgress("surface", landed, surface,
                    first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LANDED));
        }
        assertTrue(landed.fuelMb() == 2_000,
                "surface capsule fuel=" + landed.fuelMb() + " mB, expected 2000 mB; ticket="
                        + landed.transitionTicket());
        assertTrue(landed.fuelTank().reservation().isEmpty(),
                "surface capsule retained fuel reservation=" + landed.fuelTank().reservation()
                        + "; ticket=" + landed.transitionTicket());
        assertTrue(player.serverLevel() == surface,
                "pilot level=" + player.serverLevel().dimension().location()
                        + ", expected " + surface.dimension().location()
                        + "; vehicle=" + entityIdentity(player.getVehicle()));
        assertTrue(player.getVehicle() == landed,
                "pilot vehicle=" + entityIdentity(player.getVehicle())
                        + ", expected capsule=" + entityIdentity(landed)
                        + "; firstPassenger=" + entityIdentity(landed.getFirstPassenger()));
        assertTrue(landed.getFirstPassenger() == player,
                "surface capsule firstPassenger=" + entityIdentity(landed.getFirstPassenger())
                        + ", expected pilot=" + entityIdentity(player)
                        + "; pilotVehicle=" + entityIdentity(player.getVehicle()));
        motionAudit.assertLandingObserved();
        assertTrue(landed.position().distanceTo(new net.minecraft.world.phys.Vec3(
                        landingBase.getX() + 0.5D, landingBase.getY(), landingBase.getZ() + 0.5D)) < 0.01D,
                "return capsule did not land on its persisted surface endpoint");
        assertTrue(!first.wildfires.space.capsule.ReturnCapsuleService.allDocked(
                        surface.getServer(), data.station(stationId).orElseThrow()),
                "surface capsule incorrectly passed the station departure interlock");
        helper.runAfterDelay(2, () -> {
            Entity stableEntity = surface.getEntity(capsuleId);
            assertTrue(stableEntity instanceof ReusableReturnCapsuleEntity,
                    "surface capsule disappeared before the post-landing input audit");
            ReusableReturnCapsuleEntity stable = (ReusableReturnCapsuleEntity) stableEntity;
            assertTrue(stable.transitionTicket().isEmpty()
                            && first.wildfires.space.capsule.ReturnCapsuleService
                            .requestPrimaryAction(player, stable)
                            == first.wildfires.space.capsule.ReturnCapsuleService.ActionResult.BUSY
                            && stable.fuelMb() == 2_000
                            && stable.fuelTank().reservation().isEmpty()
                            && stable.capsuleState()
                            == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LANDED,
                    "landing press replay skipped NTM LANDED/open-door state or started another ascent");
            first.wildfires.space.capsule.ReturnCapsuleService.releasePrimaryAction(player, stable);
            assertTrue(first.wildfires.space.capsule.ReturnCapsuleService
                            .requestPrimaryAction(player, stable).successful()
                            && stable.capsuleState()
                            == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_CLOSING
                            && stable.transitionTicket().isEmpty()
                            && stable.fuelMb() == 2_000,
                    "fresh input did not reactivate the internal tape as a door-close-only edge");
            helper.runAfterDelay(2, () -> {
                StationService.setReturnCapsule(data, stationId, capsuleId, false,
                        surface.getServer().overworld().getGameTime());
                stableEntity.discard();
                surface.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                orbit.setChunkForced(orbitChunk.x, orbitChunk.z, false);
                helper.succeed();
            });
        });
    }

    /** Locks the real server entity to one upstream NTM movement integration per game tick. */
    private static final class NtmCapsuleMotionAudit {
        private first.wildfires.space.capsule.ReturnCapsuleState previousApproachState;
        private double previousApproachY;
        private int previousApproachTicks = -1;
        private int approachMovingSteps;
        private int approachWaitingSteps;
        private first.wildfires.space.capsule.ReturnCapsuleState previousLandingState;
        private double previousLandingY;
        private int previousLandingTicks = -1;
        private int landingSteps;

        void observeApproach(ReusableReturnCapsuleEntity capsule) {
            var state = capsule.capsuleState();
            int ticks = capsule.phaseTicks();
            if (state == first.wildfires.space.capsule.ReturnCapsuleState.STATION_APPROACH
                    && previousApproachState == state && ticks == previousApproachTicks + 1) {
                double delta = capsule.getY() - previousApproachY;
                if (ticks <= 20) {
                    assertTrue(Math.abs(delta) < 1.0E-9D,
                            "NTM's 20-tick docking-port wait moved the capsule by " + delta);
                    approachWaitingSteps++;
                } else {
                    assertTrue(Math.abs(delta - 0.4D) < 1.0E-9D
                                    && Math.abs(capsule.flightVelocity() - 0.1D) < 1.0E-6D,
                            "one server approach tick moved " + delta
                                    + " blocks instead of NTM +0.1 velocity times motionMult 4");
                    approachMovingSteps++;
                }
            }
            previousApproachState = state;
            previousApproachY = capsule.getY();
            previousApproachTicks = ticks;
        }

        void observeLanding(ReusableReturnCapsuleEntity capsule, ServerLevel surface,
                            BlockPos landingBase) {
            var state = capsule.capsuleState();
            int ticks = capsule.phaseTicks();
            boolean moving = state == first.wildfires.space.capsule.ReturnCapsuleState.REENTRY
                    || state == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LANDING;
            boolean previousMoving = previousLandingState
                    == first.wildfires.space.capsule.ReturnCapsuleState.REENTRY
                    || previousLandingState
                    == first.wildfires.space.capsule.ReturnCapsuleState.SURFACE_LANDING;
            if (moving && previousMoving && state == previousLandingState
                    && ticks == previousLandingTicks + 1) {
                int targetHeight = surface.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        landingBase.getX(), landingBase.getZ());
                double expected = net.minecraft.util.Mth.clamp(
                        (targetHeight - previousLandingY) * 0.01D, -1.0D, -0.005D);
                double delta = capsule.getY() - previousLandingY;
                assertTrue(Math.abs(delta - expected
                                * first.wildfires.space.capsule.ReturnCapsuleService.NTM_MOTION_MULTIPLIER) < 1.0E-8D
                                && Math.abs(capsule.flightVelocity() - expected) < 1.0E-6D,
                        "one server landing tick moved " + delta + " with velocity "
                                + capsule.flightVelocity() + ", expected velocity " + expected
                                + " times inherited motion multiplier");
                landingSteps++;
            }
            previousLandingState = state;
            previousLandingY = capsule.getY();
            previousLandingTicks = ticks;
        }

        void assertApproachObserved() {
            assertTrue(approachWaitingSteps > 0 && approachMovingSteps > 40,
                    "round-trip test did not observe enough NTM docking wait/movement ticks: wait="
                            + approachWaitingSteps + ", moving=" + approachMovingSteps);
        }

        void assertLandingObserved() {
            assertTrue(landingSteps > 100,
                    "round-trip test did not observe enough NTM terrain-distance landing ticks: "
                            + landingSteps);
        }
    }

    private static void armWaitingSourceTransfer(
            ServerLevel source, UUID capsuleId, ServerPlayer player,
            first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Direction direction) {
        Entity sourceEntity = source.getEntity(capsuleId);
        if (!(sourceEntity instanceof ReusableReturnCapsuleEntity capsule)) return;
        var ticket = capsule.transitionTicket().orElse(null);
        if (ticket == null
                || ticket.stage() != first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.PREPARED
                || ticket.direction() != direction) return;
        boolean atBoundary = direction
                == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Direction.TO_STATION
                ? capsule.capsuleState()
                == first.wildfires.space.capsule.ReturnCapsuleState.ASCENT_TRANSITION
                : capsule.capsuleState() == first.wildfires.space.capsule.ReturnCapsuleState.DEORBIT;
        if (!atBoundary) return;

        Vec3 beforePosition = capsule.position();
        int beforeFuel = capsule.fuelMb();
        UUID wrongTicket = UUID.fromString("a4a32781-e0e0-4e40-a8b0-f79e18bd8120");
        UUID wrongCapsule = UUID.fromString("d503a672-04cc-4d2c-882a-e13b78ac1190");
        first.wildfires.space.capsule.ReturnCapsuleService.confirmClientArmed(
                player, wrongTicket, capsuleId);
        first.wildfires.space.capsule.ReturnCapsuleService.confirmClientArmed(
                player, ticket.ticketId(), wrongCapsule);
        first.wildfires.space.capsule.ReturnCapsuleService.confirmClientTracking(
                player, ticket.ticketId(), capsuleId);
        assertTrue(capsule.transitionTicket().filter(current -> current.stage()
                        == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.PREPARED)
                        .isPresent()
                        && player.serverLevel() == source && player.getVehicle() == capsule
                        && capsule.position().distanceToSqr(beforePosition) < 0.0001D
                        && capsule.fuelMb() == beforeFuel
                        && capsule.fuelTank().reservation().filter(ticket.ticketId()::equals).isPresent(),
                "wrong/early source ACK changed dimension, mount, position, fuel or ticket stage");

        first.wildfires.space.capsule.ReturnCapsuleService.confirmClientArmed(
                player, ticket.ticketId(), capsuleId);
        first.wildfires.space.capsule.ReturnCapsuleService.confirmClientArmed(
                player, ticket.ticketId(), capsuleId);
        assertTrue(capsule.transitionTicket().filter(current -> current.stage()
                        == first.wildfires.space.capsule.ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED)
                        .isPresent()
                        && player.serverLevel() == source && player.getVehicle() == capsule
                        && capsule.position().distanceToSqr(beforePosition) < 0.0001D
                        && capsule.fuelMb() == beforeFuel
                        && capsule.fuelTank().reservation().filter(ticket.ticketId()::equals).isPresent(),
                "valid/idempotent source ACK transferred or mutated the source transaction early");
    }

    /** Keeps repeated GameTest runs isolated after an earlier process stopped mid-round-trip. */
    private static boolean clearFixtureDockClaim(
            first.wildfires.space.content.StationCoreBlockEntity core) {
        return core.claimedCapsuleId().map(core::releaseDockLock).orElse(true);
    }

    private static String capsuleProgress(String label, ReusableReturnCapsuleEntity capsule,
                                          ServerLevel level,
                                          first.wildfires.space.capsule.ReturnCapsuleState expected) {
        return label + " capsule state=" + capsule.capsuleState() + ", expected " + expected
                + "; phaseTicks=" + capsule.phaseTicks() + ", tickCount=" + capsule.tickCount
                + ", pos=" + capsule.position() + ", chunk=" + capsule.chunkPosition()
                + ", entityTicking=" + level.getChunkSource().isPositionTicking(
                capsule.chunkPosition().toLong()) + "; ticket=" + capsule.transitionTicket();
    }

    private static String entityIdentity(Entity entity) {
        if (entity == null) return "null";
        return entity.getType() + "[uuid=" + entity.getUUID() + ",id=" + entity.getId()
                + ",level=" + entity.level().dimension().location() + "]";
    }

    private static void awaitTravelReady(GameTestHelper helper, ServerLevel orbit, UUID stationId,
                                         UUID owner, int attempts) {
        StationRecord station = SpaceSavedData.get(orbit.getServer()).station(stationId).orElseThrow();
        if (station.journey().isPresent()) {
            if (attempts >= 60) {
                throw new AssertionError("pre-existing P5 journey did not finish in time");
            }
            helper.runAfterDelay(20, () -> awaitTravelReady(helper, orbit, stationId, owner, attempts + 1));
            return;
        }
        assertTrue(station.status() == StationStatus.ACTIVE,
                "P5 travel station is not active: " + station.status());
        beginTravelAcceptance(helper, orbit, station, owner);
    }

    private static void beginTravelAcceptance(GameTestHelper helper, ServerLevel orbit,
                                              StationRecord station, UUID owner) {
        BlockPos computerPos = station.primaryDock().position().east(3);
        BlockPos enginePos = computerPos.east();
        BlockPos jumpEnginePos = computerPos.west();
        ChunkPos stationChunk = new ChunkPos(computerPos);
        orbit.setChunkForced(stationChunk.x, stationChunk.z, true);
        orbit.getChunkAt(computerPos);
        orbit.setBlockAndUpdate(computerPos, SpaceContentRegister.STATION_CONTROL_COMPUTER.get()
                .defaultBlockState());
        orbit.setBlockAndUpdate(enginePos, Blocks.AIR.defaultBlockState());
        orbit.setBlockAndUpdate(jumpEnginePos, Blocks.AIR.defaultBlockState());
        assertTrue(StationCoreService.ensureCore(orbit.getServer(), station),
                "station core structure was not complete before travel acceptance");
        assertTrue(orbit.getBlockEntity(computerPos) != null,
                "station control computer block entity was not created");

        ResourceLocation moon = ResourceLocation.fromNamespaceAndPath("wildfires", "moon");
        ResourceLocation europa = ResourceLocation.fromNamespaceAndPath("wildfires", "europa");
        ResourceLocation target = station.currentBody().equals(moon) ? europa : moon;
        ResourceLocation routeId = first.wildfires.space.route.StationRouteDefinition.freeTransferId(
                station.currentBody(), target);
        var generatedRoutes = StationRouteRuntime.current().routesFrom(station.currentBody());
        assertTrue(generatedRoutes.stream().anyMatch(route -> route.id().equals(routeId)
                        && route.connects(station.currentBody(), target)),
                "stable orbit did not generate a server-authoritative route to " + target);
        StationTravelRequest request = new StationTravelRequest(computerPos, station.stationId(),
                station.revision(), routeId);
        StationTravelService.ValidationContext validation = new StationTravelService.ValidationContext() {
            @Override
            public boolean allReturnCapsulesDocked(StationRecord current) {
                return current.ownedReturnCapsules().isEmpty();
            }

            @Override
            public boolean validControlComputer(StationRecord current, StationTravelRequest intent) {
                return orbit.getBlockState(intent.computerPos())
                        .is(SpaceContentRegister.STATION_CONTROL_COMPUTER.get())
                        && current.region().containsBuildArea(intent.computerPos());
            }

            @Override
            public boolean hasLoadedTestEngine(StationRecord current) {
                return StationDriveIndex.hasLoadedEngine(orbit, current);
            }

            @Override
            public boolean hasLoadedJumpTestEngine(StationRecord current) {
                return StationJumpDriveIndex.hasLoadedEngine(orbit, current);
            }
        };
        SpaceSavedData data = SpaceSavedData.get(orbit.getServer());
        StationTravelResult withoutEngine = StationTravelService.start(data, owner, request,
                StationRouteRuntime.current(), CelestialRegistryRuntime.current(), validation,
                orbit.getServer().overworld().getGameTime(), UUID.randomUUID());
        assertTrue(withoutEngine.status() == StationTravelResult.Status.NO_TEST_ENGINE
                        && data.station(station.stationId()).orElseThrow().revision() == station.revision(),
                "missing test engine did not reject atomically: " + withoutEngine.status());

        orbit.setBlockAndUpdate(enginePos, SpaceContentRegister.STATION_TEST_ENGINE.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            StationRecord before = data.station(station.stationId()).orElseThrow();
            var engine = orbit.getBlockEntity(enginePos);
            boolean indexed = StationDriveIndex.hasLoadedEngine(orbit, before);
            assertTrue(engine != null && indexed,
                    "placed test engine was not registered as loaded station drive"
                            + "; engine=" + (engine == null ? "null" : engine.getClass().getName())
                            + "; engineLevel=" + (engine == null ? "null" : engine.getLevel())
                            + "; stationAt=" + data.stationAt(enginePos.getX(), enginePos.getZ())
                            .map(value -> value.stationId().toString()).orElse("none")
                            + "; expected=" + before.stationId()
                            + "; dimension=" + orbit.dimension().location());
            StationTravelRequest currentRequest = new StationTravelRequest(computerPos,
                    before.stationId(), before.revision(), routeId);
            StationTravelResult started = StationTravelService.start(data, owner, currentRequest,
                    StationRouteRuntime.current(), CelestialRegistryRuntime.current(), validation,
                    orbit.getServer().overworld().getGameTime(), UUID.randomUUID());
            assertTrue(started.successful()
                            && started.station().orElseThrow().journey().isPresent(),
                    "loaded test engine did not allow departure: " + started.status());
            long startedRevision = started.station().orElseThrow().revision();
            helper.runAfterDelay(1005, () -> {
                StationRecord arrived = data.station(station.stationId()).orElseThrow();
                assertTrue(arrived.currentBody().equals(target)
                                && arrived.journey().isEmpty()
                                && arrived.status() == StationStatus.ACTIVE
                                && arrived.revision() == startedRevision + 3L,
                        "server game-time ticker did not complete the fixed route exactly");
                ResourceLocation jumpTarget = ResourceLocation.fromNamespaceAndPath("wildfires", "mars");
                ResourceLocation jumpRouteId = first.wildfires.space.route.StationRouteDefinition
                        .freeTransferId(arrived.currentBody(), jumpTarget);
                StationTravelRequest jumpRequest = new StationTravelRequest(computerPos,
                        arrived.stationId(), arrived.revision(), jumpRouteId, StationTravelMode.JUMP);
                StationTravelResult missingJumpEngine = StationTravelService.start(data, owner, jumpRequest,
                        StationRouteRuntime.current(), CelestialRegistryRuntime.current(), validation,
                        orbit.getServer().overworld().getGameTime(), UUID.randomUUID());
                assertTrue(missingJumpEngine.status() == StationTravelResult.Status.NO_JUMP_TEST_ENGINE
                                && data.station(arrived.stationId()).orElseThrow().revision() == arrived.revision(),
                        "missing loaded jump engine did not reject atomically: " + missingJumpEngine.status());

                orbit.setBlockAndUpdate(jumpEnginePos,
                        SpaceContentRegister.STATION_JUMP_TEST_ENGINE.get().defaultBlockState());
                helper.runAfterDelay(2, () -> {
                    StationRecord beforeJump = data.station(arrived.stationId()).orElseThrow();
                    assertTrue(orbit.getBlockEntity(jumpEnginePos) != null
                                    && StationDriveIndex.hasLoadedEngine(orbit, beforeJump)
                                    && StationJumpDriveIndex.hasLoadedEngine(orbit, beforeJump),
                            "both ordinary and jump engines must be loaded before a jump");
                    StationTravelRequest currentJumpRequest = new StationTravelRequest(computerPos,
                            beforeJump.stationId(), beforeJump.revision(), jumpRouteId, StationTravelMode.JUMP);
                    StationTravelResult jumpStarted = StationTravelService.start(data, owner, currentJumpRequest,
                            StationRouteRuntime.current(), CelestialRegistryRuntime.current(), validation,
                            orbit.getServer().overworld().getGameTime(), UUID.randomUUID());
                    assertTrue(jumpStarted.successful()
                                    && jumpStarted.station().orElseThrow().journey().orElseThrow().mode()
                                    == StationTravelMode.JUMP,
                            "loaded dual engines did not allow a server-authoritative jump: "
                                    + jumpStarted.status());
                    long jumpStartedRevision = jumpStarted.station().orElseThrow().revision();
                    helper.runAfterDelay(685, () -> {
                        StationRecord jumpArrived = data.station(arrived.stationId()).orElseThrow();
                        assertTrue(jumpArrived.currentBody().equals(jumpTarget)
                                        && jumpArrived.journey().isEmpty()
                                        && jumpArrived.status() == StationStatus.ACTIVE
                                        && jumpArrived.revision() == jumpStartedRevision + 5L,
                                "server ticker did not complete the 3s/8s/3s jump and normal arrival exactly");
                        orbit.setBlockAndUpdate(enginePos, Blocks.AIR.defaultBlockState());
                        orbit.setBlockAndUpdate(jumpEnginePos, Blocks.AIR.defaultBlockState());
                        helper.runAfterDelay(2, () -> {
                            assertTrue(!StationDriveIndex.hasLoadedEngine(orbit, jumpArrived)
                                            && !StationJumpDriveIndex.hasLoadedEngine(orbit, jumpArrived),
                                    "removed test engines remained in a loaded-drive index");
                            orbit.setChunkForced(stationChunk.x, stationChunk.z, false);
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    private static void prepareTemplate(ServerLevel level) {
        level.getStructureManager().getOrCreate(EMPTY_TEMPLATE).fillFromWorld(level,
                new BlockPos(0, 200, 0), new Vec3i(3, 3, 3), false, Blocks.STRUCTURE_VOID);
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
        verifyOnlyEarthBindsOverworld(CelestialRegistryRuntime.current());
    }

    private static void verifyExistingEphemerisAdapter(ServerLevel level, BlockPos position) {
        CelestialState directState = OverworldCelestialProvider.INSTANCE.state(
                level, position.getCenter(), 0.0F);
        CelestialState adaptedState = ExistingCelestialEphemeris.INSTANCE.state(
                level, position.getCenter(), 0.0F);
        assertTrue(directState.equals(adaptedState),
                "existing ephemeris adapter changed the authoritative overworld CelestialState");
    }

    private static void verifyOnlyEarthBindsOverworld(CelestialRegistrySnapshot snapshot) {
        ResourceLocation earthId = ResourceLocation.fromNamespaceAndPath("wildfires", "earth");
        ResourceLocation overworldId = Level.OVERWORLD.location();
        assertTrue(snapshot.generation() > 0L && snapshot.validation().definitions().size() == 20,
                "synchronized celestial registry did not load all 20 built-in definitions");
        long boundCount = snapshot.validation().definitions().values().stream()
                .filter(definition -> definition.surfaceDimension().isPresent())
                .count();
        assertTrue(boundCount == 1L
                        && snapshot.validation().definitions().get(earthId)
                        .surfaceDimension().orElseThrow().equals(overworldId),
                "Earth was not the unique built-in minecraft:overworld surface binding");
        assertTrue(snapshot.lookup(snapshot.generation(), earthId).status()
                        == CelestialRegistrySnapshot.LookupStatus.PRESENT
                        && snapshot.validation().get(earthId).orElseThrow().landingAvailable(),
                "resolved Earth binding was unavailable for landing");
        snapshot.validation().resolved().forEach((id, definition) -> {
            if (!id.equals(earthId)) {
                assertTrue(definition.surfaceDimension().isEmpty() && !definition.landingAvailable(),
                        "non-Earth celestial acquired a surface binding: " + id);
            }
        });
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

    private static long findInactiveBloodMoonTicks(ServerLevel level, BlockPos position, long startTick) {
        int daysInMonth = Calendars.SERVER.getCalendarDaysInMonth();
        CelestialRuntimeSettings settings = CelestialConfig.serverSettings();
        double scale = TfeHemisphereScale.get(level);
        long step = (long) CelestialMath.TICKS_IN_DAY / 4L;
        long limit = (long) Math.ceil(settings.resolvedSynodicDays(daysInMonth) * 2.0D
                * CelestialMath.TICKS_IN_DAY);
        for (long offset = 0L; offset <= limit; offset += step) {
            long tick = startTick + offset;
            CelestialMath.Result result = celestialResultAt(position, scale, tick, daysInMonth, settings);
            if (result.bloodMoon() <= CelestialGameplayRules.ACTIVE_THRESHOLD
                    || result.moonElevation() <= 0.0D) {
                return tick;
            }
        }
        throw new AssertionError("no inactive blood-moon control time found across two synodic cycles");
    }

    private static long findVisibleBloodMoonTicks(ServerLevel level, BlockPos position, long startTick) {
        int daysInMonth = Calendars.SERVER.getCalendarDaysInMonth();
        CelestialRuntimeSettings settings = CelestialConfig.serverSettings();
        double maximumDays = CelestialMath.daysInYear(daysInMonth) * settings.nodalYears();
        double scale = TfeHemisphereScale.get(level);
        long maximumTicks = (long) Math.ceil(maximumDays * CelestialMath.TICKS_IN_DAY);
        for (long offset = 0L; offset < maximumTicks; offset += 120L) {
            long tick = startTick + offset;
            CelestialMath.Result result = celestialResultAt(position, scale, tick, daysInMonth, settings);
            if (result.bloodMoon() > CelestialGameplayRules.ACTIVE_THRESHOLD
                    && result.moonElevation() > 0.1D && result.solarElevation() <= 0.0D) {
                return tick;
            }
        }
        throw new AssertionError("no visible blood moon found across one configured nodal cycle");
    }

    private static long findVisiblePenumbralLunarEclipseTicks(ServerLevel level, BlockPos position,
                                                               long startTick) {
        int daysInMonth = Calendars.SERVER.getCalendarDaysInMonth();
        CelestialRuntimeSettings settings = CelestialConfig.serverSettings();
        double scale = TfeHemisphereScale.get(level);
        double synodicDays = settings.resolvedSynodicDays(daysInMonth);
        long firstIndex = (long) Math.ceil((double) startTick
                / (CelestialMath.TICKS_IN_DAY * synodicDays));
        long fullMoons = (long) Math.ceil(CelestialMath.daysInYear(daysInMonth)
                * settings.nodalYears() / synodicDays);
        for (long offsetIndex = 0L; offsetIndex <= fullMoons; offsetIndex++) {
            long centerTick = Math.round((firstIndex + offsetIndex) * synodicDays
                    * CelestialMath.TICKS_IN_DAY);
            for (long offset = -9_000L; offset <= 9_000L; offset += 120L) {
                long tick = centerTick + offset;
                CelestialMath.Result result = celestialResultAt(position, scale, tick,
                        daysInMonth, settings);
                if (result.lunarEclipseRegion().penumbralOnly()
                        && result.moonElevation() > 0.1D && result.solarElevation() <= 0.0D) {
                    return tick;
                }
            }
        }
        throw new AssertionError("no visible penumbral lunar eclipse found across one nodal cycle");
    }

    private static CelestialMath.Result celestialResultAt(BlockPos position, double scale, long calendarTick,
                                                           int daysInMonth,
                                                           CelestialRuntimeSettings settings) {
        return CelestialMath.calculate(new CelestialMath.Input(position.getZ(), scale, calendarTick, daysInMonth,
                settings.resolvedSynodicDays(daysInMonth), settings.resolvedAnomalisticDays(daysInMonth),
                settings.nodalYears(), settings.lunarInclinationRadians(),
                settings.sunScale(), settings.moonScale()));
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
                settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale())).solarElevation();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

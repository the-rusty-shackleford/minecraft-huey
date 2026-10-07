/*
 * The Huey - a Bell UH-1H for Rotorcraft.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.huey.gametest;

import com.chunkworks.rotorcraft.Aircraft;
import com.chunkworks.rotorcraft.RotorcraftContent;
import com.chunkworks.rotorcraft.SlungLoad;
import com.chunkworks.rotorcraft.client.RotorcraftKeys;
import com.chunkworks.rotorcraft.domain.FlightInput;
import com.chunkworks.vanillawheels.Vehicle;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Huey on film, in a flat world at noon: parked, from the front quarter, the left side and the
 * rear quarter; dyed red; its open cabin with eight aboard; the pilot's own view; hovering with its
 * rotors spun up, seen from the ground; the Sling Container hanging under it; and the crop sprayer
 * over a wheat field, flown by the booth's own player with the keys held, seen from behind. One
 * {@code booth: PASS} or {@code booth: FAIL} line per check; the Gradle task reads them. Client
 * only, active only under {@code huey.photobooth}.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class HueyBooth {
    private HueyBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Huey booth");
    private static final boolean ACTIVE = Boolean.getBoolean("huey.photobooth");
    private static final ResourceLocation HUEY = ResourceLocation.fromNamespaceAndPath("huey", "huey");
    private static final ResourceLocation CONTAINER = ResourceLocation.fromNamespaceAndPath("rotorcraft", "sling_container");

    private enum Phase { TITLE, LOADING, PLACING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    private static final int HOLD = 100;
    private static final int SETTLE = 60;
    /** Where the Huey stands (its mast), facing east; the field lies east of it. */
    private static final double HX = 0.5;
    private static final double HZ = 30.5;
    private static final float EAST = -90.0f;

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static UUID huey;
    private static UUID box;
    private static double ground;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!muted) {
            // Silent from the first tick, before the title music: Rusty listens to music while these run.
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            muted = true;
        }
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null
                        && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.PLACING;
                    mc.options.hideGui = true;
                    steps = plan(mc);
                    onServer(mc, HueyBooth::setUp);
                }
            }
            case PLACING -> {
                if (mc.player != null && client(mc, huey) != null) {
                    phase = Phase.RUNNING;
                    tick = 0;
                }
            }
            case RUNNING -> {
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        LevelSettings settings = new LevelSettings("Huey booth", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("huey-booth", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    /** Noon; the Huey parked facing east; a wheat field laid east of it for the sprayer. */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000L);
        ground = level.getMinBuildHeight() + 4;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        sp.teleportTo(level, HX + 9, ground + 3, HZ - 12, 30.0f, 10.0f);
        // A field of young wheat, 20 by 14, from 14 blocks east of the mast.
        for (int x = 14; x < 34; x++) {
            for (int z = -7; z < 7; z++) {
                BlockPos soil = BlockPos.containing(HX + x, ground - 1, HZ + z);
                level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState());
                level.setBlockAndUpdate(soil.above(), Blocks.WHEAT.defaultBlockState());
            }
        }
        huey = spawnHuey(level).getUUID();
    }

    private static Aircraft spawnHuey(ServerLevel level) {
        Vehicle v = Vehicle.create(level, HUEY, new Vec3(HX, ground, HZ), EAST);
        if (!(v instanceof Aircraft a)) {
            LOG.error("booth: FAIL the Huey is an aircraft -- {}", v);
            throw new IllegalStateException("no Huey");
        }
        a.setFuel(a.tank().capacity());
        level.addFreshEntity(a);
        return a;
    }

    /** effects: puts the booth's player at {@code from}, looking at {@code at} */
    private static void aim(ServerPlayer sp, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        sp.teleportTo(sp.serverLevel(), from.x, from.y - sp.getEyeHeight(), from.z, yaw, pitch);
    }

    /** effects: returns the middle of the Huey's hull, a block and a half up: where a camera aims (the mast is 2.7 ahead of it) */
    private static Vec3 middle(Aircraft a) {
        return a.position().add(a.rotate(new com.chunkworks.vanillawheels.domain.Vec(0.0, 1.5, -2.7)));
    }

    /** effects: aims the booth's player from {@code offset} (blocks, the world's axes) off the Huey's middle at it */
    private static void shot(ServerPlayer sp, double dx, double dy, double dz) {
        withHuey(sp, a -> {
            Vec3 m = middle(a);
            aim(sp, m.add(dx, dy, dz), m);
        });
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int t = HOLD;
        // --- parked: the front quarter, the side, the rear quarter --------------------------------
        s.add(new Step(t - SETTLE, () -> onServer(mc, sp -> shot(sp, 9.5, 3.5, -9.5))));
        s.add(new Step(t, () -> {
            int olive = count(mc, HueyBooth::olive);
            shoot(mc, "booth-threequarter");
            verdict("the stock Huey shows olive drab", () -> olive > 20000 ? null : "olive pixels " + olive);
            onServer(mc, sp -> shot(sp, 0.0, 1.0, -15.0));
        }));
        s.add(new Step(t += SETTLE, () -> {
            shoot(mc, "booth-side");
            onServer(mc, sp -> shot(sp, -11.0, 4.0, 9.0));
        }));
        s.add(new Step(t += SETTLE, () -> {
            shoot(mc, "booth-rear");
            onServer(mc, sp -> {
                withHuey(sp, a -> a.setPaint(DyeColor.RED));
                shot(sp, 9.5, 3.5, -9.5);
            });
        }));
        s.add(new Step(t += SETTLE, () -> {
            int red = count(mc, HueyBooth::redPaint);
            shoot(mc, "booth-red");
            verdict("dye paints it red", () -> red > 12000 ? null : "red pixels " + red);
            // The tail from its left and behind: the fin, the tail rotor on its left, the elevator.
            onServer(mc, sp -> withHuey(sp, a -> {
                Vec3 fin = a.position().add(a.rotate(new com.chunkworks.vanillawheels.domain.Vec(0.0, 2.3, -8.6)));
                aim(sp, fin.add(-3.5, 0.8, -5.5), fin.add(0.0, -0.3, 0.0));
            }));
        }));
        s.add(new Step(t += SETTLE, () -> {
            shoot(mc, "booth-tail");
            // The nose close up, from ahead and to its left, olive again.
            onServer(mc, sp -> {
                ServerLevel level = sp.serverLevel();
                if (level.getEntity(huey) instanceof Aircraft red) {
                    red.discard();
                }
                Aircraft a = spawnHuey(level);
                huey = a.getUUID();
                Vec3 nose = a.position().add(a.rotate(new com.chunkworks.vanillawheels.domain.Vec(0.0, 1.3, 2.9)));
                aim(sp, nose.add(3.6, 1.4, -3.4), nose);
            });
        }));
        s.add(new Step(t += SETTLE, () -> shoot(mc, "booth-nose")));
        // --- the cabin with eight people aboard ---------------------------------------------------
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            ServerLevel level = sp.serverLevel();
            if (level.getEntity(huey) instanceof Aircraft old) {
                old.discard();
            }
            Aircraft a = spawnHuey(level);
            huey = a.getUUID();
            for (int i = 0; i < 8; i++) {
                ServerPlayer rider = standIn(sp, "rider" + i, a.position());
                if (!rider.startRiding(a, true)) {
                    LOG.error("booth: FAIL rider {} takes a seat", i);
                }
            }
            aim(sp, a.position().add(-0.5, 2.6, -7.0), a.position().add(0.0, 1.4, 0.0));
        })));
        s.add(new Step(t += SETTLE, () -> {
            Aircraft a = client(mc, huey);
            shoot(mc, "booth-cabin");
            verdict("eight aboard", () -> a != null && a.getPassengers().size() == 8 ? null : "passengers " + (a == null ? null : a.getPassengers().size()));
            onServer(mc, sp -> withHuey(sp, h -> aim(sp, h.position().add(2.2, 2.3, 3.2), h.position().add(0.0, 1.4, 0.4))));
        }));
        s.add(new Step(t += SETTLE, () -> shoot(mc, "booth-cabin-front")));
        // --- the pilot's own view ----------------------------------------------------------------
        s.add(new Step(t += 2, () -> onServer(mc, sp -> withHuey(sp, a -> {
            for (var rider : List.copyOf(a.getPassengers())) {
                rider.stopRiding();
                if (rider instanceof ServerPlayer standIn) {
                    standIn.connection.disconnect(net.minecraft.network.chat.Component.literal("booth"));
                }
            }
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.startRiding(a, true);
        }))));
        s.add(new Step(t += 20, () -> {
            if (mc.player != null) {
                mc.player.setYRot(EAST);
                mc.player.setXRot(12.0f);
            }
        }));
        s.add(new Step(t += SETTLE, () -> {
            Aircraft a = client(mc, huey);
            shoot(mc, "booth-cockpit");
            verdict("the booth's player flies it from the pilot's seat", () -> a != null && a.getControllingPassenger() == mc.player ? null : "pilot " + (a == null ? null : a.getControllingPassenger()));
        }));
        // --- hovering six up, rotors spun, seen from the ground -----------------------------------
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            sp.stopRiding();
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            withHuey(sp, a -> {
                ArmorStand stand = EntityType.ARMOR_STAND.create(sp.serverLevel());
                if (stand != null) {
                    stand.setInvisible(true);
                    stand.setPos(a.getX(), a.getY(), a.getZ());
                    sp.serverLevel().addFreshEntity(stand);
                    stand.startRiding(a, true);
                }
                a.setScriptedFlight(new FlightInput(0, 0, 1, true, false, 0.0));
            });
        })));
        s.add(new Step(t += 75, () -> onServer(mc, sp -> withHuey(sp, a -> {
            a.setScriptedFlight(new FlightInput(0, 0, 0, true, false, 0.0));
            a.setPos(a.getX(), ground + 6.0, a.getZ());
        }))));
        s.add(new Step(t += 30, () -> onServer(mc, sp -> withHuey(sp, a -> aim(sp, middle(a).add(7.0, -3.5, -11.0), middle(a).add(0.0, 0.8, 0.0))))));
        s.add(new Step(t += SETTLE, () -> {
            Aircraft a = client(mc, huey);
            shoot(mc, "booth-hover");
            verdict("its rotor turns at speed, seen by the client", () -> a != null && a.rotor() > 0.9f ? null : "rotor " + (a == null ? null : a.rotor()));
            verdict("and it is off the ground", () -> a != null && a.getY() > ground + 4.0 ? null : "height " + (a == null ? null : a.getY() - ground));
        }));
        // --- the Sling Container on its hook --------------------------------------------------------
        s.add(new Step(t += 2, () -> onServer(mc, sp -> withHuey(sp, a -> {
            Vec3 hook = a.hookPoint();
            Vehicle c = Vehicle.create(sp.serverLevel(), CONTAINER, new Vec3(hook.x, ground, hook.z), EAST);
            if (!(c instanceof SlungLoad load)) {
                LOG.error("booth: FAIL the Sling Container is a slung load -- {}", c);
                return;
            }
            sp.serverLevel().addFreshEntity(load);
            box = load.getUUID();
        }))));
        s.add(new Step(t += 10, () -> onServer(mc, sp -> withHuey(sp, a -> {
            a.hookKey(sp);
            a.setScriptedFlight(new FlightInput(0, 0, 1, true, false, 0.0));
        }))));
        s.add(new Step(t += 25, () -> onServer(mc, sp -> withHuey(sp, a -> a.setScriptedFlight(new FlightInput(0, 0, 0, true, false, 0.0))))));
        s.add(new Step(t += 40, () -> onServer(mc, sp -> withHuey(sp, a -> aim(sp, middle(a).add(-3.0, -5.0, -17.0), middle(a).add(0.0, -3.0, 0.0))))));
        s.add(new Step(t += SETTLE, () -> {
            SlungLoad load = entity(mc, box) instanceof SlungLoad l ? l : null;
            shoot(mc, "booth-sling");
            verdict("the Sling Container hangs from the hook, off the ground", () -> load != null && load.tower() != null && load.getY() > ground + 1.0
                    ? null : "load " + load + (load == null ? "" : ", tower " + load.tower() + ", height " + (load.getY() - ground)));
        }));
        // --- the crop sprayer over the wheat, flown low by the booth's player ---------------------------
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            ServerLevel level = sp.serverLevel();
            for (UUID id : new UUID[] {huey, box}) {
                if (id != null && level.getEntity(id) instanceof Vehicle old) {
                    old.ejectPassengers();
                    old.discard();
                }
            }
            Vehicle v = Vehicle.create(level, HUEY, new Vec3(HX + 8.0, ground, HZ), EAST);
            if (!(v instanceof Aircraft a)) {
                LOG.error("booth: FAIL the Huey is an aircraft");
                return;
            }
            a.setFuel(a.tank().capacity());
            level.addFreshEntity(a);
            huey = a.getUUID();
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RotorcraftContent.CROP_SPRAYER.get()));
            a.interact(sp, InteractionHand.MAIN_HAND);
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, 64));
            a.interact(sp, InteractionHand.MAIN_HAND);
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.startRiding(a, true);
            a.sprayKey(sp);
        })));
        s.add(new Step(t += 20, () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (mc.player != null) {
                mc.player.setYRot(EAST);
                mc.player.setXRot(18.0f);
            }
            RotorcraftKeys.ASCEND.setDown(true);
        }));
        // The rotor started spooling when the pilot boarded, twenty ticks before: it lifts some 34 ticks
        // into the climb, and about a dozen ticks more and the drift after letting go take it five up.
        s.add(new Step(t += 46, () -> RotorcraftKeys.ASCEND.setDown(false)));
        s.add(new Step(t += 15, () -> mc.options.keyUp.setDown(true)));
        s.add(new Step(t += 28, () -> mc.options.keyUp.setDown(false)));
        s.add(new Step(t += 8, () -> {
            Aircraft a = client(mc, huey);
            shoot(mc, "booth-spraying");
            verdict("the sprayer is fitted and spraying", () -> a != null && a.sprayerFitted() && a.spraying() ? null
                    : "fitted " + (a != null && a.sprayerFitted()) + ", spraying " + (a != null && a.spraying()));
            LOG.info("booth: spraying at {} over the ground", a == null ? null : a.getY() - ground);
        }));
        s.add(new Step(t += 50, () -> onServer(mc, sp -> {
            int grown = 0;
            for (int x = 14; x < 34; x++) {
                for (int z = -7; z < 7; z++) {
                    var state = sp.serverLevel().getBlockState(BlockPos.containing(HX + x, ground, HZ + z));
                    if (state.getBlock() instanceof CropBlock crop && crop.getAge(state) > 0) {
                        grown++;
                    }
                }
            }
            int n = grown;
            verdict("the pass grew wheat under the boom", () -> n > 20 ? null : "grown " + n);
        })));
        s.add(new Step(t += 20, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            LOG.info("booth: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    /** effects: returns a stand-in player at {@code at}: a server player whose connection goes nowhere, seen by the booth's client as anyone else */
    private static ServerPlayer standIn(ServerPlayer sp, String name, Vec3 at) {
        var server = sp.getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer p = new ServerPlayer(server, sp.serverLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        p.teleportTo(sp.serverLevel(), at.x, at.y, at.z, 0.0f, 0.0f);
        return p;
    }

    // --- reading the frame -----------------------------------------------

    /** effects: an olive drab pixel in daylight: green over blue, red near green, not bright */
    private static boolean olive(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return g > 30 && g < 140 && g > b + 8 && Math.abs(r - g) < 20;
    }

    private static boolean redPaint(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return r > 40 && r > g * 1.4 && r > b * 1.3;
    }

    /** effects: returns how many pixels of the frame's middle (rows 15..85 %, columns 10..90 %) satisfy {@code test} */
    private static int count(Minecraft mc, IntPredicate test) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int n = 0;
            int w = image.getWidth(), h = image.getHeight();
            for (int y = (int) (h * .15); y < (int) (h * .85); y++) {
                for (int x = (int) (w * .1); x < (int) (w * .9); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int rgb = (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                    if (test.test(rgb)) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    // --- plumbing --------------------------------------------------------

    /** effects: returns the client's aircraft of {@code id}, or null */
    @org.jetbrains.annotations.Nullable
    private static Aircraft client(Minecraft mc, UUID id) {
        return entity(mc, id) instanceof Aircraft a ? a : null;
    }

    /** effects: returns the client's entity of {@code id}, or null */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.world.entity.Entity entity(Minecraft mc, UUID id) {
        if (mc.level == null || id == null) {
            return null;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e.getUUID().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void withHuey(ServerPlayer sp, Consumer<Aircraft> action) {
        if (sp.serverLevel().getEntity(huey) instanceof Aircraft a) {
            action.accept(a);
        } else {
            LOG.error("booth: FAIL the Huey is in the level -- gone");
        }
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> LOG.info("booth: {}", message.getString()));
    }

    /** Runs {@code check}; null is a pass, anything else the failure's detail. */
    private static void verdict(String what, Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail);
        }
    }
}

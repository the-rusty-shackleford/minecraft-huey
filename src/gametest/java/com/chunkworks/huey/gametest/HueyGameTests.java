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
import com.chunkworks.rotorcraft.SlungLoad;
import com.chunkworks.rotorcraft.api.AircraftProfile;
import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.chunkworks.rotorcraft.domain.FlightInput;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.LiftMotion;
import com.chunkworks.vanillawheels.domain.LiftStatus;
import com.chunkworks.vanillawheels.lift.LiftBlockEntity;
import com.chunkworks.vanillawheels.lift.LiftMenu;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Huey on a headless server: its two profiles make it an aircraft of eight seats on skids,
 * flown by Rotorcraft; its chassis crafts from six steel blocks and three glass panes; the lift
 * builds it from the chassis and an engine and paints it; flown by script it climbs, crosses the
 * pad, turns and lands softly with nobody hurt; it carries the Sling Container on its hook, a
 * cow and apples aboard, and sets it down whole; and its baggage store opens from either hatch and
 * from the seat. The template is encased in barriers: every flight
 * keeps the whole hull, nine metres of tail behind the mast, inside its 48 blocks.
 */
@GameTestHolder("huey")
@PrefixGameTestTemplate(false)
public final class HueyGameTests {
    private static final int SIZE = 48;
    private static final int FLOOR = 2;
    private static final ResourceLocation HUEY = ResourceLocation.fromNamespaceAndPath("huey", "huey");
    private static final ResourceLocation CONTAINER = ResourceLocation.fromNamespaceAndPath("rotorcraft", "sling_container");

    public HueyGameTests() {}

    /** effects: lays stone FLOOR deep over the template */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    /** effects: returns a fuelled vehicle of profile {@code id} at (x, FLOOR, z) facing {@code yaw}, in the level */
    private static Vehicle spawn(GameTestHelper helper, ResourceLocation id, double x, double z, float yaw) {
        Vehicle v = Vehicle.create(helper.getLevel(), id, helper.absoluteVec(new Vec3(x, FLOOR, z)), yaw);
        helper.assertTrue(v != null, id + " is in the registry");
        if (v.profile().engine().isPresent()) {
            v.setFuel(v.tank().capacity());
        }
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /**
     * effects: returns a villager with no AI in {@code a}'s second seat, behind an armor stand at the
     * controls: a pilot that is no player, so the server flies the script (Vanilla Wheels' D-0018
     * rig). A villager with no AI never heals, so what it loses is the damage.
     */
    private static LivingEntity crew(GameTestHelper helper, Aircraft a) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
        Villager rider = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(stand != null && rider != null, "an armor stand and a villager");
        for (LivingEntity e : List.of(stand, rider)) {
            e.setPos(a.getX(), a.getY(), a.getZ());
            helper.getLevel().addFreshEntity(e);
        }
        rider.setNoAi(true);
        helper.assertTrue(stand.startRiding(a, true), "the stand-in takes the controls");
        helper.assertTrue(rider.startRiding(a, true), "the villager takes a seat");
        return rider;
    }

    /** effects: returns the script's input: stick, pedals and collective, powered */
    private static FlightInput fly(int forward, int turn, int lift) {
        return new FlightInput(forward, turn, lift, true, false, 0.0);
    }

    @GameTest(template = "pad", timeoutTicks = 40)
    public void theProfilesMakeItAnAircraftOfEightSeatsOnSkids(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        Optional<VehicleProfile> vehicle = VanillaWheels.profile(registries, HUEY).map(h -> h.value());
        helper.assertTrue(vehicle.isPresent(), "huey:huey is a vehicle");
        VehicleProfile p = vehicle.get();
        helper.assertValueEqual(p.seats().size(), 8, "a pilot, a copilot and six");
        helper.assertTrue(p.seats().get(0).driver() && p.seats().get(0).at().x() < 0, "the pilot flies from the right-hand seat");
        helper.assertTrue(!p.wheels().drawn(), "skids: no wheels drawn");
        helper.assertValueEqual(p.wheels().positions().size(), 4, "standing on the skids' two ends each");
        helper.assertValueEqual(p.engine().orElseThrow().maxSpeed(), 1.4, "1.4 a tick at the top");
        helper.assertValueEqual(p.fuel().orElseThrow().capacity(), 36000, "its tank");
        helper.assertValueEqual(p.paint().orElseThrow().factory(), Optional.of(0x4f5536), "olive drab from the factory");
        helper.assertValueEqual(p.sounds().engine(), Optional.of(ResourceLocation.fromNamespaceAndPath("huey", "rotor")), "its own rotor loop");
        helper.assertTrue(p.sounds().pitch().full() < 1.1, "a rotor's note, which hardly climbs: " + p.sounds().pitch());
        Optional<AircraftProfile> flying = Rotorcraft.aircraft(registries, HUEY);
        helper.assertTrue(flying.isPresent(), "and an aircraft");
        AircraftProfile a = flying.get();
        helper.assertValueEqual(a.rotors().size(), 2, "a main rotor and a tail rotor");
        helper.assertTrue(Math.abs(p.blocks(a.rotors().get(0).radius()) - 7.355) < 0.01, "the main rotor 14.71 across: " + p.blocks(a.rotors().get(0).radius()));
        helper.assertTrue(a.hook().isPresent() && a.sprayer().isPresent(), "a cargo hook and a sprayer mount");
        helper.assertValueEqual(a.sprayer().get().width(), 11.0, "an eleven-block swath");
        helper.assertValueEqual(a.hull().size(), 6, "a hull of six boxes");
        Vehicle v = spawn(helper, HUEY, 24.5, 24.5, 0.0f);
        helper.assertTrue(v instanceof Aircraft, "made as Rotorcraft's aircraft: " + v);
        helper.assertValueEqual(v.getName().getString(), "Huey", "named");
        helper.succeed();
    }

    @GameTest(template = "pad", timeoutTicks = 40)
    public void theChassisCraftsFromSixSteelBlocksAndThreeGlassPanes(GameTestHelper helper) {
        var steelBlock = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("metalsandmaterials", "steel_block"));
        helper.assertTrue(steelBlock != Items.AIR, "Metals and Materials' steel block is here");
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            grid.set(i, new ItemStack(i < 3 ? Items.GLASS_PANE : steelBlock));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "the grid crafts something");
        helper.assertValueEqual(recipe.get().id(), ResourceLocation.fromNamespaceAndPath("huey", "huey_chassis"), "the Huey's chassis recipe");
        ItemStack result = recipe.get().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModContent.CHASSIS.get()), "a chassis: " + result);
        helper.assertValueEqual(VanillaWheels.vehicleOf(result).orElse(null), HUEY, "for the Huey");
        helper.succeed();
    }

    @GameTest(template = "pad", timeoutTicks = 200)
    public void theLiftBuildsItFromItsChassisAndAnEngineAloneAndPaintsIt(GameTestHelper helper) {
        floor(helper);
        BlockPos controller = new BlockPos(24, FLOOR, 22);
        ServerPlayer sp = player(helper, "lift-tester", new Vec3(28.5, FLOOR, 25.5));
        sp.setYRot(Direction.SOUTH.toYRot());
        ItemStack liftItem = new ItemStack(ModContent.LIFT_ITEM.get());
        sp.setItemInHand(InteractionHand.MAIN_HAND, liftItem);
        BlockPos below = helper.absolutePos(controller).below();
        BlockHitResult down = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        helper.assertTrue(ModContent.LIFT_ITEM.get().place(new BlockPlaceContext(helper.getLevel(), sp, InteractionHand.MAIN_HAND, liftItem, down)).consumesAction(), "the lift is placed");
        BlockHitResult at = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(controller)), Direction.UP, helper.absolutePos(controller), false);
        helper.assertTrue(helper.getBlockState(controller).useWithoutItem(helper.getLevel(), sp, at).consumesAction(), "its menu opens");
        helper.assertTrue(sp.containerMenu instanceof LiftMenu, "the lift's menu");
        LiftMenu menu = (LiftMenu) sp.containerMenu;
        menu.getSlot(LiftMenu.CHASSIS).set(ModContent.chassisStack(HUEY));
        menu.getSlot(LiftMenu.ENGINE).set(new ItemStack(ModContent.ENGINE.get()));
        menu.broadcastChanges();
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.READY, "a chassis and an engine build it: skids need no wheels");
        helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "built");
        LiftBlockEntity lift = (LiftBlockEntity) helper.getBlockEntity(controller);
        List<Vehicle> built = helper.getLevel().getEntitiesOfClass(Vehicle.class, lift.deckBox());
        helper.assertValueEqual(built.size(), 1, "one helicopter on the deck");
        helper.assertTrue(built.get(0) instanceof Aircraft, "made as an aircraft: " + built.get(0));
        Aircraft a = (Aircraft) built.get(0);
        helper.runAtTickTime(LiftMotion.JOB + 4, () -> {
            menu.getSlot(LiftMenu.DYE).set(new ItemStack(Items.RED_DYE));
            menu.broadcastChanges();
            helper.assertValueEqual(menu.paintStatus(), LiftStatus.Paint.READY, "the Huey on the deck can be painted");
            helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.PAINT_BUTTON), "painted");
            helper.assertValueEqual(a.paint(), DyeColor.RED, "red");
            sp.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
            helper.succeed();
        });
    }

    @GameTest(template = "pad", timeoutTicks = 900)
    public void itClimbsCrossesThePadTurnsAndLandsSoftlyWithNobodyHurt(GameTestHelper helper) {
        floor(helper);
        Aircraft a = (Aircraft) spawn(helper, HUEY, 24.5, 12.5, 0.0f);   // facing south, its tail nine blocks north
        LivingEntity rider = crew(helper, a);
        float health = rider.getHealth();
        float[] lowest = {health};
        double floorY = helper.absoluteVec(new Vec3(0, FLOOR, 0)).y;
        Vec3 start = a.position();
        float yaw0 = a.getYRot();
        StringBuilder trace = new StringBuilder();
        int[] tick = {0};
        helper.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], rider.getHealth());
            if (tick[0]++ % 20 == 0) {
                trace.append(String.format(Locale.ROOT, " t%d:(%.1f,%.1f,%.1f)yaw%.0f,spool%.2f,cond%d", tick[0], a.getX() - start.x,
                        a.getY() - floorY, a.getZ() - start.z, a.getYRot(), a.rotor(), a.condition()));
            }
        });
        a.setScriptedFlight(fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getY() > floorY + 6.0, "up:" + trace))
                .thenExecute(() -> a.setScriptedFlight(fly(1, 0, 0)))
                .thenWaitUntil(() -> helper.assertTrue(a.getZ() - start.z > 12.0, "across the pad:" + trace))
                .thenExecute(() -> a.setScriptedFlight(fly(0, 1, 0)))
                .thenWaitUntil(() -> helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(a.getYRot() - yaw0)) > 85.0, "turned:" + trace))
                .thenExecute(() -> a.setScriptedFlight(fly(0, 0, -1)))
                .thenWaitUntil(() -> helper.assertTrue(a.onGround() && a.getY() < floorY + 0.01, "landed:" + trace))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(a.position().distanceTo(start) > 12.0, "it went somewhere: " + a.position().distanceTo(start));
                    helper.assertValueEqual(a.condition(), com.chunkworks.vanillawheels.domain.Condition.MAX, "it touched nothing hard and landed soft:" + trace);
                    helper.assertValueEqual(lowest[0], health, "nobody aboard was hurt:" + trace);
                })
                .thenSucceed();
    }

    @GameTest(template = "pad", timeoutTicks = 1400)
    public void itCarriesTheSlingContainerOnItsHookAndSetsItDownWhole(GameTestHelper helper) {
        floor(helper);
        Aircraft a = (Aircraft) spawn(helper, HUEY, 24.5, 12.5, 0.0f);
        crew(helper, a);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        double floorY = helper.absoluteVec(new Vec3(0, FLOOR, 0)).y;
        SlungLoad[] box = new SlungLoad[1];
        Cow[] cow = new Cow[1];
        float[] health = new float[1];
        double[] from = new double[1];
        StringBuilder trace = new StringBuilder();
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (box[0] != null && tick[0]++ % 20 == 0) {
                trace.append(String.format(Locale.ROOT, " t%d:huey%.1f/%.1f,box%.1f/%.1f,rope%.2f,cond%d", tick[0], a.getZ(), a.getY() - floorY,
                        box[0].getZ(), box[0].getY() - floorY, box[0].eyePoint().distanceTo(a.hookPoint()), box[0].condition()));
            }
        });
        a.setScriptedFlight(fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getY() > floorY + 5.0, "up"))
                .thenExecute(() -> a.setScriptedFlight(fly(0, 0, 0)))
                .thenIdle(40)
                .thenExecute(() -> {
                    // The container under the hovering hook, a cow behind its doors and apples in a chest.
                    Vec3 under = helper.relativeVec(a.hookPoint());
                    box[0] = (SlungLoad) spawn(helper, CONTAINER, under.x, under.z, 0.0f);
                    box[0].toggleDoors();
                    cow[0] = EntityType.COW.create(helper.getLevel());
                    cow[0].moveTo(box[0].getX(), box[0].getY(), box[0].getZ(), 0.0f, 0.0f);
                    cow[0].setNoAi(true);
                    helper.getLevel().addFreshEntity(cow[0]);
                    helper.assertTrue(cow[0].startRiding(box[0], true), "the cow boards through the open doors");
                    box[0].toggleDoors();
                    health[0] = cow[0].getHealth();
                    box[0].setItem(0, new ItemStack(Items.APPLE, 10));
                    a.hookKey(pilot);
                    helper.assertTrue(a.trailer() == box[0], "hooked from the hover: the eye " + box[0].eyePoint().distanceTo(a.hookPoint()) + " below");
                    from[0] = box[0].getZ();
                    a.setScriptedFlight(fly(0, 0, 1));
                })
                .thenWaitUntil(() -> helper.assertTrue(box[0].getY() > floorY + 2.0, "the container lifted off:" + trace))
                .thenExecute(() -> a.setScriptedFlight(fly(1, 0, 0)))
                .thenWaitUntil(() -> helper.assertTrue(box[0].getZ() > from[0] + 8.0, "carried south:" + trace))
                .thenExecute(() -> a.setScriptedFlight(fly(0, 0, 0)))
                .thenIdle(80)
                .thenExecute(() -> a.setScriptedFlight(fly(0, 0, -1)))
                .thenWaitUntil(() -> helper.assertTrue(box[0].resting(), "set down:" + trace))
                .thenIdle(40)
                .thenExecute(() -> {
                    a.setScriptedFlight(fly(0, 0, 0));
                    a.hookKey(pilot);
                    helper.assertTrue(a.trailer() == null, "let go on the ground");
                    helper.assertValueEqual(box[0].condition(), com.chunkworks.vanillawheels.domain.Condition.MAX, "set down whole:" + trace);
                    helper.assertTrue(cow[0].getVehicle() == box[0], "the cow still aboard");
                    helper.assertValueEqual(cow[0].getHealth(), health[0], "and unhurt:" + trace);
                    helper.assertValueEqual(box[0].getItem(0).getCount(), 10, "the apples all there");
                })
                .thenSucceed();
    }

    /**
     * The baggage store (D-0002): a double chest's six rows hidden in the boom. A click on either
     * hatch, sent as the client sends it, opens it; a rider's inventory key opens it; a click on the
     * cabin still boards, the store far beyond its reach.
     */
    @GameTest(template = "pad", timeoutTicks = 60)
    public void itsBaggageStoreOpensFromEitherHatchAndFromTheSeat(GameTestHelper helper) {
        floor(helper);
        Vehicle v = spawn(helper, HUEY, 24.5, 24.5, 30.0f);
        VehicleProfile p = v.profile();
        helper.assertTrue(p.storage().isPresent() && p.storage().get().chests().size() == 1, "one store");
        helper.assertValueEqual(p.storage().get().chests().get(0).rows(), 6, "a double chest's six rows");
        Vec3 store = v.position().add(v.rotate(p.localBlocks(p.storage().get().chests().get(0).at())));
        ServerPlayer porter = player(helper, "porter", new Vec3(2.5, FLOOR, 2.5));
        helper.runAtTickTime(5, () -> {
            for (int side : new int[] {1, -1}) {
                // Standing beside the hatch, 1.4 out from the boom's middle, looking at its middle.
                Vec3 across = v.rotate(new com.chunkworks.vanillawheels.domain.Vec(side, 0.0, 0.0));
                Vec3 feet = new Vec3(store.x + across.x * 1.4, v.getY(), store.z + across.z * 1.4);
                porter.teleportTo(feet.x, feet.y, feet.z);
                Vec3 eye = porter.getEyePosition();
                Vec3 hatch = new Vec3(store.x + across.x * 0.49, v.getY() + 1.36, store.z + across.z * 0.49);
                Vec3 inside = hatch.add(hatch.subtract(eye).normalize().scale(0.3));
                Vehicle.Part part = null;
                Vec3 hit = null;
                for (var e : v.getParts()) {
                    if (e instanceof Vehicle.Part candidate) {
                        var clip = candidate.getBoundingBox().clip(eye, inside);
                        if (clip.isPresent() && (hit == null || clip.get().distanceToSqr(eye) < hit.distanceToSqr(eye))) {
                            part = candidate;
                            hit = clip.get();
                        }
                    }
                }
                helper.assertTrue(part != null, "a hit box covers the " + (side > 0 ? "left" : "right") + " hatch");
                helper.assertTrue(porter.canInteractWithEntity(part.getBoundingBox(), 3.0), "within the server's reach");
                porter.connection.handleInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(
                        part, false, InteractionHand.MAIN_HAND, hit.subtract(part.position())));
                helper.assertTrue(porter.containerMenu instanceof net.minecraft.world.inventory.ChestMenu m && m.getRowCount() == 6,
                        "the " + (side > 0 ? "left" : "right") + " hatch opens six rows: " + porter.containerMenu);
                porter.closeContainer();
            }
            // The cabin's own box: the click boards (the Huey is whole), the store out of reach.
            Vec3 cabin = v.position().add(v.rotate(p.localBlocks(p.body().parts().get(0).at())));
            porter.teleportTo(cabin.x + 2.0, v.getY(), cabin.z);
            porter.connection.handleInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(
                    v, false, InteractionHand.MAIN_HAND, Vec3.ZERO));
            porter.connection.handleInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(
                    v, false, InteractionHand.MAIN_HAND));
            helper.assertTrue(porter.getVehicle() == v, "a click on the body still boards");
            helper.assertTrue(!(porter.containerMenu instanceof net.minecraft.world.inventory.ChestMenu), "and opens no store");
            // Aboard, the inventory key: the store, as a car's first chest.
            v.openCustomInventoryScreen(porter);
            helper.assertTrue(porter.containerMenu instanceof net.minecraft.world.inventory.ChestMenu m && m.getRowCount() == 6,
                    "a rider's inventory key opens the store: " + porter.containerMenu);
            porter.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
            helper.succeed();
        });
    }

    /** A server player with a connection that goes nowhere, so the lift's menu takes the real path. */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, sp, cookie);
        sp.setGameMode(GameType.SURVIVAL);
        Vec3 abs = helper.absoluteVec(at);
        sp.teleportTo(abs.x, abs.y, abs.z);
        return sp;
    }
}

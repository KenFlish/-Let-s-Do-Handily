package ru.codex.stirandcharm;

import java.util.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.state.*;
import com.pockyl.diegesis.api.station.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;
import net.satisfy.farm_and_charm.core.block.entity.*;
import net.satisfy.farm_and_charm.core.recipe.*;
import ru.codex.stirandcharm.mixin.CookingPotAccess;

@GameTestHolder(StirAndCharm.ID)
@PrefixGameTestTemplate(false)
public final class KitchenGameTests {
    private record Context(ServerLevel level, BlockPos pos, StationType type, StationState state) implements StationContext {
        public BlockState blockState() { return level.getBlockState(pos); }
        public Direction facing() { return Direction.NORTH; }
        public Collection<? extends Player> users() { return List.of(); }
        public void effect(ResourceLocation effect, String node, CompoundTag data) {}
    }
    private record Setup(Context context, KitchenLogic logic, Container inventory) {}
    private static Setup station(GameTestHelper h, boolean bowl) {
        BlockPos p = new BlockPos(2, 2, 2);
        h.setBlock(p.below(), Blocks.REDSTONE_BLOCK);
        var id = bowl ? StirAndCharm.BOWL : StirAndCharm.POT;
        h.setBlock(p, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("farm_and_charm", bowl ? "crafting_bowl" : "cooking_pot")));
        StationType type = Diegesis.stationType(id).orElseThrow();
        Context c = new Context(h.getLevel(), h.absolutePos(p), type, new StationState(type.schema()));
        var logic = new KitchenLogic(c, bowl);
        return new Setup(c, logic, (Container) c.blockEntity());
    }
    private static Player player(GameTestHelper h) { return h.makeMockPlayer(GameType.SURVIVAL); }
    private static void seed(Setup s, Recipe<?> recipe) {
        int i = 0;
        for (Ingredient ingredient : recipe.getIngredients()) if (!ingredient.isEmpty()) s.inventory.setItem(i++, ingredient.getItems()[0].copyWithCount(1));
        ItemStack dish = KitchenRecipes.dish(recipe);
        if (!dish.isEmpty()) s.inventory.setItem(6, dish.copyWithCount(1));
    }
    private static CookingPotRecipe potRecipe(GameTestHelper h) {
        return KitchenRecipes.all(h.getLevel(), false).stream().map(RecipeHolder::value).map(r -> (CookingPotRecipe) r)
            .filter(r -> !r.requiresLearning() && r.getIngredients().stream().allMatch(i -> i.getItems().length > 0)).findFirst().orElseThrow();
    }
    @GameTest(template = "empty") public static void slotsAreNativeAndSurviveClose(GameTestHelper h) {
        for (boolean bowl : List.of(false, true)) {
            Setup s = station(h, bowl);
            s.context.state.setItem(SlotRef.of("ingredients", 0), new ItemStack(Items.CARROT));
            h.assertTrue(s.inventory.getItem(0).is(Items.CARROT), "Station must write into native inventory");
            s.logic.onClose(s.context, player(h));
            h.assertTrue(s.inventory.getItem(0).is(Items.CARROT), "Closing must leave ingredients in the block");
        }
        h.succeed();
    }
    @GameTest(template = "empty") public static void transferFailureIsAtomic(GameTestHelper h) {
        Player p = player(h);
        p.getInventory().setItem(0, new ItemStack(Items.CARROT, 2));
        var inventory = new SimpleContainer(8);
        var plan = RecipeTransfer.plan(p.getInventory(), inventory, 6, false,
            List.of(Ingredient.of(Items.CARROT), Ingredient.of(Items.BEEF)), ItemStack.EMPTY);
        h.assertTrue(plan.isEmpty() && inventory.isEmpty() && p.getInventory().getItem(0).getCount() == 2,
            "An incomplete recipe must not take any ingredients"); h.succeed();
    }
    @GameTest(template = "empty") public static void overlappingIngredientsAndPartialBowl(GameTestHelper h) {
        Player p = player(h);
        p.getInventory().setItem(0, new ItemStack(Items.CARROT));
        p.getInventory().setItem(1, new ItemStack(Items.POTATO));
        var inv = new SimpleContainer(5);
        inv.setItem(0, new ItemStack(Items.WHEAT));
        var ingredients = List.of(Ingredient.of(Items.CARROT, Items.POTATO), Ingredient.of(Items.CARROT), Ingredient.of(Items.WHEAT));
        var plan = RecipeTransfer.plan(p.getInventory(), inv, 4, true, ingredients, ItemStack.EMPTY);
        h.assertTrue(plan.isPresent(), "Broad ingredient must not consume the only narrow match");
        plan.orElseThrow().commit(p.getInventory(), inv);
        int count = 0; for (int i = 0; i < 4; i++) count += inv.getItem(i).getCount();
        h.assertTrue(count == 3 && p.getInventory().isEmpty(), "Exactly the missing ingredients move"); h.succeed();
    }
    @GameTest(template = "empty") public static void missingDishAndFullOutput(GameTestHelper h) {
        Player p = player(h); p.getInventory().setItem(0, new ItemStack(Items.CARROT));
        var inv = new SimpleContainer(8);
        h.assertTrue(RecipeTransfer.plan(p.getInventory(), inv, 6, false, List.of(Ingredient.of(Items.CARROT)), new ItemStack(Items.BOWL)).isEmpty(),
            "Missing serving container must reject the entire fill");
        var bowl = new SimpleContainer(5); bowl.setItem(4, new ItemStack(Items.BREAD, 64));
        h.assertTrue(RecipeTransfer.plan(p.getInventory(), bowl, 4, true, List.of(Ingredient.of(Items.CARROT)), ItemStack.EMPTY).isEmpty(),
            "A finished bowl must not accept another recipe"); h.succeed();
    }
    @GameTest(template = "empty") public static void concurrentFillCannotDuplicate(GameTestHelper h) {
        var inv = new SimpleContainer(5);
        for (int cook = 0; cook < 2; cook++) {
            Player p = player(h); p.getInventory().setItem(0, new ItemStack(Items.CARROT));
            var plan = RecipeTransfer.plan(p.getInventory(), inv, 4, true, List.of(Ingredient.of(Items.CARROT)), ItemStack.EMPTY);
            plan.orElseThrow().commit(p.getInventory(), inv);
            h.assertTrue(p.getInventory().getItem(0).getCount() == cook, "Second fill must recognize an already present ingredient");
        }
        h.assertTrue(inv.getItem(0).getCount() == 1, "One ingredient cannot become two"); h.succeed();
    }
    @GameTest(template = "empty") public static void motionRejectsInvalidAndRepeatedPackets(GameTestHelper h) {
        StirMotion motion = new StirMotion();
        h.assertTrue(motion.accept(1, Float.NaN, 0) == null && motion.accept(1, 10, 0) == null, "Reject non-finite and out-of-range samples");
        h.assertTrue(motion.accept(1, 2, 0).impulse() == 0, "First sample does no work");
        h.assertTrue(motion.accept(1, 0, 2) == null, "One server tick cannot accept multiple impulses");
        h.assertTrue(motion.accept(3, 1, 0).impulse() == 0, "Radial motion is not stirring");
        var turning = motion.accept(5, 0, 2);
        h.assertTrue(turning.impulse() > 0 && turning.impulse() <= .25F, "Circular motion is capped at the native click impulse"); h.succeed();
    }
    @GameTest(template = "empty") public static void potTimeAndOutputStayNative(GameTestHelper h) {
        Setup s = station(h, false); var pot = (CookingPotBlockEntity) s.inventory;
        CookingPotRecipe recipe = potRecipe(h); seed(s, recipe);
        Player p = player(h); s.logic.onOpen(s.context, p);
        for (int i = 0; i < CookingPotBlockEntity.getMaxCookingTime() - 1; i++) {
            pot.tick(h.getLevel(), s.context.pos, pot.getBlockState(), pot); s.logic.tick(s.context);
        }
        h.assertTrue(!pot.hasOutputItem(), "Visual station must not accelerate cooking");
        pot.tick(h.getLevel(), s.context.pos, pot.getBlockState(), pot);
        h.assertTrue(ItemStack.matches(pot.getItem(7), KitchenRecipes.result(h.getLevel(), recipe)), "Native recipe output and components must be preserved"); h.succeed();
    }
    @GameTest(template = "empty") public static void unheatedPotDoesNotCook(GameTestHelper h) {
        Setup s = station(h, false); var pot = (CookingPotBlockEntity) s.inventory; seed(s, potRecipe(h));
        h.getLevel().setBlockAndUpdate(s.context.pos.below(), Blocks.STONE.defaultBlockState());
        for (int i = 0; i < 910; i++) pot.tick(h.getLevel(), s.context.pos, pot.getBlockState(), pot);
        h.assertTrue(!pot.hasOutputItem() && ((CookingPotAccess) pot).stirandcharm$cookingTime() == 0, "No heat means no cooking"); h.succeed();
    }
    @GameTest(template = "empty") public static void fullPotOutputPreservesIngredients(GameTestHelper h) {
        Setup s = station(h, false); var pot = (CookingPotBlockEntity) s.inventory; seed(s, potRecipe(h));
        ItemStack first = pot.getItem(0).copy();
        pot.setItem(7, new ItemStack(Items.COBBLESTONE, 64));
        for (int i = 0; i < 910; i++) { pot.tick(h.getLevel(), s.context.pos, pot.getBlockState(), pot); s.logic.tick(s.context); }
        h.assertTrue(ItemStack.matches(pot.getItem(0), first) && pot.getItem(7).getCount() == 64, "Blocked output must not consume ingredients or overwrite items");
        h.succeed();
    }
    @GameTest(template = "empty") public static void forgedRecipeDoesNotMoveItems(GameTestHelper h) {
        Setup s = station(h, true); Player p = player(h);
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        CompoundTag packet = new CompoundTag(); packet.putString("recipe", "minecraft:diamond");
        s.logic.onCustomAction(s.context, p, "fill", packet);
        h.assertTrue(s.inventory.isEmpty() && p.getInventory().getItem(0).is(Items.DIAMOND), "Unknown or wrong-type recipe must do nothing");
        packet.putString("recipe", "!!! invalid"); s.logic.onCustomAction(s.context, p, "fill", packet);
        h.assertTrue(s.inventory.isEmpty(), "Malformed recipe ID must be rejected"); h.succeed();
    }
    @GameTest(template = "empty") public static void stationsDoNotClaimOtherMods(GameTestHelper h) {
        var foreign = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "cooking_pot"));
        if (foreign != Blocks.AIR) h.assertTrue(Diegesis.stationFor(foreign.defaultBlockState()).map(t -> !t.id().getNamespace().equals(StirAndCharm.ID)).orElse(true),
            "Farmer's Delight must keep its own station");
        for (boolean bowl : List.of(false, true)) {
            Setup s = station(h, bowl);
            h.assertTrue(Diegesis.stationFor(s.context.blockState()).orElseThrow().id().getNamespace().equals(StirAndCharm.ID), "Farm & Charm stations must remain ours");
        }
        h.succeed();
    }
    @GameTest(template = "empty") public static void sneakKeepsNativeInteraction(GameTestHelper h) {
        Setup s = station(h, false); Player p = player(h); p.setShiftKeyDown(true);
        h.assertTrue(!s.context.type.openCondition().test(p, s.context.pos, s.context.blockState()), "Sneaking must bypass the station");
        h.succeed();
    }
    @GameTest(template = "empty") public static void bowlUsesNativeRecipeAndRemainders(GameTestHelper h) {
        Setup s = station(h, true); var be = (CraftingBowlBlockEntity) s.inventory;
        var recipe = (CraftingBowlRecipe) KitchenRecipes.all(h.getLevel(), true).stream()
            .filter(r -> r.id().getPath().endsWith("dough")).findFirst().orElseThrow().value();
        seed(s, recipe);
        // Compare with an untouched native bowl; do not invent different bottle/bucket remainder rules.
        BlockPos other = s.context.pos.offset(2, 0, 0);
        h.getLevel().setBlockAndUpdate(other.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(other, be.getBlockState());
        var baseline = (CraftingBowlBlockEntity) h.getLevel().getBlockEntity(other);
        for (int i = 0; i < 4; i++) baseline.setItem(i, be.getItem(i).copy());
        for (int i = 0; i < 300 && !be.isFinished(); i++) { be.addWhiskImpulse(.25F); be.tick(h.getLevel(), s.context.pos, be.getBlockState(), be); }
        for (int i = 0; i < 300 && !baseline.isFinished(); i++) { baseline.addWhiskImpulse(.25F); baseline.tick(h.getLevel(), other, baseline.getBlockState(), baseline); }
        h.assertTrue(ItemStack.matches(be.getItem(4), KitchenRecipes.result(h.getLevel(), recipe)), "Bowl output must come from native recipe");
        for (int i = 0; i < 5; i++) h.assertTrue(ItemStack.matches(be.getItem(i), baseline.getItem(i)), "Remainders must match native Farm & Charm");
        h.succeed();
    }
    @GameTest(template = "empty") public static void nativeInventoriesPersist(GameTestHelper h) {
        for (boolean bowl : List.of(false, true)) {
            Setup s = station(h, bowl);
            s.inventory.setItem(0, new ItemStack(Items.CARROT, bowl ? 1 : 3));
            BlockEntity original = s.context.blockEntity();
            CompoundTag saved = original.saveWithFullMetadata(h.getLevel().registryAccess());
            BlockEntity loaded = BlockEntity.loadStatic(s.context.pos, original.getBlockState(), saved, h.getLevel().registryAccess());
            h.assertTrue(loaded instanceof Container c && ItemStack.matches(c.getItem(0), s.inventory.getItem(0)), "Native save/load must preserve items");
        }
        h.succeed();
    }
    @GameTest(template = "empty") public static void hopperCanExtractNativeResult(GameTestHelper h) {
        Setup s = station(h, false);
        s.inventory.setItem(7, new ItemStack(Items.BREAD, 3));
        BlockPos below = s.context.pos.below(); h.getLevel().setBlockAndUpdate(below, Blocks.HOPPER.defaultBlockState());
        var hopper = (HopperBlockEntity) h.getLevel().getBlockEntity(below);
        h.assertTrue(HopperBlockEntity.suckInItems(h.getLevel(), hopper), "Hopper must pull from native output slot");
        h.assertTrue(hopper.getItem(0).is(Items.BREAD) && s.context.state.getItems("result")[0].getCount() == 2, "Station must see automation changes"); h.succeed();
    }
    @GameTest(template = "empty") public static void scenesDecodeAgainstRealLibrary(GameTestHelper h) throws Exception {
        for (String name : List.of("cooking_pot", "crafting_bowl")) {
            try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(KitchenGameTests.class.getResourceAsStream(
                "/assets/handson_farm_charm/diegesis/scenes/" + name + ".json")), java.nio.charset.StandardCharsets.UTF_8)) {
                var parsed = SceneDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader));
                h.assertTrue(parsed.result().isPresent(), "Scene must parse: " + parsed.error());
            }
        }
        h.succeed();
    }
    @GameTest(template = "empty") public static void pouredIngredientsReturnWithoutLoss(GameTestHelper h) {
        Setup s = station(h, false); Player p = player(h);
        s.inventory.setItem(0, new ItemStack(Items.WATER_BUCKET));
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        s.logic.onCustomAction(s.context, p, "take_liquid", new CompoundTag());
        h.assertTrue(s.inventory.getItem(0).is(Items.WATER_BUCKET), "Full inventory must leave the poured item in the pot");
        p.getInventory().clearContent();
        s.logic.onCustomAction(s.context, p, "take_liquid", new CompoundTag());
        h.assertTrue(s.inventory.getItem(0).isEmpty() && p.getInventory().getItem(0).is(Items.WATER_BUCKET), "An unconsumed pour can be taken back exactly once");
        h.succeed();
    }
    @GameTest(template = "empty") public static void twoCooksCannotTakeTheSameResult(GameTestHelper h) {
        Setup s = station(h, false); s.inventory.setItem(7, new ItemStack(Items.BREAD, 3));
        Player a = player(h), b = player(h);
        var click = new com.pockyl.diegesis.impl.action.StationAction.Click(
            new com.pockyl.diegesis.impl.action.StationAction.Target.Station(SlotRef.of("result", 0)), 0,
            com.pockyl.diegesis.impl.action.StationAction.ClickKind.PICKUP);
        com.pockyl.diegesis.impl.action.ActionHandler.apply(s.context, s.logic, a, click);
        com.pockyl.diegesis.impl.action.ActionHandler.apply(s.context, s.logic, b, click);
        int taken = a.inventoryMenu.getCarried().getCount() + b.inventoryMenu.getCarried().getCount();
        h.assertTrue(taken == 3 && s.inventory.getItem(7).isEmpty(), "Two same-tick actions must not duplicate a result"); h.succeed();
    }
    @GameTest(template = "empty") public static void fullInventoryCannotDeleteResult(GameTestHelper h) {
        Setup s = station(h, false); s.inventory.setItem(7, new ItemStack(Items.BREAD, 3)); Player p = player(h);
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        var click = new com.pockyl.diegesis.impl.action.StationAction.Click(
            new com.pockyl.diegesis.impl.action.StationAction.Target.Station(SlotRef.of("result", 0)), 0,
            com.pockyl.diegesis.impl.action.StationAction.ClickKind.QUICK_MOVE);
        com.pockyl.diegesis.impl.action.ActionHandler.apply(s.context, s.logic, p, click);
        h.assertTrue(s.inventory.getItem(7).getCount() == 3 && p.inventoryMenu.getCarried().isEmpty(), "Failed shift-take must leave the result untouched"); h.succeed();
    }
    @GameTest(template = "empty") public static void circularPacketsDriveNativeWhiskOnce(GameTestHelper h) {
        Setup s = station(h, true); var be = (CraftingBowlBlockEntity) s.inventory;
        seed(s, KitchenRecipes.all(h.getLevel(), true).getFirst().value());
        Player a = player(h), b = player(h);
        CompoundTag start = new CompoundTag(); start.putFloat("x", 2); start.putFloat("z", 0);
        s.logic.onCustomAction(s.context, a, "stir", start); s.logic.onCustomAction(s.context, b, "stir", start);
        h.assertTrue(be.getWhiskSpeed() == 0, "An initial cursor position does no work");
        h.runAfterDelay(2, () -> {
            CompoundTag turn = new CompoundTag(); turn.putFloat("x", 0); turn.putFloat("z", 2);
            s.logic.onCustomAction(s.context, a, "stir", turn); s.logic.onCustomAction(s.context, b, "stir", turn);
            h.assertTrue(be.getWhiskSpeed() > 0 && be.getWhiskSpeed() <= .251F, "All cooks together are capped at one native impulse per interval");
            h.succeed();
        });
    }
}

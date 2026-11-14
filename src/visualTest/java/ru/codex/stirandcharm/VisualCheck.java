package ru.codex.stirandcharm;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.CraftingBowlBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Only included with -PvisualTests; never packaged into the release jar. */
@EventBusSubscriber(modid = StirAndCharm.ID, value = Dist.CLIENT)
public final class VisualCheck {
    private static int ticks;
    private static int frames;
    private static final BlockPos POT = new BlockPos(0, 70, 0), BOWL = new BlockPos(-2, 70, 0);
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (Boolean.getBoolean("handily.compatVisual")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            if (++frames > 8000) mc.stop();
            return;
        }
        ticks++;
        var server = mc.getSingleplayerServer();
        if (ticks == 30) {
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(5);
            server.execute(() -> {
                var level = server.overworld();
                level.setDayTime(6000);
                for (int x = -9; x <= 7; x++) for (int z = -6; z <= 7; z++) level.setBlockAndUpdate(new BlockPos(x, 69, z), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(POT.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(POT, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("farm_and_charm", "cooking_pot")).defaultBlockState());
                level.setBlockAndUpdate(BOWL, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("farm_and_charm", "crafting_bowl")).defaultBlockState());
                var p = server.getPlayerList().getPlayers().getFirst();
                p.setGameMode(GameType.CREATIVE); p.teleportTo(.5, 70, -2.2);
                p.setYRot(0); p.setXRot(25);
                var inv = p.getInventory(); inv.clearContent();
                var recipe = KitchenRecipes.all(level, false).stream().map(h -> h.value()).filter(r ->
                    (!(r instanceof net.satisfy.farm_and_charm.core.recipe.CookingPotRecipe cooking) || !cooking.requiresLearning())
                    && r.getIngredients().stream().allMatch(i -> i.getItems().length > 0)).findFirst().orElseThrow();
                var pot = (CookingPotBlockEntity) level.getBlockEntity(POT);
                pot.clearContent();
                int i = 0; for (Ingredient ingredient : recipe.getIngredients()) if (!ingredient.isEmpty()) {
                    pot.setItem(i++, ingredient.getItems()[0].copyWithCount(4));
                }
                if (!KitchenRecipes.dish(recipe).isEmpty()) pot.setItem(6, KitchenRecipes.dish(recipe).copyWithCount(4));
                var bowlRecipe = KitchenRecipes.all(level, true).stream().filter(r -> r.id().getPath().endsWith("dough")).findFirst().orElseThrow().value();
                var bowl = (CraftingBowlBlockEntity) level.getBlockEntity(BOWL);
                bowl.clearContent(); bowl.resetStirring();
                i = 0; for (Ingredient ingredient : bowlRecipe.getIngredients()) if (!ingredient.isEmpty()) {
                    bowl.setItem(i++, ingredient.getItems()[0].copyWithCount(1));
                }
                for (Ingredient ingredient : recipe.getIngredients()) if (ingredient.getItems().length > 0) inv.add(ingredient.getItems()[0].copyWithCount(3));
                inv.add(new ItemStack(net.minecraft.world.item.Items.BOWL, 8));
                inv.add(new ItemStack(net.minecraft.world.item.Items.CARROT, 8));
                inv.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farm_and_charm", "strawberry")), 8));
            });
        }
        if (ticks == 100) {
            mc.setScreen(null);
            var result = com.pockyl.diegesis.impl.client.DiegesisClientRuntime.onUseBlock(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POT), net.minecraft.core.Direction.UP, POT, false));
            LogUtils.getLogger().info("Visual check open: {}", result);
        }
        if (ticks == 180 || ticks == 370) {
            if (!DiegesisClient.isFocused()) throw new IllegalStateException("Visual check did not open a station");
            Screenshot.grab(mc.gameDirectory, ticks == 180 ? "stir-and-charm-pot.png" : "stir-and-charm-bowl.png", mc.getMainRenderTarget(),
                message -> LogUtils.getLogger().info("Visual check: {}", message.getString()));
        }
        if (ticks == 250) com.pockyl.diegesis.impl.client.DiegesisClientRuntime.goOver(BOWL, .45F);
        if ((ticks > 120 && ticks < 160 || ticks > 280 && ticks < 350) && ticks % 2 == 0) {
            DiegesisClient.focusedStation().ifPresent(view -> {
                var data = new net.minecraft.nbt.CompoundTag();
                data.putFloat("x", (float) Math.cos(ticks * .15) * 2);
                data.putFloat("z", (float) Math.sin(ticks * .15) * 2);
                view.sendAction("stir", data);
            });
        }
        if (ticks == 410) {
            com.pockyl.diegesis.impl.client.DiegesisClientRuntime.goOver(POT, .45F);
            fillPot(mc, net.minecraft.world.item.Items.WATER_BUCKET);
        }
        if (ticks == 470 || ticks == 560 || ticks == 650) {
            String name = ticks == 470 ? "water" : ticks == 560 ? "strawberry" : "greens";
            Screenshot.grab(mc.gameDirectory, "stir-and-charm-" + name + ".png", mc.getMainRenderTarget(),
                message -> LogUtils.getLogger().info("Visual check: {}", message.getString()));
        }
        if (ticks == 500) fillPot(mc, BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farm_and_charm", "strawberry")));
        if (ticks == 590) fillPot(mc, BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farm_and_charm", "lettuce")));
        if (ticks == 690) mc.stop();
    }
    private static void fillPot(Minecraft mc, net.minecraft.world.item.Item food) {
        mc.getSingleplayerServer().execute(() -> {
            var pot = (CookingPotBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(POT);
            pot.clearContent(); pot.setItem(0, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            if (food != net.minecraft.world.item.Items.WATER_BUCKET) pot.setItem(1, new ItemStack(food, 2));
        });
    }
}

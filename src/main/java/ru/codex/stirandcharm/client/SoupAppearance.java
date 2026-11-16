package ru.codex.stirandcharm.client;

import java.util.*;
import com.pockyl.diegesis.api.client.*;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import ru.codex.stirandcharm.*;

/** The liquid's colour, pieces, ripples and heat are visual only; no recipe or inventory is changed. */
final class SoupAppearance {
    private int target = 0x8877B9D8;
    private float mottle, steam, current, lastAngle;
    private boolean angleKnown;

    void refresh(StationView view) {
        var food = Arrays.stream(view.state().getItems("ingredients")).filter(s -> !s.isEmpty() && !KitchenIngredients.emptyContainer(s)).toList();
        boolean onlyWater = !food.isEmpty() && food.stream().allMatch(KitchenIngredients::water);
        var coloured = food.stream().filter(s -> !KitchenIngredients.water(s)).toList();
        ItemStack meal = view.state().getItems("result")[0];
        var pieces = new ArrayList<Floater>();
        int count = 0;
        for (ItemStack stack : coloured) {
            if (KitchenIngredients.liquid(stack) || KitchenIngredients.dissolved(stack) || KitchenIngredients.egg(stack)) continue;
            int rgb = FoodColors.main(stack);
            boolean leaf = FoodColors.isGreen(rgb);
            pieces.add(new Floater(leaf ? Floater.LEAF : Floater.CHUNK, 0xFF000000 | rgb, leaf ? 1.1F : .85F));
            count += Math.min(4, stack.getCount() + 1);
        }
        int raw = BrothLook.color(view.level(), food, ItemStack.EMPTY);
        float progress = view.state().getControl("progress");
        ItemStack preview = meal.isEmpty() ? FarmCookbook.matching(view, false)
            .map(h -> KitchenRecipes.result(view.level(), h.value())).orElse(ItemStack.EMPTY) : meal;
        int alpha;
        if (onlyWater && meal.isEmpty()) { target = 0x8877B9D8; mottle = .02F; }
        else {
            int cooked = preview.isEmpty() ? raw : FoodColors.broth(view.level(), preview);
            float cookedPart = meal.isEmpty() ? progress * .85F : 1;
            alpha = meal.isEmpty() ? 0xB8 + Math.round(progress * 50) : 0xEC;
            target = alpha << 24 | blend(raw, cooked, cookedPart);
            mottle = coloured.isEmpty() && meal.isEmpty() ? .04F : .2F + .65F * (meal.isEmpty() ? progress : 1);
        }
        if (coloured.isEmpty() && meal.isEmpty() && food.isEmpty()) target = 0x8877B9D8;
        if (!meal.isEmpty() && count == 0) {
            int rgb = FoodColors.broth(view.level(), meal);
            pieces.add(new Floater(Floater.NUGGET, 0xFF000000 | FoodColors.lighter(rgb, .3F), .65F));
            pieces.add(new Floater(Floater.LEAF, 0xFF6E9B43, .6F));
            count = 8;
        }
        final int floatingCount = Math.min(24, count);
        view.node("broth").ifPresent(n -> {
            n.setLiquidColor(target);
            n.setMottle(mottle);
            n.setWakeLook(onlyWater ? 1 : 2.5F, onlyWater ? .25F : .55F);
            n.setFloating(pieces, floatingCount);
        });
    }

    void frame(StationView view, float dt) {
        boolean heated = view.state().getControl("heated") > 0;
        float x = view.state().getControl("spoon_x"), z = view.state().getControl("spoon_z");
        if (x * x + z * z > .2F) {
            float angle = (float) Math.atan2(z, x);
            if (angleKnown && Math.abs(angle - lastAngle) > .001F) {
                float turn = (float) Math.atan2(Math.sin(angle - lastAngle), Math.cos(angle - lastAngle));
                current += Math.clamp(turn * 2, -2F, 2F);
                view.node("broth").ifPresent(n -> n.ripple(8 + x * .65F, 8 + z * .65F, .13F));
            }
            angleKnown = true; lastAngle = angle;
        }
        current *= (float) Math.exp(-dt * 2);
        current = Math.clamp(current, -3F, 3F);
        view.node("broth").ifPresent(n -> {
            n.setAgitation(heated ? .25F + Math.min(.3F, Math.abs(current) * .1F) : .02F);
            n.setEdgeBoil(heated ? .55F : 0);
        });
        if (heated) {
            steam += dt;
            if (steam >= .3F) {
                steam = 0;
                view.spawnOnLiquid(DiegesisParticles.STEAM, "broth", 1, 0x99FFFFFF, new Vector3f(0, 2, 0));
                view.spawnAtLiquidEdge(DiegesisParticles.BUBBLE_POP, "broth", 2, 0xFFEAD8B0, .45F);
            }
        }
    }
    private static int blend(int a, int b, float t) {
        int c = 0;
        for (int shift = 0; shift < 24; shift += 8) c |= Math.round(((a >> shift) & 255) * (1 - t) + ((b >> shift) & 255) * t) << shift;
        return c;
    }
}

package ru.codex.stirandcharm;

import java.util.*;
import com.pockyl.diegesis.api.state.*;
import com.pockyl.diegesis.api.station.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;
import net.satisfy.farm_and_charm.core.block.CraftingBowlBlock;
import net.satisfy.farm_and_charm.core.block.entity.*;
import ru.codex.stirandcharm.mixin.CookingPotAccess;

public final class KitchenLogic implements StationLogic {
    private final Container container;
    private final boolean bowl;
    private final Map<UUID, StirMotion> motion = new HashMap<>();
    private long lastImpulse = Long.MIN_VALUE;
    private ItemStack[] knownInputs;

    public KitchenLogic(StationContext station, boolean bowl) {
        this.bowl = bowl;
        container = (Container) station.blockEntity();
        station.state().bindSlots("ingredients", SlotStorage.of(container, 0, bowl ? 4 : 6));
        station.state().bindSlots("result", SlotStorage.of(container, bowl ? 4 : 7, 1));
        if (!bowl) station.state().bindSlots("containers", SlotStorage.of(container, 6, 1));
        snapshot();
    }

    @Override public void onOpen(StationContext station, Player player) {
        // The native menu normally sets this. Preserve learned recipes and owner-dependent effects.
        if (!bowl) ((CookingPotAccess) container).stirandcharm$owner(player.getUUID());
        publish(station);
    }
    @Override public void onClose(StationContext station, Player player) { motion.remove(player.getUUID()); }

    @Override public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.group().equals("result")) return false;
        if (slot.group().equals("containers")) return KitchenRecipes.all(station.level(), false).stream()
            .map(h -> KitchenRecipes.dish(h.value())).anyMatch(s -> !s.isEmpty() && s.is(stack.getItem()));
        if (bowl && ((CraftingBowlBlockEntity) container).isFinished()) return false;
        if (!bowl && slot.group().equals("ingredients")) {
            var recipes = KitchenRecipes.all(station.level(), false);
            boolean servingDish = recipes.stream().map(h -> KitchenRecipes.dish(h.value()))
                .anyMatch(s -> !s.isEmpty() && s.is(stack.getItem()));
            if (servingDish && recipes.stream().noneMatch(h -> h.value().getIngredients().stream().anyMatch(i -> i.test(stack)))) return false;
        }
        return slot.group().equals("ingredients");
    }
    @Override public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        if (!bowl && slot.group().equals("result")) return false;
        return !bowl || !slot.group().equals("result") || ((CraftingBowlBlockEntity) container).canTakeOut(station.level());
    }
    @Override public void onSlotChanged(StationContext station, SlotRef slot) {
        if (bowl && slot.group().equals("ingredients")) ((CraftingBowlBlockEntity) container).resetStirring();
        container.setChanged();
        snapshot();
    }
    @Override public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (!bowl && slot.group().equals("result") && station.level() instanceof ServerLevel level)
            ((CookingPotBlockEntity) container).dropExperience(level, Vec3.atCenterOf(station.pos()));
    }

    @Override public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (station.level().isClientSide) return;
        if (action.equals("stir")) {
            var sample = motion.computeIfAbsent(player.getUUID(), id -> new StirMotion())
                .accept(station.level().getGameTime(), data.getFloat("x"), data.getFloat("z"));
            if (sample == null) return;
            station.state().setControl("spoon_x", sample.x());
            station.state().setControl("spoon_z", sample.z());
            long now = station.level().getGameTime();
            if (!bowl && sample.impulse() > 0) ((PotStirring)container).handily$stirred(now);
            // Shared station cap: several cooks cannot multiply the native whisk impulse rate.
            if (bowl && sample.impulse() > 0 && (lastImpulse == Long.MIN_VALUE || now - lastImpulse >= 2)) {
                CraftingBowlBlockEntity be = (CraftingBowlBlockEntity) container;
                if (!be.isFinished() && be.hasRecipe()) {
                    be.markStirred(station.level());
                    be.addWhiskImpulse(sample.impulse());
                    lastImpulse = now;
                }
            }
        } else if (action.equals("collect") && !bowl) {
            int taken=MealServing.collect(player,container,data.getBoolean("one"),data.getBoolean("inventory"));
            if (taken>0 && station.level() instanceof ServerLevel level)
                ((CookingPotBlockEntity)container).dropExperience(level,Vec3.atCenterOf(station.pos()));
            else if (taken==0 && !container.getItem(7).isEmpty()) player.displayClientMessage(Component.translatable("handson_farm_charm.serving_failed"),true);
        } else if (action.equals("serve") && !bowl) {
            ServingContainers.place(player, container, data.getBoolean("one"),
                stack -> mayPlace(station, player, new SlotRef("containers", 0), stack));
        } else if (action.equals("take_liquid") && !bowl) {
            for (int i = 5; i >= 0; i--) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty() || !KitchenIngredients.recoverablePour(stack)) continue;
                ItemStack remainder = stack.copy();
                player.getInventory().add(remainder);
                container.setItem(i, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                snapshot();
                break;
            }
        } else if (action.equals("fill") && Settings.BOOK.get()) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString("recipe"));
            if (id == null) return;
            Optional<? extends RecipeHolder<?>> recipe = KitchenRecipes.all(station.level(), bowl).stream().filter(h -> h.id().equals(id)).findFirst();
            if (recipe.isEmpty()) return;
            var value = recipe.get().value();
            var plan = RecipeTransfer.plan(player.getInventory(), container, bowl ? 4 : 6, bowl, value.getIngredients(), ItemStack.EMPTY);
            if (plan.isEmpty()) {
                player.displayClientMessage(Component.translatable("handson_farm_charm.fill_failed"), true);
                return;
            }
            plan.get().commit(player.getInventory(), container);
            if (bowl) ((CraftingBowlBlockEntity) container).resetStirring();
            snapshot();
            player.inventoryMenu.broadcastChanges();
        }
    }

    @Override public void tick(StationContext station) {
        if (bowl) {
            boolean changed = false;
            for (int i = 0; i < 4; i++) if (!ItemStack.matches(knownInputs[i], container.getItem(i))) changed = true;
            if (changed) { ((CraftingBowlBlockEntity) container).resetStirring(); snapshot(); }
        }
        publish(station);
    }
    private void snapshot() {
        knownInputs = new ItemStack[bowl ? 4 : 6];
        for (int i = 0; i < knownInputs.length; i++) knownInputs[i] = container.getItem(i).copy();
    }
    private void publish(StationContext station) {
        float progress;
        boolean heated = false;
        if (bowl) {
            CraftingBowlBlockEntity be = (CraftingBowlBlockEntity) container;
            progress = be.isFinished() ? 1 : be.getStirred() / CraftingBowlBlock.STIRS_NEEDED;
        } else {
            progress = ((CookingPotAccess) container).stirandcharm$cookingTime() / (float) CookingPotBlockEntity.getMaxCookingTime();
            heated = ((CookingPotBlockEntity) container).isBeingBurned();
        }
        station.state().setControl("progress", progress);
        station.state().setControl("heated", heated ? 1 : 0);
        boolean any = !container.getItem(bowl ? 4 : 7).isEmpty();
        for (int i = 0; i < knownInputs.length; i++) any |= !container.getItem(i).isEmpty() && !KitchenIngredients.emptyContainer(container.getItem(i));
        station.state().setTank("broth", !bowl && any ? TankContents.of(StirAndCharm.id("soup"), 75) : TankContents.EMPTY);
        var data = station.state().data();
        if (!data.contains("book") || data.getBoolean("book") != Settings.BOOK.get()) {
            data.putBoolean("book", Settings.BOOK.get()); station.state().setData(data);
        }
        if (!bowl) MealServing.publish(station);
    }
}

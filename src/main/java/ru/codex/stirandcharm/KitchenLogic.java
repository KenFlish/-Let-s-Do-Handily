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
    private long lastStir = Long.MIN_VALUE;

    public KitchenLogic(StationContext station, boolean bowl) {
        this.bowl = bowl;
        container = (Container) station.blockEntity();
        station.state().bindSlots("ingredients", SlotStorage.of(container, 0, bowl ? 4 : 6));
        station.state().bindSlots("result", SlotStorage.of(container, bowl ? 4 : 7, 1));
        snapshot();
    }

    @Override public void onOpen(StationContext station, Player player) {
        // The native menu normally sets this. Preserve learned recipes and owner-dependent effects.
        if (!bowl) ((CookingPotAccess) container).stirandcharm$owner(player.getUUID());
        if(station.blockState().hasProperty(RoasterLid.OPEN) && !station.blockState().getValue(RoasterLid.OPEN))
            station.level().setBlock(station.pos(),station.blockState().setValue(RoasterLid.OPEN,true),3);
        publish(station);
    }
    @Override public void onClose(StationContext station, Player player) { motion.remove(player.getUUID()); }

    @Override public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.group().equals("result")) return false;
        if(slot.group().equals("ingredients") && station.blockState().hasProperty(RoasterLid.OPEN) && !station.blockState().getValue(RoasterLid.OPEN))return false;
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
        poured(station);
        snapshot();
        if (!bowl) publish(station);
    }
    @Override public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (!bowl && slot.group().equals("result") && station.level() instanceof ServerLevel level)
            ((CookingPotBlockEntity) container).dropExperience(level, Vec3.atCenterOf(station.pos()));
        if (!bowl) publish(station);
    }

    @Override public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (station.level().isClientSide) return;
        if(action.equals("lid") && station.blockState().hasProperty(RoasterLid.OPEN)) {
            boolean open=station.blockState().getValue(RoasterLid.OPEN);
            station.level().setBlock(station.pos(),station.blockState().setValue(RoasterLid.OPEN,!open),3);
            station.level().playSound(null,station.pos(),open?net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE:net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,net.minecraft.sounds.SoundSource.BLOCKS,.5F,1.2F);
        } else if (action.equals("stir")) {
            if(station.blockState().hasProperty(RoasterLid.OPEN) && !station.blockState().getValue(RoasterLid.OPEN))return;
            var sample = motion.computeIfAbsent(player.getUUID(), id -> new StirMotion())
                .accept(station.level().getGameTime(), data.getFloat("x"), data.getFloat("z"));
            if (sample == null) return;
            station.state().setControl("spoon_x", sample.x());
            station.state().setControl("spoon_z", sample.z());
            long now = station.level().getGameTime();
            if (!bowl && sample.impulse() > 0) { ((PotStirring)container).handily$stirred(now); lastStir=now; if(container instanceof BrothState mixture)mixture.handily$mixture(mixture.handily$mixture()+sample.impulse()*.3F); }
            // Shared station cap: several cooks cannot multiply the native whisk impulse rate.
            if (bowl && sample.impulse() > 0 && (lastImpulse == Long.MIN_VALUE || now - lastImpulse >= 2)) {
                CraftingBowlBlockEntity be = (CraftingBowlBlockEntity) container;
                if (!be.isFinished() && be.hasRecipe()) {
                    be.markStirred(station.level());
                    be.addWhiskImpulse(sample.impulse());
                    lastImpulse = now;
                }
            }
        } else if(action.equals("collect") && bowl){
            var be=(CraftingBowlBlockEntity)container;var out=container.getItem(4);
            if(be.canTakeOut(station.level()) && !out.isEmpty() && InventoryDelivery.fits(player,List.of(out.copy()))){
                var taken=container.removeItem(4,out.getCount());InventoryDelivery.give(player,List.of(taken));ServingEffects.served(station,taken,ItemStack.EMPTY);publish(station);
            }
        } else if (action.equals("collect") && !bowl) {
            var meal=container.getItem(7).copy();var vessel=((MealVessel)container).handily$vessel().copy();
            int taken=MealServing.collect(player,container,data.getBoolean("one"),data.getBoolean("inventory"));
            if(taken>0)ServingEffects.served(station,meal,vessel);
            if (taken>0 && station.level() instanceof ServerLevel level)
                ((CookingPotBlockEntity)container).dropExperience(level,Vec3.atCenterOf(station.pos()));
            else if (taken==0 && !container.getItem(7).isEmpty()) player.displayClientMessage(Component.translatable("handson_farm_charm.serving_failed"),true);
        } else if (action.equals("serve") && !bowl) {
            ServingContainers.place(player, container, data.getBoolean("one"),
                stack -> MealServing.acceptsVessel(station.level(), stack));
        } else if (action.equals("take_liquid") && !bowl && player.inventoryMenu.getCarried().isEmpty()) {
            for (int i = 5; i >= 0; i--) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty() || !(KitchenIngredients.liquid(stack)||KitchenIngredients.dissolved(stack)||KitchenIngredients.egg(stack))) continue;
                if(!InventoryDelivery.give(player,List.of(stack.copy())))break;
                container.setItem(i,ItemStack.EMPTY);
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                snapshot();
                container.setChanged(); publish(station);
                break;
            }
        } else if (action.equals("fill") && Settings.BOOK.get()) {
            if(station.blockState().hasProperty(RoasterLid.OPEN) && !station.blockState().getValue(RoasterLid.OPEN))return;
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
            poured(station);snapshot();
            player.inventoryMenu.broadcastChanges();
        }
    }

    @Override public void tick(StationContext station) {
        if(!bowl){poured(station);snapshot();}
        if (bowl) {
            boolean changed = false;
            for (int i = 0; i < 4; i++) if (!ItemStack.matches(knownInputs[i], container.getItem(i))) changed = true;
            if (changed) { ((CraftingBowlBlockEntity) container).resetStirring(); snapshot(); }
        }
        publish(station);
    }
    private void poured(StationContext c){
        if(bowl || knownInputs==null)return;
        for(int i=0;i<6;i++){var item=container.getItem(i);var old=knownInputs[i];
            if(KitchenIngredients.liquid(item) && (!ItemStack.isSameItemSameComponents(item,old) || item.getCount()>old.getCount())){
                ((BrothState)container).handily$poured(c.level().getGameTime());break;
            }
        }
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
        station.state().setControl("lid_open",!station.blockState().hasProperty(RoasterLid.OPEN) || station.blockState().getValue(RoasterLid.OPEN)?1:0);
        station.state().setControl("stirring", lastStir!=Long.MIN_VALUE && station.level().getGameTime()-lastStir<12 ? 1 : 0);
        station.state().setControl("progress", progress);
        station.state().setControl("heated", heated ? 1 : 0);
        if(!bowl && container instanceof BrothState mixture){
            if(Broth.contents(container).isEmpty() && container.getItem(7).isEmpty())mixture.handily$mixture(0);
            station.state().setControl("mixture",mixture.handily$mixture());
        }
        int broth = bowl ? 0 : Broth.amount(container);
        station.state().setTank("broth", broth > 0 ? TankContents.of(StirAndCharm.id("soup"), broth) : TankContents.EMPTY);
        var data = station.state().data();
        if (!data.contains("book") || data.getBoolean("book") != Settings.BOOK.get()) {
            data.putBoolean("book", Settings.BOOK.get()); station.state().setData(data);
        }
        if (!bowl) MealServing.publish(station);
    }
}

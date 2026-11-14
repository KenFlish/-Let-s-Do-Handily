package ru.codex.stirandcharm;

import java.util.*;
import com.pockyl.diegesis.api.state.*;
import com.pockyl.diegesis.api.station.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.satisfy.farm_and_charm.core.block.entity.*;
import dev.architectury.registry.fuel.FuelRegistry;
import ru.codex.stirandcharm.mixin.*;

public final class ApplianceLogic implements StationLogic {
    private final Appliance kind;
    private final Container container;
    private final Map<UUID, StirMotion> motion = new HashMap<>();
    private long lastCrank = Long.MIN_VALUE;
    public ApplianceLogic(StationContext ctx, Appliance kind) {
        this.kind = kind;
        container = (Container) ctx.blockEntity();
        ctx.state().bindSlots("ingredients", SlotStorage.of(container, kind.start, kind.inputs));
        ctx.state().bindSlots("result", SlotStorage.of(container, kind.output, 1));
        if (kind == Appliance.STOVE) {
            ctx.state().bindSlots("fuel", SlotStorage.of(container, 4, 1));
        }
    }
    @Override public void onOpen(StationContext ctx, Player player) {
        if (kind == Appliance.ROASTER) ((RoasterAccess) container).handily$owner(player.getUUID());
        if (kind == Appliance.STOVE) ((StoveBlockEntity) container).setOwner(player.getUUID());
        tick(ctx);
    }
    @Override public void onClose(StationContext ctx, Player player) { motion.remove(player.getUUID()); }
    @Override public boolean mayPlace(StationContext ctx, Player player, SlotRef ref, ItemStack stack) {
        return switch (ref.group()) {
            case "ingredients" -> (kind != Appliance.ROASTER || accessible(ctx))
                && kind.recipes(ctx.level()).stream().anyMatch(h -> h.value().getIngredients().stream().anyMatch(i -> i.test(stack)));
            case "fuel" -> FuelRegistry.get(stack) > 0;
            default -> false;
        };
    }
    @Override public boolean mayTake(StationContext ctx, Player player, SlotRef ref) {
        if (kind == Appliance.ROASTER) return !ref.group().equals("result")
            && (!ref.group().equals("ingredients") || accessible(ctx));
        return true;
    }
    @Override public void onSlotChanged(StationContext ctx, SlotRef ref) { container.setChanged(); }
    @Override public void onTaken(StationContext ctx, Player player, SlotRef ref, ItemStack stack) {
        if (!(ctx.level() instanceof ServerLevel level) || !ref.group().equals("result")) return;
        if (kind == Appliance.ROASTER) ((RoasterBlockEntity) container).dropExperience(level, Vec3.atCenterOf(ctx.pos()));
        if (kind == Appliance.STOVE) ((StoveBlockEntity) container).dropExperience(level, Vec3.atCenterOf(ctx.pos()));
    }
    @Override public void onCustomAction(StationContext ctx, Player player, String action, CompoundTag data) {
        if (ctx.level().isClientSide) return;
        if (action.equals("crank") && kind == Appliance.MINCER) {
            var sample = motion.computeIfAbsent(player.getUUID(), id -> new StirMotion())
                .accept(ctx.level().getGameTime(), data.getFloat("x"), data.getFloat("z"));
            if (sample == null || sample.impulse() <= 0) return;
            long now = ctx.level().getGameTime();
            if (lastCrank != Long.MIN_VALUE && now - lastCrank < 10) return;
            var mincer = (MincerBlockEntity) container;
            if (!container.getItem(0).isEmpty() && kind.recipes(ctx.level()).stream()
                    .anyMatch(h -> h.value().getIngredients().stream().anyMatch(i -> i.test(container.getItem(0))))) {
                mincer.crank(); lastCrank = now;
            }
        } else if (kind == Appliance.STOVE && action.equals("ignite")) {
            var stove=(StoveBlockEntity)container;boolean was=stove.isLit();stove.ignite();
            if(!was && stove.isLit())ctx.level().playSound(null,ctx.pos(),net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
        } else if (kind == Appliance.STOVE && action.equals("extinguish")) {
            var stove=(StoveBlockEntity)container;boolean was=stove.isLit();stove.extinguish();
            if(was)ctx.level().playSound(null,ctx.pos(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,.7F,1);
        } else if (kind == Appliance.ROASTER && action.equals("lid")) {
            var state = ctx.blockState();
            if (!state.hasProperty(RoasterLid.OPEN)) return;
            ctx.level().setBlock(ctx.pos(),state.cycle(RoasterLid.OPEN),3);
            ctx.level().playSound(null,ctx.pos(),state.getValue(RoasterLid.OPEN)?net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE:net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,net.minecraft.sounds.SoundSource.BLOCKS,.45F,1.3F);
            container.setChanged(); tick(ctx);
        } else if (kind == Appliance.ROASTER && action.equals("collect")) {
            int taken=MealServing.collect(player,container,data.getBoolean("one"),data.getBoolean("inventory"));
            if (taken>0 && ctx.level() instanceof ServerLevel level)
                ((RoasterBlockEntity)container).dropExperience(level,Vec3.atCenterOf(ctx.pos()));
            else if (taken==0 && !container.getItem(7).isEmpty()) player.displayClientMessage(Component.translatable("handson_farm_charm.serving_failed"),true);
        } else if (action.equals("serve") && kind == Appliance.ROASTER) {
            ServingContainers.place(player, container, data.getBoolean("one"),
                stack -> kind.recipes(ctx.level()).stream().map(h -> kind.dish(h.value())).anyMatch(s -> !s.isEmpty() && s.is(stack.getItem())));
        } else if (action.equals("fill") && Settings.BOOK.get()) {
            if (kind == Appliance.ROASTER && !accessible(ctx)) return;
            ResourceLocation id = ResourceLocation.tryParse(data.getString("recipe"));
            if (id == null) return;
            var recipe = kind.recipes(ctx.level()).stream().filter(h -> h.id().equals(id)).findFirst();
            if (recipe.isEmpty()) return;
            // Plan in a canonical layout, then copy only the input and serving slots back.
            var canonical = new SimpleContainer(8);
            for (int i = 0; i < kind.inputs; i++) canonical.setItem(i, container.getItem(kind.start + i).copy());
            if (kind == Appliance.ROASTER) canonical.setItem(6, container.getItem(6).copy());
            var plan = RecipeTransfer.plan(player.getInventory(), canonical, kind.inputs, false,
                recipe.get().value().getIngredients(), ItemStack.EMPTY, kind == Appliance.STOVE);
            if (plan.isEmpty()) { player.displayClientMessage(Component.translatable("handson_farm_charm.fill_failed"), true); return; }
            plan.get().commit(player.getInventory(), canonical);
            if (kind==Appliance.MINCER && data.getBoolean("batch")) {
                ItemStack queued=canonical.getItem(0);
                for (int i=0;i<36 && queued.getCount()<queued.getMaxStackSize();i++) {
                    ItemStack stack=player.getInventory().getItem(i);
                    if (ItemStack.isSameItemSameComponents(stack,queued)) {
                        int n=Math.min(stack.getCount(),queued.getMaxStackSize()-queued.getCount()); queued.grow(n); stack.shrink(n);
                    }
                }
            }
            for (int i = 0; i < kind.inputs; i++) container.setItem(kind.start + i, canonical.getItem(i));
            if (kind == Appliance.ROASTER) container.setItem(6, canonical.getItem(6));
            container.setChanged(); player.inventoryMenu.broadcastChanges();
        }
    }
    private static boolean accessible(StationContext ctx) {
        return !ctx.blockState().hasProperty(RoasterLid.OPEN) || ctx.blockState().getValue(RoasterLid.OPEN);
    }
    @Override public void tick(StationContext ctx) {
        float progress = 0; boolean heated = true;
        if (kind == Appliance.ROASTER) {
            progress = ((RoasterAccess) container).handily$time() / (float) RoasterBlockEntity.getMaxRoastingTime();
            heated = ((RoasterBlockEntity) container).isBeingBurned();
            ctx.state().setControl("lid_open",accessible(ctx) ? 1 : 0);
        } else if (kind == Appliance.STOVE) {
            progress = ((StoveAccess) container).handily$time() / (float) StoveBlockEntity.TOTAL_COOKING_TIME;
            heated = ((StoveBlockEntity) container).isLit();
        } else progress = ((MincerBlockEntity) container).getCranked() / 80F;
        ctx.state().setControl("progress", progress);
        ctx.state().setControl("heated", heated ? 1 : 0);
        var data = ctx.state().data();
        if (!data.contains("book") || data.getBoolean("book") != Settings.BOOK.get()) {
            data.putBoolean("book", Settings.BOOK.get()); ctx.state().setData(data);
        }
        if (kind==Appliance.ROASTER) MealServing.publish(ctx);
    }
}

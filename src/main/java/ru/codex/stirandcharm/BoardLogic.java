package ru.codex.stirandcharm;

import java.util.*;
import com.pockyl.diegesis.api.state.*;
import com.pockyl.diegesis.api.station.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.*;
import ru.codex.stirandcharm.BoardAssembly.Dish;
import ru.codex.stirandcharm.BoardAssembly.Made;

/** Native board owns the cut item, knife, durability and experience; layers and dishes are ours. */
public final class BoardLogic implements StationLogic {
    private static final ThreadLocal<StationContext> CAPTURE = new ThreadLocal<>();
    private static final int BATCH = 16;
    private static final int CHOP_GAP = 4;

    private final CuttingBoardBlockEntity board;
    private BoardCutPlan plan;
    private ItemStack cutting = ItemStack.EMPTY;
    private int marks;
    private long lastChop = Long.MIN_VALUE;

    public static void registerOutputs() {
        CuttingBoardBlockEntity.registerOutputHandler((level, pos, stack) -> {
            var ctx = CAPTURE.get();
            if (ctx == null || ctx.level() != level || !ctx.pos().equals(pos)) return stack;
            ItemStack left = stack.copy();
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < 8 && !left.isEmpty(); i++) {
                var ref = SlotRef.of("result", i);
                var old = ctx.state().getItem(ref);
                if (pass == 0 && !old.isEmpty() && ItemStack.isSameItemSameComponents(old, left)) {
                    int n = Math.min(left.getCount(), old.getMaxStackSize() - old.getCount());
                    if (n > 0) { var next = old.copy(); next.grow(n); left.shrink(n); ctx.state().setItem(ref, next); }
                } else if (pass == 1 && old.isEmpty()) {
                    int n = Math.min(left.getCount(), left.getMaxStackSize());
                    ctx.state().setItem(ref, left.copyWithCount(n));
                    left.shrink(n);
                }
            }
            return left;
        });
    }

    public BoardLogic(StationContext ctx) {
        board = (CuttingBoardBlockEntity) ctx.blockEntity();
        ctx.state().bindSlots("ingredients", new SlotStorage() {
            public int size() { return 9; }
            public ItemStack get(int i) { return i < board.getItems().size() ? board.getItems().get(i) : ItemStack.EMPTY; }
            public void set(int i, ItemStack stack) {
                var items = new ArrayList<>(board.getItems());
                if (i < items.size()) { if (stack.isEmpty()) items.remove(i); else items.set(i, stack); }
                else if (!stack.isEmpty()) items.add(stack);
                contents(items);
            }
        });
        ctx.state().bindSlots("pending", new SlotStorage() {
            public int size() { return 1; }
            public ItemStack get(int i) { return board.getPending(); }
            public void set(int i, ItemStack s) { board.startCut(s); }
        });
        ctx.state().bindSlots("knife", new SlotStorage() {
            public int size() { return 1; }
            public ItemStack get(int i) { return board.getKnife(); }
            public void set(int i, ItemStack s) { board.setKnife(s); }
        });
        restart(ctx);
    }

    private void contents(List<ItemStack> items) {
        board.setContents(ItemStack.EMPTY);
        for (var item : items) if (!item.isEmpty()) board.addItem(item.copy());
    }

    private List<ItemStack> layers() {
        var list = new ArrayList<ItemStack>();
        for (var stack : board.getItems()) if (!stack.isEmpty()) list.add(stack.copy());
        return list;
    }

    private void restart(StationContext ctx) {
        cutting = board.getPending().copyWithCount(1);
        var recipe = BoardRecipes.cut(ctx.level(), cutting);
        plan = recipe.map(h -> BoardCutPlan.of(ctx.level(), cutting, h.value())).orElse(null);
        marks = 0;
    }

    public boolean mayPlace(StationContext ctx, Player p, SlotRef ref, ItemStack s) { return false; }
    public boolean mayTake(StationContext ctx, Player p, SlotRef ref) { return ref.group().equals("result"); }
    public void onOpen(StationContext ctx, Player p) { tick(ctx); }

    public void onCustomAction(StationContext ctx, Player player, String action, CompoundTag data) {
        if (ctx.level().isClientSide) return;
        switch (action) {
            case "place" -> place(ctx, player, data);
            case "carve" -> carve(ctx, player);
            case "take" -> take(ctx, player);
            case "chop" -> chop(ctx, player, data);
            case "assemble" -> assemble(ctx, player);
            case "fill" -> fill(ctx, player, data);
            default -> {}
        }
        tick(ctx);
        player.inventoryMenu.broadcastChanges();
    }

    private ItemStack source(Player p, int from) {
        return from == -1 ? p.inventoryMenu.getCarried() : from >= 0 && from < 36 ? p.getInventory().getItem(from) : ItemStack.EMPTY;
    }

    private void consumed(Player p, int from, ItemStack s) {
        if (s.isEmpty()) {
            if (from == -1) p.inventoryMenu.setCarried(ItemStack.EMPTY);
            else p.getInventory().setItem(from, ItemStack.EMPTY);
        }
        p.getInventory().setChanged();
    }

    private void sound(StationContext c, SoundEvent event, float pitch) {
        c.level().playSound(null, c.pos(), event, SoundSource.BLOCKS, .6F, pitch);
    }

    private void give(Player p, ItemStack s) {
        while (!s.isEmpty()) {
            ItemStack part = s.split(s.getMaxStackSize());
            if (!p.getInventory().add(part)) p.drop(part, false);
        }
    }

    private void effect(StationContext c, String id, String node, CompoundTag data) {
        c.effect(StirAndCharm.id(id), node, data);
    }

    private void refuse(StationContext c, ItemStack stack) {
        var data = new CompoundTag();
        if (!stack.isEmpty()) data.put("stack", stack.copyWithCount(1).saveOptional(c.level().registryAccess()));
        effect(c, "refused", "surface", data);
    }

    private void carve(StationContext c, Player p) {
        ItemStack s = p.inventoryMenu.getCarried();
        if (!BoardTools.canStick(s) || !board.isEmpty() || board.isCutting() || board.hasKnife()) return;
        board.setKnife(s.split(1));
        consumed(p, -1, s);
        sound(c, SoundEvents.AXE_STRIP, .8F);
    }

    private void place(StationContext c, Player p, CompoundTag tag) {
        int from = tag.contains("from") ? tag.getInt("from") : -1;
        ItemStack s = source(p, from);
        if (s.isEmpty() || BoardTools.canStick(s)) return;
        boolean one = tag.getBoolean("one");
        var level = c.level();
        var layers = layers();

        if (board.isCutting()) {
            var base = board.getPending();
            if (base.getCount() == 1 && !ItemStack.isSameItemSameComponents(base, s)
                && BoardAssembly.fits(level, List.of(base.copyWithCount(1), s.copyWithCount(1)))) {
                board.startCut(ItemStack.EMPTY);
                contents(List.of(base.copyWithCount(1)));
                layers = layers();
            } else if (ItemStack.isSameItemSameComponents(base, s)) {
                int n = Math.min(one ? 1 : s.getCount(), BATCH - base.getCount());
                if (n > 0) {
                    var queued = base.copy(); queued.grow(n); s.shrink(n);
                    board.startCut(queued); consumed(p, from, s);
                    sound(c, SoundEvents.ITEM_PICKUP, 1.1F);
                }
                return;
            } else {
                refuse(c, s);
                return;
            }
        }

        if (!layers.isEmpty()) {
            var next = new ArrayList<>(layers);
            next.add(s.copyWithCount(1));
            if (!BoardAssembly.fits(level, next)) { refuse(c, s); return; }
            int batch = layers.get(0).getCount();
            int n = Math.min(one ? 1 : s.getCount(), Math.max(1, batch));
            if (n <= 0) return;
            if (board.hasKnife()) give(p, board.takeKnife());
            s.shrink(n); consumed(p, from, s);
            board.addItem(s.copyWithCount(n));
            sound(c, SoundEvents.ITEM_PICKUP, 1.1F);
            finishIfWanted(c, p);
            return;
        }

        boolean cuttable = BoardRecipes.cut(level, s).isPresent();
        if (cuttable) {
            if (board.hasKnife()) give(p, board.takeKnife());
            int n = Math.min(one ? 1 : s.getCount(), BATCH);
            board.startCut(s.split(n)); consumed(p, from, s);
            restart(c);
            sound(c, SoundEvents.ITEM_PICKUP, 1.05F);
            return;
        }
        if (BoardAssembly.isBase(level, s)) {
            if (board.hasKnife()) give(p, board.takeKnife());
            int n = Math.min(one ? 1 : s.getCount(), BATCH);
            s.shrink(n); consumed(p, from, s);
            board.addItem(s.copyWithCount(n));
            sound(c, SoundEvents.ITEM_PICKUP, 1.1F);
            finishIfWanted(c, p);
            return;
        }
        refuse(c, s);
    }

    private void finishIfWanted(StationContext c, Player p) {
        var layers = layers();
        if (layers.isEmpty()) return;
        var complete = BoardAssembly.complete(c.level(), layers);
        if (complete.isEmpty()) return;
        var selected = ((BoardSelection) board).handily$recipe();
        boolean wanted = selected != null && selected.equals(complete.get().holder().id());
        if (wanted || !BoardAssembly.canGrow(c.level(), layers)) assemble(c, p);
    }

    private void take(StationContext c, Player p) {
        var layers = layers();
        if (!layers.isEmpty()) {
            var items = new ArrayList<>(board.getItems());
            for (int i = items.size() - 1; i >= 0; i--) {
                var stack = items.get(i);
                if (stack.isEmpty()) continue;
                ItemStack one = stack.copyWithCount(1);
                stack.shrink(1);
                if (stack.isEmpty()) items.remove(i);
                contents(items);
                give(p, one);
                sound(c, SoundEvents.ITEM_PICKUP, .85F);
                return;
            }
        }
        if (board.isCutting()) {
            var s = board.getPending().copy();
            board.startCut(ItemStack.EMPTY);
            give(p, s);
            restart(c);
        } else if (board.hasKnife()) {
            give(p, board.takeKnife());
        }
        sound(c, SoundEvents.ITEM_PICKUP, .85F);
    }

    private record Tool(int slot, ItemStack stack) {}

    private Tool tool(StationContext c, Player p) {
        ItemStack input = board.getPending();
        if (board.hasKnife() && BoardTools.supports(board.getKnife(), input)) return new Tool(-2, board.getKnife());
        if (BoardTools.supports(p.inventoryMenu.getCarried(), input)) return new Tool(-1, p.inventoryMenu.getCarried());
        int selected = p.getInventory().selected;
        if (BoardTools.supports(p.getInventory().getItem(selected), input)) return new Tool(selected, p.getInventory().getItem(selected));
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (BoardTools.supports(p.getInventory().getItem(i), input)) return new Tool(i, p.getInventory().getItem(i));
        return null;
    }

    private void chop(StationContext c, Player p, CompoundTag data) {
        long now = c.level().getGameTime();
        if (!board.isCutting() || plan == null || lastChop != Long.MIN_VALUE && now - lastChop < CHOP_GAP) return;
        int mark = data.getInt("mark");
        float at = data.getFloat("at");
        if (mark != marks || !Float.isFinite(at) || at < 0F || at > 16F) { refuse(c, ItemStack.EMPTY); return; }
        Tool tool = tool(c, p);
        if (tool == null) { refuse(c, ItemStack.EMPTY); return; }
        var recipe = BoardRecipes.cut(c.level(), board.getPending());
        if (recipe.isEmpty() || !room(c, outputs(recipe.get().value()))) { refuse(c, ItemStack.EMPTY); return; }

        float spot = plan.spot(marks), angle = plan.angle(marks);
        lastChop = now;
        marks++;
        if (marks < plan.marks()) {
            var tag = new CompoundTag();
            tag.putInt("mark", marks - 1);
            tag.putFloat("spot", spot);
            tag.putFloat("angle", angle);
            tag.putBoolean("done", false);
            effect(c, "chopped", "item", tag);
            sound(c, SoundEvents.WOOD_HIT, 1.2F);
            return;
        }

        ItemStack parked = board.getKnife();
        if (tool.slot != -2) board.setKnife(tool.stack.copy());
        // The marks are ours; the native block only counts its own pending chops. One native
        // cut per item finishes the batch, without spending extra time on each item's marks.
        int required = CuttingBoardBlockEntity.requiredChops(tool.stack, recipe.get().value());
        int done = 0;
        CAPTURE.set(c);
        try {
            while (done < BATCH && board.isCutting() && room(c, outputs(recipe.get().value()))) {
                ((ChopState) board).handily$pendingChops(Math.max(0, required - 1));
                board.chopPending(c.level());
                done++;
            }
        } finally {
            CAPTURE.remove();
            if (tool.slot != -2) {
                var used = board.takeKnife();
                if (tool.slot == -1) p.inventoryMenu.setCarried(used);
                else p.getInventory().setItem(tool.slot, used);
                board.setKnife(parked);
            }
        }
        if (done == 0) { refuse(c, ItemStack.EMPTY); restart(c); return; }
        var tag = new CompoundTag();
        tag.putInt("mark", plan.marks());
        tag.putFloat("spot", spot);
        tag.putFloat("angle", angle);
        tag.putBoolean("done", true);
        tag.putInt("count", done);
        effect(c, "chopped", "item", tag);
        restart(c);
        sound(c, SoundEvents.ITEM_PICKUP, 1.2F);
    }

    private void assemble(StationContext c, Player p) {
        if (board.isCutting()) return;
        var layers = layers();
        if (layers.isEmpty()) return;
        var selected = ((BoardSelection) board).handily$recipe();
        Dish dish = null;
        if (selected != null) {
            dish = BoardAssembly.all(c.level()).stream().filter(d -> d.holder().id().equals(selected)).findFirst().orElse(null);
        }
        if (dish == null) dish = BoardAssembly.complete(c.level(), layers).orElse(null);
        if (dish == null) { refuse(c, ItemStack.EMPTY); return; }
        Made made = BoardAssembly.make(dish, layers, c.level(), BATCH);
        if (made.rounds() == 0) { refuse(c, ItemStack.EMPTY); return; }
        if (!room(c, made.dishes())) { refuse(c, ItemStack.EMPTY); return; }
        contents(List.of());
        made.remaining().forEach(left -> give(p, left));
        made.leftovers().forEach(left -> give(p, left));
        CAPTURE.set(c);
        try {
            for (var result : made.dishes()) CuttingBoardBlockEntity.output(c.level(), c.pos(), result.copy());
        } finally {
            CAPTURE.remove();
        }
        var tag = new CompoundTag();
        tag.putString("finish", dish.finish().name());
        var from = new ListTag();
        layers.forEach(layer -> from.add(layer.copyWithCount(1).saveOptional(c.level().registryAccess())));
        tag.put("layers", from);
        if (!made.dishes().isEmpty()) tag.put("dish", made.dishes().get(0).saveOptional(c.level().registryAccess()));
        effect(c, "assembled", "surface", tag);
        ((BoardSelection) board).handily$recipe(null);
        sound(c, SoundEvents.ITEM_PICKUP, 1.2F);
    }

    private void fill(StationContext c, Player p, CompoundTag data) {
        if (!Settings.BOOK.get()) return;
        var id = ResourceLocation.tryParse(data.getString("recipe"));
        var holder = BoardRecipes.all(c.level()).stream().filter(h -> h.id().equals(id)).findFirst();
        if (holder.isEmpty()) return;
        boolean cut = holder.get().value() instanceof CuttingBoardRecipe;
        if (board.isCutting()) return;
        if (cut && !board.isEmpty()) return;
        var plan = BoardRecipes.plan(p.getInventory(), board.getItems(), holder.get().value());
        if (plan.isEmpty()) { p.displayClientMessage(Component.translatable("handson_farm_charm.fill_failed"), true); return; }
        var temp = new SimpleContainer(9);
        plan.get().commit(p.getInventory(), temp);
        ((BoardSelection) board).handily$recipe(cut ? null : holder.get().id());
        if (board.hasKnife()) give(p, board.takeKnife());
        if (cut) {
            ItemStack input = temp.getItem(0);
            if (data.getBoolean("batch")) for (int i = 0; i < 36 && input.getCount() < BATCH; i++) {
                ItemStack next = p.getInventory().getItem(i);
                if (ItemStack.isSameItemSameComponents(input, next)) {
                    int n = Math.min(next.getCount(), BATCH - input.getCount());
                    input.grow(n); next.shrink(n);
                }
            }
            board.startCut(input);
            restart(c);
        } else {
            var list = new ArrayList<ItemStack>();
            for (int i = 0; i < 9; i++) if (!temp.getItem(i).isEmpty()) list.add(temp.getItem(i));
            contents(list);
            finishIfWanted(c, p);
        }
        sound(c, SoundEvents.ITEM_PICKUP, 1.1F);
    }

    private List<ItemStack> outputs(CuttingBoardRecipe r) {
        var list = new ArrayList<ItemStack>();
        list.add(r.getResult());
        list.addAll(r.getByproducts());
        return list;
    }

    private boolean room(StationContext c, List<ItemStack> outputs) {
        var container = new SimpleContainer(8);
        for (int i = 0; i < 8; i++) container.setItem(i, c.state().getItem(SlotRef.of("result", i)).copy());
        for (var out : outputs) if (!container.addItem(out.copy()).isEmpty()) return false;
        return true;
    }

    public void tick(StationContext c) {
        var pending = board.getPending();
        if (!ItemStack.isSameItemSameComponents(pending, cutting)) restart(c);
        int required = plan == null ? 1 : plan.marks();
        c.state().setControl("progress", marks / (float) Math.max(1, required));
        c.state().setControl("chops", marks);
        c.state().setControl("required", Math.max(1, required));
        var data = c.state().data();
        var recipeId = ((BoardSelection) board).handily$recipe();
        String selected = recipeId == null ? "" : recipeId.toString();
        String dish = BoardAssembly.complete(c.level(), layers()).map(d -> d.holder().id().toString()).orElse("");
        boolean changed = !data.getString("selected_recipe").equals(selected) || !data.getString("dish").equals(dish)
            || !data.contains("book") || data.getBoolean("book") != Settings.BOOK.get()
            || data.getInt("marks") != marks || data.getInt("required") != required
            || data.getInt("style") != (plan == null ? -1 : plan.style().ordinal());
        if (changed) {
            data.putString("selected_recipe", selected);
            data.putString("dish", dish);
            data.putBoolean("book", Settings.BOOK.get());
            data.putInt("marks", marks);
            data.putInt("required", required);
            data.putInt("style", plan == null ? -1 : plan.style().ordinal());
            var spots = new ListTag();
            var angles = new ListTag();
            if (plan != null) for (int i = 0; i < plan.marks(); i++) {
                spots.add(FloatTag.valueOf(plan.spot(i)));
                angles.add(FloatTag.valueOf(plan.angle(i)));
            }
            data.put("spots", spots);
            data.put("angles", angles);
            c.state().setData(data);
        }
    }
}
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
    private static final ThreadLocal<Player> COOK = new ThreadLocal<>();
    private static final ThreadLocal<List<ItemStack>> DELIVERED = new ThreadLocal<>();
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
            var cook=COOK.get();if(cook!=null){DELIVERED.get().add(left);return ItemStack.EMPTY;}
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
        var old=ctx.state().getItems("result");var savedOutputs=((BoardSelection)board).handily$outputs();
        for(int i=0;i<8;i++)if(savedOutputs.get(i).isEmpty() && !old[i].isEmpty())savedOutputs.set(i,old[i].copy());
        ctx.state().bindSlots("result",new SlotStorage(){
            public int size(){return 8;}
            public ItemStack get(int i){return ((BoardSelection)board).handily$outputs().get(i);}
            public void set(int i,ItemStack stack){((BoardSelection)board).handily$outputs().set(i,stack);((BoardSelection)board).handily$sync();}
        });
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
        var saved=((BoardSelection)board).handily$cuts();
        boolean same=ItemStack.isSameItemSameComponents(cutting,ItemStack.parseOptional(ctx.level().registryAccess(),saved.getCompound("item")));
        plan=same?BoardCutPlan.load(saved):null;
        marks=plan==null?0:Math.clamp(saved.getInt("completed"),0,plan.marks()-1);
        lastChop=plan!=null && saved.contains("last")?saved.getLong("last"):Long.MIN_VALUE;
        if(plan==null)plan = recipe.map(h -> BoardCutPlan.of(ctx.level(), cutting, h.value())).orElse(null);
        saveCuts(ctx);
    }
    private void saveCuts(StationContext c){var saved=plan==null?new CompoundTag():plan.save();if(plan!=null){saved.put("item",cutting.saveOptional(c.level().registryAccess()));saved.putInt("completed",marks);saved.putLong("last",lastChop);}((BoardSelection)board).handily$cuts(saved);}

    public boolean mayPlace(StationContext ctx, Player p, SlotRef ref, ItemStack s) { return false; }
    public boolean mayTake(StationContext ctx, Player p, SlotRef ref) { return ref.group().equals("result"); }
    public void onOpen(StationContext ctx, Player p) { migrate(ctx,p);tick(ctx); }
    private void migrate(StationContext c,Player p){
        for(int i=0;i<8;i++){var ref=SlotRef.of("result",i);var stack=c.state().getItem(ref);if(stack.isEmpty())continue;
            var left=stack.copy();p.getInventory().add(left);c.state().setItem(ref,left);}
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
    }

    public void onCustomAction(StationContext ctx, Player player, String action, CompoundTag data) {
        if (ctx.level().isClientSide) return;
        switch (action) {
            case "place" -> place(ctx, player, data);
            case "carve" -> carve(ctx, player);
            case "take" -> take(ctx, player);
            case "chop" -> chop(ctx, player, data);
            case "assemble" -> assemble(ctx, player, data.getBoolean("all")?BATCH:1);
            case "fill" -> fill(ctx, player, data);
            default -> {}
        }
        migrate(ctx,player);tick(ctx);
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
        s=s.copy();
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
        if(board.hasKnife() && !InventoryDelivery.fits(p,List.of(board.getKnife().copy()))){refuse(c,s);return;}
        boolean one = tag.getBoolean("one");
        var level = c.level();
        var layers = layers();

        if (board.isCutting()) {
            var base = board.getPending();
            if (!ItemStack.isSameItemSameComponents(base, s)
                && BoardAssembly.fits(level, List.of(base.copyWithCount(1), s.copyWithCount(1)))) {
                int n = Math.min(base.getCount(), BATCH);
                var first = base.copyWithCount(n);
                if (base.getCount() > n) give(p, base.copyWithCount(base.getCount() - n));
                board.startCut(ItemStack.EMPTY);
                contents(List.of(first));
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
            // Split before the source can become empty: copyWithCount(0) is an empty stack, and the
            // native board saves its item list without rejecting empty entries.
            ItemStack placed = s.split(n);
            consumed(p, from, s);
            board.addItem(placed);
            placed(c,placed,board.getItems().size()-1);
            sound(c, SoundEvents.ITEM_PICKUP, 1.1F);
            finishIfWanted(c, p);
            return;
        }

        boolean startAssembly=BoardAssembly.fits(level,List.of(s.copyWithCount(1)));
        // Hands-On: a cuttable first ingredient always lies under the knife. A matching second
        // ingredient above converts it into layers; browsing a cookbook tab never changes the action.
        boolean cuttable = BoardRecipes.cut(level, s).isPresent();
        if (cuttable) {
            ((BoardSelection)board).handily$recipe(null);
            if (board.hasKnife()) give(p, board.takeKnife());
            int n = Math.min(one ? 1 : s.getCount(), BATCH);
            board.startCut(s.split(n)); consumed(p, from, s);
            restart(c);
            sound(c, SoundEvents.ITEM_PICKUP, 1.05F);
            return;
        }
        if (startAssembly) {
            ((BoardSelection)board).handily$recipe(null);
            if (board.hasKnife()) give(p, board.takeKnife());
            int n = Math.min(one ? 1 : s.getCount(), BATCH);
            ItemStack placed = s.split(n);
            consumed(p, from, s);
            board.addItem(placed);
            placed(c,placed,board.getItems().size()-1);
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
    private void placed(StationContext c,ItemStack stack,int index){var tag=new CompoundTag();tag.put("stack",stack.copyWithCount(1).saveOptional(c.level().registryAccess()));tag.putInt("index",index);effect(c,"placed","surface",tag);}
    private void received(StationContext c,ItemStack stack){var tag=new CompoundTag();tag.put("stack",stack.copyWithCount(1).saveOptional(c.level().registryAccess()));effect(c,"received","surface",tag);}

    private void take(StationContext c, Player p) {
        var layers = layers();
        if (!layers.isEmpty()) {
            var items = new ArrayList<>(board.getItems());
            for (int i = items.size() - 1; i >= 0; i--) {
                var stack = items.get(i);
                if (stack.isEmpty()) continue;
                if(!InventoryDelivery.fits(p,List.of(stack))){refuse(c,stack);return;}
                items.remove(i);
                contents(items);
                DeliveryPresentation.before(c,p,List.of(stack),.3F);
                give(p, stack.copy());
                received(c,stack);
                sound(c, SoundEvents.ITEM_PICKUP, .85F);
                return;
            }
        }
        if (board.isCutting()) {
            var s = board.getPending().copy();
            if(!InventoryDelivery.fits(p,List.of(s))){refuse(c,s);return;}
            board.startCut(ItemStack.EMPTY);
            DeliveryPresentation.before(c,p,List.of(s),.3F);
            give(p, s);
            received(c,s);
            restart(c);
        } else if (board.hasKnife()) {
            if(!InventoryDelivery.fits(p,List.of(board.getKnife()))){refuse(c,board.getKnife());return;}
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
        if (mark != marks || !Float.isFinite(at) || at < 0F || at > 16F
            || !Float.isFinite(data.getFloat("x")) || !Float.isFinite(data.getFloat("z"))
            || !data.contains("x") || !data.contains("z") || Math.abs(at-data.getFloat("z"))>.01F
            || (data.getFloat("x")<1 || data.getFloat("x")>11 || data.getFloat("z")<3 || data.getFloat("z")>13)) { refuse(c, ItemStack.EMPTY); return; }
        Tool tool = tool(c, p);
        if (tool == null) { refuse(c, ItemStack.EMPTY); return; }
        var recipe = BoardRecipes.cut(c.level(), board.getPending());
        if (recipe.isEmpty() || !InventoryDelivery.fits(p, multiplied(outputs(recipe.get().value()),board.getPending().getCount()))) { refuse(c, ItemStack.EMPTY); return; }

        float spot = plan.spot(marks), angle = plan.angle(marks);
        lastChop = now;
        marks++;
        if (marks < plan.marks()) {
            saveCuts(c);
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
        var delivered=new ArrayList<ItemStack>();
        CAPTURE.set(c);COOK.set(p);DELIVERED.set(delivered);
        try {
            while (done < BATCH && board.isCutting() && !board.getKnife().isEmpty()
                && InventoryDelivery.fits(p,outputs(recipe.get().value()))) {
                ((ChopState) board).handily$pendingChops(Math.max(0, required - 1));
                board.chopPending(c.level());
                done++;
            }
        } finally {
            CAPTURE.remove();COOK.remove();
            DELIVERED.remove();
            if (tool.slot != -2) {
                var used = board.takeKnife();
                if (tool.slot == -1) p.inventoryMenu.setCarried(used);
                else p.getInventory().setItem(tool.slot, used);
                board.setKnife(parked);
            }
        }
        if (done == 0) { refuse(c, ItemStack.EMPTY); restart(c); return; }
        DeliveryPresentation.before(c,p,delivered,.3F);
        InventoryDelivery.give(p,delivered);
        var tag = new CompoundTag();
        tag.putInt("mark", plan.marks());
        tag.putFloat("spot", spot);
        tag.putFloat("angle", angle);
        tag.putBoolean("done", true);
        tag.putInt("count", done);
        tag.put("stack",recipe.get().value().getResult().saveOptional(c.level().registryAccess()));
        effect(c, "chopped", "item", tag);
        ((BoardSelection)board).handily$cuts(new CompoundTag());
        restart(c);
        sound(c, SoundEvents.ITEM_PICKUP, 1.2F);
    }

    private void assemble(StationContext c,Player p){assemble(c,p,BATCH);}
    private void assemble(StationContext c, Player p,int limit) {
        if (board.isCutting()) return;
        var layers = layers();
        if (layers.isEmpty()) return;
        var selected = ((BoardSelection) board).handily$recipe();
        Dish dish = null;
        if (selected != null) {
            dish = BoardAssembly.all(c.level()).stream().filter(d -> d.holder().id().equals(selected)).findFirst().orElse(null);
        }
        if (dish == null) dish = BoardAssembly.complete(c.level(), layers).orElseGet(()->BoardAssembly.all(c.level()).stream().filter(d -> BoardAssembly.ready(d,layers)).findFirst().orElse(null));
        if (dish == null) { refuse(c, ItemStack.EMPTY); return; }
        var working=new ArrayList<ItemStack>(layers);
        var planned=InventoryDelivery.copy(p);int vesselSlot=-2,cost=0;
        if(BoardAssembly.ready(dish,layers)){
            var finish=((CuttingBoardAssemblyRecipe)dish.holder().value()).getFinisher().orElseThrow();
            var held=p.inventoryMenu.getCarried();
            if(finish.test(held))vesselSlot=-1;
            else for(int i=0;i<36;i++)if(finish.test(planned[i])){vesselSlot=i;break;}
            if(vesselSlot==-2){refuse(c,ItemStack.EMPTY);return;}
            var offered=vesselSlot==-1?held:planned[vesselSlot];
            cost=Math.min(limit,Math.min(offered.getCount(),layers.stream().mapToInt(ItemStack::getCount).min().orElse(0)));
            working.add(offered.copyWithCount(cost));
        }
        Made made = BoardAssembly.make(dish, working, c.level(), limit);
        if (made.rounds() == 0) { refuse(c, ItemStack.EMPTY); return; }
        if(vesselSlot>=0)planned[vesselSlot].shrink(made.rounds());
        var delivered=new ArrayList<ItemStack>();delivered.addAll(made.dishes());delivered.addAll(made.leftovers());delivered.addAll(made.remaining());
        for(var out:delivered)if(!InventoryDelivery.insert(planned,out)){refuse(c,ItemStack.EMPTY);return;}
        if(vesselSlot==-1){var held=p.inventoryMenu.getCarried();held.shrink(made.rounds());p.inventoryMenu.setCarried(held.isEmpty()?ItemStack.EMPTY:held);}
        DeliveryPresentation.before(c,p,delivered,1.45F);
        contents(List.of());InventoryDelivery.commit(p,planned);
        for(var result:made.dishes())result.onCraftedBy(c.level(),p,result.getCount());
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

    private static List<ItemStack> multiplied(List<ItemStack> outputs,int n){return outputs.stream().map(s -> s.copyWithCount(s.getCount()*Math.min(BATCH,n))).toList();}
    private boolean room(StationContext c, List<ItemStack> outputs) {
        var container = new SimpleContainer(8);
        for (int i = 0; i < 8; i++) container.setItem(i, c.state().getItem(SlotRef.of("result", i)).copy());
        for (var out : outputs) if (!container.addItem(out.copy()).isEmpty()) return false;
        return true;
    }

    public void tick(StationContext c) {
        if(c.level().getGameTime()%20==0)for(var player:c.users())migrate(c,player);
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
        boolean finishReady=BoardAssembly.all(c.level()).stream().anyMatch(d -> BoardAssembly.ready(d,layers()));
        var finishDish=BoardAssembly.all(c.level()).stream().filter(d -> BoardAssembly.ready(d,layers())).findFirst();
        String vessel=finishDish.flatMap(d -> ((CuttingBoardAssemblyRecipe)d.holder().value()).getFinisher()).filter(i -> i.getItems().length>0).map(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i.getItems()[0].getItem()).toString()).orElse("");
        boolean changed = data.getBoolean("finish_ready")!=finishReady || !data.getString("vessel").equals(vessel) || !data.getString("selected_recipe").equals(selected) || !data.getString("dish").equals(dish)
            || !data.contains("book") || data.getBoolean("book") != Settings.BOOK.get()
            || data.getInt("marks") != marks || data.getInt("required") != required
            || data.getInt("style") != (plan == null ? -1 : plan.style().ordinal());
        if (changed) {
            data.putBoolean("finish_ready",finishReady);data.putString("vessel",vessel);
            var vessels=new ListTag();finishDish.flatMap(d -> ((CuttingBoardAssemblyRecipe)d.holder().value()).getFinisher()).ifPresent(f -> {for(var item:f.getItems())vessels.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString()));});data.put("vessels",vessels);
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

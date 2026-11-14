package ru.codex.stirandcharm;

import java.util.*;
import com.pockyl.diegesis.api.state.*;
import com.pockyl.diegesis.api.station.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.satisfy.farm_and_charm.core.block.CuttingBoardBlock;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.*;
import net.satisfy.farm_and_charm.core.registry.RecipeTypeRegistry;

/** Native board owns ingredients, pending cut, knife, animation, durability and experience. */
public final class BoardLogic implements StationLogic {
    private static final ThreadLocal<StationContext> CAPTURE = new ThreadLocal<>();
    private final CuttingBoardBlockEntity board;
    private long lastChop=Long.MIN_VALUE;
    public static void registerOutputs() {
        CuttingBoardBlockEntity.registerOutputHandler((level,pos,stack) -> {
            var ctx=CAPTURE.get();
            if (ctx==null || ctx.level()!=level || !ctx.pos().equals(pos)) return stack;
            ItemStack left=stack.copy();
            for(int pass=0;pass<2;pass++) for(int i=0;i<8 && !left.isEmpty();i++) {
                var ref=SlotRef.of("result",i); var old=ctx.state().getItem(ref);
                if (pass==0 && !old.isEmpty() && ItemStack.isSameItemSameComponents(old,left)) {
                    int n=Math.min(left.getCount(),old.getMaxStackSize()-old.getCount());
                    if(n>0) { var next=old.copy();next.grow(n);left.shrink(n);ctx.state().setItem(ref,next); }
                } else if(pass==1 && old.isEmpty()) {
                    int n=Math.min(left.getCount(),left.getMaxStackSize());ctx.state().setItem(ref,left.copyWithCount(n));left.shrink(n);
                }
            }
            return left; // Native handler safely drops any exceptional overflow.
        });
    }
    public BoardLogic(StationContext ctx) {
        board=(CuttingBoardBlockEntity)ctx.blockEntity();
        ctx.state().bindSlots("ingredients",new SlotStorage() {
            public int size(){return 5;}
            public ItemStack get(int i){return i<board.getItems().size()?board.getItems().get(i):ItemStack.EMPTY;}
            public void set(int i,ItemStack stack){
                var items=new ArrayList<>(board.getItems());
                if(i<items.size()) { if(stack.isEmpty()) items.remove(i);else items.set(i,stack); }
                else if(!stack.isEmpty()) items.add(stack);
                board.setContents(ItemStack.EMPTY);for(var item:items) board.addItem(item);
            }
        });
        ctx.state().bindSlots("pending",new SlotStorage() {
            public int size(){return 1;} public ItemStack get(int i){return board.getPending();}
            public void set(int i,ItemStack s){board.startCut(s);}
        });
        ctx.state().bindSlots("knife",new SlotStorage() {
            public int size(){return 1;}public ItemStack get(int i){return board.getKnife();}
            public void set(int i,ItemStack s){board.setKnife(s);}
        });
    }
    public boolean mayPlace(StationContext ctx,Player player,SlotRef ref,ItemStack stack) {
        return switch(ref.group()) {
            case "knife" -> BoardTools.knife(stack) && board.isEmpty() && !board.isCutting();
            case "pending" -> board.hasKnife() && board.isEmpty() && !board.isCutting() &&
                ctx.level().getRecipeManager().getRecipeFor(RecipeTypeRegistry.CUTTING_BOARD_RECIPE_TYPE.get(),new SingleRecipeInput(stack),ctx.level()).isPresent();
            case "ingredients" -> {
                if(board.hasKnife() || board.isCutting() || ref.index()!=board.getItems().size()) yield false;
                var candidate=new ArrayList<>(board.getItems());candidate.add(stack.copyWithCount(1));
                yield ctx.level().getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.CUTTING_BOARD_ASSEMBLY_RECIPE_TYPE.get())
                    .stream().anyMatch(h -> h.value().accepts(candidate));
            }
            default -> false;
        };
    }
    public boolean mayTake(StationContext ctx,Player player,SlotRef ref) {
        return switch(ref.group()) {
            case "knife" -> !board.isCutting();
            case "ingredients" -> ref.index()==board.getItems().size()-1;
            default -> true;
        };
    }
    public void onOpen(StationContext ctx,Player player){tick(ctx);}
    public void onCustomAction(StationContext ctx,Player player,String action,CompoundTag data) {
        if(ctx.level().isClientSide) return;
        if(action.equals("take")) {
            ItemStack item=board.isCutting() ? board.getPending() : board.getItems().isEmpty() ? ItemStack.EMPTY : board.getItems().getLast();
            if(item.isEmpty())return;
            var left=item.copy();player.getInventory().add(left);
            if(board.isCutting()) board.startCut(left);
            else {board.removeLastItem();if(!left.isEmpty())board.addItem(left);}
            player.inventoryMenu.broadcastChanges();
        } else if(action.equals("chop")) {
            long now=ctx.level().getGameTime();
            if(!board.hasKnife() || !board.isCutting() || lastChop!=Long.MIN_VALUE && now-lastChop<4) return;
            var recipe=ctx.level().getRecipeManager().getRecipeFor(RecipeTypeRegistry.CUTTING_BOARD_RECIPE_TYPE.get(),new SingleRecipeInput(board.getPending()),ctx.level());
            if(recipe.isEmpty() || !room(ctx,outputs(recipe.get().value()))) return;
            int required=CuttingBoardBlockEntity.requiredChops(board.getKnife(),recipe.get().value());
            float x=data.getFloat("x"),z=data.getFloat("z");
            if(!data.contains("mark") || data.getInt("mark")!=board.getPendingChops() || !Float.isFinite(x) || !Float.isFinite(z)
                || x<1 || x>11 || z<3 || z>13) return;
            lastChop=now;CAPTURE.set(ctx);
            try {board.chopPending(ctx.level());}finally{CAPTURE.remove();}
        } else if(action.equals("assemble")) {
            if(board.hasKnife() || board.isCutting()) return;
            var input=new CuttingBoardAssemblyRecipe.Input(List.copyOf(board.getItems()));
            var recipe=ctx.level().getRecipeManager().getRecipeFor(RecipeTypeRegistry.CUTTING_BOARD_ASSEMBLY_RECIPE_TYPE.get(),input,ctx.level());
            if(recipe.isEmpty()) return;
            var result=recipe.get().value().assemble(input,ctx.level().registryAccess());
            if(!room(ctx,List.of(result))) return;
            board.setContents(ItemStack.EMPTY);CAPTURE.set(ctx);
            try{CuttingBoardBlockEntity.output(ctx.level(),ctx.pos(),result);}finally{CAPTURE.remove();}
            ctx.level().playSound(null,ctx.pos(),SoundEvents.ITEM_PICKUP,SoundSource.BLOCKS,.6F,1);
        } else if(action.equals("fill") && Settings.BOOK.get()) {
            var id=ResourceLocation.tryParse(data.getString("recipe"));
            var recipe=BoardRecipes.all(ctx.level()).stream().filter(h -> h.id().equals(id)).findFirst();
            if(recipe.isEmpty()) return;
            boolean cut=recipe.get().value() instanceof CuttingBoardRecipe;
            if(cut ? !board.hasKnife() || !board.isEmpty() || board.isCutting() : board.hasKnife() || board.isCutting()) return;
            var plan=BoardRecipes.plan(player.getInventory(),board.getItems(),recipe.get().value());
            if(plan.isEmpty()) {player.displayClientMessage(Component.translatable("handson_farm_charm.fill_failed"),true);return;}
            var c=new SimpleContainer(5);plan.get().commit(player.getInventory(),c);
            if(cut) board.startCut(c.getItem(0));
            else {board.setContents(ItemStack.EMPTY);for(int i=0;i<5;i++) if(!c.getItem(i).isEmpty()) board.addItem(c.getItem(i));}
            player.inventoryMenu.broadcastChanges();
        }
        tick(ctx);
    }
    private List<ItemStack> outputs(CuttingBoardRecipe r) {
        var list=new ArrayList<ItemStack>();list.add(r.getResult());list.addAll(r.getByproducts());return list;
    }
    private boolean room(StationContext ctx,List<ItemStack> outputs) {
        var c=new SimpleContainer(8);for(int i=0;i<8;i++)c.setItem(i,ctx.state().getItem(SlotRef.of("result",i)).copy());
        for(var out:outputs) if(!c.addItem(out.copy()).isEmpty()) return false;
        return true;
    }
    public void tick(StationContext ctx) {
        float progress=0;
        var recipe=ctx.level().getRecipeManager().getRecipeFor(RecipeTypeRegistry.CUTTING_BOARD_RECIPE_TYPE.get(),new SingleRecipeInput(board.getPending()),ctx.level());
        if(recipe.isPresent()) progress=board.getPendingChops()/(float)Math.max(1,CuttingBoardBlockEntity.requiredChops(board.getKnife(),recipe.get().value()));
        ctx.state().setControl("progress",progress);
        ctx.state().setControl("chops",board.getPendingChops());
        ctx.state().setControl("required",recipe.map(h -> (float)CuttingBoardBlockEntity.requiredChops(board.getKnife(),h.value())).orElse(1F));
        var tag=ctx.state().data();if(!tag.contains("book") || tag.getBoolean("book")!=Settings.BOOK.get()) {tag.putBoolean("book",Settings.BOOK.get());ctx.state().setData(tag);}
    }
}

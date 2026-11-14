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
            public int size(){return 9;}
            public ItemStack get(int i){return i<board.getItems().size()?board.getItems().get(i):ItemStack.EMPTY;}
            public void set(int i,ItemStack stack){
                var items=new ArrayList<>(board.getItems());
                if(i<items.size()) {if(stack.isEmpty())items.remove(i);else items.set(i,stack);}
                else if(!stack.isEmpty())items.add(stack);
                contents(items);
            }
        });
        ctx.state().bindSlots("pending",new SlotStorage() {
            public int size(){return 1;}public ItemStack get(int i){return board.getPending();}
            public void set(int i,ItemStack s){board.startCut(s);}
        });
        ctx.state().bindSlots("knife",new SlotStorage() {
            public int size(){return 1;}public ItemStack get(int i){return board.getKnife();}
            public void set(int i,ItemStack s){board.setKnife(s);}
        });
    }
    private void contents(List<ItemStack> items){board.setContents(ItemStack.EMPTY);for(var item:items)board.addItem(item);}
    public boolean mayPlace(StationContext ctx,Player p,SlotRef ref,ItemStack s){return false;}
    public boolean mayTake(StationContext ctx,Player p,SlotRef ref){return ref.group().equals("result");}
    public void onOpen(StationContext ctx,Player p){tick(ctx);}
    public void onCustomAction(StationContext ctx,Player player,String action,CompoundTag data) {
        if(ctx.level().isClientSide)return;
        switch(action) {
            case "place" -> place(ctx,player,data);
            case "carve" -> carve(ctx,player);
            case "take" -> take(ctx,player);
            case "chop" -> chop(ctx,player,data);
            case "assemble" -> assemble(ctx);
            case "fill" -> fill(ctx,player,data);
            default -> {}
        }
        tick(ctx);player.inventoryMenu.broadcastChanges();
    }
    private ItemStack source(Player p,int from){return from==-1?p.inventoryMenu.getCarried():from>=0 && from<36?p.getInventory().getItem(from):ItemStack.EMPTY;}
    private void consumed(Player p,int from,ItemStack s){if(s.isEmpty()){if(from==-1)p.inventoryMenu.setCarried(ItemStack.EMPTY);else p.getInventory().setItem(from,ItemStack.EMPTY);}p.getInventory().setChanged();}
    private void sound(StationContext c,net.minecraft.sounds.SoundEvent event,float pitch){c.level().playSound(null,c.pos(),event,SoundSource.BLOCKS,.6F,pitch);}
    private void give(Player p,ItemStack s){var left=s.copy();p.getInventory().add(left);if(!left.isEmpty())p.drop(left,false);}
    private void carve(StationContext c,Player p){
        ItemStack s=p.inventoryMenu.getCarried();
        if(!BoardTools.canStick(s) || !board.isEmpty() || board.isCutting() || board.hasKnife())return;
        board.setKnife(s.split(1));consumed(p,-1,s);sound(c,SoundEvents.AXE_STRIP,.8F);
    }
    private void place(StationContext c,Player p,CompoundTag tag){
        int from=tag.contains("from")?tag.getInt("from"):-1;
        ItemStack s=source(p,from);if(s.isEmpty() || BoardTools.canStick(s))return;
        ((BoardSelection)board).handily$recipe(null);
        var candidate=new ArrayList<>(board.getItems());
        boolean cutting=BoardRecipes.cut(c.level(),s).isPresent();
        if(board.isCutting()) {
            var base=board.getPending();
            var combined=List.of(base.copyWithCount(1),s.copyWithCount(1));
            if(base.getCount()==1 && !ItemStack.isSameItemSameComponents(base,s) && dishes(c).stream().anyMatch(h -> BoardDishes.accepts(h.value(),combined))) {
                board.startCut(ItemStack.EMPTY);candidate.add(base);contents(candidate);
            } else if(ItemStack.isSameItemSameComponents(base,s)) {
                int n=Math.min(tag.getBoolean("one")?1:s.getCount(),16-base.getCount());
                if(n>0){var queued=base.copy();queued.grow(n);s.shrink(n);board.startCut(queued);consumed(p,from,s);sound(c,SoundEvents.ITEM_PICKUP,1.1F);}return;
            } else return;
        }
        if(!candidate.isEmpty() || !cutting) {
            candidate.add(s.copyWithCount(1));
            if(candidate.size()>9 || dishes(c).stream().noneMatch(h -> BoardDishes.accepts(h.value(),candidate)))return;
            if(board.hasKnife())give(p,board.takeKnife());
            s.shrink(1);consumed(p,from,s);contents(candidate);sound(c,SoundEvents.ITEM_PICKUP,1.1F);
            boolean full=dishes(c).stream().anyMatch(h -> BoardDishes.make(c.level(),h.value(),candidate).isPresent());
            boolean grows=dishes(c).stream().anyMatch(h -> BoardDishes.needs(h.value()).size()>candidate.size() && BoardDishes.accepts(h.value(),candidate));
            if(full && !grows)assemble(c);
        } else {
            if(board.hasKnife())give(p,board.takeKnife());
            int n=Math.min(tag.getBoolean("one")?1:s.getCount(),16);
            board.startCut(s.split(n));consumed(p,from,s);sound(c,SoundEvents.ITEM_PICKUP,1.05F);
        }
    }
    private List<RecipeHolder<?>> dishes(StationContext c){return BoardRecipes.all(c.level()).stream().filter(h -> !(h.value() instanceof CuttingBoardRecipe)).toList();}
    private void take(StationContext c,Player p){
        if(board.isCutting()){var s=board.getPending().copy();board.startCut(ItemStack.EMPTY);give(p,s);}
        else if(!board.isEmpty())give(p,board.removeLastItem());
        else if(board.hasKnife())give(p,board.takeKnife());
        sound(c,SoundEvents.ITEM_PICKUP,.85F);
    }
    private record Tool(int slot,ItemStack stack){}
    private Tool tool(StationContext c,Player p){
        ItemStack input=board.getPending();
        if(board.hasKnife() && BoardTools.supports(board.getKnife(),input))return new Tool(-2,board.getKnife());
        if(BoardTools.supports(p.inventoryMenu.getCarried(),input))return new Tool(-1,p.inventoryMenu.getCarried());
        int selected=p.getInventory().selected;
        if(BoardTools.supports(p.getInventory().getItem(selected),input))return new Tool(selected,p.getInventory().getItem(selected));
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(BoardTools.supports(p.getInventory().getItem(i),input))return new Tool(i,p.getInventory().getItem(i));
        return null;
    }
    private void chop(StationContext c,Player p,CompoundTag data){
        long now=c.level().getGameTime();
        if(!board.isCutting() || lastChop!=Long.MIN_VALUE && now-lastChop<4)return;
        Tool tool=tool(c,p);if(tool==null)return;
        var recipe=BoardRecipes.cut(c.level(),board.getPending());
        if(recipe.isEmpty() || !room(c,outputs(recipe.get().value())))return;
        float x=data.getFloat("x"),z=data.getFloat("z");
        if(!data.contains("mark") || data.getInt("mark")!=board.getPendingChops() || !Float.isFinite(x) || !Float.isFinite(z) || x<1 || x>11 || z<3 || z>13)return;
        ItemStack parked=board.getKnife();
        if(tool.slot!=-2)board.setKnife(tool.stack.copy());
        lastChop=now;CAPTURE.set(c);
        try{board.chopPending(c.level());}finally{
            CAPTURE.remove();
            if(tool.slot!=-2){var used=board.takeKnife();if(tool.slot==-1)p.inventoryMenu.setCarried(used);else p.getInventory().setItem(tool.slot,used);board.setKnife(parked);}
        }
    }
    private void assemble(StationContext c){
        if(board.isCutting() || board.isEmpty())return;
        var selected=((BoardSelection)board).handily$recipe();
        var recipes=new ArrayList<>(dishes(c));
        recipes.sort(Comparator.comparing(h -> !h.id().equals(selected)));
        for(var holder:recipes) {
            var meal=BoardDishes.make(c.level(),holder.value(),board.getItems());if(meal.isEmpty())continue;
            var outputs=new ArrayList<ItemStack>();outputs.add(meal.get().result());outputs.addAll(meal.get().remainders());
            if(!room(c,outputs))return;
            contents(List.of());CAPTURE.set(c);
            try{for(var out:outputs)CuttingBoardBlockEntity.output(c.level(),c.pos(),out.copy());}finally{CAPTURE.remove();}
            ((BoardSelection)board).handily$recipe(null);
            sound(c,SoundEvents.ITEM_PICKUP,1.2F);return;
        }
    }
    private void fill(StationContext c,Player p,CompoundTag data){
        if(!Settings.BOOK.get())return;
        var id=ResourceLocation.tryParse(data.getString("recipe"));
        var holder=BoardRecipes.all(c.level()).stream().filter(h -> h.id().equals(id)).findFirst();if(holder.isEmpty())return;
        boolean cut=holder.get().value() instanceof CuttingBoardRecipe;
        if(board.isCutting())return;
        if(cut && !board.isEmpty())return;
        var plan=BoardRecipes.plan(p.getInventory(),board.getItems(),holder.get().value());
        if(plan.isEmpty()){p.displayClientMessage(Component.translatable("handson_farm_charm.fill_failed"),true);return;}
        var temp=new SimpleContainer(9);plan.get().commit(p.getInventory(),temp);
        ((BoardSelection)board).handily$recipe(cut?null:holder.get().id());
        if(board.hasKnife())give(p,board.takeKnife());
        if(cut){
            ItemStack input=temp.getItem(0);
            if(data.getBoolean("batch"))for(int i=0;i<36 && input.getCount()<16;i++){
                ItemStack next=p.getInventory().getItem(i);if(ItemStack.isSameItemSameComponents(input,next)){int n=Math.min(next.getCount(),16-input.getCount());input.grow(n);next.shrink(n);}
            }
            board.startCut(input);
        } else {
            var list=new ArrayList<ItemStack>();for(int i=0;i<9;i++)if(!temp.getItem(i).isEmpty())list.add(temp.getItem(i));contents(list);
        }
        sound(c,SoundEvents.ITEM_PICKUP,1.1F);
    }
    private List<ItemStack> outputs(CuttingBoardRecipe r){var list=new ArrayList<ItemStack>();list.add(r.getResult());list.addAll(r.getByproducts());return list;}
    private boolean room(StationContext c,List<ItemStack> outputs){
        var container=new SimpleContainer(8);for(int i=0;i<8;i++)container.setItem(i,c.state().getItem(SlotRef.of("result",i)).copy());
        for(var out:outputs)if(!container.addItem(out.copy()).isEmpty())return false;return true;
    }
    public void tick(StationContext c){
        var recipe=BoardRecipes.cut(c.level(),board.getPending());
        int required=recipe.map(h -> CuttingBoardBlockEntity.requiredChops(board.getKnife(),h.value())).orElse(1);
        c.state().setControl("progress",board.getPendingChops()/(float)Math.max(1,required));
        c.state().setControl("chops",board.getPendingChops());c.state().setControl("required",Math.max(1,required));
        var data=c.state().data();
        var recipeId=((BoardSelection)board).handily$recipe();
        String selected=recipeId==null?"":recipeId.toString();
        boolean changed=!data.getString("selected_recipe").equals(selected) || !data.contains("book") || data.getBoolean("book")!=Settings.BOOK.get();
        if(changed){data.putString("selected_recipe",selected);data.putBoolean("book",Settings.BOOK.get());c.state().setData(data);}
    }
}

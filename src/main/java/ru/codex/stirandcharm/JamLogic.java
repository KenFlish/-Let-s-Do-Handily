package ru.codex.stirandcharm;

import java.util.*;
import com.pockyl.diegesis.api.state.*;
import com.pockyl.diegesis.api.station.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.satisfy.bakery.core.block.SmallCookingPotBlock;
import net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity;
import net.satisfy.bakery.core.recipe.JamRecipe;
import net.satisfy.bakery.core.registry.RecipeTypeRegistry;

/** All cooking, missed stirs, consistency, jars and experience remain Bakery's own. */
public final class JamLogic implements StationLogic {
    private final SmallCookingPotBlockEntity pot;
    private final Map<UUID,StirMotion> motion=new HashMap<>();
    private long lastStir=Long.MIN_VALUE;
    public JamLogic(StationContext c) {
        pot=(SmallCookingPotBlockEntity)c.blockEntity();
        c.state().bindSlots("ingredients",new SlotStorage(){
            public int size(){return 4;}public ItemStack get(int i){return i<pot.getIngredients().size()?pot.getIngredients().get(i):ItemStack.EMPTY;}
            public void set(int i,ItemStack stack){if(!ItemStack.matches(get(i),stack))throw new IllegalStateException("Jam ingredients use Bakery's addIngredient action");}
        });
    }
    public static List<RecipeHolder<JamRecipe>> recipes(net.minecraft.world.level.Level level){return level.getRecipeManager().getAllRecipesFor(RecipeTypeRegistry.JAM_COOKING_TYPE.get());}
    public boolean mayPlace(StationContext c,Player p,SlotRef r,ItemStack stack){return false;}
    public boolean mayTake(StationContext c,Player p,SlotRef r){return false;}
    public void onOpen(StationContext c,Player p){tick(c);}
    public void onClose(StationContext c,Player p){motion.remove(p.getUUID());}
    private ItemStack source(Player p,int from){return from==-1?p.inventoryMenu.getCarried():from>=0&&from<36?p.getInventory().getItem(from):ItemStack.EMPTY;}
    public void onCustomAction(StationContext c,Player p,String action,CompoundTag data){
        if(c.level().isClientSide)return;
        if(action.equals("stir")) {
            var sample=motion.computeIfAbsent(p.getUUID(),id -> new StirMotion()).accept(c.level().getGameTime(),data.getFloat("x"),data.getFloat("z"));
            long now=c.level().getGameTime();
            if(sample!=null && sample.impulse()>0 && (lastStir==Long.MIN_VALUE || now-lastStir>=4)) {pot.stir();lastStir=now;}
        } else if(action.equals("place")) {
            int from=data.contains("from")?data.getInt("from"):-1;var stack=source(p,from);
            if(SmallCookingPotBlockEntity.isJar(stack))bottle(c,p,stack,from);
            else if(!stack.isEmpty())pot.addIngredient(stack,p);
        } else if(action.equals("bottle")) {
            int made=0;
            do {
                ItemStack jar=p.inventoryMenu.getCarried();int slot=-1;
                if(!SmallCookingPotBlockEntity.isJar(jar))for(int i=0;i<36;i++)if(SmallCookingPotBlockEntity.isJar(p.getInventory().getItem(i))){jar=p.getInventory().getItem(i);slot=i;break;}
                if(!bottle(c,p,jar,slot))break;made++;
            }while(data.getBoolean("all") && made<64 && pot.canBottle());
        } else if(action.equals("dump") && p.inventoryMenu.getCarried().isEmpty())pot.dump(p);
        else if(action.equals("fill") && Settings.BOOK.get()) {
            var id=ResourceLocation.tryParse(data.getString("recipe"));
            var holder=recipes(c.level()).stream().filter(h -> h.id().equals(id)).findFirst();
            if(holder.isEmpty() || pot.isCooking() || pot.isBottling() || pot.isBurnt())return;
            var canonical=new SimpleContainer(4);for(int i=0;i<pot.getIngredients().size();i++)canonical.setItem(i,pot.getIngredients().get(i).copy());
            var plan=RecipeTransfer.plan(p.getInventory(),canonical,4,false,holder.get().value().getIngredients(),ItemStack.EMPTY,true);
            if(plan.isEmpty()){p.displayClientMessage(Component.translatable("handson_farm_charm.fill_failed"),true);return;}
            // Find a native-safe insertion order before touching inventory; a shorter jam must not start halfway through.
            var missing=new ArrayList<ItemStack>();
            for(int i=0;i<36;i++){
                int n=p.getInventory().getItem(i).getCount()-plan.get().player()[i].getCount();
                for(int j=0;j<n;j++)missing.add(p.getInventory().getItem(i).copyWithCount(1));
            }
            var order=new ArrayList<ItemStack>();
            if(!order(c,holder.get(),new ArrayList<>(pot.getIngredients()),missing,new boolean[missing.size()],order))return;
            for(var needed:order) {
                for(int i=0;i<36;i++)if(ItemStack.isSameItemSameComponents(p.getInventory().getItem(i),needed)){
                    pot.addIngredient(p.getInventory().getItem(i),p);break;
                }
            }
        }
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();tick(c);
    }
    private boolean order(StationContext c,RecipeHolder<JamRecipe> wanted,List<ItemStack> prefix,List<ItemStack> missing,boolean[] used,List<ItemStack> sorted){
        if(sorted.size()==missing.size())return wanted.value().matches(new JamRecipe.Input(prefix),c.level());
        for(int i=0;i<missing.size();i++)if(!used[i]){
            prefix.add(missing.get(i));used[i]=true;sorted.add(missing.get(i));
            boolean premature=prefix.size()<wanted.value().getIngredients().size() && recipes(c.level()).stream().anyMatch(h -> h.value().matches(new JamRecipe.Input(prefix),c.level()));
            if(!premature && wanted.value().accepts(prefix) && order(c,wanted,prefix,missing,used,sorted))return true;
            used[i]=false;sorted.removeLast();prefix.removeLast();
        }
        return false;
    }
    public static ItemStack preview(SmallCookingPotBlockEntity pot){
        var item=pot.getResult().copyWithCount(1);
        if(!item.isEmpty() && pot.currentConsistency()==net.satisfy.bakery.core.recipe.JamConsistency.PERFECT){
            item.set(net.satisfy.bakery.core.registry.DataComponentRegistry.PERFECT_JAM.get(),((ru.codex.stirandcharm.mixin.JamAccess)pot).handily$cook());
            item.set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable("item.bakery.perfect_jam",pot.getResult().getHoverName()));
        }
        return item;
    }
    private boolean bottle(StationContext c,Player p,ItemStack jar,int from){
        if(!pot.canBottle() || !SmallCookingPotBlockEntity.isJar(jar))return false;
        var planned=InventoryDelivery.copy(p);boolean consume=!p.getAbilities().instabuild;
        if(consume && from>=0)planned[from].shrink(1);
        if(!InventoryDelivery.insert(planned,preview(pot))){p.displayClientMessage(Component.translatable("handson_farm_charm.serving_failed"),true);return false;}
        var jam=pot.bottle();if(jam.isEmpty())return false;
        if(consume && from==-1){jar.shrink(1);p.inventoryMenu.setCarried(jar.isEmpty()?ItemStack.EMPTY:jar);}
        InventoryDelivery.commit(p,planned);ServingEffects.served(c,jam,new ItemStack(net.satisfy.bakery.core.registry.ObjectRegistry.JAR.get()));return true;
    }
    public void tick(StationContext c){
        c.state().setControl("progress",pot.getCookingTime()>0?Math.min(1,pot.getTimer()/(float)pot.getCookingTime()):0);
        c.state().setControl("heated",c.blockState().getValue(SmallCookingPotBlock.LIT)?1:0);
        c.state().setControl("stir_due",pot.isStirDue()?1:0);c.state().setControl("ready",pot.canBottle()?1:0);
        var tag=c.state().data();
        tag.putBoolean("book",Settings.BOOK.get());tag.putBoolean("busy",pot.isCooking() || pot.isBottling() || pot.isBurnt());tag.putBoolean("burnt",pot.isBurnt());tag.putInt("jars",pot.getJarsLeft());
        tag.putString("vessel", "bakery:jar");
        tag.putBoolean("ready",pot.canBottle());
        tag.putString("consistency",pot.isEmpty()?"":pot.currentConsistency().getSerializedName());
        if(pot.getResult().isEmpty())tag.remove("meal");else tag.put("meal",pot.getResult().save(c.level().registryAccess()));
        c.state().setData(tag);
    }
}

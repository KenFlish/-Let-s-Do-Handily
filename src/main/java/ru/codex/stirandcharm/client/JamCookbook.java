package ru.codex.stirandcharm.client;
import java.util.*;
import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import ru.codex.stirandcharm.*;
final class JamCookbook implements RecipeBook.Source {
    public List<RecipeBook.Page> pages(StationView view){
        return JamLogic.recipes(view.level()).stream().map(h -> new RecipeBook.Page(h.id(),h.value().getResult(),h.value().getIngredients(),
            List.of(new ItemStack(net.satisfy.bakery.core.registry.ObjectRegistry.JAR.get())),
            Component.translatable("handson_farm_charm.book.seconds",h.value().getCookingTime()/20),false)).toList();
    }
    public List<ItemStack> inStation(StationView view){return List.of(view.state().getItems("ingredients"));}
    public boolean available(StationView view,RecipeBook.Page page){
        var player=Minecraft.getInstance().player;if(player==null || view.state().data().getBoolean("busy"))return false;
        var recipe=JamLogic.recipes(view.level()).stream().filter(h -> h.id().equals(page.key())).findFirst();if(recipe.isEmpty())return false;
        var c=new SimpleContainer(4);var items=view.state().getItems("ingredients");for(int i=0;i<4;i++)c.setItem(i,items[i].copy());
        return RecipeTransfer.plan(player.getInventory(),c,4,false,recipe.get().value().getIngredients(),ItemStack.EMPTY,true).isPresent();
    }
    public void picked(StationView view,RecipeBook.Page page){var tag=new CompoundTag();tag.putString("recipe",page.key().toString());view.sendAction("fill",tag);}
}

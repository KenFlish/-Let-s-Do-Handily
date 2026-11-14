package ru.codex.stirandcharm.client;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.event.SceneEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import ru.codex.stirandcharm.JamLogic;

/** Bakery's renderer supplies the actual pot, fruit, jam and animated whisk in both views. */
final class JamBehavior implements StationBehavior {
    private final RecipeBook book=new RecipeBook(new JamCookbook());
    private final HandMotion hand=new HandMotion();
    private boolean stirring,pinned;
    private ResultBadge result;
    private long start,sent=Long.MIN_VALUE;
    public void onSceneReady(StationView view){view.node("book_open").ifPresent(n -> n.setPainter(book));result=new ResultBadge(view);view.node("result").ifPresent(n -> n.setPainter(result));}
    public void onOpen(StationView view){view.setInventoryFilter(null);book.opened(view);}
    public void onClose(StationView view){stirring=false;pinned=false;book.shut(view);}
    public void tick(StationView view){
        long now=view.level().getGameTime();
        if(stirring && view.isFocused() && (sent==Long.MIN_VALUE || now-sent>=2)){
            var point=hand.sample();var data=new CompoundTag();data.putFloat("x",point[0]);data.putFloat("z",point[1]);view.sendAction("stir",data);sent=now;
        }
    }
    public void frame(StationView view,float dt){
        view.setCarriedHidden(false);
        var data=view.state().data();
        view.node("result").ifPresent(n -> {
            ItemStack out=data.contains("meal")?ItemStack.parseOptional(view.level().registryAccess(),data.getCompound("meal")):ItemStack.EMPTY;
            n.setVisible(view.isFocused() && !out.isEmpty());n.setEnabled(view.isFocused() && !out.isEmpty());n.setOnTop(true);
        });
        if(result!=null)result.refresh();
        view.node("dump").ifPresent(n -> {n.setVisible(view.isFocused());n.setEnabled(view.isFocused());});
        book.frame(view,dt);
    }
    public void onEvent(StationView view,SceneEvent event){
        if(event instanceof SceneEvent.Stroke s && s.node().equals("stir_handle") && s.button()==0){
            if(s.phase()==SceneEvent.Stroke.Phase.START){stirring=true;if(!pinned)hand.grab(true);start=System.nanoTime();}
            if(s.phase()==SceneEvent.Stroke.Phase.END){if(!pinned && System.nanoTime()-start<250_000_000L){pinned=true;view.holdStroke();}else{stirring=false;pinned=false;}}
        }else if(event instanceof SceneEvent.Click click){
            if(click.node().equals("book_open") && click.button()==0)book.click(view);
            if(click.node().equals("result"))view.sendAction("bottle",new CompoundTag());
            if(click.node().equals("dump") && click.button()==0)view.sendAction("dump",new CompoundTag());
        }
    }
    public boolean onItemClick(StationView view,String node,ItemStack stack,int button,boolean shift){
        if(node.equals("book_open")){if(button==0)book.click(view);return true;}
        if(node.equals("mouth") || node.equals("result") || node.equals("stir_handle")){view.sendAction("place",new CompoundTag());return true;}return false;
    }
    public boolean onInventoryShiftClick(StationView view,int slot,ItemStack stack){var tag=new CompoundTag();tag.putInt("from",slot);view.sendAction("place",tag);return true;}
}

package ru.codex.stirandcharm.client;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.event.SceneEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import ru.codex.stirandcharm.JamLogic;

/** Bakery's renderer supplies the actual pot, fruit, jam and animated whisk in both views. */
final class JamBehavior implements StationBehavior {
    private final ServingGesture serving=new ServingGesture("bottle",6);
    private final RecipeBook book=new RecipeBook(new JamCookbook());
    private final HandMotion hand=new HandMotion();
    private boolean stirring,pinned;
    private ResultBadge result;
    private long start,sent=Long.MIN_VALUE;
    public void onEffect(StationView view,net.minecraft.resources.ResourceLocation id,String node,CompoundTag tag){serving.effect(view,id,tag);}
    public void onSceneReady(StationView view){view.node("book_open").ifPresent(n -> n.setPainter(book));result=new ResultBadge(view);view.node("result").ifPresent(n -> n.setPainter(result));view.node("dump_handle").ifPresent(n -> n.setPainter(new HandleOutline(view)));ApproachCamera.open(view);}
    public void onOpen(StationView view){view.setInventoryFilter(null);ApproachCamera.open(view);book.opened(view);}
    public void onClose(StationView view){stirring=false;pinned=false;book.shut(view);}
    public void tick(StationView view){
        long now=view.level().getGameTime();
        if(stirring && view.isFocused() && (sent==Long.MIN_VALUE || now-sent>=2)){
            var point=hand.sample();var data=new CompoundTag();data.putFloat("x",point[0]);data.putFloat("z",point[1]);view.sendAction("stir",data);sent=now;
        }
    }
    public void frame(StationView view,float dt){
        view.setCarriedHidden(false);
        view.node("stir_handle").ifPresent(n->n.setEnabled(view.isFocused()));
        view.node("mouth").ifPresent(n->n.setEnabled(view.isFocused()));
        var data=view.state().data();
        view.node("result").ifPresent(n -> {
            ItemStack out=data.contains("meal")?ItemStack.parseOptional(view.level().registryAccess(),data.getCompound("meal")):ItemStack.EMPTY;
            n.setVisible(false);n.setEnabled(false);
        });
        if(result!=null)result.refresh();
        view.node("dump_handle").ifPresent(n -> {n.setVisible(view.isFocused());n.setEnabled(view.isFocused());});
        serving.frame(view,view.state().getControl("ready")>.5F,stirring);
        book.frame(view,dt);
    }
    public void onEvent(StationView view,SceneEvent event){
        if(serving.event(view,event))return;
        if(event instanceof SceneEvent.Stroke s && s.node().equals("stir_handle") && s.button()==0){
            if(s.phase()==SceneEvent.Stroke.Phase.START){stirring=true;if(!pinned)hand.grab(true);start=System.nanoTime();}
            if(s.phase()==SceneEvent.Stroke.Phase.END){if(!pinned && System.nanoTime()-start<250_000_000L){pinned=true;view.holdStroke();}else{stirring=false;pinned=false;}}
        }else if(event instanceof SceneEvent.Click click){
            if(click.node().equals("book_open") && click.button()==0)book.click(view);
            if(click.node().equals("result"))view.sendAction("bottle",new CompoundTag());
            if(click.node().equals("dump_handle") && click.button()==0)view.sendAction("dump",new CompoundTag());
        }
    }
    public boolean onItemClick(StationView view,String node,ItemStack stack,int button,boolean shift){
        if(serving.item(view,node,stack,shift))return true;
        if(node.equals("book_open")){if(button==0)book.click(view);return true;}
        if(node.equals("mouth") || node.equals("result") || node.equals("stir_handle")){view.sendAction("place",new CompoundTag());return true;}return false;
    }
    public boolean onInventoryShiftClick(StationView view,int slot,ItemStack stack){var tag=new CompoundTag();tag.putInt("from",slot);view.sendAction("place",tag);return true;}
}

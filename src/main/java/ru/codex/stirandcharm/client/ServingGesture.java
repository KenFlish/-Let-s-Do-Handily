package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/** Hands-On style: offer the inventory's vessel over the food, then send a server-checked action. */
final class ServingGesture {
    private final String action;private final float height;private boolean ready;
    private net.minecraft.world.item.Item shown;
    private KitchenMotions.Motion dip;
    private long frameNanos;
    ServingGesture(String action,float height){this.action=action;this.height=height;}
    void frame(StationView view,boolean ready,boolean busy){
        long now=System.nanoTime();float dt=frameNanos==0?0:Math.min(.1F,(now-frameNanos)/1E9F);frameNanos=now;
        if(!view.isFocused() && dip!=null){dip.stop();dip=null;}
        if(dip!=null){if(!dip.frame(view,dt))dip=null;else {view.setCarriedHidden(true);view.node("offer_vessel").ifPresent(n->n.setVisible(false));view.node("serve_area").ifPresent(n->n.setEnabled(false));this.ready=false;return;}}
        float surface=switch(view.type().id().getPath()){
            case "cooking_pot","candlelight_pot" -> PotMotion.surface(view);
            case "cutting_board" -> 2.5F;
            case "roaster" -> 4.2F;
            case "candlelight_pan" -> 3.3F;
            case "jam_pot" -> 7.1F;
            default -> height;
        };
        this.ready=ready && view.isFocused() && !busy;
        view.node("serve_area").ifPresent(n -> {n.setVisible(this.ready);n.setEnabled(this.ready);});
        if(view.type().id().getPath().equals("cooking_pot") || view.type().id().getPath().equals("candlelight_pot"))
            view.node("serve_area").ifPresent(n->n.moveTo(0,surface+.35F-(view.type().id().getPath().equals("candlelight_pot")?9.2F:7.6F),0,0));
        var player=Minecraft.getInstance().player;ItemStack vessel=ItemStack.EMPTY;
        if(player!=null && this.ready && player.inventoryMenu.getCarried().isEmpty()){
            int selected=player.getInventory().selected;
            if(Vessels.holds(view,player.getInventory().getItem(selected)))vessel=player.getInventory().getItem(selected);
            else for(int i=0;i<36;i++)if(Vessels.holds(view,player.getInventory().getItem(i))){vessel=player.getInventory().getItem(i);break;}
        }
        var point=view.cursorOn(surface+1.5F).filter(p -> Math.abs(p.x-8)<5 && Math.abs(p.z-8)<5);
        final var offered=vessel;
        view.node("offer_vessel").ifPresent(n -> {
            n.setVisible(!offered.isEmpty() && point.isPresent());
            if(offered.isEmpty() || point.isEmpty())return;
            if(shown!=offered.getItem()){shown=offered.getItem();n.setVisual(Visual.ofItem(shown).withAnchor(new Vector3f(8,8,8)));}
            n.setTint(0x8CFFFFFF);n.scaleTo(.23F,0);
            n.moveTo(point.get().x-8,surface+1.5F-8,point.get().z-8,0);ContentFacing.turn(view,n);
        });
    }
    boolean event(StationView view,SceneEvent event){
        if(ready && event instanceof SceneEvent.Click c && c.button()==0 && c.node().equals("serve_area")){collect(view,Screen.hasShiftDown());return true;}return false;
    }
    boolean item(StationView view,String node,ItemStack stack,boolean shift){
        if(ready && (node.equals("serve_area") || node.equals("mouth") || node.startsWith("ingredient_") || node.equals("surface")) && Vessels.holds(view,stack)){
            collect(view,shift);return true;
        }return false;
    }
    private void collect(StationView view,boolean all){var tag=new CompoundTag();tag.putBoolean("one",!all);tag.putBoolean("inventory",all);tag.putBoolean("all",all);view.sendAction(action,tag);}
    void effect(StationView view,net.minecraft.resources.ResourceLocation effect,CompoundTag tag){
        if(!effect.equals(ru.codex.stirandcharm.StirAndCharm.id("served")) || !view.isFocused())return;
        var meal=ItemStack.parseOptional(view.level().registryAccess(),tag.getCompound("meal"));if(meal.isEmpty())return;
        var vessel=ItemStack.parseOptional(view.level().registryAccess(),tag.getCompound("vessel"));
        float y=view.type().id().getPath().equals("cooking_pot")||view.type().id().getPath().equals("candlelight_pot")?PotMotion.surface(view):height;
        var at=view.cursorOn(y+1.5F).orElse(new Vector3f(8,y+1.5F,8));
        view.node("arrival").ifPresent(n->{
            if(dip!=null)dip.stop();KitchenMotions.showItem(n,(vessel.isEmpty()?meal:vessel).getItem(),3,0xFFFFFFFF);
            dip=new KitchenMotions.Dip(n,meal.getItem(),at,new Vector3f(8,8,8),new Vector3f(at).add(0,8,9),()->y,view.node("broth").orElse(null),3F/8F,3,0xA0D9C49A,()->{});
        });
    }
}

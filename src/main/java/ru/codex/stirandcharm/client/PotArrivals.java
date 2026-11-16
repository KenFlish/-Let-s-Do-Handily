package ru.codex.stirandcharm.client;

import java.util.*;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.scene.Visual;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import ru.codex.stirandcharm.KitchenIngredients;

/** HOD's arrival arc and KitchenMotions, driven only by acknowledged native inventory changes. */
final class PotArrivals {
    private final ItemStack[] shown=new ItemStack[6];
    private final KitchenMotions.Motion[] motions=new KitchenMotions.Motion[6];
    private final float[] flight=new float[6];
    private final Vector3f[] from=new Vector3f[6];
    private boolean initialized;
    PotArrivals(){Arrays.fill(shown,ItemStack.EMPTY);Arrays.fill(flight,Float.NaN);}
    static boolean sinks(ItemStack s){return KitchenIngredients.liquid(s)||KitchenIngredients.dissolved(s)||KitchenIngredients.egg(s);}
    void opened(StationView view){
        stop();var inputs=view.state().getItems("ingredients");
        for(int i=0;i<6;i++)shown[i]=inputs[i].copy();initialized=true;
    }
    void stop(){for(var m:motions)if(m!=null)m.stop();Arrays.fill(motions,null);Arrays.fill(flight,Float.NaN);}
    void frame(StationView view,float dt,boolean stirring){
        if(!initialized)opened(view);
        var inputs=view.state().getItems("ingredients");int stagger=0;
        for(int i=0;i<6;i++){
            var stack=inputs[i];final int index=i;
            if(!ItemStack.matches(stack,shown[i])){
                boolean added=!stack.isEmpty() && (!ItemStack.isSameItemSameComponents(stack,shown[i]) || stack.getCount()>shown[i].getCount());
                if(motions[i]!=null)motions[i].stop();motions[i]=null;flight[i]=Float.NaN;shown[i]=stack.copy();
                if(added){
                    from[i]=view.cursorOn(PotMotion.surface(view)+9).orElse(new Vector3f(13,PotMotion.surface(view)+9,8));
                    flight[i]=-stagger++*.16F;
                }
            }
            if(!Float.isNaN(flight[i])){
                flight[i]+=dt;
                if(flight[i]>=0 && sinks(stack)){
                    view.node("hand_"+i).ifPresent(hand -> {
                        var onto=new Vector3f(9,0,7+(index%3));
                        if(KitchenIngredients.liquid(stack))motions[index]=new KitchenMotions.Pour(hand,"pour_"+index,stack,from[index],onto,()->PotMotion.surface(view),"broth",3.8F,-1,ContentFacing.yaw(view)).lipAtLeast(PotMotion.surface(view)+3);
                        else if(KitchenIngredients.egg(stack))motions[index]=new KitchenMotions.Crack(hand,view.node("spill_"+index).orElse(hand),stack,from[index],new Vector3f(4,PotMotion.surface(view)+1,8),onto,()->PotMotion.surface(view),view.node("broth").orElse(null),3,2,true,ContentFacing.yaw(view));
                        else motions[index]=new KitchenMotions.Shake(hand,stack,from[index],onto,()->PotMotion.surface(view),view.node("broth").orElse(null),3,-1,ContentFacing.yaw(view));
                    });flight[i]=Float.NaN;
                }else if(flight[i]>=.4F){flight[i]=Float.NaN;view.node("broth").ifPresent(n->n.ripple(8,8,.17F));}
                else if(!sinks(stack))view.node("ingredient_"+i).ifPresent(n -> {
                    if(flight[index]<0){n.setVisible(false);return;}
                    float k=KitchenMotions.out(flight[index]/.4F);var end=n.sceneMatrix().getTranslation(new Vector3f());
                    var at=new Vector3f(from[index]).lerp(end,k).add(0,4*(float)Math.sin(k*Math.PI),0);var base=n.definition().transform().pos();
                    n.moveTo(at.x-base.x(),at.y-base.y(),at.z-base.z(),0);
                });
            }
            if(motions[i]!=null && !motions[i].frame(view,dt))motions[i]=null;
            if(sinks(stack))view.node("ingredient_"+i).ifPresent(n->{n.setVisible(false);n.setEnabled(false);});
            else view.node("ingredient_"+i).ifPresent(n->n.setEnabled(view.isFocused() && !stack.isEmpty() && Float.isNaN(flight[index])));
        }
        recover(view,stirring);
    }
    private void recover(StationView view,boolean stirring){
        var player=Minecraft.getInstance().player;ItemStack recover=ItemStack.EMPTY;
        for(int i=0;i<6;i++)if(!shown[i].isEmpty() && sinks(shown[i]) && motions[i]==null && Float.isNaN(flight[i]))recover=shown[i];
        boolean on=view.isFocused() && !stirring && player!=null && player.inventoryMenu.getCarried().isEmpty()
            && view.state().getItems("result")[0].isEmpty() && !recover.isEmpty();
        float y=PotMotion.surface(view);
        view.node("contents").ifPresent(n->{n.setVisible(on);n.setEnabled(on);n.moveTo(0,y+.03F-(view.type().id().getPath().equals("candlelight_pot")?8.65F:7.15F),0,0);});
        var point=view.cursorOn(y+1.5F);final ItemStack offered=recover;
        view.node("recover_preview").ifPresent(n->{
            boolean hover=view.node("contents").map(NodeView::isHovered).orElse(false);
            n.setVisible(on && hover && point.isPresent());if(!on || !hover || point.isEmpty())return;
            var item=KitchenMotions.containerOf(offered).orElse(offered.getItem());
            n.setVisual(Visual.ofItem(item).withAnchor(new Vector3f(8,8,8)));n.setTint(0x8CFFFFFF);n.scaleTo(.3F,0);
            n.moveTo(point.get().x-8,y+1.5F-8,point.get().z-8,0);ContentFacing.turn(view,n);
        });
    }
}

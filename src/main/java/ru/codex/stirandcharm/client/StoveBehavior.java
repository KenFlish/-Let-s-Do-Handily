/* Adapted from Hands-On Vanilla, Copyright (c) 2026 Pocky. MIT: LICENSE_HANDSON_VANILLA. */
package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.event.SceneEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import ru.codex.stirandcharm.*;

/** Native upper shutter and firebox door; recipes, fuel and cooking stay on the server. */
final class StoveBehavior implements StationBehavior {
    private static final ResourceLocation EMBERS=ResourceLocation.parse("farm_and_charm:block/stove_front");
    // beta.11 calls holdsBlock while the leaves shut; beta.8 needs the closed world model immediately on exit.
    private static final boolean CLOSING_SUPPORTED=java.util.Arrays.stream(StationBehavior.class.getMethods()).anyMatch(m->m.getName().equals("holdsBlock"));
    private final ApplianceCookbook source=new ApplianceCookbook(Appliance.STOVE);
    private final RecipeBook book=new RecipeBook(source);
    private float doors,clock,arrival=-1;
    private boolean wasUsed,hasRecipe;
    private ItemStack receipt=ItemStack.EMPTY;

    public void onSceneReady(StationView view){
        view.node("book_open").ifPresent(n->n.setPainter(book));
        view.node("body").ifPresent(n->n.setPainter(new StoveModels(view,0)));
        view.node("mouth_door").ifPresent(n->n.setPainter(new StoveModels(view,1)));
        view.node("firebox_door").ifPresent(n->n.setPainter(new StoveModels(view,2)));
        for(int i=0;i<3;i++)StoveContents.install(view,"ingredient_"+i,"ingredients",i,2.2F);
        StoveContents.install(view,"fuel","fuel",0,2.8F);
        StoveContents.install(view,"result","result",0,2.8F);
        view.node("arrival").ifPresent(n->n.setPainter((pose,buffers,light)->StoveContents.item(view,receipt,2.8F,pose,buffers,light)));
    }
    public void onOpen(StationView view){view.setInventoryFilter(null);book.opened(view);}
    public void onClose(StationView view){book.shut(view);receipt=ItemStack.EMPTY;arrival=-1;}
    public void tick(StationView view){hasRecipe=view.isFocused() && source.matching(view).isPresent();}
    // Intentionally no @Override: this also compiles against beta.8, which has no closing-scene hook.
    public boolean holdsBlock(StationView view){return Settings.STOVE.get() && !view.isInUse() && doors>0;}
    public void frame(StationView view,float dt){
        ClientDelivery.frame(view,dt);view.setCarriedHidden(false);clock+=dt;
        boolean used=view.isInUse() && Settings.STOVE.get();
        if(used!=wasUsed){
            view.playSound(used?SoundEvents.IRON_TRAPDOOR_OPEN:SoundEvents.IRON_TRAPDOOR_CLOSE,"body",.25F,1.25F);
            wasUsed=used;
        }
        doors=Mth.clamp(doors+(used?dt:-dt)/.35F,0,1);
        if(!used && !CLOSING_SUPPORTED)doors=0;
        float eased=doors*doors*(3-2*doors);
        boolean shown=used || doors>0;
        view.node("body").ifPresent(n->n.setVisible(shown));
        for(String id:new String[]{"mouth_door","firebox_door"})
            view.node(id).ifPresent(n->{n.setVisible(shown);n.rotateTo(0,105*eased,0,0);});
        boolean interactive=view.isFocused() && doors>.85F;
        for(String id:new String[]{"ingredient_0","ingredient_1","ingredient_2","fuel","result","mouth","fire"})
            view.node(id).ifPresent(n->{n.setVisible(shown);n.setEnabled(interactive);n.setOnTop(false);});
        progress(view);
        if(arrival>=0){
            arrival+=Math.min(dt,.1F);float t=Mth.clamp(arrival/.32F,0,1);
            view.node("arrival").ifPresent(n->{n.setVisible(t<1 && view.isFocused());n.moveTo(0,2.5F*(float)Math.sin(t*Math.PI),-6*t,0);});
            if(t>=1){receipt=ItemStack.EMPTY;arrival=-1;}
        }
        book.frame(view,dt);
    }
    private void progress(StationView view){
        float fill=Mth.clamp(view.state().getControl("progress"),0,1);
        boolean cooking=view.isFocused() && hasRecipe;
        view.node("progress").ifPresent(n->{n.setVisible(cooking);if(cooking){n.moveTo(0,.08F*(float)Math.sin(clock*2),0,0);n.setShape(bar(4.5F,-4.5F,6,0),EMBERS);}});
        view.node("progress_fill").ifPresent(n->{n.setVisible(cooking && fill>0);if(cooking && fill>0)n.setShape(bar(4.5F,4.5F-9*fill,1,-.02F),EMBERS,true);});
    }
    private static MeshShape bar(float left,float right,int band,float z){
        var b=MeshShape.builder();float u0=1.25F/16,u1=1.75F/16,v0=(band==1?1.25F:2.25F)/16,v1=v0+.25F/16;
        // Sample the installed native facade, keeping the Hands-On strip dimensions and motion.
        b.corner(left,0,z,u0,v1,0,0,-1);b.corner(right,0,z,u1,v1,0,0,-1);
        b.corner(right,.25F,z,u1,v0,0,0,-1);b.corner(left,.25F,z,u0,v0,0,0,-1);
        return b.build();
    }
    public void onEvent(StationView view,SceneEvent event){
        if(!(event instanceof SceneEvent.Click c))return;
        if(c.node().equals("book_open") && c.button()==0)book.click(view);
        if(c.node().equals("result")){var data=new CompoundTag();data.putBoolean("all",Screen.hasShiftDown());view.sendAction("collect",data);}
        if(c.node().equals("fire") && c.button()==1)view.sendAction("extinguish",new CompoundTag());
    }
    public boolean onItemClick(StationView view,String node,ItemStack stack,int button,boolean shift){
        if((node.equals("fuel") || node.equals("fire")) && (stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL) || stack.is(net.minecraft.world.item.Items.FIRE_CHARGE))){
            view.sendAction("ignite",new CompoundTag());return true;
        }
        return node.equals("result");
    }
    public void onEffect(StationView view,ResourceLocation effect,String node,CompoundTag data){
        ClientDelivery.effect(view,effect,data);
        var player=Minecraft.getInstance().player;
        if(!effect.equals(StirAndCharm.id("stove_taken")) || player==null || !view.isFocused() || !data.hasUUID("player") || !data.getUUID("player").equals(player.getUUID()))return;
        receipt=ItemStack.parseOptional(view.level().registryAccess(),data.getCompound("item"));arrival=0;
        view.playSound(SoundEvents.FIRE_EXTINGUISH,"result",.12F,1.7F);
    }
}

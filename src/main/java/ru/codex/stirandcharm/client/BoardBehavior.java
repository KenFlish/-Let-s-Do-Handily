package ru.codex.stirandcharm.client;

import java.util.*;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector3f;
import ru.codex.stirandcharm.*;
import net.minecraft.client.Minecraft;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;

/** Pixel pieces and a knife following the pointer, adapted to the native Farm & Charm board. */
final class BoardBehavior implements StationBehavior {
    private final RecipeBook book=new RecipeBook(new BoardCookbook());
    private final Map<String,String> meshes=new HashMap<>();
    private long lastClick;
    private float knifeX=6,knifeZ=8,chopAge=1;
    private ItemStack shownKnife=ItemStack.EMPTY;
    private String cutsKey="",marksKey="";
    private List<ItemVoxels.Cut> cuts=List.of();
    private MeshShape marksShape;
    private int iconGeneration=-1,invStamp=-1,carriedStamp=0;
    public void onSceneReady(StationView view) {view.node("book_open").ifPresent(n -> n.setPainter(book));ApproachCamera.open(view);}
    public void onOpen(StationView view) {
        view.setInventoryFilter(null);ApproachCamera.open(view);book.opened(view);
        var player=Minecraft.getInstance().player;
        if(view.openedByUse() && player!=null && !BoardTools.canStick(player.getMainHandItem())) {
            var tag=new CompoundTag();tag.putInt("from",player.getInventory().selected);tag.putBoolean("one",false);view.sendAction("place",tag);
        }
    }
    public void onClose(StationView view) {book.shut(view);}
    public void onStateChanged(StationView view,StationState.Changes changes){book.invalidate();}
    public void tick(StationView view){
        var player=Minecraft.getInstance().player;if(player==null)return;
        int inv=player.getInventory().getTimesChanged(),carried=player.inventoryMenu.getCarried().hashCode();
        if(inv!=invStamp||carried!=carriedStamp){invStamp=inv;carriedStamp=carried;book.invalidate();}
    }
    public void frame(StationView view,float dt) {
        var results=view.state().getItems("result");
        for(int i=0;i<results.length;i++){boolean present=!results[i].isEmpty();view.node("result_"+i).ifPresent(n -> {n.setVisible(view.isFocused() && present);n.setEnabled(view.isFocused() && present);});}
        chopAge+=dt;
        int generation=ItemVoxels.generation();
        if(generation!=iconGeneration){iconGeneration=generation;meshes.clear();cutsKey="";marksKey="";}
        ItemStack parked=view.state().getItems("knife")[0];
        ItemStack pending=view.state().getItems("pending")[0];
        ItemStack tool=tool(view);
        boolean knife=!tool.isEmpty(), focused=view.isFocused();
        var player=Minecraft.getInstance().player;
        view.setCarriedHidden(player!=null && !pending.isEmpty() && BoardTools.supports(player.inventoryMenu.getCarried(),pending));
        int chops=(int)view.state().getControl("chops"),total=pending.isEmpty()?1:required(view,tool,pending);
        view.node("surface").ifPresent(n -> {n.setVisible(focused);n.setEnabled(focused);});
        view.node("mouth").ifPresent(n -> {n.setVisible(focused);n.setEnabled(focused);});
        boolean carved=!parked.isEmpty() && pending.isEmpty();
        view.node("carved").ifPresent(n -> {n.setVisible(view.isInUse() && carved);if(carved)n.setVisual(Visual.ofItem(parked.getItem()).withAnchor(new Vector3f(8,8,8)));});
        String itemKey=pending.getItem()+":"+pending.getComponents().hashCode()+":"+chops+":"+total;
        if(!itemKey.equals(cutsKey)){
            cutsKey=itemKey;cuts=new ArrayList<>();
            for(int i=0;i<Math.min(chops,32);i++) cuts.add(line(pending,i,total));
        }
        mesh(view,"food",pending,BoardTools.ITEM_SIZE,cuts,chops,total);
        var layers=view.state().getItems("ingredients");
        for(int i=0;i<9;i++) mesh(view,"layer_"+i,layers[i],8,List.of(),0,0);
        ItemVoxels.Cut target=line(pending,chops,total);
        if(!marksKey.equals(itemKey)){marksKey=itemKey;marksShape=marks(pending,target);}
        view.node("marks").ifPresent(n -> {
            n.setVisible(focused && !pending.isEmpty());
            if(marksShape!=null){n.setShape(marksShape,ResourceLocation.parse("minecraft:block/white_concrete"),true);n.setTint(0xC0FFFFFF);}
        });
        var cursor=view.cursorOn(1.1F);
        float wantX=BoardTools.CENTER_X,wantZ=3;
        if(cursor.isPresent() && cursor.get().x>=1 && cursor.get().x<=11 && cursor.get().z>=3 && cursor.get().z<=13) {
            wantX=cursor.get().x;wantZ=cursor.get().z;
            if(!pending.isEmpty()) {
                float distance=target.distance(wantX-BoardTools.CENTER_X,wantZ-BoardTools.CENTER_Z);
                if(Math.abs(distance)<1.2F) {wantX-=target.normalX()*distance;wantZ-=target.normalZ()*distance;}
            }
        }
        float follow=1-(float)Math.exp(-Math.min(dt,.1F)*25);
        knifeX+=(wantX-knifeX)*follow;knifeZ+=(wantZ-knifeZ)*follow;
        if(!ItemStack.matches(shownKnife,tool)) {
            shownKnife=tool.copy();
            view.node("knife_model").ifPresent(n -> {if(!tool.isEmpty())n.setVisual(Visual.ofItem(tool.getItem()).withAnchor(new Vector3f(8,8,8)));});
        }
        float dip=chopAge<.2F ? (float)Math.sin(chopAge/.2F*Math.PI)*2.5F : 0;
        view.node("knife_turn").ifPresent(n -> {n.setVisible(view.isInUse() && knife && !pending.isEmpty());n.moveTo(knifeX-BoardTools.CENTER_X,3-dip,knifeZ-BoardTools.CENTER_Z,0);n.rotateTo(0,-(float)Math.toDegrees(target.angle()),0,0);});
        book.frame(view,dt);
    }
    private static ItemStack tool(StationView view) {
        var food=view.state().getItems("pending")[0];var parked=view.state().getItems("knife")[0];
        if(food.isEmpty())return ItemStack.EMPTY;
        if(BoardTools.supports(parked,food))return parked;
        var p=Minecraft.getInstance().player;if(p==null)return ItemStack.EMPTY;
        if(BoardTools.supports(p.inventoryMenu.getCarried(),food))return p.inventoryMenu.getCarried();
        if(BoardTools.supports(p.getMainHandItem(),food))return p.getMainHandItem();
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(BoardTools.supports(p.getInventory().getItem(i),food))return p.getInventory().getItem(i);
        return ItemStack.EMPTY;
    }
    private static int required(StationView view,ItemStack tool,ItemStack pending) {
        return BoardRecipes.cut(view.level(),pending)
            .map(h -> net.satisfy.farm_and_charm.core.util.Strippables.strip(pending).isPresent()?2:CuttingBoardBlockEntity.requiredChops(tool,h.value())).orElse(1);
    }
    private static MeshShape marks(ItemStack pending,ItemVoxels.Cut target) {
        if(pending.isEmpty())return null;
        var shape=MeshShape.builder();
        float[] span=ItemVoxels.icon(pending).map(icon -> icon.solidSpan(target.x(),target.z(),target.angle(),BoardTools.ITEM_SIZE)).orElse(new float[]{-3.5F,3.5F});
        float alongX=(float)Math.cos(target.angle()),alongZ=(float)Math.sin(target.angle());
        float wideX=target.normalX()*.07F,wideZ=target.normalZ()*.07F;
        for(float t=span[0];t<span[1];t+=.95F) {
            float end=Math.min(span[1],t+.55F);
            float ax=target.x()+alongX*t,az=target.z()+alongZ*t,bx=target.x()+alongX*end,bz=target.z()+alongZ*end;
            shape.corner(ax-wideX,.72F,az-wideZ,0,0,0,1,0).corner(ax+wideX,.72F,az+wideZ,0,1,0,1,0)
                .corner(bx+wideX,.72F,bz+wideZ,1,1,0,1,0).corner(bx-wideX,.72F,bz-wideZ,1,0,0,1,0);
        }
        return shape.build();
    }
    private static ItemVoxels.Cut line(ItemStack item,int mark,int total) {
        if(item.isEmpty())return new ItemVoxels.Cut(0,0,0);
        var axis=ItemVoxels.icon(item).map(icon -> icon.axis(BoardTools.ITEM_SIZE)).orElse(ItemVoxels.Axis.square(3));
        float[] at=axis.at((mark+1F)/(Math.max(1,total)+1F));
        float lean=(mark%2==0 ? .6F : -.45F)*(axis.elongation()>=1.4F ? .35F : .85F);
        return new ItemVoxels.Cut(at[0],at[1],ItemVoxels.Axis.normalized(axis.across()+lean));
    }
    private void mesh(StationView view,String node,ItemStack item,float size,List<ItemVoxels.Cut> cuts,int chop,int total) {
        view.node(node).ifPresent(n -> {
            n.setVisible(view.isInUse() && !item.isEmpty());if(item.isEmpty())return;
            String key=item.getItem()+":"+item.getComponents().hashCode()+":"+chop+":"+total+"@"+ItemVoxels.generation();
            if(key.equals(meshes.get(node)))return;meshes.put(node,key);
            var icon=ItemVoxels.icon(item);
            if(icon.isPresent()) {n.scaleTo(1,0);n.moveTo(0,0,0,0);n.setShape(ItemVoxels.build(icon.get(),size,cuts,.55F),icon.get().sprite(),true);}
            else {n.setVisual(Visual.ofItem(item.getItem()).withAnchor(new Vector3f(8,8,8)));n.scaleTo(.4F,0);n.moveTo(0,3.2F,0,0);}
        });
    }
    public void onEvent(StationView view,SceneEvent event) {
        if(!(event instanceof SceneEvent.Click c))return;
        if(c.node().equals("book_open")){if(c.button()==0)book.click(view);return;}
        if(!c.node().equals("surface") && !c.node().equals("mouth"))return;
        if(!view.state().getItems("knife")[0].isEmpty() && view.state().getItems("pending")[0].isEmpty()) {view.sendAction("take",new CompoundTag());return;}
        if(c.button()==1){view.sendAction("take",new CompoundTag());return;}
        if(c.button()!=0)return;
        if(!view.state().getItems("pending")[0].isEmpty()) {
            if(!tool(view).isEmpty())chop(view);else view.sendAction("take",new CompoundTag());
        } else view.sendAction("assemble",new CompoundTag());
    }
    public boolean onItemClick(StationView view,String node,ItemStack stack,int button,boolean shift) {
        if(node.equals("book_open")){if(button==0)book.click(view);return true;}
        if(!node.equals("surface") && !node.equals("mouth"))return false;
        boolean empty=view.state().getItems("pending")[0].isEmpty() && Arrays.stream(view.state().getItems("ingredients")).allMatch(ItemStack::isEmpty);
        if(shift && empty && BoardTools.canStick(stack)){view.sendAction("carve",new CompoundTag());return true;}
        if(BoardTools.canStick(stack)) {
            if(button==1)view.putAwayCarried();
            else if(!tool(view).isEmpty())chop(view);
            else view.playSound(SoundEvents.WOOD_HIT,"surface",.4F,1.2F);
            return true;
        }
        var tag=new CompoundTag();tag.putBoolean("one",button==1);view.sendAction("place",tag);return true;
    }
    public boolean onInventoryShiftClick(StationView view,int slot,ItemStack stack) {
        if(BoardTools.canStick(stack))return false;
        var tag=new CompoundTag();tag.putInt("from",slot);view.sendAction("place",tag);return true;
    }
    private void chop(StationView view) {
        long now=System.nanoTime();if(now-lastClick<200_000_000L)return;
        var cursor=view.cursorOn(1.1F);if(cursor.isEmpty())return;
        int mark=(int)view.state().getControl("chops"),total=required(view,tool(view),view.state().getItems("pending")[0]);
        var p=cursor.get();chopAge=0;lastClick=now;
        if(p.x<1 || p.x>11 || p.z<3 || p.z>13 || Math.abs(line(view.state().getItems("pending")[0],mark,total).distance(p.x-BoardTools.CENTER_X,p.z-BoardTools.CENTER_Z))>1.2F) {view.playSound(SoundEvents.WOOD_HIT,"food",.3F,1.5F);return;}
        var tag=new CompoundTag();tag.putInt("mark",mark);tag.putFloat("x",p.x);tag.putFloat("z",p.z);view.sendAction("chop",tag);
    }
}

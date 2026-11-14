package ru.codex.stirandcharm.client;

import java.util.*;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.event.SceneEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector3f;
import ru.codex.stirandcharm.BoardTools;

/** Pixel pieces and a knife following the pointer, adapted to the native Farm & Charm board. */
final class BoardBehavior implements StationBehavior {
    private final RecipeBook book=new RecipeBook(new BoardCookbook());
    private final Map<String,String> meshes=new HashMap<>();
    private long lastClick;
    private float knifeX=6,knifeZ=8,chopAge=1;
    private ItemStack shownKnife=ItemStack.EMPTY;
    public void onSceneReady(StationView view) {view.node("book_open").ifPresent(n -> n.setPainter(book));}
    public void onOpen(StationView view) {view.setInventoryFilter(null);book.opened(view);}
    public void onClose(StationView view) {book.shut(view);}
    public void frame(StationView view,float dt) {
        view.setCarriedHidden(true);chopAge+=dt;
        ItemStack tool=view.state().getItems("knife")[0];
        boolean knife=!tool.isEmpty(), focused=view.isFocused();
        ItemStack pending=view.state().getItems("pending")[0];
        int chops=(int)view.state().getControl("chops"),total=(int)view.state().getControl("required");
        view.node("pending").ifPresent(n -> {n.setVisible(focused && knife);n.setEnabled(focused && knife);});
        view.node("mouth").ifPresent(n -> {n.setVisible(focused && !knife);n.setEnabled(focused && !knife);});
        view.node("surface").ifPresent(n -> {n.setVisible(focused);n.setEnabled(focused);});
        view.node("assemble").ifPresent(n -> {n.setVisible(focused && !knife);n.setEnabled(focused && !knife);});
        var cuts=new ArrayList<ItemVoxels.Cut>();
        for(int i=0;i<Math.min(chops,32);i++) cuts.add(line(pending,i,total));
        mesh(view,"food",pending,BoardTools.ITEM_SIZE,cuts,chops);
        var layers=view.state().getItems("ingredients");
        for(int i=0;i<5;i++) mesh(view,"layer_"+i,layers[i],8,List.of(),0);
        ItemVoxels.Cut target=line(pending,chops,total);
        view.node("marks").ifPresent(n -> {
            n.setVisible(focused && !pending.isEmpty());
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
            n.setShape(shape.build(),ResourceLocation.parse("minecraft:block/white_concrete"),true);n.setTint(0xC0FFFFFF);
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
        view.node("knife_turn").ifPresent(n -> {n.setVisible(view.isInUse() && knife);n.moveTo(knifeX-BoardTools.CENTER_X,3-dip,knifeZ-BoardTools.CENTER_Z,0);n.rotateTo(0,-(float)Math.toDegrees(target.angle()),0,0);});
        book.frame(view,dt);
    }
    private static ItemVoxels.Cut line(ItemStack item,int mark,int total) {
        var axis=ItemVoxels.icon(item).map(icon -> icon.axis(BoardTools.ITEM_SIZE)).orElse(ItemVoxels.Axis.square(3));
        float[] at=axis.at((mark+1F)/(Math.max(1,total)+1F));
        float lean=(mark%2==0 ? .6F : -.45F)*(axis.elongation()>=1.4F ? .35F : .85F);
        return new ItemVoxels.Cut(at[0],at[1],ItemVoxels.Axis.normalized(axis.across()+lean));
    }
    private void mesh(StationView view,String node,ItemStack item,float size,List<ItemVoxels.Cut> cuts,int chop) {
        view.node(node).ifPresent(n -> {
            n.setVisible(view.isInUse() && !item.isEmpty());if(item.isEmpty())return;
            String key=item.getItem()+":"+item.getComponents().hashCode()+":"+chop;
            if(key.equals(meshes.get(node)))return;meshes.put(node,key);
            var icon=ItemVoxels.icon(item);
            if(icon.isPresent()) {n.scaleTo(1,0);n.moveTo(0,0,0,0);n.setShape(ItemVoxels.build(icon.get(),size,cuts,.55F),icon.get().sprite(),true);}
            else {n.setVisual(Visual.ofItem(item.getItem()).withAnchor(new Vector3f(8,8,8)));n.scaleTo(.4F,0);n.moveTo(0,3.2F,0,0);}
        });
    }
    public void onEvent(StationView view,SceneEvent event) {
        if(event instanceof SceneEvent.Click c) {
            if(c.node().equals("book_open") && c.button()==0) {book.click(view);return;}
            if(c.node().equals("assemble") && c.button()==0) {view.sendAction("assemble",new CompoundTag());return;}
            if(!c.node().equals("surface"))return;
            if(c.button()==1) {view.sendAction("take",new CompoundTag());return;}
            if(c.button()!=0)return;
            if(view.state().getItems("pending")[0].isEmpty()) {view.sendAction("assemble",new CompoundTag());return;}
            chop(view);
        }
    }
    private void chop(StationView view) {
        long now=System.nanoTime();if(now-lastClick<200_000_000L)return;
        var cursor=view.cursorOn(1.1F);if(cursor.isEmpty())return;
        int mark=(int)view.state().getControl("chops"),total=(int)view.state().getControl("required");
        var p=cursor.get();chopAge=0;lastClick=now;
        if(p.x<1 || p.x>11 || p.z<3 || p.z>13 || Math.abs(line(view.state().getItems("pending")[0],mark,total).distance(p.x-BoardTools.CENTER_X,p.z-BoardTools.CENTER_Z))>1.2F) {view.playSound(SoundEvents.WOOD_HIT,"food",.3F,1.5F);return;}
        var tag=new CompoundTag();tag.putInt("mark",mark);tag.putFloat("x",p.x);tag.putFloat("z",p.z);view.sendAction("chop",tag);
    }
}

package ru.codex.stirandcharm.client;

import java.util.*;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector3f;
import ru.codex.stirandcharm.*;
import net.minecraft.client.Minecraft;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;

/** Pixel pieces and a knife following the pointer, adapted to the native Farm & Charm board. */
final class BoardBehavior implements StationBehavior {
    private final ServingGesture serving=new ServingGesture("assemble",2);
    private final RecipeBook book=new RecipeBook(new BoardCookbook());
    private final Map<String,String> meshes=new HashMap<>();
    private long lastClick;
    private float knifeX=6,knifeZ=8,chopAge=1;
    private ItemStack shownKnife=ItemStack.EMPTY;
    private String cutsKey="",marksKey="";
    private List<ItemVoxels.Cut> cuts=List.of();
    private MeshShape marksShape;
    private int iconGeneration=-1,invStamp=-1,carriedStamp=0;
    private final List<Step> steps=new ArrayList<>();
    private float clock,assembling,partAge=1;
    private ItemStack assemblyDish=ItemStack.EMPTY;
    private ResultBadge result;
    private record Step(float at,Runnable run){}
    public void onSceneReady(StationView view) {view.node("book_open").ifPresent(n -> n.setPainter(book));result=new ResultBadge(view,false);view.node("result").ifPresent(n->n.setPainter(result));view.node("dish").ifPresent(n -> n.setVisible(false));clock=0;steps.clear();assembling=0;reset(view);ApproachCamera.open(view);}
    public void onOpen(StationView view) {
        view.setInventoryFilter(null);ApproachCamera.open(view);book.opened(view);
        reset(view);
        var player=Minecraft.getInstance().player;
        if(view.openedByUse() && player!=null && !BoardTools.canStick(player.getMainHandItem())) {
            var tag=new CompoundTag();tag.putInt("from",player.getInventory().selected);tag.putBoolean("one",false);view.sendAction("place",tag);
        }
    }
    public void onClose(StationView view) {book.shut(view);steps.clear();assembling=0;reset(view);}
    public void onStateChanged(StationView view,StationState.Changes changes){book.invalidate();if(result!=null)result.refresh();if(assembling<=0)reset(view);}
    @Override public void onEffect(StationView view,net.minecraft.resources.ResourceLocation effect,String node,CompoundTag data){
        ClientDelivery.effect(view,effect,data);
        if(effect.equals(StirAndCharm.id("chopped")))partAge=0;
        if(effect.equals(StirAndCharm.id("placed"))){
            int index=data.getInt("index");var from=view.cursorOn(9).orElse(new Vector3f(6,9,8));
            after(.08F,()->view.node("layer_"+index).ifPresent(n->{if(assembling>0)return;var base=n.definition().transform().pos();n.moveTo(from.x-base.x(),9,from.z-base.z(),0);n.hopTo(0,0,0,4,.4F);}));
        }
        if(effect.equals(StirAndCharm.id("received")) || effect.equals(StirAndCharm.id("chopped")) && data.getBoolean("done")){
            var stack=ItemStack.parseOptional(view.level().registryAccess(),data.getCompound("stack"));
            view.node("arrival").ifPresent(n->{display(n,stack,4);n.moveTo(-2,-5,0,0);n.hopTo(-2,1,6,2,.3F);n.scaleTo(.15F,.3F);});
            after(.3F,()->view.node("arrival").ifPresent(n->n.setVisible(false)));
        }
        if(effect.equals(StirAndCharm.id("assembled"))){
            var layers=new ArrayList<ItemStack>();
            var list=data.getList("layers",Tag.TAG_COMPOUND);
            for(int i=0;i<list.size();i++){var stack=ItemStack.parseOptional(view.level().registryAccess(),list.getCompound(i));if(!stack.isEmpty())layers.add(stack);}
            var dish=data.contains("dish")?ItemStack.parseOptional(view.level().registryAccess(),data.getCompound("dish")):ItemStack.EMPTY;
            assembled(view,data.getString("finish"),layers,dish);
        }
    }
    public void tick(StationView view){
        var player=Minecraft.getInstance().player;if(player==null)return;
        int inv=player.getInventory().getTimesChanged(),carried=player.inventoryMenu.getCarried().hashCode();
        if(inv!=invStamp||carried!=carriedStamp){invStamp=inv;carriedStamp=carried;book.invalidate();}
    }
    public void frame(StationView view,float dt) {
        ClientDelivery.frame(view,dt);
        view.node("result").ifPresent(n->{n.setVisible(view.isFocused() && result!=null && result.hasMeal() && assembling<=0);n.setEnabled(false);});
        clock+=dt;
        if(!steps.isEmpty()){
            var due=steps.stream().filter(step -> step.at()<=clock).toList();
            steps.removeAll(due);
            due.forEach(step -> step.run().run());
        }
        if(assembling>0)assembling=Math.max(0,assembling-dt);
        var results=view.state().getItems("result");
        for(int i=0;i<results.length;i++){boolean present=false;view.node("result_"+i).ifPresent(n -> {n.setVisible(view.isFocused() && present && assembling<=0);n.setEnabled(view.isFocused() && present);});}
        chopAge+=dt;partAge+=dt;
        int generation=ItemVoxels.generation();
        if(generation!=iconGeneration){iconGeneration=generation;meshes.clear();cutsKey="";marksKey="";}
        ItemStack parked=view.state().getItems("knife")[0];
        ItemStack pending=view.state().getItems("pending")[0];
        ItemStack tool=tool(view);
        boolean knife=!tool.isEmpty(), focused=view.isFocused();
        var player=Minecraft.getInstance().player;
        boolean overBoard=view.cursorOn(1.1F).filter(p->p.x>=1 && p.x<=11 && p.z>=3 && p.z<=13).isPresent();
        boolean drawingKnife=overBoard || chopAge<.19F;
        view.setCarriedHidden(player!=null && drawingKnife && !pending.isEmpty() && BoardTools.supports(player.inventoryMenu.getCarried(),pending));
        int chops=(int)view.state().getControl("chops"),total=pending.isEmpty()?1:required(view);
        view.node("surface").ifPresent(n -> {n.setVisible(focused);n.setEnabled(focused);});
        view.node("mouth").ifPresent(n -> {n.setVisible(focused);n.setEnabled(focused);});
        boolean carved=!parked.isEmpty() && pending.isEmpty();
        view.node("carved").ifPresent(n -> {n.setVisible(view.isInUse() && carved);if(carved)n.setVisual(Visual.ofItem(parked.getItem()).withAnchor(new Vector3f(8,8,8)));});
        String itemKey=pending.getItem()+":"+pending.getComponents().hashCode()+":"+chops+":"+total+":"+view.state().data().get("spots")+":"+view.state().data().get("angles");
        if(!itemKey.equals(cutsKey)){
            cutsKey=itemKey;cuts=new ArrayList<>();
            for(int i=0;i<Math.min(chops,32);i++) cuts.add(mark(view,pending,i));
        }
        mesh(view,"food",pending,BoardTools.ITEM_SIZE,cuts,chops,total);
        var layers=view.state().getItems("ingredients");
        if(assembling<=0) {
            for(int i=0;i<9;i++) mesh(view,"layer_"+i,layers[i],8,List.of(),0,0);
            for (int i=0;i<9;i++) view.node("layer_"+i).ifPresent(n -> ContentFacing.turn(view,n));
        }
        view.node("food").ifPresent(n -> ContentFacing.turn(view,n));
        ItemVoxels.Cut target=mark(view,pending,chops);
        if(!marksKey.equals(itemKey)){marksKey=itemKey;marksShape=marks(pending,target);}
        view.node("marks").ifPresent(n -> {
            n.setVisible(focused && !pending.isEmpty());
            if(marksShape!=null){n.setShape(marksShape,ResourceLocation.parse("minecraft:block/white_concrete"),true);n.setTint(0xC0FFFFFF);}
            ContentFacing.turn(view,n);
        });
        var cursor=view.cursorOn(1.1F);
        float wantX=BoardTools.CENTER_X,wantZ=3;
        if(cursor.isPresent() && cursor.get().x>=1 && cursor.get().x<=11 && cursor.get().z>=3 && cursor.get().z<=13) {
            var point=new Vector3f(cursor.get());
            if(!pending.isEmpty()) {
                var food=view.node("food").orElseThrow();
                var local=new org.joml.Matrix4f(food.sceneMatrix()).invert().transformPosition(new Vector3f(point));
                float distance=target.distance(local.x,local.z);
                if(Math.abs(distance)<1.2F){local.x-=target.normalX()*distance;local.z-=target.normalZ()*distance;point=food.sceneMatrix().transformPosition(local);}
            }
            wantX=point.x;wantZ=point.z;
        }
        float follow=1-(float)Math.exp(-Math.min(dt,.1F)*25);
        knifeX+=(wantX-knifeX)*follow;knifeZ+=(wantZ-knifeZ)*follow;
        if(!ItemStack.matches(shownKnife,tool)) {
            shownKnife=tool.copy();
            view.node("knife_model").ifPresent(n -> {if(!tool.isEmpty())n.setVisual(Visual.ofItem(tool.getItem()).withAnchor(new Vector3f(8,8,8)));});
        }
        float down=chopAge<.05F?chopAge/.05F:Math.max(0,1-(chopAge-.05F)/.14F);
        float dip=down*2.4F;
        view.node("knife_turn").ifPresent(n -> {n.setVisible(view.isInUse() && drawingKnife && knife && !pending.isEmpty());n.moveTo(knifeX-BoardTools.CENTER_X,3-dip,knifeZ-BoardTools.CENTER_Z,0);n.rotateTo(0,ContentFacing.yaw(view)-(float)Math.toDegrees(target.angle()),0,0);});
        serving.frame(view,view.state().data().getBoolean("finish_ready"),assembling>0);
        book.frame(view,dt);
    }
    private static ItemStack tool(StationView view) {
        var food=view.state().getItems("pending")[0];var parked=view.state().getItems("knife")[0];
        if(food.isEmpty())return ItemStack.EMPTY;
        if(Arrays.stream(view.state().getItems("ingredients")).anyMatch(stack -> !stack.isEmpty()))return ItemStack.EMPTY;
        if(BoardTools.supports(parked,food))return parked;
        var p=Minecraft.getInstance().player;if(p==null)return ItemStack.EMPTY;
        var carried=p.inventoryMenu.getCarried();
        if(!carried.isEmpty())return BoardTools.supports(carried,food)?carried:ItemStack.EMPTY;
        if(BoardTools.supports(p.getMainHandItem(),food))return p.getMainHandItem();
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(BoardTools.supports(p.getInventory().getItem(i),food))return p.getInventory().getItem(i);
        return ItemStack.EMPTY;
    }
    private static int required(StationView view) {
        return Math.max(1,(int)view.state().getControl("required"));
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
    private static ItemVoxels.Cut mark(StationView view,ItemStack item,int mark) {
        if(item.isEmpty())return new ItemVoxels.Cut(0,0,0);
        var data=view.state().data();
        var spots=data.getList("spots",Tag.TAG_FLOAT);
        var angles=data.getList("angles",Tag.TAG_FLOAT);
        int style=data.getInt("style");
        float spot=mark<spots.size()?spots.getFloat(mark):(mark+1F)/(Math.max(1,data.getInt("required"))+1F);
        float angle=mark<angles.size()?angles.getFloat(mark):0F;
        return line(item,style,spot,angle);
    }
    private static ItemVoxels.Cut line(ItemStack item,int style,float spot,float angle) {
        var axis=ItemVoxels.icon(item).map(icon -> icon.axis(BoardTools.ITEM_SIZE)).orElse(ItemVoxels.Axis.square(3));
        if(style==BoardCutPlan.Style.WEDGES.ordinal()) return new ItemVoxels.Cut(axis.x(),axis.z(),ItemVoxels.Axis.normalized(angle));
        float[] at=axis.at(spot);
        float lean=style==BoardCutPlan.Style.ACROSS.ordinal()?angle*(axis.elongation()>=1.4F?.35F:.85F):0F;
        return new ItemVoxels.Cut(at[0],at[1],ItemVoxels.Axis.normalized(axis.across()+lean));
    }
    private void mesh(StationView view,String node,ItemStack item,float size,List<ItemVoxels.Cut> cuts,int chop,int total) {
        view.node(node).ifPresent(n -> {
            n.setVisible(view.isInUse() && !item.isEmpty());if(item.isEmpty())return;
            int phase=node.equals("food")?Math.min(8,(int)(partAge/.22F*8)):8;
            String key=item.getItem()+":"+item.getComponents().hashCode()+":"+chop+":"+total+":"+phase+"@"+ItemVoxels.generation();
            if(key.equals(meshes.get(node)))return;meshes.put(node,key);
            var icon=ItemVoxels.icon(item);
            if(icon.isPresent()) {n.scaleTo(1,0);n.moveTo(0,0,0,0);n.setShape(ItemVoxels.build(icon.get(),size,cuts,.55F*phase/8F),icon.get().sprite(),true);}
            else {n.setVisual(Visual.ofItem(item.getItem()).withAnchor(new Vector3f(8,8,8)));n.scaleTo(.4F,0);n.moveTo(0,3.2F,0,0);}
        });
    }
    public void onEvent(StationView view,SceneEvent event) {
        if(serving.event(view,event))return;
        if(!(event instanceof SceneEvent.Click c))return;
        if(c.node().equals("book_open")){if(c.button()==0)book.click(view);return;}
        if(!c.node().equals("surface") && !c.node().equals("mouth"))return;
        if(!view.state().getItems("knife")[0].isEmpty() && view.state().getItems("pending")[0].isEmpty()) {view.sendAction("take",new CompoundTag());return;}
        boolean layers=Arrays.stream(view.state().getItems("ingredients")).anyMatch(stack -> !stack.isEmpty());
        if(layers) {
            boolean ready=!view.state().data().getString("dish").isEmpty() || view.state().data().getBoolean("finish_ready");
            view.sendAction(c.button()==0&&ready?"assemble":"take",new CompoundTag());
            return;
        }
        if(c.button()==1){view.sendAction("take",new CompoundTag());return;}
        if(c.button()!=0)return;
        if(!view.state().getItems("pending")[0].isEmpty()) {
            if(!tool(view).isEmpty())chop(view);else view.sendAction("take",new CompoundTag());
        } else view.sendAction("assemble",new CompoundTag());
    }
    public boolean onItemClick(StationView view,String node,ItemStack stack,int button,boolean shift) {
        if(serving.item(view,node,stack,shift))return true;
        if(node.equals("book_open")){if(button==0)book.click(view);return true;}
        if(!node.equals("surface") && !node.equals("mouth") && !node.equals("serve_area"))return false;
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
        int mark=(int)view.state().getControl("chops");
        var p=cursor.get();var local=new org.joml.Matrix4f(view.node("food").orElseThrow().sceneMatrix()).invert().transformPosition(new Vector3f(p));chopAge=0;lastClick=now;
        if(p.x<1 || p.x>11 || p.z<3 || p.z>13 || Math.abs(mark(view,view.state().getItems("pending")[0],mark).distance(local.x,local.z))>1.2F) {view.playSound(SoundEvents.WOOD_HIT,"food",.3F,1.5F);return;}
        var tag=new CompoundTag();tag.putInt("mark",mark);tag.putFloat("at",p.z);tag.putFloat("x",p.x);tag.putFloat("z",p.z);view.sendAction("chop",tag);
    }

    private void after(float seconds,Runnable run){steps.add(new Step(clock+Math.max(0,seconds),run));}
    /** Puts every reused node back to its static transform; animations and cached shapes may leave scale, offset or tint behind. */
    private void reset(StationView view){
        meshes.clear();cutsKey="";marksKey="";marksShape=null;
        for(int i=0;i<9;i++) view.node("layer_"+i).ifPresent(n -> {rest(n);n.setTint(0xFFFFFFFF);n.setVisible(false);});
        view.node("dish").ifPresent(n -> {rest(n);n.setTint(0xFFFFFFFF);n.setVisible(false);});
        view.node("food").ifPresent(n -> {rest(n);n.setVisible(false);});
        view.node("marks").ifPresent(n -> n.setVisible(false));
    }
    private static void rest(NodeView n){n.scaleTo(1,0);n.moveTo(0,0,0,0);n.rotateTo(0,0,0,0);}
    private static void display(NodeView node,ItemStack stack,float size){
        if(stack.isEmpty()){node.setVisible(false);return;}
        node.setTint(0xFFFFFFFF);
        var icon=ItemVoxels.icon(stack);
        if(icon.isPresent()){node.scaleTo(1,0);node.moveTo(0,0,0,0);node.setShape(ItemVoxels.build(icon.get(),size,List.of(),0),icon.get().sprite(),true);}
        else {node.setVisual(Visual.ofItem(stack.getItem()).withAnchor(new Vector3f(8,8,8)));node.scaleTo(.4F,0);node.moveTo(0,3.2F,0,0);}
        node.setVisible(true);
    }
    /** HOD-like finish: the layers come together, then the dish pops up in their place. */
    private void assembled(StationView view,String finish,List<ItemStack> layers,ItemStack dish){
        assembling=1.6F;
        for(int i=0;i<9;i++){
            var node=view.node("layer_"+i);if(node.isEmpty())continue;
            var stack=i<layers.size()?layers.get(i):ItemStack.EMPTY;
            if(stack.isEmpty()){node.get().setVisible(false);continue;}
            display(node.get(),stack,8);
            node.get().rotateTo(0,ContentFacing.yaw(view),0,0);
            final int index=i;
            after(.04F*i,() -> {
                float hop=switch(finish){
                    case "TOSS" -> 2.5F;
                    case "SANDWICH" -> index==layers.size()-1?2.4F:1.2F;
                    case "ROLL" -> .8F;
                    case "SKEWER" -> 1.8F;
                    case "KNEAD" -> .5F;
                    case "ARRANGE" -> 1.6F;
                    case "SHAPE" -> 1.2F;
                    case "FREEZE" -> .3F;
                    default -> 1.4F;
                };
                node.get().hopTo(0,0,0,hop,.22F);
                if(finish.equals("TOSS"))node.get().rotateTo(25,(index%2==0?1:-1)*70,15,.22F);
                else if(finish.equals("SANDWICH") && index==layers.size()-1)node.get().rotateTo(-18,0,0,.22F);
                else if(finish.equals("ROLL"))node.get().rotateTo(0,index%2==0?18:-18,0,.22F);
                else if(finish.equals("ARRANGE"))node.get().moveTo((index%2==0?-1:1)*.6F,0,(index/2%2==0?-1:1)*.5F,.22F);
                else if(finish.equals("SHAPE"))node.get().moveTo(0,0,0,.22F);
                else if(finish.equals("FREEZE"))node.get().setTint(0xFFDFF4FF);
                else if(finish.equals("KNEAD"))node.get().scaleTo(.85F,.18F);
            });
            after(.55F,() -> {
                node.get().scaleTo(.08F,.3F);
                node.get().moveTo(0,-.03F*index,0,.3F);
            });
            after(.85F,()->node.get().setVisible(false));
        }
        after(.68F,() -> {
            var node=view.node("dish");if(node.isEmpty())return;
            display(node.get(),dish,8);
            node.get().scaleTo(.2F,0);
            view.spawnParticles(DiegesisParticles.DUST,new Vector3f(6,2.4F,8),new Vector3f(0,2.2F,0),2.5F,12,0xFFFFFFFF);
            view.spawnParticles(DiegesisParticles.GLINT,new Vector3f(6,2.6F,8),new Vector3f(0,1.6F,0),2F,6,0xFFFFE9A0);
            view.playSound(SoundEvents.ITEM_PICKUP,"surface",.7F,1.1F);
        });
        after(.78F,() -> {
            view.node("dish").ifPresent(n -> {n.setVisible(true);n.scaleTo(1,.18F);n.hopTo(0,1.2F,0,2F,.22F);});
        });
        after(1.45F,() -> {
            // Completion is also the cancel point for a close/reopen: every animated node goes back to
            // its static transform, and the cached meshes are rebuilt from the state next frame.
            view.node("dish").ifPresent(n -> n.setVisible(false));
            reset(view);
            assembling=0;
        });
    }
}

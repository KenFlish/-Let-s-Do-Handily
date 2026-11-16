package ru.codex.stirandcharm.client;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import net.minecraft.nbt.CompoundTag;
import ru.codex.stirandcharm.*;

final class ApplianceBehavior implements StationBehavior {
    private final Appliance kind;
    private final RecipeBook book;
    private final ApplianceCookbook source;
    private ResultBadge result;
    private final ServingGesture serving=new ServingGesture("collect",7);
    private boolean cranking, pinned;
    private final HandMotion hand = new HandMotion();
    private float lidAngle;
    private long start, sent = Long.MIN_VALUE;
    ApplianceBehavior(Appliance kind) { this.kind = kind; source = new ApplianceCookbook(kind); book = new RecipeBook(source); }
    public void onEffect(StationView view,net.minecraft.resources.ResourceLocation id,String node,CompoundTag tag){serving.effect(view,id,tag);}
    public void onSceneReady(StationView view) {
        view.node("book_open").ifPresent(n -> n.setPainter(book));
        result = new ResultBadge(view, kind, source);
        view.node("result").ifPresent(n -> n.setPainter(result));
        if(kind==Appliance.ROASTER)TrayFood.frame(view);
        ApproachCamera.open(view);
        if (kind == Appliance.ROASTER && view.level().getBlockState(view.pos()).hasProperty(RoasterLid.OPEN)) {
            view.node("roaster_body").ifPresent(n -> n.setPainter(new RoasterBody(view, false)));
            view.node("lid").ifPresent(n -> n.setPainter(new RoasterBody(view, true)));
            lidAngle=view.level().getBlockState(view.pos()).getValue(RoasterLid.OPEN) ? 105 : 0;
        }
    }
    public void onOpen(StationView view) { view.setInventoryFilter(null); ApproachCamera.open(view); book.opened(view); }
    public void onClose(StationView view) { cranking = false; pinned = false; book.shut(view); }
    public void onStateChanged(StationView view, StationState.Changes changes) { if (result != null) result.refresh(); }
    public void tick(StationView view) {
        long now = view.level().getGameTime();
        if (!cranking || !view.isFocused() || sent != Long.MIN_VALUE && now - sent < 2) return;
        {
            float[] p=hand.sample(); float x=p[0],z=p[1];
            var data = new CompoundTag(); data.putFloat("x", x); data.putFloat("z", z);
            view.sendAction("crank", data); sent = now;
        }
    }
    public void frame(StationView view, float dt) {
        view.setCarriedHidden(false);
        view.node("result").ifPresent(n -> { n.setVisible(kind==Appliance.STOVE && view.isFocused()); n.setEnabled(kind==Appliance.STOVE && view.isFocused()); });
        view.node("fuel").ifPresent(n -> { n.setVisible(view.isFocused()); n.setEnabled(view.isFocused()); n.setOnTop(true); });
        view.node("fire").ifPresent(n -> { n.setVisible(view.isFocused()); n.setEnabled(view.isFocused()); n.setOnTop(true); });
        if(kind==Appliance.ROASTER)TrayFood.frame(view);
        if (kind == Appliance.ROASTER && view.level().getBlockState(view.pos()).hasProperty(RoasterLid.OPEN)) {
            view.node("roaster_body").ifPresent(n -> n.setVisible(view.isInUse()));
            boolean open=view.state().getControl("lid_open")>.5F;
            lidAngle += ((open ? 105 : 0)-lidAngle)*(1-(float)Math.exp(-Math.min(dt,.1F)*12));
            float rumble = !open && view.state().getControl("progress")>0 && view.state().getControl("heated")>0
                ? (float)Math.sin(view.age()*18)*.35F : 0;
            view.node("lid").ifPresent(n -> { n.setVisible(view.isInUse()); n.rotateTo(lidAngle+rumble,0,0,0); });
            // Hanging roasters use the same bowl two pixels above the standing version.
            boolean hanging = view.level().getBlockState(view.pos()).getValue(net.satisfy.farm_and_charm.core.block.RoasterBlock.HANGING);
            view.node("lid").ifPresent(n -> n.moveTo(0, hanging ? 2 : 0, 0, 0));
            for (int i = 0; i < 6; i++) view.node("ingredient_" + i).ifPresent(n -> {
                n.moveTo(0, hanging ? 2 : 0, 0, 0); n.setVisible(open && lidAngle>40); n.setEnabled(open);
            });
            view.node("mouth").ifPresent(n -> { n.setVisible(open); n.setEnabled(open); n.moveTo(0,hanging?2:0,0,0); });
            view.node("cooked_food").ifPresent(n->{n.setVisible(open && lidAngle>40 && !view.state().getItems("result")[0].isEmpty());n.moveTo(0,hanging?2:0,0,0);});
        } else if (kind == Appliance.STOVE) {
            for (int i=0;i<3;i++) view.node("ingredient_"+i).ifPresent(n -> {
                n.setVisible(view.isFocused()); n.setEnabled(view.isFocused()); n.setOnTop(true);
            });
        }
        serving.frame(view,kind==Appliance.ROASTER && !view.state().getItems("result")[0].isEmpty(),view.state().getControl("lid_open")<.5F && view.level().getBlockState(view.pos()).hasProperty(RoasterLid.OPEN));
        book.frame(view, dt);
    }
    public void onEvent(StationView view, SceneEvent event) {
        if(serving.event(view,event))return;
        if (event instanceof SceneEvent.Stroke s && s.node().equals("crank_handle") && s.button() == 0) {
            if (s.phase() == SceneEvent.Stroke.Phase.START) { cranking = true; if (!pinned) hand.grab(false); start = System.nanoTime(); }
            if (s.phase() == SceneEvent.Stroke.Phase.END) {
                if (!pinned && System.nanoTime() - start < 250_000_000L) { pinned = true; view.holdStroke(); }
                else { cranking = false; pinned = false; }
            }
        } else if (event instanceof SceneEvent.Click c) {
            if (c.node().equals("book_open") && c.button() == 0) book.click(view);
            if (c.node().equals("fire")) view.sendAction(c.button() == 1 ? "extinguish" : "ignite", new CompoundTag());
            if (kind==Appliance.ROASTER && c.node().equals("lid") && c.button()==0) view.sendAction("lid",new CompoundTag());
            if (kind==Appliance.ROASTER && c.node().equals("result")) collect(view,c.button());
        }
    }
    public boolean onItemClick(StationView view, String node, net.minecraft.world.item.ItemStack stack, int button, boolean shift) {
        if(serving.item(view,node,stack,shift))return true;
        if (kind==Appliance.ROASTER && node.equals("result")) { collect(view, button, Vessels.holds(view, stack), shift); return true; }
        if (kind != Appliance.ROASTER || !(node.equals("mouth") || node.startsWith("ingredient_"))) return false;
        if (kind.recipes(view.level()).stream().anyMatch(h -> h.value().getIngredients().stream().anyMatch(i -> i.test(stack)))) return false;
        if (kind.recipes(view.level()).stream().map(h -> kind.dish(h.value())).noneMatch(d -> !d.isEmpty() && d.is(stack.getItem()))) return false;
        var data = new CompoundTag(); data.putBoolean("one", button == 1); view.sendAction("serve", data); return true;
    }
    private void collect(StationView view, int button) {
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        collect(view, button, false, shift);
    }
    private void collect(StationView view, int button, boolean vessel, boolean shift) {
        var data=new CompoundTag();
        data.putBoolean("one", !shift && (vessel || button == 1));
        data.putBoolean("inventory", shift);
        view.sendAction("collect",data);
    }
}

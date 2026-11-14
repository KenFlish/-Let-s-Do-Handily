package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import ru.codex.stirandcharm.*;

public final class KitchenBehavior implements StationBehavior {
    private final boolean bowl;
    private final RecipeBook book;
    private final SoupAppearance soup = new SoupAppearance();
    private ResultBadge result;
    private boolean stirring, pinned;
    private final HandMotion hand = new HandMotion();
    private float foodAngle, spin, knownAngle;
    private float spoonAngle, spoonTarget;
    private boolean angleKnown;
    private long start, lastSent = Long.MIN_VALUE;
    public KitchenBehavior(boolean bowl) { this.bowl = bowl; book = new RecipeBook(new FarmCookbook(bowl)); }
    @Override public void onSceneReady(StationView view) {
        view.node("book_open").ifPresent(n -> n.setPainter(book));
        result = new ResultBadge(view, bowl);
        view.node("result").ifPresent(n -> n.setPainter(result));
        if (!bowl) {
            view.node("pot_body").ifPresent(n -> n.setPainter(new PotBody(view, false)));
            view.node("spoon").ifPresent(n -> n.setPainter(new PotBody(view, true)));
        }
        apply(view);
        if (!bowl) ApproachCamera.open(view);
    }
    @Override public void onOpen(StationView view) {
        book.opened(view);
        // Keep every item visible, including prepared dishes put back from the station.
        view.setInventoryFilter(null);
    }
    @Override public void onClose(StationView view) { stirring = false; pinned = false; book.shut(view); }
    @Override public void onStateChanged(StationView view, StationState.Changes changes) { apply(view); }
    @Override public void tick(StationView view) {
        if (!stirring || !view.isFocused() || view.level().getGameTime() == lastSent) return;
        if (lastSent != Long.MIN_VALUE && view.level().getGameTime() - lastSent < 2) return;
        {
            float[] point = hand.sample();
            float x = point[0], z = point[1];
            CompoundTag data = new CompoundTag(); data.putFloat("x", x); data.putFloat("z", z);
            view.sendAction("stir", data);
            lastSent = view.level().getGameTime();
        }
    }
    @Override public void frame(StationView view, float delta) {
        view.setCarriedHidden(false);
        view.node("result").ifPresent(n -> { n.setVisible(view.isFocused()); n.setEnabled(view.isFocused()); n.setOnTop(true); });
        if (!bowl) {
            view.node("pot_body").ifPresent(n -> n.setVisible(view.isInUse()));
            float x = view.state().getControl("spoon_x"), z = view.state().getControl("spoon_z");
            if (x*x+z*z > .1F) {
                float a=(float)Math.atan2(z,x);
                if (angleKnown) {
                    float turn=(float)Math.atan2(Math.sin(a-knownAngle),Math.cos(a-knownAngle));
                    if (Math.abs(turn)>.001F) { spin=Math.clamp(turn / Math.max(delta,.02F),-5F,5F); spoonTarget-=(float)Math.toDegrees(turn); }
                }
                angleKnown=true; knownAngle=a;
            }
            foodAngle += spin*delta; spin *= (float)Math.exp(-delta*3);
            for (int i=0;i<6;i++) view.node("ingredient_"+i).ifPresent(n -> {
                var base=n.definition().transform().pos();
                float bx=base.x()-8,bz=base.z()-8;
                float radius=(float)Math.hypot(bx,bz); if (radius>3.4F) { bx*=3.4F/radius; bz*=3.4F/radius; }
                float rx=(float)(bx*Math.cos(foodAngle)-bz*Math.sin(foodAngle));
                float rz=(float)(bx*Math.sin(foodAngle)+bz*Math.cos(foodAngle));
                n.moveTo(8+rx-base.x(),0,8+rz-base.z(),0);
            });
            // Rotate the actual spoon from the installed pot model, as the native bowl rotates its whisk.
            spoonAngle += (spoonTarget-spoonAngle)*(1-(float)Math.exp(-delta*18));
            view.node("spoon").ifPresent(n -> n.rotateTo(0, view.state().getControl("lid_open")>.5F?spoonAngle:0, 0, 0));
            soup.frame(view, delta);
        }
        book.frame(view, delta);
    }
    @Override public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke s && s.node().equals("stir_handle") && s.button() == 0) {
            if (s.phase() == SceneEvent.Stroke.Phase.START) { stirring = true; if (!pinned) hand.grab(true); start = System.nanoTime(); }
            if (s.phase() == SceneEvent.Stroke.Phase.END) {
                if (!pinned && System.nanoTime() - start < 250_000_000L) { pinned = true; view.holdStroke(); }
                else { stirring = false; pinned = false; }
            }
        } else if (event instanceof SceneEvent.Click click) {
            if (click.node().equals("book_open") && click.button() == 0) book.click(view);
            else if (click.node().equals("result") && !bowl) collect(view,click.button());
        }
    }
    @Override public boolean onItemClick(StationView view, String node, net.minecraft.world.item.ItemStack stack, int button, boolean shift) {
        if (!bowl && node.equals("result")) { collect(view, button, Vessels.holds(view, stack), shift); return true; }
        if (bowl || !(node.equals("mouth") || node.startsWith("ingredient_"))) return false;
        var recipes = KitchenRecipes.all(view.level(), false);
        if (recipes.stream().anyMatch(h -> h.value().getIngredients().stream().anyMatch(i -> i.test(stack)))) return false;
        if (recipes.stream().map(h -> KitchenRecipes.dish(h.value())).noneMatch(d -> !d.isEmpty() && d.is(stack.getItem()))) return false;
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
    private void apply(StationView view) {
        if (!bowl) {
            soup.refresh(view);
            var inputs = view.state().getItems("ingredients");
            for (int i = 0; i < inputs.length; i++) {
                var stack = inputs[i];
                view.node("ingredient_" + i).ifPresent(n -> n.setVisible(!KitchenIngredients.liquid(stack) && !KitchenIngredients.dissolved(stack)));
            }
        }
        if (result != null) result.refresh();
    }
}

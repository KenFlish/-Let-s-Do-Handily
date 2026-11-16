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
    private final ServingGesture serving;
    private final PotArrivals arrivals=new PotArrivals();
    private final SoupAppearance soup = new SoupAppearance();
    private ResultBadge result;
    private boolean stirring, pinned;
    private final HandMotion hand = new HandMotion();
    private final PotMotion potMotion = new PotMotion();
    private long start, lastSent = Long.MIN_VALUE;
    public KitchenBehavior(boolean bowl) { this.bowl = bowl;serving=new ServingGesture("collect",bowl?8:7.25F); book = new RecipeBook(new FarmCookbook(bowl)); }
    public void onEffect(StationView view,net.minecraft.resources.ResourceLocation id,String node,CompoundTag tag){serving.effect(view,id,tag);}
    @Override public void onSceneReady(StationView view) {
        view.node("book_open").ifPresent(n -> n.setPainter(book));
        result = new ResultBadge(view, bowl);
        view.node("result").ifPresent(n -> n.setPainter(result));
        if (!bowl) {
            view.node("pot_body").ifPresent(n -> n.setPainter(new PotBody(view, false)));
            view.node("spoon").ifPresent(n -> n.setPainter(new PotBody(view, true)));
            view.node("contents").ifPresent(n -> n.setTooltip(Component.translatable("handson_farm_charm.pot_contents")));
        }
        apply(view);
        if (!bowl) ApproachCamera.open(view);
    }
    @Override public void onOpen(StationView view) {
        book.opened(view);if(!bowl)arrivals.opened(view);
        // Keep every item visible, including prepared dishes put back from the station.
        view.setInventoryFilter(null);
    }
    @Override public void onClose(StationView view) { stirring = false; pinned = false; arrivals.stop();book.shut(view); }
    @Override public void onStateChanged(StationView view, StationState.Changes changes) { apply(view); }
    @Override public void tick(StationView view) {
        if (!stirring || !view.isFocused() || view.level().getGameTime() == lastSent) return;
        if (lastSent != Long.MIN_VALUE && view.level().getGameTime() - lastSent < 2) return;
        {
            float[] point = bowl ? hand.sample() : PotMotion.point(view);
            if (point == null) return;
            float x = point[0], z = point[1];
            CompoundTag data = new CompoundTag(); data.putFloat("x", x); data.putFloat("z", z);
            view.sendAction("stir", data);
            lastSent = view.level().getGameTime();
        }
    }
    @Override public void frame(StationView view, float delta) {
        view.setCarriedHidden(false);
        view.node("stir_handle").ifPresent(n->n.setEnabled(view.isFocused()));
        view.node("mouth").ifPresent(n->n.setEnabled(view.isFocused()));
        view.node("result").ifPresent(n -> { n.setVisible(false); n.setEnabled(false); });
        if (!bowl) {
            view.node("pot_body").ifPresent(n -> n.setVisible(view.isInUse()));
            boolean poured=java.util.Arrays.stream(view.state().getItems("ingredients")).anyMatch(KitchenIngredients::liquid);
            view.node("contents").ifPresent(n -> n.setEnabled(view.isFocused() && poured && !stirring));
            soup.frame(view, delta);
            potMotion.frame(view, delta, stirring && view.isFocused());
            arrivals.frame(view,delta,stirring);
        }
        serving.frame(view,!view.state().getItems("result")[0].isEmpty(),stirring);
        if(bowl)view.node("cooked_food").ifPresent(n->{var meal=view.state().getItems("result")[0];var built=TrayFood.build(meal);n.setVisible(view.isInUse() && built!=null);if(built!=null){n.setShape(built.shape(),built.texture());ContentFacing.turn(view,n);}});
        book.frame(view, delta);
    }
    @Override public void onEvent(StationView view, SceneEvent event) {
        if(serving.event(view,event))return;
        if (event instanceof SceneEvent.Stroke s && s.node().equals("stir_handle") && s.button() == 0) {
            if (s.phase() == SceneEvent.Stroke.Phase.START) { stirring = true; if (!pinned) hand.grab(true); start = System.nanoTime(); }
            if (s.phase() == SceneEvent.Stroke.Phase.END) {
                if (!pinned && System.nanoTime() - start < 250_000_000L) { pinned = true; view.holdStroke(); }
                else { stirring = false; pinned = false; }
            }
        } else if (event instanceof SceneEvent.Click click) {
            if (click.node().equals("book_open") && click.button() == 0) book.click(view);
            else if (click.node().equals("result") && !bowl) collect(view,click.button());
            else if (!bowl && click.node().equals("contents") && click.button()==1 && !stirring
                && net.minecraft.client.Minecraft.getInstance().player.inventoryMenu.getCarried().isEmpty())
                view.sendAction("take_liquid",new CompoundTag());
        }
    }
    @Override public boolean onItemClick(StationView view, String node, net.minecraft.world.item.ItemStack stack, int button, boolean shift) {
        if(serving.item(view,node,stack,shift))return true;
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
                view.node("ingredient_" + i).ifPresent(n -> n.setVisible(!stack.isEmpty() && !KitchenIngredients.liquid(stack)));
            }
        }
        if (result != null) result.refresh();
    }
}

package ru.codex.stirandcharm;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.*;
import com.pockyl.diegesis.api.station.StationType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(StirAndCharm.ID)
public final class StirAndCharm {
    // Retained for existing installations; the public name is [Let's Do] Handily.
    public static final String ID = "handson_farm_charm";
    public static final ResourceLocation POT = id("cooking_pot");
    public static final ResourceLocation BOWL = id("crafting_bowl");
    public static final ResourceLocation BOARD = id("cutting_board");
    public static final ResourceLocation JAM = id("jam_pot");
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public StirAndCharm(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Settings.SPEC);
        Diegesis.register(station(POT, "cooking_pot", 6, false));
        Diegesis.register(station(BOWL, "crafting_bowl", 4, true));
        for (var kind : Appliance.values()) Diegesis.register(appliance(kind));
        Diegesis.register(Diegesis.station(BOARD)
            .block(() -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:cutting_board")))
            .schema(StationSchema.builder()
                .slots("ingredients",9,SlotRules.DEFAULT.kept().maxStack(1).accepts(s -> false))
                .slots("pending",1,SlotRules.DEFAULT.kept().maxStack(16).accepts(s -> false))
                .slots("knife",1,SlotRules.DEFAULT.kept().maxStack(1).accepts(s -> false))
                .slots("result",8,SlotRules.DEFAULT.accepts(s -> false))
                .control("progress",ControlSpec.range(0,1,0).locked())
                .control("chops",ControlSpec.range(0,1024,0).locked())
                .control("required",ControlSpec.range(1,1024,1).locked()).build())
            .orientation(StationType.Orientation.BLOCK)
            .openWhen((p,pos,s) -> !p.isSecondaryUseActive() && Settings.BOARD.get())
            .logic(BoardLogic::new).build());
        BoardLogic.registerOutputs();
        if (net.neoforged.fml.ModList.get().isLoaded("candlelight")) {
            Diegesis.register(station(id("candlelight_pot"), "candlelight:cooking_pot",6,false));
            Diegesis.register(appliance(Appliance.ROASTER,"candlelight:cooking_pan","candlelight_pan"));
        }
        if(net.neoforged.fml.ModList.get().isLoaded("bakery"))Diegesis.register(Diegesis.station(JAM)
            .block(() -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse("bakery:small_cooking_pot")))
            .schema(StationSchema.builder().slots("ingredients",4,SlotRules.DEFAULT.kept().accepts(s -> false))
                .control("progress",ControlSpec.range(0,1,0).locked()).control("heated",ControlSpec.toggle(false).locked())
                .control("stir_due",ControlSpec.toggle(false).locked()).control("ready",ControlSpec.toggle(false).locked()).build())
            .orientation(StationType.Orientation.BLOCK)
            .openWhen((p,pos,state) -> Settings.BAKERY.get() && !p.isSecondaryUseActive())
            .logic(JamLogic::new).build());
    }

    private static StationType station(ResourceLocation id, String block, int inputs, boolean bowl) {
        var schema = StationSchema.builder()
            .slots("ingredients", inputs, SlotRules.DEFAULT.kept().maxStack(bowl ? 1 : 64))
            .slots("result", 1, SlotRules.DEFAULT.kept().accepts(s -> false))
            .control("progress", ControlSpec.range(0, 1, 0).locked())
            .control("heated", ControlSpec.toggle(false).locked())
            .control("lid_open",ControlSpec.toggle(true).locked())
            .control("spoon_x", ControlSpec.range(-3, 3, 0).locked())
            .control("spoon_z", ControlSpec.range(-3, 3, 0).locked())
            .tank("broth", TankSpec.of(100));
        if (!bowl) schema.slots("containers", 1, SlotRules.DEFAULT.kept());
        var type = Diegesis.station(id)
            .block(() -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse(block.contains(":") ? block : "farm_and_charm:"+block)))
            .schema(schema.build()).orientation(StationType.Orientation.PLAYER)
            .openWhen((player, pos, state) -> !player.isSecondaryUseActive() && (!block.startsWith("candlelight:") || Settings.CANDLELIGHT.get()) && (bowl ? Settings.BOWL.get() : Settings.POT.get()))
            .logic(context -> new KitchenLogic(context, bowl));
        if (!bowl) type.hidesBlock();
        return type.build();
    }

    private static StationType appliance(Appliance kind) {
        return appliance(kind,"farm_and_charm:"+kind.block,kind.block);
    }
    private static StationType appliance(Appliance kind,String block,String scene) {
        var schema = StationSchema.builder()
            .slots("ingredients", kind.inputs, SlotRules.DEFAULT.kept())
            .slots("result", 1, SlotRules.DEFAULT.kept().accepts(s -> false))
            .control("progress", ControlSpec.range(0, 1, 0).locked())
            .control("heated", ControlSpec.toggle(false).locked());
        if (kind == Appliance.ROASTER) schema.slots("containers", 1, SlotRules.DEFAULT.kept());
        if (kind == Appliance.STOVE) schema.slots("fuel", 1, SlotRules.DEFAULT.kept());
        if (kind == Appliance.ROASTER) schema.control("lid_open", ControlSpec.toggle(false).locked());
        var type = Diegesis.station(id(scene))
            .block(() -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse(block)))
            .schema(schema.build()).orientation(kind == Appliance.MINCER ? StationType.Orientation.PLAYER : StationType.Orientation.BLOCK)
            .openWhen((player, pos, state) -> !player.isSecondaryUseActive() && kind.enabled() && (!block.startsWith("candlelight:") || Settings.CANDLELIGHT.get()))
            .logic(ctx -> new ApplianceLogic(ctx, kind));
        if (kind == Appliance.MINCER || kind == Appliance.ROASTER && !block.startsWith("candlelight:")) type.hidesBlock();
        return type.build();
    }
}

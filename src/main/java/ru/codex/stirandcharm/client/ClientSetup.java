package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import ru.codex.stirandcharm.StirAndCharm;

@Mod(value = StirAndCharm.ID, dist = Dist.CLIENT)
public final class ClientSetup {
    public ClientSetup(IEventBus bus) {
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            DiegesisClient.registerBehavior(StirAndCharm.POT, view -> new KitchenBehavior(false));
            DiegesisClient.registerBehavior(StirAndCharm.BOWL, view -> new KitchenBehavior(true));
            DiegesisClient.registerBehavior(StirAndCharm.BOARD, view -> new BoardBehavior());
            for (var kind : ru.codex.stirandcharm.Appliance.values())
                if (kind != ru.codex.stirandcharm.Appliance.MINCER)
                DiegesisClient.registerBehavior(StirAndCharm.id(kind.block), view -> kind == ru.codex.stirandcharm.Appliance.STOVE ? new StoveBehavior() : new ApplianceBehavior(kind));
            DiegesisClient.registerLiquid(StirAndCharm.id("soup"), LiquidLook.of(0x8877B9D8).thick(0.3F));
            if (net.neoforged.fml.ModList.get().isLoaded("candlelight")) {
                DiegesisClient.registerBehavior(StirAndCharm.id("candlelight_pot"), view -> new KitchenBehavior(false));
                DiegesisClient.registerBehavior(StirAndCharm.id("candlelight_pan"), view -> new ApplianceBehavior(ru.codex.stirandcharm.Appliance.ROASTER));
            }
            if(net.neoforged.fml.ModList.get().isLoaded("bakery"))DiegesisClient.registerBehavior(StirAndCharm.JAM,view -> new JamBehavior());
        }));
    }
}

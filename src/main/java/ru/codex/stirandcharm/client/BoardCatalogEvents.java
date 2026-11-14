package ru.codex.stirandcharm.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import ru.codex.stirandcharm.StirAndCharm;

/** The board catalog is rebuilt on recipe or tag reload, never on opening the book. */
@EventBusSubscriber(modid = StirAndCharm.ID, value = Dist.CLIENT)
public final class BoardCatalogEvents {
    @SubscribeEvent public static void recipes(RecipesUpdatedEvent event) {
        BoardCatalog.invalidate();
        BoardCatalog.warm();
    }
    @SubscribeEvent public static void tags(TagsUpdatedEvent event) {
        BoardCatalog.invalidate();
        BoardCatalog.warm();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BoardCatalog.invalidate(); }
    private BoardCatalogEvents() {}
}
# Credits

Handily is an independent addon for Farm & Charm, inspired by the in-world cooking interactions of Hands-On Delight by Pocky (https://github.com/Pocky-l/handson-delight).

Diegesis by Pocky is an external dependency (MIT, https://github.com/Pocky-l/diegesis). Farm & Charm by satisfy and Architectury API are external dependencies. None of their binaries, textures, sounds or models is bundled in this addon. The addon references installed Farm & Charm resources at runtime.

The cookbook renderer (`RecipeBook.java`, `RecipeBookModel.java`, `FlatLit.java`), food colour sampling (`FoodColors.java`), two cookbook textures and the book node placement are adapted from Hands-On Delight by Pocky, branch `1.21.1`, commit `4dd9d55bbb38f18ed85dcb0f29e4adf237026787`, under the MIT license. Copyright (c) 2026 Pocky. The two textures are byte-identical to the ones in the official NeoForge 1.4.0 JAR. The full license is included as `LICENSE_HANDSON_DELIGHT`. Adaptations connect to Farm & Charm recipes and ingredient types, remove quality bonuses, and retain an independent namespace.

Station integration, transfer logic and motion validation are independently implemented. The pot's stirrer and the bowl's whisk use the installed Farm & Charm models. Hands-On Delight and Farmer's Delight are not runtime dependencies.

In version 0.2.0 the cover references Minecraft's enchanting-table book texture at runtime; that Minecraft texture is not redistributed. The original adapted paper texture remains in use. Roaster lid/body and mincer crank reference the installed Farm & Charm models and textures at runtime.

Version 0.4.0 also adapts `ItemVoxels.java` from the same Hands-On Delight commit under its MIT license, for pixel-volume ingredients and separated cut pieces. Knife motion and dashed cut marks follow Hands-On Delight's board interaction design, using Farm & Charm's native recipes, chop thresholds, inventories, durability and byproducts. Hands-On Delight remains optional.

Candlelight 2.1.15 by satisfy is an optional external integration. Its installed block models and textures are used at runtime; no Candlelight assets or binaries are included. Minecraft's vanilla `BookModel` supplies the cookbook geometry with its original UV mapping.

Bakery 2.1.11 by satisfy is an optional external integration in 0.5.0. Its native jam inventory, recipes, renderer, whisk, sounds, stirring rules, consistency and filled-jar components remain owned by Bakery. Its JAR is a compile-only dependency; no Bakery binaries or assets are redistributed. Tray food uses the MIT-adapted `ItemVoxels` renderer above. Action sounds reference existing Minecraft sound events.

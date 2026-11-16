# [Let's Do] Handily 0.6.3

- Ported Hands-On Delight's pouring, dry-ingredient shaking, egg cracking and serving motions under MIT, adapted to native Let's Do models and inventories.
- Fixed duplicate flat cursors while a board knife is drawn. Cutting uses the food's transform and a thin work-surface hitbox, including side cameras.
- Cut plans and completed marks survive closing and native save/load; server checks sequence, tool, interval and finite in-bounds coordinates.
- Unordered board assembly accepts any matching first ingredient. A vessel finishes the native recipe; cheese can start Candlelight's tomato mozzarella salad.
- Results and remainders go straight to inventory. Removed the eight side output cells; retained leftovers migrate from saved native storage without dropping on full inventories.
- Empty pots show clear water. Solids colour it through stirring; poured liquids spread colour during pouring. World and scene use the same colour, height and mixture state.
- Dissolved ingredients have a take-back preview and can be recovered into inventory with an empty-cursor right click.
- Ready meals offer an inventory vessel over the food. Click serves one, Shift a batch, including the last cursor vessel. Full inventories do not consume food or vessels.
- Finished pan/roaster food is drawn on the cookware surface. Bakery uses its real wooden handle for dumping, with a hover outline and discard warning; jar components and native XP remain intact.
- Spoon and whisk hints are separate. Removed speed and multiplier claims from current UI and description.
- Mincer and HerbalBrews kettle scenes remain disabled. Hands-On Delight remains optional; no stars, bonus portions or recipe rebalance.

## 0.6.2

- Adapted Hands-On Delight's spoon-following and ingredient-drift algorithms to Farm & Charm's native spoon model. The spoon's end follows the pointer on the soup plane, the handle tilts, and the current eases out after stirring stops.
- Floating ingredients bob, move apart and avoid the spoon and pot walls.
- Sugar, flour and other solid ingredients remain visible and can be picked back out of the native inventory.
- Right-clicking the soup with an empty cursor recovers the last poured ingredient into the inventory. A full inventory leaves it in the pot.
- Added a server-synchronized stirring state that expires after movement stops, plus regression checks for sugar retrieval and liquid recovery.
- Mincer and HerbalBrews kettle scenes remain disabled.

Required versions remain unchanged. Replace the previous Handily JAR on both client and server.

## 0.6.1

- Disabled Handily's 3D mincer scene. Farm & Charm handles the mincer's input, crank, timing and output again, without Handily's mincer mixins.
- HerbalBrews kettles keep their native interaction. Experimental kettle scenes and animations are not included.
- Fixed cutting-board ingredient placement when the source stack becomes empty, returning whole assembly layers, and stopping a batch when the knife breaks.
- Reset board animation transforms when switching or reopening scenes, preventing shrunken ingredients after interrupted assembly animations.
- Restricted general crafting recipes on the board to Farm & Charm dishes.
- Preserved Candlelight pot support geometry in the scene.
- Use one flat cursor icon for carried items in Handily scenes.

Minecraft 1.21.1, NeoForge 21.1.253+, Java 21. Required: Farm & Charm 1.1.29, Diegesis 1.0.0-beta.8 and Architectury API 13.0.11.

Replace the previous Handily JAR on both client and server. Keep your existing config and worlds. The legacy `mincer` config entry cannot re-enable its removed scene.

Source code, source JAR and project archive accompany the release. See TESTING.md for the completed checks and their limits.

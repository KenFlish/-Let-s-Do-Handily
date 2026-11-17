# [Let's Do] Handily 0.6.5

- Reworked the Farm & Charm stove scene: its own upper shutter and lower firebox door open smoothly, with native food on the grate, fuel below and a thin cooking progress strip.
- Uses only the installed Farm & Charm stove model and textures. No Hands-On Vanilla model or texture assets are included; its door easing and progress design are adapted under MIT.
- Removed the floating stove output and flint-and-steel button. Use flint and steel or a fire charge on the firebox; successful ignition consumes the native fuel and the ignition tool's durability or charge.
- Click cooked food for one portion, Shift for the stack, into inventory after the pickup presentation. Full inventories leave output intact. Native cooking time, recipes, item components, experience and automation remain unchanged.
- Includes the cutting selection, dish/progress preview, hint removal, inventory animation order and Diegesis beta.11 fixes from 0.6.4. Mincer and kettle scenes remain disabled.
- Verification is limited to one dedicated-server GameTest run and the build; the final native stove scene has not been visually checked in Minecraft. On Diegesis beta.8 the scene closes immediately; beta.11 supplies the smooth closing-scene hook.

## 0.6.4

- Cuttable first ingredients always start cutting, as in Hands-On Delight. A matching second ingredient starts assembly; cookbook tabs only filter recipes. Stale dish selections no longer override cutting.
- Restored a non-clickable dish preview and progress badge beside stations, including board assembly.
- Removed hover instructions for stirring and serving; the Bakery discard warning remains on its handle.
- Inventory gains wait visually for the cut/assembly/serving animation, then fly into their slots. Ownership and capacity checks remain server-authoritative. Applied to board results/returns, pots, bowls, roasters, Candlelight pans and Bakery jars.
- Fixed the carried-renderer injection for Diegesis beta.11 and expanded the dependency range through beta.11. beta.8 remains supported.
- Added an opt-in background verification launch that hides its window and blocks physical mouse capture.
- Cooking times and recipe balance are unchanged.

## 0.6.3

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

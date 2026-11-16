# Handily 0.6.4 release verification

Minecraft 1.21.1, NeoForge 21.1.265, Java 21.0.7 on Windows. Farm & Charm 1.1.29 and Architectury 13.0.11. Tested Diegesis builds: beta.8 and beta.11.

## Current server checks

```text
gradlew.bat runGameTestServer -PgameTests --offline --console=plain --no-watch-fs --no-problems-report
gradlew.bat runGameTestServer -PgameTests -Pcompat -PnewDiegesis --offline --console=plain --no-watch-fs --no-problems-report
```

Both runs passed **43 required tests**. The core run uses Diegesis beta.8; the compatibility run uses beta.11, Candlelight 2.1.15, Bakery 2.1.11, HerbalBrews 1.1.4, Hands-On Delight 1.4.0 and Farmer's Delight 1.3.4. Optional integrations are skipped when absent.

Two new regression tests verify that a remembered dishes tab/stale selection cannot turn a cuttable first ingredient into assembly, and that a matching second ingredient can convert the pending cut stack into layers. Existing tests cover native outputs, tool durability, batch quantities, storage migration, cheese-first salad, full inventories, vessel consumption, jar components, liquid recovery and serialized serving attempts.

## Current background client checks

```text
gradlew.bat runClient -PvisualTests -PregressionVisual -PbackgroundClient -Pcompat -PnewDiegesis --offline --console=plain --no-watch-fs --no-problems-report
```

The targeted client run completed successfully on beta.11. It checked:

- The test window had no keyboard focus and the physical mouse was not captured.
- Actual cursor placement of a dual-purpose tomato stack began cutting despite a stale dish selection; the dish/progress badge became visible.
- Mouse-driven cutting completed the batch. The server-owned result was visually hidden until the scene receipt, then appeared after the inventory flight.
- A real click served the last inventory vessel at the pot and at the roaster. Each result stayed visually hidden during the scoop and appeared after its flight.

Input uses Diegesis's forced virtual pointer and screen handlers. Early diagnostic drivers failed before the final run: one clicked before the camera/input had settled, one assumed a nonexistent board node, and one checked a badge before its first updated frame. The final driver waits for state/frame synchronization. The separate background launch disables NeoForge's early window for that development profile. Normal player launches do not enable the background property.

The original 0.6.3 JAR rejected beta.11 because of its exact beta.8 dependency restriction. In 0.6.4 the dependency range and the changed carried-renderer injection are fixed. Versions beta.9 and beta.10 are admitted by the range but were not separately launched.

Only the selected cutting/pot/roaster paths received this new mouse-driven visual check. Bowl, Candlelight and Bakery use the same inventory-presentation path and have server/resource checks; no exhaustive new visual tour of every recipe or a long multiplayer soak is claimed. The prior release's 32-case cutting matrix is historical evidence below, not a rerun on 0.6.4.

The release archive excludes local worlds, raw logs, credentials and dependency JARs. A verification archive contains extracted pass/failure records. The runtime JAR excludes the visual/GameTest drivers and retains MIT notices. Cooking times and balance were not changed.

---

## Historical 0.6.3 checks

# Handily 0.6.3 release verification

Environment: Windows, Java 21.0.7, Minecraft 1.21.1, NeoForge 21.1.265.
Required mods: Farm & Charm 1.1.29, Diegesis 1.0.0-beta.8 and Architectury 13.0.11. Farm & Charm includes Foundation 1.0.7.
Compatibility set: Candlelight 2.1.15, Bakery 2.1.11, HerbalBrews 1.1.4, Hands-On Delight 1.4.0 and Farmer's Delight 1.3.4.

## Dedicated server

```text
gradlew.bat runGameTestServer -PgameTests --offline --console=plain --no-watch-fs --no-problems-report
gradlew.bat runGameTestServer -PgameTests -Pcompat --offline --console=plain --no-watch-fs --no-problems-report
```

Both separate runs passed **41 of 41 required tests**. Tests for optional integrations return without exercising that integration when its mod is absent. These runs use the real native block entities and inventories.

Covered cases include:

- Native cutting recipes, knife and cleaver durability, byproducts, batches of 16, rejected invalid/early/repeated cuts and full-inventory safety.
- Saved cut plans and marks, legacy board-output NBT persistence and migration, layer return and interrupted assembly state.
- Candlelight salad starting with cheese, unordered matching, vessel completion, batches, missing ingredients, remainders and inventory capacity before consumption.
- Clear-water baseline, mixture persistence, heat requirements, native cooking, recovery of an entire sugar stack and a poured ingredient without duplication or loss on a full inventory.
- Serving one portion or a Shift batch, cursor and inventory vessels, the final vessel freeing an inventory slot, missing vessels and blocked destinations.
- Two server-side players attempting to take the last portion in sequence; only one receives it. This is not a test with two live network clients.
- Native roaster, Candlelight pan/pot, bowl, stove and Bakery recipes; Bakery jar components, native dumping and full-inventory rejection.
- Disabled mincer/kettle scenes and the mincer's native crank progression.

## Client

```text
gradlew.bat runClient -PvisualTests -PbaseVisual --offline --console=plain --no-watch-fs --no-problems-report
gradlew.bat runClient -PvisualTests -PboardMatrix -Pcompat --offline --console=plain --no-watch-fs --no-problems-report
gradlew.bat runClient -PvisualTests -PcompatVisual -Pcompat -PpotVisualOnly --offline --console=plain --no-watch-fs --no-problems-report
gradlew.bat runClient -PvisualTests -PcompatVisual -Pcompat --offline --console=plain --no-watch-fs --no-problems-report
```

The core-only client opened the cooking pot and mixing bowl and completed its water, ingredient and stirring sequence successfully.

The cutting matrix passed **32 actual mouse-driven cutting cases**: four block facings, four world approach sides and two tools (Farm & Charm cleaver and Farmer's Delight knife). Every case closed and reopened the scene after its first cut, completed the remaining cuts and checked native result quantity. The test also checked that the flat carried cursor is suppressed while the knife is drawn. A case may retry a cut; this does not establish that every cut succeeds on its first click.

The targeted pot/Bakery client run completed all seven checks: retrieving 32 dissolved sugar items with a real right click; spoon tilt and server stirring state; ingredient drift within pot bounds; a single animated pouring hand; poured-ingredient recovery; an offered last inventory vessel serving into inventory with an empty cursor; and hovering/clicking the real Bakery handle to empty the native pot.

Mouse gestures use Diegesis's `FocusScreen.forcedMouse` and actual screen input handlers. These are automated input checks, not manual play. The water bucket in the pouring fixture is only an animation/recovery test: cooking does not require adding water to the clear-water baseline.

The long compatibility tour is **not a passing end-to-end check**. One run passed placement, the east camera, normal and interrupted assembly scale restoration (ratios 1.0 and 1.0000001), native mincer motion, sugar recovery, stirring, drift and pouring, then failed its timed liquid-recovery click. The driver was changed to wait for the recovery control; its next run failed the initial timed board-placement assertion before reaching that change. The separate cutting matrix and targeted pot/Bakery run above passed. The long tour still needs reliable input/synchronization; these failures are not presented as proof that the full interaction sequence is bug-free.

The verification archive contains selected screenshots and extracted success/failure records, including these incomplete tours.

## Build and limits

```text
gradlew.bat build --offline --console=plain --no-watch-fs --no-problems-report
```

The release JAR excludes test classes, mincer scenes and kettle scenes. It includes the Hands-On Delight MIT license and attribution. Local logs, worlds, credentials, dependencies and unrelated experimental kettle code are excluded from the project archive.

Coverage uses selected native recipes and interaction paths. It does not exhaust every dish, shader combination, long-running multiplayer session or the user's complete modpack. The new spoon gesture was exercised on Farm & Charm; Candlelight received native recipe/inventory checks and scene screenshots rather than a separate full mouse-driven stirring sequence. HerbalBrews mechanics were not changed. Environment warnings about WMI, absent optional Sodium classes and external mod textures are not claimed as fixed by this release.

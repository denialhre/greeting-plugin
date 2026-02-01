# SwapBingo - Minecraft Minigame Plugin

## Project Overview
Paper 1.21.1 plugin featuring **Item Bingo** minigame with integrated **Death Swap** mechanic.

**Core Gameplay Loop:**
1. Players spawn in barrier cages in a fresh world
2. 10-second countdown, then cages drop
3. Grace period (10 min default) - no PvP, no swaps
4. After grace period: first swap + PvP enabled
5. Players complete bingo tasks while swapping periodically
6. First to complete a row/column wins
7. Victory celebration with podium + fireworks
8. World resets, new round begins

---

## Build & Deploy
```bash
mvn clean package
```
Output: `target/SwapBingo-1.0-SNAPSHOT.jar`

Copy JAR to Paper 1.21.1 server `plugins/` folder and restart.

---

## Package Structure

```
com.example.swapbingo/
├── SwapBingoPlugin.java              # Main entry point, registers everything
├── core/
│   ├── GameManager.java              # Central game state coordinator
│   ├── GameState.java                # Enum: IDLE, COUNTDOWN, RUNNING, ENDED
│   └── PlayerSession.java            # Per-player data (bingo card, location, etc)
├── bingo/
│   ├── BingoGame.java                # Main game orchestration (START THIS FILE FOR GAME LOGIC)
│   ├── BingoCard.java                # 5x5 grid of tasks, clone() for shared cards
│   ├── BingoTask.java                # Individual task cell with completion state
│   ├── WinPattern.java               # Enum: ROW, COLUMN
│   ├── BingoCardItem.java            # Nether Star item to open GUI
│   ├── BingoItemListener.java        # Handles right-click on Nether Star
│   ├── gui/
│   │   ├── BingoCardGUI.java         # 54-slot inventory GUI for bingo card
│   │   ├── RecipeGUI.java            # Shows crafting recipes when clicking items
│   │   └── GUIListener.java          # Handles all GUI clicks (recipes, location purchase)
│   ├── tasks/
│   │   ├── TaskType.java             # Enum: COLLECT_ITEM, KILL_ENTITY, VISIT_BIOME, DISCOVER_STRUCTURE
│   │   └── TaskDefinition.java       # Holds target, display name, weight
│   ├── tracking/
│   │   ├── ItemCollectTracker.java   # EntityPickupItemEvent, CraftItemEvent
│   │   ├── EntityKillTracker.java    # EntityDeathEvent
│   │   ├── BiomeVisitTracker.java    # PlayerMoveEvent (chunk boundary optimized)
│   │   ├── StructureDiscoverTracker.java  # PlayerMoveEvent (async structure check)
│   │   └── LocationTrackerManager.java    # Diamond-purchased compass tracking
│   └── pool/
│       ├── TaskPool.java             # Container for all available tasks
│       └── TaskPoolLoader.java       # Loads JSON data files + recipes
├── world/
│   ├── WorldManager.java             # World creation, reset, deletion (incl. nether/end)
│   ├── SpawnCageManager.java         # Barrier cages in circle pattern
│   └── WinCelebrationManager.java    # Podium, fireworks, countdown
├── scoreboard/
│   └── WinTracker.java               # Persistent win counts
├── commands/
│   ├── BingoCommand.java             # /bingo start|stop|card|status
│   └── AdminCommand.java             # /swapbingo reload|config|stats
├── listeners/
│   ├── PlayerJoinLeaveListener.java  # Handle joins/leaves during game
│   ├── PlayerRespawnListener.java    # Respawn in bingo world, not overworld
│   ├── PvPListener.java              # Blocks PvP during grace period
│   ├── PortalListener.java           # Redirects portals to bingo_world dimensions
│   └── DebugListener.java            # Shows biome/structure info when debug enabled
├── config/
│   └── ConfigManager.java            # All config getters/setters
└── util/
    ├── LocationUtil.java             # Safe spawn finding
    └── MessageUtil.java              # Color code translation, broadcasting
```

---

## Key Files to Understand

| File | Purpose |
|------|---------|
| `BingoGame.java` | **THE MAIN FILE** - game start/stop, swap logic, grace period, win detection |
| `ConfigManager.java` | All config access - check here for available options |
| `GUIListener.java` | GUI click handling - recipes + location purchase |
| `TaskPoolLoader.java` | Loads items/entities/biomes/structures/recipes from JSON |
| `WorldManager.java` | World creation and reset (deletes nether/end too) |
| `PortalListener.java` | Ensures portals go to bingo_world dimensions |

---

## Game Flow (in BingoGame.java)

```
start() called
  ├── Show "Generating World..." title
  ├── WorldManager.resetBingoWorld() - deletes old, creates new
  ├── resetPlayer() for all - clear inventory, full health/hunger
  ├── SpawnCageManager.spawnPlayersInCages() - circular pattern
  ├── 10-second countdown with titles
  ├── Cages drop, fall damage grace period starts
  ├── registerTrackers() - enables task completion detection
  ├── IF grace period enabled:
  │     ├── startGracePeriod() - no PvP, no swaps
  │     └── After X minutes: endGracePeriod() → first swap → startSwapTimer()
  └── ELSE: startSwapTimer() immediately

performSwap() - rotation swap (1→2→3→1), updates compasses after

checkWinCondition() → declareWinner() → WinCelebrationManager.celebrate()
  ├── Build podium at 0,0
  ├── Barrier cage (25 blocks high for fireworks)
  ├── Winner cage on podium
  ├── Teleport winner to top, others around
  ├── Fireworks for 10 seconds
  └── 20-second countdown → world reset → new round
```

---

## Multi-World Dimension System

**World Names:**
- Overworld: `bingo_world`
- Nether: `bingo_world_nether`
- End: `bingo_world_the_end`

**Portal Handling (PortalListener.java):**
- Intercepts `PlayerPortalEvent`
- Creates dimension worlds on-demand with same seed
- Applies 1:8 coordinate scaling for nether
- On world reset: all 3 worlds are deleted

**Note:** This is a manual implementation, not Paper's automatic dimension linking.

---

## Location Tracking System

**Purchase Flow (GUIListener + LocationTrackerManager):**
1. Player clicks biome/structure task in GUI
2. Check for diamonds (configurable cost)
3. Find nearest location (searches correct dimension)
4. Create lodestone compass pointing to location
5. After swaps: all compasses are relocated to new nearest target

**Dimension Detection:**
- Nether targets: `nether_fortress`, `bastion_remnant`, `crimson_*`, `warped_*`
- End targets: `end_city`, `end_*`
- Others: search in overworld

---

## Configuration (config.yml)

### Key Settings

| Path | Default | Description |
|------|---------|-------------|
| `swap.enabled` | true | Enable death swap mechanic |
| `swap.interval-seconds` | 180 | Time between swaps |
| `bingo.shared-card` | true | All players get same tasks |
| `bingo.spawn.cage-distance` | 50 | Distance of spawn cages from center |
| `bingo.spawn.start-countdown` | 10 | Countdown before cages drop |
| `bingo.grace-period.enabled` | true | Enable no-PvP/no-swap grace period |
| `bingo.grace-period.duration-minutes` | 10 | Grace period length |
| `bingo.location-purchase.diamond-cost` | 1 | Cost to track a location |
| `bingo.win.next-round-delay` | 20 | Seconds before next round |

---

## Data Files (src/main/resources/data/)

| File | Format | Usage |
|------|--------|-------|
| `itens.json` | minecraft-data | Items for task pool |
| `full-itens.json` | minecraft-data | ALL items (for recipe ID lookups) |
| `entities.json` | minecraft-data | Entities for kill tasks |
| `biomes.json` | minecraft-data | Biomes for visit tasks |
| `structures.json` | minecraft-data | Structures for discover tasks |
| `recipes.json` | minecraft-data | Crafting recipes (inShape + ingredients formats) |
| `pool-config.yml` | Custom | Which items are enabled + weights |

### Recipe JSON Formats
```json
// Shaped recipe (inShape)
"835": [{ "inShape": [[40, null, 40], [40, 40, 40]], "result": {"id": 835, "count": 1} }]

// Shapeless recipe (ingredients)
"40": [{ "ingredients": [138], "result": {"id": 40, "count": 4} }]
```

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/bingo start` | `swapbingo.bingo.start` | Start a game with online players |
| `/bingo stop` | `swapbingo.bingo.stop` | Force stop current game |
| `/bingo card` | `swapbingo.bingo` | Open bingo card GUI |
| `/bingo status` | `swapbingo.bingo` | Show game status |
| `/swapbingo reload` | `swapbingo.admin` | Reload config |
| `/swapbingo stats [player]` | `swapbingo.admin` | Show win statistics |

---

## Code Style & Guidelines

### Must Use
- **Adventure API** for all text (Component, not deprecated ChatColor)
- **Java 21 features** (records, switch expressions, var)
- **Async operations** for structure detection
- **Chunk boundary checks** to optimize PlayerMoveEvent

### Patterns
- Trackers register/unregister with Bukkit event system
- All world modifications tracked in cleanup sets
- BukkitRunnable for timers (not raw schedulers)
- PersistentDataContainer for item metadata

### GUI Slot Layout (54-slot inventory)
```
Row 0 (0-8):   [border...]
Row 1 (9-17):  [border] [task0,0-0,4] [border] [border] [border]
Row 2 (18-26): [border] [task1,0-1,4] [border] [border] [border]
Row 3 (27-35): [border] [task2,0-2,4] [border] [border] [border]
Row 4 (36-44): [border] [task3,0-3,4] [border] [border] [border]
Row 5 (45-53): [border] [task4,0-4,4] [border] [border] [border]

Task slots: 11-15, 20-24, 29-33, 38-42, 47-51
```

---

## Known Limitations / TODOs

1. **Deprecated APIs**: `StructureType` and `locateNearestBiome` are deprecated - should migrate to newer Registry-based APIs
2. **Nether safe-spawn**: Simple Y-level loop, not robust for all terrain
3. **No spectator mode**: Dead players just respawn, no spectating others
4. **Single game instance**: Only one game can run at a time

---

## Debugging

Enable in config:
```yaml
debug:
  enabled: true
  show-biome-info: true
  show-structure-info: true
```

Shows in chat when entering biomes or nearing structures.

---

## Paper API Reference
- Context7 Library ID: `/websites/jd_papermc_io_paper_1_21_11`
- Key events: `PlayerPortalEvent`, `EntityPickupItemEvent`, `EntityDeathEvent`, `PlayerMoveEvent`
- Key classes: `WorldCreator`, `CompassMeta`, `PersistentDataContainer`

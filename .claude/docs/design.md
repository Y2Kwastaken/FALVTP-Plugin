# FALVTP design

Package root: `src/main/java/sh/miles/falvtp/`. Plugin descriptor: `src/main/resources/paper-plugin.yml`
(Paper plugin format, no commands, no bootstrapper).

## Layers

### `trader/` — the trading model (no world, no entity)
- `FalvtpTrader` — one villager's state: type, profession, level (1–5), experience, offers, restock bookkeeping,
  current customer. Rules are copied from vanilla `AbstractVillager` / `Villager`:
  - XP table `{0, 10, 70, 150, 250}`; level-up re-rolls the new level's trades.
  - Restocks: max/day + cooldown from `TradeRules` (vanilla 2 and 2400), new day = >12000 ticks since last restock or day number advanced;
    a new day catches up missed demand updates. `lastRestockCheckDay` is intentionally not saved (matches vanilla).
  - Time in 26.x: use the overworld clock, like vanilla, never Bukkit `getFullTime()`/`getTime()` (those read the
    world's own clock; the nether has none, so they are always 0 there, and the End has a separate one).
    `Trades.overworldDay` = `minecraft:day` timeline period count; `Trader#isWorkTime` = `villager_activity`
    environment attribute == WORK (ticks 2000–9000, universal timeline, datapack-overridable).
  - Restock trigger: `TraderInteractListener` calls `tryRestock` on open when `isWorkTime()` (or always, with
    `restock.work-hours-only: false`) — a lazy stand-in for `WorkAtPoi`. Stock does not refill while a menu stays open.
- `TradeRules` — the tunable numbers (restocks/day, cooldown, level-up XP table, orb XP). Passed as a **parameter** to
  `recordTrade`/`tryRestock`/`shouldRestock`, not stored per trader, so nothing has to be threaded through the chunk
  storage and a future reload applies everywhere. `TradeRules.VANILLA` = vanilla values. `maxRestocksPerDay = 0`
  disables restocking (the allowed check tests the max first; identical to vanilla for max >= 1).
  - `recordTrade(...)` returns `TradeResult(orbExperience, levelledUp)`; world side effects are the caller's job.
  - `startTrading(player, offersChanged)` / `stopTrading()` — one customer at a time; special prices are only
    applied while a customer is set. `offersChanged` fires on reprice/level-up/restock during a session.
  - `Snapshot` + `Snapshot.CODEC` — the persisted form (customer and price control are not saved).
- `PriceControl` — replaces reputation. `HERO_OF_VILLAGE` (vanilla formula) or `NONE`, chosen by config and set by
  `TradeSessions.open` before each session. Composable with `and`.
- `Trades` (package-private) — rolls offers from the NMS `TradeSet` registry using a `LootContext` built without
  a `THIS` entity (`TRADE_CTX`). Offers are kept "live" (`CraftMerchantRecipe` backed by an NMS `MerchantOffer`)
  so the merchant menu and the trader share uses/demand/price.
  - Some vanilla trades gate on villager type via a `merchantPredicate` that needs an entity. `gate()` detects the
    exact shape `LootItemEntityPropertyCondition(THIS) -> EntityPredicate with one part -> components with one
    VillagerTypePredicate`, strips the predicate (via `TradeAccess`) and checks the type manually. Results are
    cached in `GATES`.
- `TradeAccess` (package-private) — reflection into `VillagerTrade` fields/constructor and `EntityPredicate.parts`.
  Fails fast at class-init with a clear message if Mojang renames/retypes anything. First place to look after a
  Minecraft update.
- `Trader` — where a trader lives (`world()`, `pos()`, `biome()`). `TraderPair(who, where)` = model + location.

### `trader/session/` — the merchant UI
- `TradeSessions.open(player, pair, title)` — creates a Bukkit `Merchant`, calls `startTrading` *before*
  `setRecipes` (so plain recipes get converted to live ones), opens `MenuType.MERCHANT`, then re-sends offers via
  NMS `sendMerchantOffers` with the real level/xp (Paper's packet for custom merchants hides the XP bar).
  `sync` re-sends one tick later because a level-up happens inside `PlayerPurchaseEvent` before payment is taken.
- `TradeSessionsListener`
  - `PlayerPurchaseEvent` (HIGHEST): disables Paper's own use/xp handling and calls `recordTrade`; spawns the XP
    orb at the top centre of the block (amounts and on/off from `TradeRules`).
  - `InventoryCloseEvent`: removes the session and calls `stopTrading`. **This is the only place a session ends**;
    anything that needs to end a session just calls `customer.closeInventory()`.

### `world/` — persistence and lookup
- Hierarchy: `TraderServer` (by world UUID) → `TraderWorld` (by chunk key) → `TraderChunk` → `BlockTrader`.
- `BlockTrader(location, trader)` — a `Trader` at a block; `save()` writes its snapshot into the chunk PDC.
- Chunk PDC layout:
  - `falvtp:trader_index` — `LONG_ARRAY` of positions in the chunk, key = `y << 8 | (x & 15) << 4 | (z & 15)`.
  - `falvtp:block_trader/<lx>_<y>_<lz>` — `TAG_CONTAINER` with the `Snapshot` (keys `falvtp:villager_type`,
    `profession`, `level`, `experience`, `offers` (list of containers via `MerchantRecipeCodec`),
    `last_restock_game_time`, `restocks_today`).
- Dirty flag: `FalvtpTrader` marks itself dirty on every change to saved state (rolled trades, trade uses/xp,
  level, type, restock bookkeeping); `BlockTrader.save()` clears it; `TraderChunk.save()` (world save, unload,
  disable) skips clean traders, because writing chunk PDC marks the chunk unsaved (`ChunkAccess#isUnsaved`).
  Special prices are deliberately not tracked: every session reprices before the customer sees the offers.
  Anything that edits `offers()` from outside must call `markDirty()`.
- `TraderChunk` only exists for chunks with a non-empty index; traders are deserialized lazily on `get` and cached
  in `loaded`. A missing PDC entry for an indexed position self-heals the index.
- `TraderChunk.remove` closes the customer's inventory first (ending the session), then deletes data.
  Chunk unload (`TraderChunk.unload`) and world unload (`TraderWorld.unloadAll`) also close sessions before saving:
  custom merchants have no range check (`CraftMerchantCustom.stillValid` is `tradingPlayer == player`) and
  `ServerPlayer#teleport` never closes menus, so a customer can be carried out of range by a minecart, stasis pearl
  or delayed teleport. Without the close they would keep trading on an instance that is never saved, and the
  reloaded trader would have its stock back.
  `breakNaturally` = `remove` + drop the `Workstation` item carrying the snapshot in its PDC. The drop is spawned one
  tick later via the region scheduler (the block still exists during break events); `TraderServer` takes the
  `Plugin` for this and hands it to each `TraderWorld`. A trader that was never
  traded with (`experience <= 0 && level <= 1`, vanilla's `ResetProfession` rule) drops the tagged item *without* a
  snapshot (the "Basic" look), so re-placing it re-rolls — the intended way to rotate trades.

### `world/listener/`
- `ChunkListener` (MONITOR): load chunk → `TraderWorld.load`; unload chunk/world → save + drop from memory;
  `WorldSaveEvent` → save loaded traders.
- `BlockChangeListener` — every way a trader block can be removed, verified against Paper 26.3 server source:
  | Cause | Event | Handling |
  |---|---|---|
  | Player | `BlockBreakEvent` | break naturally, `setDropItems(false)`; creative + `creative-drops: false` → just remove |
  | TNT, creeper, crystal, bed, anchor, dragon wall-smash | `Entity/BlockExplodeEvent` | only when result is `DESTROY`/`DESTROY_WITH_DECAY`: always pull from list; break naturally only for `protection.explosions: drop` |
  | Wind charge / breeze (`TRIGGER_BLOCK`) | same events, blocks listed but not broken | ignored |
  | Wither body, `/setblock … destroy`, `/fill … destroy`, End platform/podium, physics | Paper `BlockDestroyEvent` (fires from `Level#destroyBlock`) | break naturally, `setWillDrop(false)` |
  | Piston push/pull (list includes popped blocks) | `BlockPistonExtend/RetractEvent` | cancel |
  | Fire (wooden stations are flammable) | `BlockBurnEvent` (HIGHEST) | `protection.fire`: protect = cancel, drop = break naturally (fire removes the block) |
  Checked and not reachable for workstations: fluid flow, enderman pickup (tag), ravager (leaves only), POPPED
  push reaction (none of the stations), support/survival pops. Plain `/setblock` (replace) and WorldEdit are out of scope.
- `TraderInteractListener`: main-hand right-click on a trader (not sneaking) cancels the event — the block's own
  menu/use would otherwise replace the merchant — and opens a session; plays villager trade/no sound.
  Cancelling makes Paper return `SUCCESS`, so no off-hand retry.
- `BlockPlaceListener` (MONITOR, ignoreCancelled, requires `canBuild`; must stay MONITOR or a cancelled place
  duplicates the trader). An item tagged `falvtp:workstation` places a trader: with a snapshot it is restored,
  without one (an untraded station, see below) `FalvtpTrader.create` rolls fresh trades at that spot, biome type
  included. A snapshot whose level is out of range reads as none, so it rolls fresh instead of throwing.

### `utils/`
- `Option` — sealed Some/None.
- `WorldCodec<T>` — PDC codec. `tryRead` returns `Option` (none when the data isn't there / has unknown registry
  ids) — use it to *check* for data (e.g. an item that may or may not carry a snapshot). `read` is the strict
  wrapper for data that must exist (indexed chunk traders). Implementations: `FalvtpTrader.Snapshot.CODEC`
  (none if `villager_type`/`profession` missing or unknown), `MerchantRecipeCodec` (none if `result` missing;
  items stored as `serializeAsBytes`). Offers inside a snapshot are read strictly — corrupt offer data still throws.
- `Workstation` — job-site block ↔ profession (cauldron matches all `Tag.CAULDRONS`); `item()` builds the bare
  block item tagged with `falvtp:workstation` (looks come from `ItemSettings`).

### `config/`
- `config.yml` (`src/main/resources`, copied by `saveDefaultConfig`) → `FalvtpConfig.load` once in `onEnable`; the
  immutable record is passed into `TradeSessions`, `TradeSessionsListener`, `TraderInteractListener`,
  `BlockChangeListener`. No reload command yet.
- Validation: every read goes through `Reader`; a wrong type/range logs one warning and uses the default, so a typo
  never leaves traders unprotected. Missing keys fall back to the jar's `config.yml` (`JavaPlugin#getConfig` defaults).
- `ItemSettings` — two `Look`s (name/glint/lore): `station` (traded, keeps snapshot, placeholders allowed) and
  `basic` (`createBasic`, untraded, rolls fresh on place; plain MiniMessage — config rejects trader placeholders
  there, since they would print literally). `TraderTags` — MiniMessage placeholders `<profession>`, `<level>`,
  `<level_name>` (vanilla `merchant.level.N`). `ItemSettings` is passed down `breakNaturally(…, items)`.
- `SoulVialSettings` — look of the *filled* soul vial only (the empty one is a plain vanilla glass bottle): material,
  optional `item-model`, name, glint, lore, `liquid-color` (shown only on materials with default `potion_contents`,
  whose "No Effects" tooltip is hidden). `create()` returns an *untagged* item; `CONSUMABLE` is always removed so a
  potion vial can't be drunk.
- Harvest: `EntityInteractListener` — sneak + right-click an adult, non-nitwit `Villager` with a glass bottle
  (`PlayerInteractAtEntityEvent`, ignoreCancelled; in 26.3 it is the only entity-interact event and cancelling it
  stops the trade menu). Takes one bottle from the stack, gives `SoulVial.mark(settings.create())`.
- `utils/SoulVial` — marker only (PDC `falvtp:soul_vial`, boolean): `mark`, `isSoulVial`. DNA is generic by design:
  **any vial binds to any workstation**, and the station's profession comes from the block — so the vial stores
  nothing about its source villager.
- Bind: `WorkstationInteractListener` (PlayerInteractEvent, **HIGH**, ignoreCancelled) — main-hand soul vial on a plain
  workstation: cancels the event, uses the vial, `FalvtpTrader.create` for the block's profession. Vial on an
  existing trader: cancels and sends `bindOccupied`. Spectators skipped. `TraderInteractListener` runs at
  **HIGHEST**, ignoreCancelled, so a click the binder handled never also opens trades, and claim plugins
  (usually LOW–NORMAL) are respected by both. Keep that priority order.
- `HarvestCooldown` (`soul-vial.cooldown-ticks`, default 24000) — per-villager; last harvest game time lives in the
  villager's PDC (`falvtp:last_harvest`). `isReady`/`remaining`/`start(villager)`. A stored time in the future
  (world time reset) counts as ready.
- `Messages` (`messages.*`) — action bar sends: `harvestSuccess`, `harvestCooldown(to, ticks)` (`<time>` like
  `4m 30s`, rounded up), `harvestIneligible`, `bindSuccess(to, profession)` (`<profession>`), `bindOccupied`.
  Empty template = not sent.
- Deliberately not configurable: pistons (always blocked — a piston moving a trader block would separate it from its
  data; a "drop" mode is possible since Paper re-reads the block state after the event (`PistonBaseBlock` ~L380), but it
  dupes on servers with `unsupported-settings.allow-piston-duplication: true`, which uses the pre-event state), event
  priorities, PDC keys, min/max level, the trades themselves (datapack `trade_set`).

### `FalvtpPlugin`
Loads the config, creates `TradeSessions` + `TraderServer`, loads traders in already-loaded chunks, registers
`TradeSessionsListener`, `BlockPlaceListener`, `ChunkListener`, `BlockChangeListener`, `TraderInteractListener`. `onDisable` closes all sessions then saves.

## Status / open work
1. No way to *create* a new trader yet: nothing calls `FalvtpTrader.create` (opening and restoring from an item
   both work). Needs a command or a creation gesture.
2. No tests.

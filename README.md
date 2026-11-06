# FALVTP

**F**ast **A**sync **L**agless **V**illager **T**rading **P**andemonium.

It is not async. It is, however, a lot less laggy than the 200 villagers crammed into your torture chambers you've got
under spawn right now.

## What is this

Villager trading halls are one of the biggest sources of lag on most survival servers. Every villager has a brain
that pathfinds, looks for beds, gossips about you and generally does a lot of work, all so you can buy your stupid ass pointless Mending books.

FALVTP moves trading out of the villager and into the job site block. It levels up,
restocks, has demand-based pricing, rolls its trades from the same vanilla loot tables, gives you Hero of the Village
discounts... everything a vanilla villager does at a trading table, minus the actual villager standing there.

## How it works in game

1. **Harvest a soul.** Sneak and right-click a villager while holding a plain glass bottle. You get a Soul Vial.
   Each villager can only be harvested once per Minecraft day (configurable). Babies and nitwits have nothing to give.
2. **Bind it.** Right-click any placed job site block with the vial. The block turns into a trader with the
   profession that block gives (lectern = librarian, composter = farmer and so on). Any vial works on any block,
   so you only ever need one villager to get started.
3. **Trade.** Right-click the block. It works like a villager: novice trades to start, level up as you trade,
   restocks twice a day during villager work hours.
4. **Move it.** Break the block and you get it back as an item with everything kept: level, trades, experience.
   Put it down somewhere else and carry on.

**Re-rolling** is just as painful as it is in vanilla. 20 rolls into breaking and placing a lectern for a Mending book
you'll wonder man... why doesn't FALVTP add a quality of life rolling feature!?

All job blocks are supported (unfortunately). That means absolutely no limitations even for you weirdo's who do leather
works and stuff.

A few other things you might run into:

- Explosions drop the trader as an item instead of destroying it. Wind charges don't affect it at all.
- Fire won't burn a trader by default.
- Pistons can't move trader blocks.
- Sneak while clicking a trader to use the block normally, or to place blocks against it.
- Claim and protection plugins are respected for harvesting, binding and trading.

## Config

Everything configuration wise is jammed right into `plugins/FALVTP/config.yml`, which is generated on first start. 
The defaults match vanilla villagers. You can change restock limits and cooldowns, the levelling curve, experience orbs, 
sounds, the menu title, what the dropped items and the Soul Vial look like (MiniMessage, plus custom item models if you run a
resource pack), the action bar messages, and how fire and explosions treat traders.

If you mistype something the plugin won't explode, usually atleast. It logs a warning saying what's wrong and uses the default
for that one value.

## Building

You need Java 25.

```sh
./gradlew build
```

The jar ends up in `build/libs/`. For a local test server:

```sh
./gradlew runServer
```

This needs **Paper 26.3**. It reaches into Minecraft internals to roll trades the same way vanilla does, so it's
tied to that Minecraft version and will need updating for new releases.

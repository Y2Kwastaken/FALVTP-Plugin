package sh.miles.falvtp.world;

import io.papermc.paper.math.Position;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import sh.miles.falvtp.config.WorkstationItemSettings;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.utils.Option;
import sh.miles.falvtp.utils.Workstation;

public final class TraderChunk {
    private static final NamespacedKey INDEX = new NamespacedKey("falvtp", "trader_index");

    private final Chunk chunk;
    private final LongSet positions;
    private final Long2ObjectMap<BlockTrader> loaded = new Long2ObjectOpenHashMap<>();

    private TraderChunk(Chunk chunk, LongSet positions) {
        this.chunk = chunk;
        this.positions = positions;
    }

    public static Option<TraderChunk> load(Chunk chunk) {
        final long[] index = chunk.getPersistentDataContainer().get(INDEX, PersistentDataType.LONG_ARRAY);
        if (index == null || index.length == 0) {
            return Option.none();
        }
        return Option.some(new TraderChunk(chunk, new LongOpenHashSet(index)));
    }

    static TraderChunk empty(Chunk chunk) {
        return new TraderChunk(chunk, new LongOpenHashSet());
    }

    public boolean has(int blockX, int blockY, int blockZ) {
        return positions.contains(key(blockX, blockY, blockZ));
    }

    public Option<BlockTrader> get(Block block) {
        final long key = key(block.getX(), block.getY(), block.getZ());
        if (!positions.contains(key)) {
            return Option.none();
        }

        final BlockTrader cached = loaded.get(key);
        if (cached != null) {
            return Option.some(cached);
        }

        if (BlockTrader.read(block) instanceof Option.Some<BlockTrader>(BlockTrader trader)) {
            loaded.put(key, trader);
            return Option.some(trader);
        }

        positions.remove(key);
        writeIndex();
        return Option.none();
    }

    public Option<BlockTrader> add(BlockTrader trader) {
        final Option<BlockTrader> previous = get(trader.location().getBlock());
        final long key = key(trader.pos());
        positions.add(key);
        loaded.put(key, trader);
        trader.save();
        writeIndex();
        return previous;
    }

    public Option<BlockTrader> remove(Block block) {
        final Option<BlockTrader> removed = get(block);
        if (removed instanceof Option.Some<BlockTrader>(BlockTrader trader)) {
            closeSession(trader);
            final long key = key(block.getX(), block.getY(), block.getZ());
            positions.remove(key);
            loaded.remove(key);
            BlockTrader.remove(block);
            writeIndex();
        }
        return removed;
    }

    public boolean breakNaturally(Block block, boolean doBlockWork, WorkstationItemSettings items, Plugin plugin) {
        if (!(remove(block) instanceof Option.Some<BlockTrader>(BlockTrader trader))) {
            return false;
        }

        final Workstation workstation = Workstation.ofType(block.getType().asBlockType());
        if (workstation == null) {
            return false;
        }

        final FalvtpTrader.Snapshot snapshot = trader.trader().snapshot();
        final ItemStack item;

        // only save the workstation if it has experience otherwise drop it so it's easy to refresh trades
        if (snapshot.experience() <= 0 && snapshot.level() <= 1) {
            item = items.createBasic(workstation);
        } else {
            item = items.create(workstation, snapshot);
            item.editPersistentDataContainer(host -> FalvtpTrader.Snapshot.CODEC.release(snapshot, host));
        }

        final World world = block.getWorld();
        final Location location = block.getLocation();
        if (doBlockWork) {
            final var group = block.getBlockSoundGroup();
            world.playSound(location, group.getBreakSound(), group.getVolume(), group.getPitch());
            world.playEffect(location, Effect.DESTROY_BLOCK, block.getBlockData());
            block.setType(Material.AIR);
        }
        // a tick later, once the block is actually gone: during a break event it is still there, so a drop spawned
        // now starts inside a solid block and gets shoved out sideways
        Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> world.dropItemNaturally(location, item), 1L);
        return true;
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public int size() {
        return positions.size();
    }

    // only changed traders: writing the chunk's PDC marks the whole chunk for saving, so rewriting clean traders
    // would resave every trader chunk on every autosave. Clean traders are already in the PDC as they are.
    public void save() {
        for (final BlockTrader trader : loaded.values()) {
            if (trader.trader().isDirty()) {
                trader.save();
            }
        }
    }

    // a customer can be carried out of range with the menu open (minecart, pearl, teleport), so end their
    // sessions first; otherwise they keep trading on an instance that is never saved again
    void unload() {
        for (final BlockTrader trader : loaded.values()) {
            closeSession(trader);
        }
        save();
    }

    // closing fires InventoryCloseEvent, which ends the session before any snapshot is taken
    private static void closeSession(BlockTrader trader) {
        final Player customer = trader.trader().customer();
        if (customer != null) {
            customer.closeInventory();
        }
    }

    private void writeIndex() {
        final PersistentDataContainer chunkData = chunk.getPersistentDataContainer();
        if (positions.isEmpty()) {
            chunkData.remove(INDEX);
        } else {
            chunkData.set(INDEX, PersistentDataType.LONG_ARRAY, positions.toLongArray());
        }
    }

    private static long key(Position pos) {
        return key(pos.blockX(), pos.blockY(), pos.blockZ());
    }

    private static long key(int blockX, int blockY, int blockZ) {
        return (long) blockY << 8 | (blockX & 15) << 4 | blockZ & 15;
    }
}

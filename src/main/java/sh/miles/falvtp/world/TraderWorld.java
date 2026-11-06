package sh.miles.falvtp.world;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import sh.miles.falvtp.config.WorkstationItemSettings;
import sh.miles.falvtp.utils.Option;

import java.util.Collection;
import java.util.Collections;

public final class TraderWorld {
    private final World world;
    private final Plugin plugin;
    private final Long2ObjectMap<TraderChunk> chunks = new Long2ObjectOpenHashMap<>();

    TraderWorld(World world, Plugin plugin) {
        this.world = world;
        this.plugin = plugin;
    }

    public World world() {
        return world;
    }

    public Option<TraderChunk> load(Chunk chunk) {
        checkWorld(chunk.getWorld());
        final TraderChunk existing = chunks.get(chunk.getChunkKey());
        if (existing != null) {
            return Option.some(existing);
        }

        final Option<TraderChunk> loaded = TraderChunk.load(chunk);
        loaded.ifSome(traderChunk -> chunks.put(chunk.getChunkKey(), traderChunk));
        return loaded;
    }

    public Option<TraderChunk> unload(Chunk chunk) {
        checkWorld(chunk.getWorld());
        final TraderChunk removed = chunks.remove(chunk.getChunkKey());
        if (removed != null) {
            removed.unload();
        }
        return Option.ofNullable(removed);
    }

    void unloadAll() {
        for (final TraderChunk chunk : chunks.values()) {
            chunk.unload();
        }
        chunks.clear();
    }

    public Option<TraderChunk> chunk(int chunkX, int chunkZ) {
        return Option.ofNullable(chunks.get(Chunk.getChunkKey(chunkX, chunkZ)));
    }

    public Option<BlockTrader> get(Block block) {
        checkWorld(block.getWorld());
        return chunk(block.getX() >> 4, block.getZ() >> 4).flatMap(chunk -> chunk.get(block));
    }

    public boolean has(int blockX, int blockY, int blockZ) {
        final TraderChunk chunk = chunks.get(Chunk.getChunkKey(blockX >> 4, blockZ >> 4));
        return chunk != null && chunk.has(blockX, blockY, blockZ);
    }

    public Option<BlockTrader> add(BlockTrader trader) {
        final Location location = trader.location();
        checkWorld(location.getWorld());
        final Chunk chunk = location.getChunk();
        return load(chunk).orElseGet(() -> {
            final TraderChunk created = TraderChunk.empty(chunk);
            chunks.put(chunk.getChunkKey(), created);
            return created;
        }).add(trader);
    }

    public boolean breakNaturally(Block block, boolean doBlockWork, WorkstationItemSettings items) {
        checkWorld(block.getWorld());
        if (!(chunk(block.getX() >> 4, block.getZ() >> 4) instanceof Option.Some<TraderChunk>(TraderChunk chunk))) {
            return false;
        }
        final boolean broken = chunk.breakNaturally(block, doBlockWork, items, plugin);
        dropIfEmpty(block, chunk);
        return broken;
    }

    public Option<BlockTrader> remove(Block block) {
        checkWorld(block.getWorld());
        if (!(chunk(block.getX() >> 4, block.getZ() >> 4) instanceof Option.Some<TraderChunk>(TraderChunk chunk))) {
            return Option.none();
        }
        final Option<BlockTrader> removed = chunk.remove(block);
        dropIfEmpty(block, chunk);
        return removed;
    }

    public Collection<TraderChunk> chunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    public void save() {
        for (final TraderChunk chunk : chunks.values()) {
            chunk.save();
        }
    }

    private void dropIfEmpty(Block block, TraderChunk chunk) {
        if (chunk.isEmpty()) {
            chunks.remove(Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4));
        }
    }

    private void checkWorld(World other) {
        Preconditions.checkArgument(world.equals(other), "Expected world %s but got %s", world.getName(), other.getName());
    }
}

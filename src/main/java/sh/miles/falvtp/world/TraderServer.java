package sh.miles.falvtp.world;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import sh.miles.falvtp.config.WorkstationItemSettings;
import sh.miles.falvtp.utils.Option;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TraderServer {
    private final Plugin plugin;
    private final Map<UUID, TraderWorld> worlds = new HashMap<>();

    // the plugin schedules the delayed drops of broken traders
    public TraderServer(Plugin plugin) {
        this.plugin = plugin;
    }

    public TraderWorld world(World world) {
        return worlds.computeIfAbsent(world.getUID(), id -> new TraderWorld(world, plugin));
    }

    public Option<TraderWorld> find(World world) {
        return Option.ofNullable(worlds.get(world.getUID()));
    }

    public void loadAll(Collection<World> loaded) {
        for (final World world : loaded) {
            final TraderWorld traderWorld = world(world);
            for (final Chunk chunk : world.getLoadedChunks()) {
                traderWorld.load(chunk);
            }
        }
    }

    public Option<TraderWorld> unload(World world) {
        final TraderWorld removed = worlds.remove(world.getUID());
        if (removed != null) {
            // a customer may be in another world (cross-dimension pearl / teleport)
            removed.unloadAll();
        }
        return Option.ofNullable(removed);
    }

    public Option<BlockTrader> get(Block block) {
        return find(block.getWorld()).flatMap(world -> world.get(block));
    }

    public Option<BlockTrader> add(BlockTrader trader) {
        return world(trader.world()).add(trader);
    }

    public Option<BlockTrader> remove(Block block) {
        return find(block.getWorld()).flatMap(world -> world.remove(block));
    }

    public boolean breakNaturally(Block block, boolean doBlockWork, WorkstationItemSettings items) {
        return find(block.getWorld()) instanceof Option.Some<TraderWorld>(TraderWorld world)
               && world.breakNaturally(block, doBlockWork, items);
    }

    public Collection<TraderWorld> worlds() {
        return Collections.unmodifiableCollection(worlds.values());
    }

    public void save() {
        for (final TraderWorld world : worlds.values()) {
            world.save();
        }
    }
}

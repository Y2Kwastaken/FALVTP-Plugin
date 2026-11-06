package sh.miles.falvtp.world.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.utils.Option;
import sh.miles.falvtp.world.TraderServer;
import sh.miles.falvtp.world.TraderWorld;

import java.util.Iterator;
import java.util.List;

public class BlockChangeListener implements Listener {

    private final TraderServer server;
    private final FalvtpConfig config;

    public BlockChangeListener(TraderServer server, FalvtpConfig config) {
        this.server = server;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (hasTrader(event.getBlock().getWorld(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (hasTrader(event.getBlock().getWorld(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (destroysBlocks(event.getExplosionResult())) {
            dropTraders(event.getBlock().getWorld(), event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (destroysBlocks(event.getExplosionResult())) {
            dropTraders(event.getEntity().getWorld(), event.blockList());
        }
    }

    // wither body, /setblock and /fill destroy, end platform/podium, player breaks and explosions don't fire this
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDestroy(BlockDestroyEvent event) {
        if (server.breakNaturally(event.getBlock(), false, config.item())) {
            event.setWillDrop(false);
        }
    }

    // HIGHEST so a DROP never happens for a burn another plugin cancels
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        final Block block = event.getBlock();
        if (!(server.find(block.getWorld()) instanceof Option.Some<TraderWorld>(TraderWorld traderWorld))
            || !traderWorld.has(block.getX(), block.getY(), block.getZ())) {
            return;
        }

        switch (config.protection().fire()) {
            case PROTECT -> event.setCancelled(true);
            // the fire removes the block itself
            case DROP -> traderWorld.breakNaturally(block, false, config.item());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        final Block block = event.getBlock();
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE && !config.protection().creativeDrops()) {
            server.remove(block);
            return;
        }

        if (server.breakNaturally(block, false, config.item())) {
            event.setDropItems(false);
        }
    }

    private static boolean destroysBlocks(ExplosionResult result) {
        return result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY;
    }

    private void dropTraders(World world, List<Block> blocks) {
        if (!(server.find(world) instanceof Option.Some<TraderWorld>(TraderWorld traderWorld))) {
            return;
        }

        final Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            final Block block = iterator.next();
            if (traderWorld.has(block.getX(), block.getY(), block.getZ())) {
                // either way the explosion itself must not destroy it
                iterator.remove();
                if (config.protection().explosions() == FalvtpConfig.Mode.DROP) {
                    traderWorld.breakNaturally(block, true, config.item());
                }
            }
        }
    }

    private boolean hasTrader(World world, List<Block> blocks) {
        if (!(server.find(world) instanceof Option.Some<TraderWorld>(TraderWorld traderWorld))) {
            return false;
        }

        for (final Block block : blocks) {
            if (traderWorld.has(block.getX(), block.getY(), block.getZ())) {
                return true;
            }
        }
        return false;
    }
}

package sh.miles.falvtp.world;

import com.google.common.base.Preconditions;
import io.papermc.paper.math.Position;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.trader.Trader;
import sh.miles.falvtp.trader.TraderPair;
import sh.miles.falvtp.utils.Option;


public record BlockTrader(Location location, FalvtpTrader trader) implements Trader {
    private static final String NAMESPACE = "falvtp";
    private static final String KEY_PREFIX = "block_trader/";

    public BlockTrader {
        Preconditions.checkArgument(location.getWorld() != null, "location must have world");
    }

    @Override
    public Location location() {
        return location.clone();
    }

    @Override
    public World world() {
        return location.getWorld();
    }

    @Override
    public Position pos() {
        return Position.block(location);
    }

    public TraderPair pair() {
        return new TraderPair(trader, this);
    }

    public void save() {
        final PersistentDataContainer chunkData = location.getChunk().getPersistentDataContainer();
        final PersistentDataContainer traderData = chunkData.getAdapterContext().newPersistentDataContainer();
        FalvtpTrader.Snapshot.CODEC.release(trader.snapshot(), traderData);
        chunkData.set(key(location.getBlockX(), location.getBlockY(), location.getBlockZ()), PersistentDataType.TAG_CONTAINER, traderData);
        trader.markSaved();
    }

    static Option<BlockTrader> read(Block block) {
        final PersistentDataContainer traderData = block.getChunk().getPersistentDataContainer()
                .get(key(block.getX(), block.getY(), block.getZ()), PersistentDataType.TAG_CONTAINER);
        if (traderData == null) {
            return Option.none();
        }
        return Option.some(restore(block.getLocation(), traderData));
    }

    public static boolean remove(Block block) {
        final PersistentDataContainer chunkData = block.getChunk().getPersistentDataContainer();
        final NamespacedKey key = key(block.getX(), block.getY(), block.getZ());
        if (!chunkData.has(key)) {
            return false;
        }
        chunkData.remove(key);
        return true;
    }

    private static BlockTrader restore(Location location, PersistentDataContainer traderData) {
        return new BlockTrader(location, FalvtpTrader.restore(FalvtpTrader.Snapshot.CODEC.read(traderData)));
    }

    private static NamespacedKey key(int blockX, int blockY, int blockZ) {
        return new NamespacedKey(NAMESPACE, KEY_PREFIX + (blockX & 15) + "_" + blockY + "_" + (blockZ & 15));
    }
}

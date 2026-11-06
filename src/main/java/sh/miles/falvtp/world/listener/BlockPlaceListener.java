package sh.miles.falvtp.world.listener;

import io.papermc.paper.math.Position;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.trader.Trader;
import sh.miles.falvtp.utils.Option;
import sh.miles.falvtp.utils.Workstation;
import sh.miles.falvtp.world.BlockTrader;
import sh.miles.falvtp.world.TraderServer;

public class BlockPlaceListener implements Listener {

    private final TraderServer server;

    public BlockPlaceListener(TraderServer server) {
        this.server = server;
    }

    // MONITOR so a cancelled or disallowed place (protection plugins) can't create a trader and keep the item
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) {
            return;
        }

        final ItemStack item = event.getItemInHand();
        Workstation workstation = Workstation.ofItem(item);
        if (workstation == null) {
            return;
        }

        final Location location = event.getBlockPlaced().getLocation();
        final FalvtpTrader trader = FalvtpTrader.Snapshot.CODEC.tryRead(item.getPersistentDataContainer())
                instanceof Option.Some<FalvtpTrader.Snapshot>(FalvtpTrader.Snapshot snapshot)
                ? FalvtpTrader.restore(snapshot)
                // tagged but no snapshot so we create a new an untraded station, so every placement rolls fresh trades
                : FalvtpTrader.create(Trader.at(location.getWorld(), Position.block(location)), workstation.profession());
        server.add(new BlockTrader(location, trader));
    }

}

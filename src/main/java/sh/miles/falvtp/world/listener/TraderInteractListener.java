package sh.miles.falvtp.world.listener;

import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.trader.session.TradeSessions;
import sh.miles.falvtp.utils.Option;
import sh.miles.falvtp.world.BlockTrader;
import sh.miles.falvtp.world.TraderServer;

public class TraderInteractListener implements Listener {

    private final TradeSessions sessions;
    private final TraderServer server;
    private final FalvtpConfig config;

    public TraderInteractListener(TradeSessions sessions, TraderServer server, FalvtpConfig config) {
        this.sessions = sessions;
        this.server = server;
        this.config = config;
    }

    // HIGHEST + ignoreCancelled: protection plugins and the soul vial binder (HIGH) get the click first
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTraderInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || config.sneakBypass() && event.getPlayer().isSneaking()
            || !(server.get(event.getClickedBlock()) instanceof Option.Some<BlockTrader>(BlockTrader trader))) {
            return;
        }

        // denies both the block's own use and the held item
        event.setCancelled(true);
        // lazy stand-in for WorkAtPoi; missed days are caught up by shouldRestock itself
        if (!config.restockWorkHoursOnly() || trader.isWorkTime()) {
            trader.trader().tryRestock(trader.world(), config.trading());
        }

        final Sound sound = sessions.open(event.getPlayer(), trader.pair(), null) ? config.openSound() : config.denySound();
        if (sound != null) {
            final Location at = trader.location().toCenterLocation();
            at.getWorld().playSound(sound, at.getX(), at.getY(), at.getZ());
        }
    }

}

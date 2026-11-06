package sh.miles.falvtp.trader.session;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.Location;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.view.MerchantView;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.trader.TraderPair;

public class TradeSessionsListener implements Listener {

    private final TradeSessions sessions;
    private final FalvtpConfig config;

    public TradeSessionsListener(TradeSessions sessions, FalvtpConfig config) {
        this.sessions = sessions;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPurchase(PlayerPurchaseEvent event) {
        final TraderPair session = sessions.sessions.get(event.getMerchant());
        if (session == null) {
            return;
        }

        final MerchantRecipe offer = event.getTrade();
        if (!session.who().offers().contains(offer)) {
            return;
        }

        event.setIncreaseTradeUses(false);
        event.setRewardExp(false);
        final FalvtpTrader.TradeResult result = session.who().recordTrade(session.where(), offer, config.trading());
        if (result.orbExperience() > 0) {
            // top centre of the trader's block
            final Location location = session.where().pos().toCenter().offset(0, 0.5, 0).toLocation(session.where().world());
            location.getWorld().spawn(location, ExperienceOrb.class, orb -> orb.setExperience(result.orbExperience()));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView() instanceof MerchantView view) {
            TraderPair trader = sessions.sessions.remove(view.getMerchant());
            if (trader != null) {
                trader.who().stopTrading();
            }
        }
    }
}

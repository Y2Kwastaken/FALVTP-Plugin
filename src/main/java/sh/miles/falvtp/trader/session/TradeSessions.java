package sh.miles.falvtp.trader.session;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Merchant;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.trader.TraderPair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeSessions {
    private final Plugin plugin;
    private final FalvtpConfig config;
    final Map<Merchant, TraderPair> sessions = new HashMap<>();

    public TradeSessions(Plugin plugin, FalvtpConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean open(Player player, TraderPair trader, @Nullable Component title) {
        FalvtpTrader who = trader.who();
        if (who.customer() != null) {
            return false;
        }

        if (title == null) {
            title = config.menuTitle(who);
        }
        who.setPriceControl(config.priceControl());

        // start trading before handing over the offers, so any plain recipe is made live first
        final Merchant merchant = Bukkit.createMerchant();
        who.startTrading(player, () -> sync(player, merchant, who));
        merchant.setRecipes(who.offers());
        sessions.put(merchant, trader);

        MenuType.MERCHANT.builder()
                .title(title)
                .merchant(merchant)
                .build(player).open();

        if (!player.equals(merchant.getTrader())) {
            sessions.remove(merchant);
            who.stopTrading();
            return false;
        }

        // replaces Paper's opening packet, which hides the xp bar for custom merchants
        sendOffers(player, who);
        return true;
    }

    public void closeAll() {
        for (Merchant merchant : List.copyOf(sessions.keySet())) {
            final HumanEntity customer = merchant.getTrader();
            if (customer != null) {
                customer.closeInventory();
            }
        }
    }

    // a tick later, since a level up happens inside PlayerPurchaseEvent before Paper has taken the payment
    private void sync(Player player, Merchant merchant, FalvtpTrader trader) {
        player.getScheduler().run(plugin, task -> {
            if (player.equals(merchant.getTrader())) {
                merchant.setRecipes(trader.offers());
                sendOffers(player, trader);
            }
        }, null);
    }

    // sends offers with the trader's real level and xp, so the client shows the xp bar, level name and restock hint
    private static void sendOffers(Player player, FalvtpTrader trader) {
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();
        if (handle.containerMenu instanceof MerchantMenu menu) {
            menu.updateSellItem();
            handle.sendMerchantOffers(menu.containerId, menu.getOffers(), trader.level(), trader.experience(), true, true);
        }
    }

}

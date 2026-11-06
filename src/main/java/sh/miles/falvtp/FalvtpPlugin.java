package sh.miles.falvtp;

import org.bukkit.plugin.java.JavaPlugin;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.trader.session.TradeSessions;
import sh.miles.falvtp.trader.session.TradeSessionsListener;
import sh.miles.falvtp.world.TraderServer;
import sh.miles.falvtp.world.listener.BlockChangeListener;
import sh.miles.falvtp.world.listener.BlockPlaceListener;
import sh.miles.falvtp.world.listener.ChunkListener;
import sh.miles.falvtp.world.listener.EntityInteractListener;
import sh.miles.falvtp.world.listener.TraderInteractListener;
import sh.miles.falvtp.world.listener.WorkstationInteractListener;

public class FalvtpPlugin extends JavaPlugin {

    private TradeSessions sessions;
    private TraderServer traders;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final FalvtpConfig config = FalvtpConfig.load(getConfig(), getSLF4JLogger());

        sessions = new TradeSessions(this, config);
        traders = new TraderServer(this);
        traders.loadAll(getServer().getWorlds());
        getServer().getPluginManager().registerEvents(new TradeSessionsListener(sessions, config), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(traders), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(traders), this);
        getServer().getPluginManager().registerEvents(new BlockChangeListener(traders, config), this);
        getServer().getPluginManager().registerEvents(new TraderInteractListener(sessions, traders, config), this);
        getServer().getPluginManager().registerEvents(new EntityInteractListener(config), this);
        getServer().getPluginManager().registerEvents(new WorkstationInteractListener(traders, config), this);
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.closeAll();
        }

        if (traders != null) {
            traders.save();
        }
    }

    public TraderServer traders() {
        return traders;
    }
}

package sh.miles.falvtp;

import org.bukkit.plugin.java.JavaPlugin;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.trader.session.TradeSessions;
import sh.miles.falvtp.trader.session.TradeSessionsListener;
import sh.miles.falvtp.world.TraderServer;
import sh.miles.falvtp.world.capture.CaptureFeature;
import sh.miles.falvtp.world.capture.SoulVialCaptureFeature;
import sh.miles.falvtp.world.capture.WorkstationCaptureFeature;
import sh.miles.falvtp.world.listener.BlockChangeListener;
import sh.miles.falvtp.world.listener.BlockPlaceListener;
import sh.miles.falvtp.world.listener.ChunkListener;
import sh.miles.falvtp.world.listener.EntityInteractListener;
import sh.miles.falvtp.world.listener.TraderInteractListener;
import sh.miles.falvtp.world.listener.WorkstationInteractListener;

import java.util.ArrayList;
import java.util.List;

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

        // order matters: the first feature to cancel a click owns it
        final List<CaptureFeature> captures = new ArrayList<>();
        if (config.soulVialEnabled()) {
            captures.add(new SoulVialCaptureFeature(traders, config));
        }
        if (config.workstationCaptureEnabled()) {
            captures.add(new WorkstationCaptureFeature(config));
        }
        if (!captures.isEmpty()) {
            getServer().getPluginManager().registerEvents(new EntityInteractListener(captures), this);
            getServer().getPluginManager().registerEvents(new WorkstationInteractListener(captures), this);
        }
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

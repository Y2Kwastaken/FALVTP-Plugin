package sh.miles.falvtp.world.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import sh.miles.falvtp.world.TraderServer;

public class ChunkListener implements Listener {

    private final TraderServer server;

    public ChunkListener(TraderServer server) {
        this.server = server;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        server.world(event.getWorld()).load(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        server.find(event.getWorld()).ifSome(world -> world.unload(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        server.find(event.getWorld()).ifSome(world -> world.save());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        server.unload(event.getWorld());
    }
}

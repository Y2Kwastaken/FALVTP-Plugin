package sh.miles.falvtp.world.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import sh.miles.falvtp.world.capture.CaptureFeature;

import java.util.List;

public class EntityInteractListener implements Listener {

    private final List<CaptureFeature> features;

    public EntityInteractListener(List<CaptureFeature> features) {
        this.features = List.copyOf(features);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        for (final CaptureFeature feature : features) {
            feature.onInteract(event);
            if (event.isCancelled()) {
                return;
            }
        }
    }
}

package sh.miles.falvtp.world.listener;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import sh.miles.falvtp.world.capture.CaptureFeature;

import java.util.List;

public class WorkstationInteractListener implements Listener {

    private final List<CaptureFeature> features;

    public WorkstationInteractListener(List<CaptureFeature> features) {
        this.features = List.copyOf(features);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWorkstationInteract(PlayerInteractEvent event) {
        for (final CaptureFeature feature : features) {
            feature.onTransform(event);
            if (event.useInteractedBlock() == Event.Result.DENY) {
                return;
            }
        }
    }

}

package sh.miles.falvtp.world.capture;

import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public interface CaptureFeature {
    void onInteract(PlayerInteractAtEntityEvent event);
    void onTransform(PlayerInteractEvent event);
}

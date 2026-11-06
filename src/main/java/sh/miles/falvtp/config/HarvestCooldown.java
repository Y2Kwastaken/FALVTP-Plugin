package sh.miles.falvtp.config;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

/**
 * How long a villager needs between soul harvests. The last harvest is stored on the villager itself, so it survives
 * restarts and follows the villager around. Uses world game time, which {@code /time set} doesn't change.
 *
 * @param ticks game ticks between harvests of one villager; 0 for no cooldown
 */
public record HarvestCooldown(long ticks) {

    public static final HarvestCooldown DEFAULT = new HarvestCooldown(24000L);

    private static final NamespacedKey LAST_HARVEST = new NamespacedKey("falvtp", "last_harvest");

    public boolean isReady(Villager villager) {
        return remaining(villager) == 0;
    }

    // ticks until the villager can be harvested again, 0 if it can be now
    public long remaining(Villager villager) {
        final Long last = villager.getPersistentDataContainer().get(LAST_HARVEST, PersistentDataType.LONG);
        if (last == null) {
            return 0;
        }

        final long now = villager.getWorld().getGameTime();
        // a time in the future means the world's time was reset
        return last > now ? 0 : Math.max(0, last + ticks - now);
    }

    // call once a harvest has gone through
    public void start(Villager villager) {
        villager.getPersistentDataContainer().set(LAST_HARVEST, PersistentDataType.LONG, villager.getWorld().getGameTime());
    }
}

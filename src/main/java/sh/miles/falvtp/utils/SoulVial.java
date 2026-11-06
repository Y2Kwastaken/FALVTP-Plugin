package sh.miles.falvtp.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

// marks an item as a filled soul vial; the look comes from SoulVialSettings. Any vial binds to any workstation,
// so it carries nothing about the villager it came from
public final class SoulVial {

    private static final NamespacedKey MARKER = new NamespacedKey("falvtp", "soul_vial");

    private SoulVial() {
    }

    public static ItemStack mark(ItemStack vial) {
        vial.editPersistentDataContainer(container -> container.set(MARKER, PersistentDataType.BOOLEAN, true));
        return vial;
    }

    public static boolean isSoulVial(ItemStack item) {
        return item.getPersistentDataContainer().has(MARKER, PersistentDataType.BOOLEAN);
    }
}

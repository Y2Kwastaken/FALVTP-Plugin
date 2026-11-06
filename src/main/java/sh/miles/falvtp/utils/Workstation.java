package sh.miles.falvtp.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

// vanilla job site blocks and the profession each one gives
public enum Workstation {
    ARMORER(BlockType.BLAST_FURNACE, Villager.Profession.ARMORER),
    BUTCHER(BlockType.SMOKER, Villager.Profession.BUTCHER),
    CARTOGRAPHER(BlockType.CARTOGRAPHY_TABLE, Villager.Profession.CARTOGRAPHER),
    CLERIC(BlockType.BREWING_STAND, Villager.Profession.CLERIC),
    FARMER(BlockType.COMPOSTER, Villager.Profession.FARMER),
    FISHERMAN(BlockType.BARREL, Villager.Profession.FISHERMAN),
    FLETCHER(BlockType.FLETCHING_TABLE, Villager.Profession.FLETCHER),
    LEATHERWORKER(BlockType.CAULDRON, Villager.Profession.LEATHERWORKER),
    LIBRARIAN(BlockType.LECTERN, Villager.Profession.LIBRARIAN),
    MASON(BlockType.STONECUTTER, Villager.Profession.MASON),
    SHEPHERD(BlockType.LOOM, Villager.Profession.SHEPHERD),
    TOOLSMITH(BlockType.SMITHING_TABLE, Villager.Profession.TOOLSMITH),
    WEAPONSMITH(BlockType.GRINDSTONE, Villager.Profession.WEAPONSMITH);

    private static final NamespacedKey ITEM_KEY = new NamespacedKey("falvtp", "workstation");

    private final BlockType block;
    private final Villager.Profession profession;

    Workstation(BlockType block, Villager.Profession profession) {
        this.block = block;
        this.profession = profession;
    }

    public Villager.Profession profession() {
        return profession;
    }

    // filled cauldrons are different blocks but still the leatherworker's job site
    public boolean matches(BlockType placed) {
        return placed == block || this == LEATHERWORKER && Tag.CAULDRONS.isTagged(placed.asMaterial());
    }

    // tagged block item only; name, glint and lore come from ItemSettings
    public ItemStack item() {
        final ItemStack item = block.getItemType().createItemStack();
        item.editPersistentDataContainer((container) -> {
            container.set(ITEM_KEY, PersistentDataType.STRING, name());
        });
        return item;
    }

    public static @Nullable Workstation ofType(BlockType type) {
        for (final Workstation value : values()) {
            if (value.matches(type)) {
                return value;
            }
        }

        return null;
    }

    public static @Nullable Workstation ofItem(ItemStack item) {
        final String name = item.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
        return name == null ? null : byName(name);
    }

    public static @Nullable Workstation byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}

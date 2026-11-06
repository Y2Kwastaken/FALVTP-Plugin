package sh.miles.falvtp.config;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How a filled soul vial looks: a vanilla glass bottle used on a villager becomes this, holding its DNA.
 * Templates are plain MiniMessage.
 *
 * @param material the item it is made from
 * @param model    resource pack item model, null for the material's own
 * @param name     item name template
 * @param glint    whether the item has the enchantment glint
 * @param lore     lore line templates, empty for none
 * @param liquid   liquid colour, null for the material's default; only potion-like materials show it
 */
public record SoulVialSettings(ItemType material, @Nullable Key model, String name, boolean glint, List<String> lore, @Nullable Color liquid) {

    public static final SoulVialSettings DEFAULT = new SoulVialSettings(ItemType.POTION, null, "<!i><aqua>Soul Vial", true,
            List.of("<!i><gray>Use on a workstation to bind it"), Color.fromRGB(0x7FD8FF));

    public SoulVialSettings {
        lore = List.copyOf(lore);
    }

    // untagged; marking the item as a vial and storing the DNA is the caller's job
    public ItemStack create() {
        final MiniMessage mini = MiniMessage.miniMessage();
        final ItemStack item = material.createItemStack();
        item.setData(DataComponentTypes.CUSTOM_NAME, mini.deserialize(name));
        if (model != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, model);
        }
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (!lore.isEmpty()) {
            item.setData(DataComponentTypes.LORE, ItemLore.lore(lore.stream().map(mini::deserialize).toList()));
        }
        if (liquid != null && material.hasDefaultData(DataComponentTypes.POTION_CONTENTS)) {
            item.setData(DataComponentTypes.POTION_CONTENTS, PotionContents.potionContents().customColor(liquid).build());
            // hides the "No Effects" line
            item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.POTION_CONTENTS).build());
        }
        // a potion (or any food) material would otherwise be drunk or eaten, losing the DNA
        item.unsetData(DataComponentTypes.CONSUMABLE);
        return item;
    }
}

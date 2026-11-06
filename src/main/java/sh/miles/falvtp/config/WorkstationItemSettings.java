package sh.miles.falvtp.config;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.utils.Workstation;

import java.util.List;

/**
 * How the dropped workstation items look.
 *
 * @param station a trader with trades kept in the item; templates may use {@link TraderTags} placeholders
 * @param basic   an untraded station that rolls fresh trades when placed; plain MiniMessage, no placeholders
 */
public record WorkstationItemSettings(Look station, Look basic) {

    public static final WorkstationItemSettings DEFAULT = new WorkstationItemSettings(
            new Look("<profession> Workstation", true, List.of()),
            new Look("<profession> Workstation", false, List.of("<gray>Place to roll new trades"))
    );

    /**
     * @param name  item name template
     * @param glint whether the item has the enchantment glint
     * @param lore  lore line templates, empty for none
     */
    public record Look(String name, boolean glint, List<String> lore) {
        public Look {
            lore = List.copyOf(lore);
        }
    }

    // the snapshot itself is written by the caller
    public ItemStack create(Workstation workstation, FalvtpTrader.Snapshot snapshot) {
        return apply(station, workstation.item(), TraderTags.of(snapshot.profession(), snapshot.level()));
    }

    // tagged so placing it rolls a trader, but carries no snapshot
    public ItemStack createBasic(Workstation workstation) {
        return apply(basic, workstation.item(), TraderTags.of(workstation.profession(), -1));
    }

    private static ItemStack apply(Look look, ItemStack item, TagResolver tags) {
        final MiniMessage mini = MiniMessage.miniMessage();
        item.setData(DataComponentTypes.CUSTOM_NAME, mini.deserialize(look.name(), tags));
        if (look.glint()) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (!look.lore().isEmpty()) {
            item.setData(DataComponentTypes.LORE, ItemLore.lore(look.lore().stream().map(line -> mini.deserialize(line, tags)).toList()));
        }
        return item;
    }
}

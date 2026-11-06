package sh.miles.falvtp.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Villager;

import java.util.List;

// MiniMessage placeholders shared by every trader template: <profession>, <level>, <level_name>
final class TraderTags {

    private TraderTags() {
    }

    private static final List<String> NAMES = List.of("profession", "level", "level_name");

    // whether a template uses any of these placeholders (as <name> or <name:...>)
    static boolean mentions(String template) {
        for (final String name : NAMES) {
            if (template.contains("<" + name + ">") || template.contains("<" + name + ":")) {
                return true;
            }
        }
        return false;
    }

    static TagResolver of(Villager.Profession profession, int level) {
        return TagResolver.resolver(
                Placeholder.component("profession", Component.translatable(profession.translationKey())),
                Placeholder.unparsed("level", Integer.toString(level)),
                // vanilla's Novice/Apprentice/... names
                Placeholder.component("level_name", Component.translatable("merchant.level." + level))
        );
    }
}

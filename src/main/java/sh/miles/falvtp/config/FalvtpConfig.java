package sh.miles.falvtp.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.trader.PriceControl;
import sh.miles.falvtp.trader.TradeRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * config.yml, read once on enable. Invalid values are logged and replaced by their default, so a typo never leaves
 * traders unprotected.
 *
 * @param trading              numbers handed to every {@link FalvtpTrader} call
 * @param restockWorkHoursOnly only restock while a vanilla villager would be working
 * @param priceControl         discount applied while a player trades
 * @param sneakBypass          sneaking skips the trader so players can build against it
 * @param openSound            played when a trade menu opens, null for none
 * @param denySound            played when a trader is busy, null for none
 * @param menuTitle            trade menu title template
 * @param item                 the dropped workstation item
 * @param soulVial             the filled soul vial a glass bottle becomes after harvesting a villager
 * @param harvestCooldown      how long each villager needs between harvests
 * @param messages             action bar messages
 * @param protection           what fire and explosions do to traders
 */
public record FalvtpConfig(
        TradeRules trading,
        boolean restockWorkHoursOnly,
        PriceControl priceControl,
        boolean sneakBypass,
        @Nullable Sound openSound,
        @Nullable Sound denySound,
        String menuTitle,
        WorkstationItemSettings item,
        SoulVialSettings soulVial,
        HarvestCooldown harvestCooldown,
        Messages messages,
        Protection protection
) {

    public Component menuTitle(FalvtpTrader trader) {
        return MiniMessage.miniMessage().deserialize(menuTitle, TraderTags.of(trader.profession(), trader.level()));
    }

    public enum Mode {
        // the trader is left in place
        PROTECT,
        // the trader breaks and drops its workstation item
        DROP
    }

    /**
     * @param fire          fire burning a trader
     * @param explosions    explosions that destroy blocks
     * @param creativeDrops whether creative players breaking a trader get the item
     */
    public record Protection(Mode fire, Mode explosions, boolean creativeDrops) {
        public static final Protection DEFAULT = new Protection(Mode.PROTECT, Mode.DROP, true);
    }

    public static FalvtpConfig load(ConfigurationSection root, Logger logger) {
        final Reader reader = new Reader(root, logger);
        final TradeRules vanilla = TradeRules.VANILLA;
        final TradeRules trading = new TradeRules(
                reader.integer("restock.max-per-day", vanilla.maxRestocksPerDay(), 0),
                reader.longValue("restock.cooldown-ticks", vanilla.restockCooldownTicks(), 0),
                reader.levelUpExperience("leveling.experience", vanilla.levelUpExperience()),
                reader.bool("experience-orbs.enabled", vanilla.experienceOrbs()),
                reader.integer("experience-orbs.base", vanilla.orbBase(), 0),
                reader.integer("experience-orbs.spread", vanilla.orbSpread(), 0),
                reader.integer("experience-orbs.level-up-bonus", vanilla.levelUpOrbBonus(), 0)
        );

        return new FalvtpConfig(
                trading,
                reader.bool("restock.work-hours-only", true),
                reader.bool("pricing.hero-of-the-village", true) ? PriceControl.HERO_OF_VILLAGE : PriceControl.NONE,
                reader.bool("interact.sneak-bypass", true),
                reader.sound("sounds.open", Key.key("entity.villager.trade")),
                reader.sound("sounds.deny", Key.key("entity.villager.no")),
                reader.string("menu.title", "<profession>"),
                new WorkstationItemSettings(
                        new WorkstationItemSettings.Look(
                                reader.string("item.name", WorkstationItemSettings.DEFAULT.station().name()),
                                reader.bool("item.glint", WorkstationItemSettings.DEFAULT.station().glint()),
                                reader.strings("item.lore", WorkstationItemSettings.DEFAULT.station().lore())
                        ),
                        new WorkstationItemSettings.Look(
                                reader.plain("item.basic.name", WorkstationItemSettings.DEFAULT.basic().name()),
                                reader.bool("item.basic.glint", WorkstationItemSettings.DEFAULT.basic().glint()),
                                reader.plainLines("item.basic.lore", WorkstationItemSettings.DEFAULT.basic().lore())
                        )
                ),
                reader.soulVial("soul-vial", SoulVialSettings.DEFAULT),
                new HarvestCooldown(reader.longValue("soul-vial.cooldown-ticks", HarvestCooldown.DEFAULT.ticks(), 0)),
                new Messages(
                        reader.string("messages.harvest-success", Messages.DEFAULT.harvestSuccess()),
                        reader.string("messages.harvest-cooldown", Messages.DEFAULT.harvestCooldown()),
                        reader.string("messages.harvest-ineligible", Messages.DEFAULT.harvestIneligible()),
                        reader.string("messages.bind-success", Messages.DEFAULT.bindSuccess()),
                        reader.string("messages.bind-occupied", Messages.DEFAULT.bindOccupied())
                ),
                new Protection(
                        reader.mode("protection.fire", Protection.DEFAULT.fire()),
                        reader.mode("protection.explosions", Protection.DEFAULT.explosions()),
                        reader.bool("protection.creative-drops", Protection.DEFAULT.creativeDrops())
                )
        );
    }

    // typed reads with a warning and fallback for anything malformed
    private record Reader(ConfigurationSection root, Logger logger) {

        int integer(String path, int fallback, int min) {
            final Object value = root.get(path);
            if (value instanceof Integer number && number >= min) {
                return number;
            }
            return invalid(path, value, "a whole number >= " + min, fallback);
        }

        long longValue(String path, long fallback, long min) {
            final Object value = root.get(path);
            if ((value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= min) {
                return ((Number) value).longValue();
            }
            return invalid(path, value, "a whole number >= " + min, fallback);
        }

        boolean bool(String path, boolean fallback) {
            return root.get(path) instanceof Boolean value ? value : invalid(path, root.get(path), "true or false", fallback);
        }

        String string(String path, String fallback) {
            return root.get(path) instanceof String value ? value : invalid(path, root.get(path), "text", fallback);
        }

        List<String> strings(String path, List<String> fallback) {
            final Object value = root.get(path);
            if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
                return list.stream().map(String.class::cast).toList();
            }
            return invalid(path, value, "a list of text", fallback);
        }

        // basic items have no trader yet, so trader placeholders would print as literal text
        String plain(String path, String fallback) {
            final String value = string(path, fallback);
            return TraderTags.mentions(value) ? invalid(path, value, "text without <profession>/<level>/<level_name>", fallback) : value;
        }

        List<String> plainLines(String path, List<String> fallback) {
            final List<String> value = strings(path, fallback);
            return value.stream().anyMatch(TraderTags::mentions)
                    ? invalid(path, value, "lines without <profession>/<level>/<level_name>", fallback)
                    : value;
        }

        List<Integer> levelUpExperience(String path, List<Integer> fallback) {
            final Object value = root.get(path);
            if (value instanceof List<?> list && list.size() == fallback.size() && list.stream().allMatch(Integer.class::isInstance)) {
                final List<Integer> numbers = new ArrayList<>(list.size());
                list.forEach(entry -> numbers.add((Integer) entry));
                if (TradeRules.isStrictlyAscending(numbers)) {
                    return numbers;
                }
            }
            return invalid(path, value, fallback.size() + " positive, strictly ascending whole numbers", fallback);
        }

        SoulVialSettings soulVial(String path, SoulVialSettings fallback) {
            return new SoulVialSettings(
                    itemType(path + ".material", fallback.material()),
                    optionalKey(path + ".item-model"),
                    string(path + ".name", fallback.name()),
                    bool(path + ".glint", fallback.glint()),
                    strings(path + ".lore", fallback.lore()),
                    color(path + ".liquid-color", fallback.liquid())
            );
        }

        ItemType itemType(String path, ItemType fallback) {
            final String id = string(path, fallback.key().asString());
            final ItemType type = Key.parseable(id) ? RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM).get(Key.key(id)) : null;
            return type != null && type != ItemType.AIR ? type : invalid(path, id, "an item id like glass_bottle", fallback);
        }

        // "" means none
        @Nullable Key optionalKey(String path) {
            final String id = string(path, "");
            if (id.isEmpty()) {
                return null;
            }
            return Key.parseable(id) ? Key.key(id) : invalid(path, id, "a namespaced key like myserver:soul_vial, or \"\"", null);
        }

        // "#RRGGBB", or "" for the material's default
        @Nullable Color color(String path, @Nullable Color fallback) {
            final String hex = string(path, "");
            if (hex.isEmpty()) {
                return null;
            }
            final TextColor parsed = TextColor.fromHexString(hex);
            return parsed != null ? Color.fromRGB(parsed.value()) : invalid(path, hex, "a hex colour like #7FD8FF, or \"\"", fallback);
        }

        Mode mode(String path, Mode fallback) {
            if (root.get(path) instanceof String value) {
                try {
                    return Mode.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // falls through to the warning
                }
            }
            return invalid(path, root.get(path), "protect or drop", fallback);
        }

        // an empty sound key turns the sound off
        @Nullable Sound sound(String path, Key fallback) {
            final String key = string(path + ".sound", fallback.asString());
            if (key.isEmpty()) {
                return null;
            }

            final float volume = (float) number(path + ".volume", 1.0);
            final float pitch = (float) number(path + ".pitch", 1.0);
            if (!Key.parseable(key)) {
                return invalid(path + ".sound", key, "a sound key like entity.villager.trade",
                        Sound.sound(fallback, Sound.Source.NEUTRAL, volume, pitch));
            }
            return Sound.sound(Key.key(key), Sound.Source.NEUTRAL, volume, pitch);
        }

        private double number(String path, double fallback) {
            final Object value = root.get(path);
            if (value instanceof Number number && number.doubleValue() >= 0) {
                return number.doubleValue();
            }
            return invalid(path, value, "a number >= 0", fallback);
        }

        private <T> T invalid(String path, @Nullable Object value, String expected, T fallback) {
            logger.warn("config.yml '{}' is {} but should be {}; using {}", path, value, expected, fallback);
            return fallback;
        }
    }
}

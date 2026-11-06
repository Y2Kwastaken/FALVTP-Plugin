package sh.miles.falvtp.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Villager;

/**
 * Action bar messages. Templates are MiniMessage; an empty template sends nothing.
 *
 * @param harvestSuccess    a villager's soul went into the vial
 * @param harvestCooldown   the villager was harvested too recently; {@code <time>} is the time left
 * @param harvestIneligible the villager can't be harvested at all
 * @param bindSuccess       a vial turned a workstation into a trader; {@code <profession>} is its profession
 * @param bindOccupied      the workstation is already a trader
 * @param captureSuccess    a villager was consumed into its workstation; {@code <profession>} is its profession
 * @param captureMismatch   the workstation isn't the villager's job site; {@code <profession>} is the villager's
 * @param captureBusy       the villager is trading with someone
 */
public record Messages(String harvestSuccess, String harvestCooldown, String harvestIneligible, String bindSuccess, String bindOccupied,
                       String captureSuccess, String captureMismatch, String captureBusy) {

    public static final Messages DEFAULT = new Messages(
            "<green>The vial fills with the villager's soul",
            "<red>This villager's soul is still recovering (<time>)",
            "<red>This villager has no soul to give",
            "<green>The <profession> workstation awakens",
            "<red>This workstation already holds a soul",
            "<green>The <profession> moves into the workstation",
            "<red>A <profession> doesn't work at this workstation",
            "<red>This villager is busy trading"
    );

    public void harvestSuccess(Audience to) {
        send(to, harvestSuccess, TagResolver.empty());
    }

    public void harvestCooldown(Audience to, long remainingTicks) {
        send(to, harvestCooldown, Placeholder.unparsed("time", formatTicks(remainingTicks)));
    }

    public void harvestIneligible(Audience to) {
        send(to, harvestIneligible, TagResolver.empty());
    }

    public void bindSuccess(Audience to, Villager.Profession profession) {
        send(to, bindSuccess, profession(profession));
    }

    public void bindOccupied(Audience to) {
        send(to, bindOccupied, TagResolver.empty());
    }

    public void captureSuccess(Audience to, Villager.Profession profession) {
        send(to, captureSuccess, profession(profession));
    }

    public void captureMismatch(Audience to, Villager.Profession profession) {
        send(to, captureMismatch, profession(profession));
    }

    public void captureBusy(Audience to) {
        send(to, captureBusy, TagResolver.empty());
    }

    private static TagResolver profession(Villager.Profession profession) {
        return Placeholder.component("profession", Component.translatable(profession.translationKey()));
    }

    private static void send(Audience to, String template, TagResolver tags) {
        if (!template.isEmpty()) {
            to.sendActionBar(MiniMessage.miniMessage().deserialize(template, tags));
        }
    }

    // 20 ticks per second, rounded up so it never shows 0s while still waiting: 1h 5m, 4m 30s, 12s
    static String formatTicks(long ticks) {
        final long total = (ticks + 19) / 20;
        final long hours = total / 3600;
        final long minutes = total % 3600 / 60;
        final long seconds = total % 60;
        final StringBuilder out = new StringBuilder();
        if (hours > 0) {
            out.append(hours).append("h ");
        }
        if (minutes > 0) {
            out.append(minutes).append("m ");
        }
        if (seconds > 0 || out.isEmpty()) {
            out.append(seconds).append('s');
        }
        return out.toString().trim();
    }
}

package sh.miles.falvtp.world.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.config.HarvestCooldown;
import sh.miles.falvtp.config.Messages;
import sh.miles.falvtp.config.SoulVialSettings;
import sh.miles.falvtp.utils.SoulVial;

public class EntityInteractListener implements Listener {

    private final SoulVialSettings settings;
    private final HarvestCooldown harvestCooldown;
    private final Messages messages;

    public EntityInteractListener(FalvtpConfig config) {
        this.settings = config.soulVial();
        this.harvestCooldown = config.harvestCooldown();
        this.messages = config.messages();
    }

    // ignoreCancelled so claim plugins that block touching entities also block harvesting
    @EventHandler(ignoreCancelled = true)
    public void onVillagerClick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack item = inventory.getItem(event.getHand());
        if (item.getType() != Material.GLASS_BOTTLE) {
            return;
        }

        event.setCancelled(true);
        if (!villager.isAdult() || villager.getProfession() == Villager.Profession.NITWIT) {
            messages.harvestIneligible(player);
            villager.getWorld().playSound(villager, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            return;
        }

        if (!harvestCooldown.isReady(villager)) {
            messages.harvestCooldown(player, harvestCooldown.remaining(villager));
            villager.getWorld().playSound(villager, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            return;
        }

        final ItemStack vial = SoulVial.mark(settings.create());
        // take only one
        if (item.getAmount() > 1) {
            item.subtract();
            player.give(vial);
        } else {
            inventory.setItem(event.getHand(), vial);
        }

        harvestCooldown.start(villager);
        messages.harvestSuccess(player);
        villager.getWorld().playSound(villager, Sound.ENTITY_VILLAGER_HURT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
    }
}

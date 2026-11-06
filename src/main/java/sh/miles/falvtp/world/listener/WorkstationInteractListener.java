package sh.miles.falvtp.world.listener;

import io.papermc.paper.math.Position;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.config.Messages;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.trader.Trader;
import sh.miles.falvtp.utils.Option;
import sh.miles.falvtp.utils.SoulVial;
import sh.miles.falvtp.utils.Workstation;
import sh.miles.falvtp.world.BlockTrader;
import sh.miles.falvtp.world.TraderServer;

public class WorkstationInteractListener implements Listener {

    private final TraderServer server;
    private final Messages messages;

    public WorkstationInteractListener(TraderServer server, FalvtpConfig config) {
        this.server = server;
        this.messages = config.messages();
    }

    // HIGH + ignoreCancelled: protection plugins decide first, and cancelling here keeps TraderInteractListener
    // (HIGHEST, ignoreCancelled) from also opening the trade menu on the same click
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWorkstationInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final Block clicked = event.getClickedBlock();
        if (event.getHand() != EquipmentSlot.HAND
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || player.getGameMode() == GameMode.SPECTATOR
            || !(Workstation.ofType(clicked.getType().asBlockType()) instanceof Workstation station)) {
            return;
        }

        final ItemStack item = player.getInventory().getItemInMainHand();
        if (!SoulVial.isSoulVial(item)) {
            return;
        }

        // the vial is being used on this block, no block menu, no trade menu
        event.setCancelled(true);
        final Location effects = clicked.getLocation().add(0.5, 1.0, 0.5);

        if (server.get(clicked) instanceof Option.Some<BlockTrader>) {
            messages.bindOccupied(player);
            clicked.getWorld().playSound(effects, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            clicked.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, effects, 5);
            return;
        }

        // the return is useless, thanks paper or spigot or bukkit or whoever the fuck W design
        item.subtract();

        final Location location = clicked.getLocation();
        final FalvtpTrader trader = FalvtpTrader.create(Trader.at(location.getWorld(), Position.block(location)), station.profession());
        server.add(new BlockTrader(location, trader));
        messages.bindSuccess(player, trader.profession());

        clicked.getWorld().playSound(effects, Sound.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.0f, 1.0f);
        clicked.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effects, 5);
    }

}

package sh.miles.falvtp.world.capture;

import io.papermc.paper.math.Position;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.config.HarvestCooldown;
import sh.miles.falvtp.config.Messages;
import sh.miles.falvtp.config.SoulVialSettings;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.trader.Trader;
import sh.miles.falvtp.utils.Option;
import sh.miles.falvtp.utils.SoulVial;
import sh.miles.falvtp.utils.Workstation;
import sh.miles.falvtp.world.BlockTrader;
import sh.miles.falvtp.world.TraderServer;

// sneak + glass bottle on a villager fills a soul vial; the vial on a plain workstation binds a new trader
public final class SoulVialCaptureFeature implements CaptureFeature {

    private final TraderServer server;
    private final SoulVialSettings settings;
    private final HarvestCooldown harvestCooldown;
    private final Messages messages;

    public SoulVialCaptureFeature(TraderServer server, FalvtpConfig config) {
        this.server = server;
        this.settings = config.soulVial();
        this.harvestCooldown = config.harvestCooldown();
        this.messages = config.messages();
    }

    @Override
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        final Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        final PlayerInventory inventory = player.getInventory();
        final ItemStack item = inventory.getItem(event.getHand());
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

    @Override
    public void onTransform(PlayerInteractEvent event) {
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

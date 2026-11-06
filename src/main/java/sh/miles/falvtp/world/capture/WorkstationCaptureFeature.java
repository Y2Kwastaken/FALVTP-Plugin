package sh.miles.falvtp.world.capture;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockType;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import sh.miles.falvtp.config.FalvtpConfig;
import sh.miles.falvtp.config.Messages;
import sh.miles.falvtp.config.WorkstationItemSettings;
import sh.miles.falvtp.trader.FalvtpTrader;
import sh.miles.falvtp.utils.Option;
import sh.miles.falvtp.utils.Workstation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

// sneak + right-click a villager with its own job site block: the villager is consumed and the block becomes a trader
// station item holding its exact trades, level and experience. Placing it is BlockPlaceListener's job
public final class WorkstationCaptureFeature implements CaptureFeature {

    // no api for Villager#lastRestockGameTime; runtime is mojang mapped
    private static final VarHandle LAST_RESTOCK_GAME_TIME;

    static {
        try {
            LAST_RESTOCK_GAME_TIME = MethodHandles.privateLookupIn(net.minecraft.world.entity.npc.villager.Villager.class, MethodHandles.lookup())
                    .findVarHandle(net.minecraft.world.entity.npc.villager.Villager.class, "lastRestockGameTime", long.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final WorkstationItemSettings items;
    private final Messages messages;

    public WorkstationCaptureFeature(FalvtpConfig config) {
        this.items = config.item();
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
        final BlockType block = item.getType().asBlockType();
        if (block == null || !(Workstation.ofType(block) instanceof Workstation station)) {
            return;
        }

        event.setCancelled(true);
        // a station that already carries trades would lose them
        if (FalvtpTrader.Snapshot.CODEC.tryRead(item.getPersistentDataContainer()) instanceof Option.Some<FalvtpTrader.Snapshot>) {
            messages.bindOccupied(player);
            deny(villager);
            return;
        }

        final Villager.Profession profession = villager.getProfession();
        if (!villager.isAdult() || profession == Villager.Profession.NONE || profession == Villager.Profession.NITWIT) {
            messages.harvestIneligible(player);
            deny(villager);
            return;
        }

        if (!profession.equals(station.profession())) {
            messages.captureMismatch(player, profession);
            deny(villager);
            return;
        }

        if (villager.isTrading()) {
            messages.captureBusy(player);
            deny(villager);
            return;
        }

        final FalvtpTrader.Snapshot snapshot = snapshot(villager);
        final ItemStack captured = items.create(station, snapshot);
        captured.editPersistentDataContainer(host -> FalvtpTrader.Snapshot.CODEC.release(snapshot, host));
        // take only one
        if (item.getAmount() > 1) {
            item.subtract();
            player.give(captured);
        } else {
            inventory.setItem(event.getHand(), captured);
        }

        final Location effects = villager.getLocation().add(0, villager.getHeight() / 2, 0);
        villager.getWorld().playSound(villager, Sound.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.0f, 1.0f);
        villager.getWorld().spawnParticle(Particle.POOF, effects, 10, 0.3, 0.5, 0.3, 0.02);
        // like a dying villager: the lead drops (unleash) and job site / bed are freed (CraftVillager#remove)
        if (villager.isLeashed()) {
            villager.setLeashHolder(null);
        }
        villager.remove();
        messages.captureSuccess(player, profession);
    }

    // placing the captured station is handled by BlockPlaceListener like any other station item
    @Override
    public void onTransform(PlayerInteractEvent event) {
    }

    private static FalvtpTrader.Snapshot snapshot(Villager villager) {
        final long lastRestock = (long) LAST_RESTOCK_GAME_TIME.get(((CraftVillager) villager).getHandle());
        // offers are copied through getters by FalvtpTrader#restore
        return new FalvtpTrader.Snapshot(
                villager.getVillagerType(),
                villager.getProfession(),
                Math.clamp(villager.getVillagerLevel(), FalvtpTrader.MIN_LEVEL, FalvtpTrader.MAX_LEVEL),
                villager.getVillagerExperience(),
                villager.getRecipes(),
                lastRestock,
                villager.getRestocksToday()
        );
    }

    private static void deny(Villager villager) {
        villager.getWorld().playSound(villager, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
    }
}

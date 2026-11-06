package sh.miles.falvtp.trader;

import com.google.common.base.Preconditions;
import io.papermc.paper.persistence.PersistentDataContainerView;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;
import sh.miles.falvtp.utils.MerchantRecipeCodec;
import sh.miles.falvtp.utils.Option;
import sh.miles.falvtp.utils.WorldCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla villager trading (levels, experience, restocks, pricing) with no entity and no owner. Reputation is replaced
 * by {@link PriceControl}; world side effects are left to the caller via {@link TradeResult}.
 */
public final class FalvtpTrader {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;

    // vanilla's new-day detection, not a balance number (tunable numbers live in TradeRules)
    private static final long HALF_DAY_TICKS = 12000L;

    private Villager.Type type;
    private Villager.Profession profession;
    private int level;
    private int experience;
    private final List<MerchantRecipe> offers;
    private long lastRestockGameTime;
    private int restocksToday;
    // not saved, matches vanilla
    private long lastRestockCheckDay;
    private PriceControl priceControl = PriceControl.HERO_OF_VILLAGE;
    private @Nullable Player customer;
    private @Nullable Runnable offersChanged;
    // whether the snapshot differs from what was last saved; special prices don't count, since every session reprices
    // before a customer sees the offers
    private boolean dirty;

    private FalvtpTrader(Villager.Type type, Villager.Profession profession, int level, List<MerchantRecipe> offers) {
        this.type = type;
        this.profession = profession;
        this.level = level;
        this.offers = offers;
        offers.replaceAll(Trades::live);
    }

    // level 1, type from the biome at the trader
    public static FalvtpTrader create(Trader trader, Villager.Profession profession) {
        return create(trader, Trades.naturalType(trader), profession);
    }

    public static FalvtpTrader create(Trader trader, Villager.Type type, Villager.Profession profession) {
        FalvtpTrader villager = new FalvtpTrader(type, profession, MIN_LEVEL, new ArrayList<>());
        // never saved yet
        villager.rollTrades(trader, -1);
        return villager;
    }

    // no trades are rolled
    public static FalvtpTrader restore(Snapshot snapshot) {
        Preconditions.checkArgument(snapshot.level() >= MIN_LEVEL && snapshot.level() <= MAX_LEVEL, "Level out of range: %s", snapshot.level());
        FalvtpTrader villager = new FalvtpTrader(snapshot.type(), snapshot.profession(), snapshot.level(), copy(snapshot.offers()));
        villager.experience = snapshot.experience();
        villager.lastRestockGameTime = snapshot.lastRestockGameTime();
        villager.restocksToday = snapshot.restocksToday();
        return villager;
    }

    // excludes customer and price control
    public Snapshot snapshot() {
        return new Snapshot(type, profession, level, experience, copy(offers), lastRestockGameTime, restocksToday);
    }

    // live list; call markDirty after changing it from outside
    public List<MerchantRecipe> offers() {
        return offers;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    // called by whatever just persisted snapshot()
    public void markSaved() {
        dirty = false;
    }

    // like Paper's Villager#addTrades, false if the profession has no trades at this level
    public boolean addTrades(Trader trader, int amount) {
        Preconditions.checkArgument(amount > 0, "Number of trades must be positive");
        return rollTrades(trader, amount);
    }

    private boolean rollTrades(Trader trader, int amount) {
        boolean rolled = Trades.roll(trader, type, profession, level, amount, offers);
        dirty = true;
        refreshSpecialPrices();
        return rolled;
    }

    public TradeResult recordTrade(Trader trader, MerchantRecipe offer, TradeRules rules) {
        return recordTrade(trader, offer, rules, true, true);
    }

    // AbstractVillager#processTrade; flags match PlayerPurchaseEvent#willIncreaseTradeUses and #isRewardingExp
    public TradeResult recordTrade(Trader trader, MerchantRecipe offer, TradeRules rules, boolean increaseUses, boolean rewardExperience) {
        if (increaseUses) {
            offer.setUses(offer.getUses() + 1);
            dirty = true;
        }
        if (!rewardExperience) {
            return TradeResult.NONE;
        }

        int orbExperience = rules.orbBase() + (rules.orbSpread() > 0 ? ThreadLocalRandom.current().nextInt(rules.orbSpread()) : 0);
        experience += offer.getVillagerExperience();
        dirty = true;
        boolean levelledUp = canLevelUpFromExperience(rules);
        if (levelledUp) {
            levelUp(trader);
            orbExperience += rules.levelUpOrbBonus();
        }

        return new TradeResult(rules.experienceOrbs() && offer.hasExperienceReward() ? orbExperience : 0, levelledUp);
    }

    // like Paper's Villager#increaseLevel, capped at MAX_LEVEL; false if already there
    public boolean increaseLevel(Trader trader, int amount) {
        Preconditions.checkArgument(amount > 0, "Level increase must be positive");
        int target = Math.clamp(level + (long) amount, MIN_LEVEL, MAX_LEVEL);
        boolean changed = target != level;
        while (level < target) {
            levelUp(trader);
        }
        return changed;
    }

    private boolean canLevelUpFromExperience(TradeRules rules) {
        return level < MAX_LEVEL && experience >= rules.experienceToLevelUp(level);
    }

    private void levelUp(Trader trader) {
        level++;
        rollTrades(trader, -1);
    }

    public int level() {
        return level;
    }

    public int experience() {
        return experience;
    }

    // never levels up by itself, like vanilla; levels are only checked on a trade
    public void setExperience(int experience) {
        Preconditions.checkArgument(experience >= 0, "Experience cannot be negative");
        this.experience = experience;
        dirty = true;
    }

    public Villager.Type type() {
        return type;
    }

    // existing offers are kept
    public void setType(Villager.Type type) {
        this.type = Preconditions.checkNotNull(type, "type");
        dirty = true;
    }

    public Villager.Profession profession() {
        return profession;
    }

    // discards offers and rolls only the current level's trades, like vanilla
    public void setProfession(Trader trader, Villager.Profession profession) {
        Preconditions.checkNotNull(profession, "profession");
        if (profession.equals(this.profession)) {
            return;
        }
        this.profession = profession;
        offers.clear();
        rollTrades(trader, -1);
    }

    // call where a vanilla villager would work at its workstation
    public boolean tryRestock(World world, TradeRules rules) {
        if (!shouldRestock(world, rules)) {
            return false;
        }
        restock(world);
        return true;
    }

    // also starts a new restock day (catching up demand) when one has begun
    public boolean shouldRestock(World world, TradeRules rules) {
        long gameTime = world.getGameTime();
        // not getFullTime(): that is the world's own clock, which the nether doesn't have (always 0)
        long currentDay = Trades.overworldDay(world);
        boolean newDay = gameTime > lastRestockGameTime + HALF_DAY_TICKS
                         || lastRestockCheckDay > 0L && currentDay > lastRestockCheckDay;
        lastRestockCheckDay = currentDay;
        if (newDay) {
            lastRestockGameTime = gameTime;
            startNewRestockDay(rules);
            dirty = true;
        }

        return isRestockAllowed(gameTime, rules) && needsRestock();
    }

    // ignores the restock rules but counts against today's limit
    public void restock(World world) {
        updateDemand();
        resetUses();
        refreshSpecialPrices();
        lastRestockGameTime = world.getGameTime();
        restocksToday++;
        dirty = true;
    }

    public int restocksToday() {
        return restocksToday;
    }

    // same as vanilla for max >= 1; checking the max first also makes 0 mean "never"
    private boolean isRestockAllowed(long gameTime, TradeRules rules) {
        return restocksToday < rules.maxRestocksPerDay()
               && (restocksToday == 0 || gameTime > lastRestockGameTime + rules.restockCooldownTicks());
    }

    private boolean needsRestock() {
        for (MerchantRecipe offer : offers) {
            if (offer.getUses() > 0) {
                return true;
            }
        }
        return false;
    }

    private void startNewRestockDay(TradeRules rules) {
        int missedRestocks = rules.maxRestocksPerDay() - restocksToday;
        if (missedRestocks > 0) {
            resetUses();
        }
        for (int i = 0; i < missedRestocks; i++) {
            updateDemand();
        }
        refreshSpecialPrices();
        restocksToday = 0;
    }

    private void resetUses() {
        for (MerchantRecipe offer : offers) {
            offer.setUses(0);
        }
    }

    // MerchantOffer#updateDemand
    private void updateDemand() {
        for (MerchantRecipe offer : offers) {
            int uses = offer.getUses();
            offer.setDemand(offer.getDemand() + uses - (offer.getMaxUses() - uses));
        }
    }

    // prices offers for the customer; offersChanged runs whenever offers change while they trade (level up, restock, reprice)
    public void startTrading(Player customer, Runnable offersChanged) {
        Preconditions.checkState(this.customer == null, "Already trading with %s", this.customer);
        this.customer = Preconditions.checkNotNull(customer, "customer");
        reprice();
        this.offersChanged = Preconditions.checkNotNull(offersChanged, "offersChanged");
    }

    // clears special prices
    public void stopTrading() {
        customer = null;
        offersChanged = null;
        reprice();
    }

    public @Nullable Player customer() {
        return customer;
    }

    public void setPriceControl(PriceControl priceControl) {
        this.priceControl = Preconditions.checkNotNull(priceControl, "priceControl");
        refreshSpecialPrices();
    }

    private void refreshSpecialPrices() {
        reprice();
        if (offersChanged != null) {
            offersChanged.run();
        }
    }

    // offers are kept live so a merchant menu shares them; added plain recipes are converted here
    private void reprice() {
        offers.replaceAll(Trades::live);
        for (MerchantRecipe offer : offers) {
            int adjustment = customer == null || offer.shouldIgnoreDiscounts() ? 0 : priceControl.adjustment(customer, offer);
            offer.setSpecialPrice(adjustment);
        }
    }

    // copies via getters: new MerchantRecipe(MerchantRecipe) reads stale fields on rolled offers
    private static List<MerchantRecipe> copy(List<MerchantRecipe> offers) {
        List<MerchantRecipe> copies = new ArrayList<>(offers.size());
        for (MerchantRecipe offer : offers) {
            MerchantRecipe copy = new MerchantRecipe(
                    offer.getResult(),
                    offer.getUses(),
                    offer.getMaxUses(),
                    offer.hasExperienceReward(),
                    offer.getVillagerExperience(),
                    offer.getPriceMultiplier(),
                    offer.getDemand(),
                    offer.getSpecialPrice(),
                    offer.shouldIgnoreDiscounts()
            );
            copy.setIngredients(offer.getIngredients());
            copies.add(copy);
        }
        return copies;
    }

    // orbExperience 0 means no orb; on a level up vanilla also gives the villager 10s of Regeneration
    public record TradeResult(int orbExperience, boolean levelledUp) {
        static final TradeResult NONE = new TradeResult(0, false);
    }

    public record Snapshot(
            Villager.Type type,
            Villager.Profession profession,
            int level,
            int experience,
            List<MerchantRecipe> offers,
            long lastRestockGameTime,
            int restocksToday
    ) {
        public static final WorldCodec<Snapshot> CODEC = new SnapshotCodec();
    }

    private static class SnapshotCodec implements WorldCodec<Snapshot> {
        private static final NamespacedKey VILLAGER_TYPE = NamespacedKey.fromString("falvtp:villager_type");
        private static final NamespacedKey PROFESSION = NamespacedKey.fromString("falvtp:profession");
        private static final NamespacedKey LEVEL = NamespacedKey.fromString("falvtp:level");
        private static final NamespacedKey EXPERIENCE = NamespacedKey.fromString("falvtp:experience");
        private static final NamespacedKey OFFERS = NamespacedKey.fromString("falvtp:offers");
        private static final NamespacedKey LAST_RESTOCK_GAME_TIME = NamespacedKey.fromString("falvtp:last_restock_game_time");
        private static final NamespacedKey RESTOCKS_TODAY = NamespacedKey.fromString("falvtp:restocks_today");

        @Override
        public Option<Snapshot> tryRead(final PersistentDataContainerView host) {
            if (!(registryValue(host, VILLAGER_TYPE, RegistryKey.VILLAGER_TYPE) instanceof Option.Some<Villager.Type>(Villager.Type type))
                || !(registryValue(host, PROFESSION, RegistryKey.VILLAGER_PROFESSION) instanceof Option.Some<Villager.Profession>(Villager.Profession profession))) {
                return Option.none();
            }

            final List<PersistentDataContainer> offerContainers = host.getOrDefault(OFFERS, PersistentDataType.LIST.dataContainers(), List.of());
            final List<MerchantRecipe> offers = new ArrayList<>(offerContainers.size());
            for (final PersistentDataContainer offerContainer : offerContainers) {
                offers.add(MerchantRecipeCodec.INSTANCE.read(offerContainer));
            }

            // restore() rejects these, so treat them as no snapshot rather than failing later
            final int level = host.getOrDefault(LEVEL, PersistentDataType.INTEGER, MIN_LEVEL);
            if (level < MIN_LEVEL || level > MAX_LEVEL) {
                return Option.none();
            }

            return Option.some(new Snapshot(
                    type,
                    profession,
                    level,
                    host.getOrDefault(EXPERIENCE, PersistentDataType.INTEGER, 0),
                    offers,
                    host.getOrDefault(LAST_RESTOCK_GAME_TIME, PersistentDataType.LONG, 0L),
                    host.getOrDefault(RESTOCKS_TODAY, PersistentDataType.INTEGER, 0)
            ));
        }

        @Override
        public void release(final Snapshot obj, final PersistentDataContainer host) {
            final PersistentDataAdapterContext context = host.getAdapterContext();
            final List<PersistentDataContainer> offerContainers = new ArrayList<>(obj.offers().size());
            for (final MerchantRecipe offer : obj.offers()) {
                final PersistentDataContainer offerContainer = context.newPersistentDataContainer();
                MerchantRecipeCodec.INSTANCE.release(offer, offerContainer);
                offerContainers.add(offerContainer);
            }

            host.set(VILLAGER_TYPE, PersistentDataType.STRING, obj.type().key().asString());
            host.set(PROFESSION, PersistentDataType.STRING, obj.profession().key().asString());
            host.set(LEVEL, PersistentDataType.INTEGER, obj.level());
            host.set(EXPERIENCE, PersistentDataType.INTEGER, obj.experience());
            host.set(OFFERS, PersistentDataType.LIST.dataContainers(), offerContainers);
            host.set(LAST_RESTOCK_GAME_TIME, PersistentDataType.LONG, obj.lastRestockGameTime());
            host.set(RESTOCKS_TODAY, PersistentDataType.INTEGER, obj.restocksToday());
        }

        // none if the key is missing, malformed or not in the registry
        private static <T extends Keyed> Option<T> registryValue(final PersistentDataContainerView host, final NamespacedKey key, final RegistryKey<T> registry) {
            final String id = host.get(key, PersistentDataType.STRING);
            final NamespacedKey valueKey = id == null ? null : NamespacedKey.fromString(id);
            if (valueKey == null) {
                return Option.none();
            }
            return Option.ofNullable(RegistryAccess.registryAccess().getRegistry(registry).get(valueKey));
        }
    }
}

package sh.miles.falvtp.trader;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import io.papermc.paper.math.Position;
import net.minecraft.advancements.predicates.entity.EntityPartialComponentsPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.predicates.VillagerTypePredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timelines;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.slf4j.Logger;
import sh.miles.falvtp.utils.Option;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class Trades {

    private static final Logger LOGGER = LogUtils.getLogger();

    // villager_trade without this entity
    private static final ContextKeySet TRADE_CTX = new ContextKeySet.Builder()
            .required(LootContextParams.ORIGIN)
            .required(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED)
            .build();

    // villager trades to "gated" trades where we've already lifted out entity checks
    private static final Map<VillagerTrade, Gated> GATES = new ConcurrentHashMap<>();

    private Trades() {
    }

    // a recipe backed by an NMS offer, so a merchant menu shares its uses, demand and price instead of copying them
    static MerchantRecipe live(MerchantRecipe recipe) {
        return CraftMerchantRecipe.fromBukkit(recipe);
    }

    // natural villager type for the biome at the trader
    static Villager.Type naturalType(Trader trader) {
        final ServerLevel level = ((CraftWorld) trader.world()).getHandle();
        final Position pos = trader.pos();
        final Holder<Biome> biome = level.getBiome(new BlockPos(pos.blockX(), pos.blockY(), pos.blockZ()));
        return CraftVillager.CraftType.minecraftHolderToBukkit(level.registryAccess().getOrThrow(VillagerType.byBiome(biome)));
    }

    // the day count vanilla villagers use (Villager#shouldRestock): the overworld clock, even in other dimensions
    static long overworldDay(World world) {
        final ServerLevel level = ((CraftWorld) world).getHandle();
        return level.registryAccess().get(Timelines.OVERWORLD_DAY)
                .map(timeline -> timeline.value().getPeriodCount(level.clockManager()))
                .orElse(0);
    }

    // whether a villager standing here would be working (the villager_activity schedule, as Brain#updateActivityFromSchedule reads it)
    static boolean isWorkTime(Trader trader) {
        final ServerLevel level = ((CraftWorld) trader.world()).getHandle();
        final Position pos = trader.pos();
        final Vec3 at = new Vec3(pos.blockX() + 0.5, pos.blockY(), pos.blockZ() + 0.5);
        return level.environmentAttributes().getValue(EnvironmentAttributes.VILLAGER_ACTIVITY, at) == Activity.WORK;
    }

    // count -1 uses the trade set amount; false if the profession has no trades at this level
    static boolean roll(Trader trader, Villager.Type type, Villager.Profession profession, int level, int count, List<MerchantRecipe> offers) {
        final ResourceKey<TradeSet> key = CraftVillager.CraftProfession.bukkitToMinecraftHolder(profession).value().getTrades(level);
        if (key == null) {
            return false;
        }

        final ServerLevel world = ((CraftWorld) trader.world()).getHandle();
        final Position pos = trader.pos();
        final Holder<VillagerType> nmsType = CraftVillager.CraftType.bukkitToMinecraftHolder(type);

        final Optional<TradeSet> found = world.registryAccess().lookupOrThrow(Registries.TRADE_SET).getOptional(key);
        if (found.isEmpty()) {
            LOGGER.debug("Missing expected trade set {}", key);
            return false;
        }

        final TradeSet tradeSet = found.get();
        final LootContext context = new LootContext.Builder(
                new LootParams.Builder(world)
                        .withParameter(LootContextParams.ORIGIN, new Vec3(pos.x(), pos.y(), pos.z()))
                        .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                        .create(TRADE_CTX)
        ).create(tradeSet.randomSequence());

        final int wanted = count != -1 ? count : tradeSet.calculateNumberOfTrades(context);
        if (tradeSet.allowDuplicates()) {
            addOffersFromItemListings(context, nmsType, offers, tradeSet.trades(), wanted);
        } else {
            addOffersFromItemListingsWithoutDuplicates(context, nmsType, offers, tradeSet.trades(), wanted);
        }
        return true;
    }

    // copied from AbstractVillager
    private static void addOffersFromItemListingsWithoutDuplicates(
            final LootContext context,
            final Holder<VillagerType> type,
            final List<MerchantRecipe> offers,
            final HolderSet<VillagerTrade> potentialOffers,
            final int wanted
    ) {
        final List<Holder<VillagerTrade>> leftover = Lists.newArrayList(potentialOffers);
        int found = 0;

        while (found < wanted && !leftover.isEmpty()) {
            final Holder<VillagerTrade> trade = leftover.remove(context.getRandom().nextInt(leftover.size()));
            if (offer(trade, type, context) instanceof Option.Some<MerchantOffer>(MerchantOffer offer)) {
                offers.add(offer.asBukkit());
                found++;
            }
        }
    }

    // copied from AbstractVillager
    private static void addOffersFromItemListings(
            final LootContext context,
            final Holder<VillagerType> type,
            final List<MerchantRecipe> offers,
            final HolderSet<VillagerTrade> potentialOffers,
            final int wanted
    ) {
        final List<Holder<VillagerTrade>> potential = Lists.newArrayList(potentialOffers);
        int found = 0;

        while (found < wanted && !potential.isEmpty()) {
            final int roll = context.getRandom().nextInt(potential.size());
            if (offer(potential.get(roll), type, context) instanceof Option.Some<MerchantOffer>(MerchantOffer offer)) {
                offers.add(offer.asBukkit());
                found++;
            } else {
                potential.remove(roll);
            }
        }
    }

    private static Option<MerchantOffer> offer(Holder<VillagerTrade> holder, Holder<VillagerType> type, LootContext context) {
        final Gated gated = GATES.computeIfAbsent(holder.value(), Trades::gate);
        if (gated.types() instanceof Option.Some<HolderSet<VillagerType>>(var types) && !types.contains(type)) {
            return Option.none();
        }
        return Option.ofNullable(gated.trade().getOffer(context));
    }

    // lift out trades with villager type check predicate
    private static Gated gate(VillagerTrade trade) {
        if (TradeAccess.merchantPredicate(trade).map(Holder::value).orElse(null)
                        instanceof LootItemEntityPropertyCondition(Optional<EntityPredicate> entity, var target)
                && target == LootContext.EntityTarget.THIS
                && entity.isPresent()
                && TradeAccess.parts(entity.get()) instanceof Collection<?> parts && parts.size() == 1
                && parts.iterator().next() instanceof EntityPartialComponentsPredicate(var components) && components.size() == 1
                && components.values().iterator().next() instanceof VillagerTypePredicate(HolderSet<VillagerType> types)) {
            return new Gated(TradeAccess.withoutMerchantPredicate(trade), Option.some(types));
        }
        return new Gated(trade, Option.none());
    }

    // object that just joins what things we can trade with already without the entity
    private record Gated(VillagerTrade trade, Option<HolderSet<VillagerType>> types) {
    }
}

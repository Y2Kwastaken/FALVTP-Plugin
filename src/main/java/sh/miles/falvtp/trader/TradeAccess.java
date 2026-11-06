package sh.miles.falvtp.trader;

import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

final class TradeAccess {

    private static final Field WANTS = field(VillagerTrade.class, "wants", TradeCost.class);
    private static final Field ADDITIONAL_WANTS = field(VillagerTrade.class, "additionalWants", Optional.class);
    private static final Field GIVES = field(VillagerTrade.class, "gives", ItemStackTemplate.class);
    private static final Field MAX_USES = field(VillagerTrade.class, "maxUses", Holder.class);
    private static final Field XP = field(VillagerTrade.class, "xp", Holder.class);
    private static final Field REPUTATION_DISCOUNT = field(VillagerTrade.class, "reputationDiscount", Holder.class);
    private static final Field MERCHANT_PREDICATE = field(VillagerTrade.class, "merchantPredicate", Optional.class);
    private static final Field GIVEN_ITEM_MODIFIER = field(VillagerTrade.class, "givenItemModifier", Optional.class);
    private static final Field DOUBLE_TRADE_PRICE_ENCHANTMENTS = field(VillagerTrade.class, "doubleTradePriceEnchantments", Optional.class);
    private static final Constructor<VillagerTrade> CONSTRUCTOR = constructor(
            VillagerTrade.class,
            TradeCost.class,          // wants
            Optional.class,           // additionalWants
            ItemStackTemplate.class,  // gives
            Holder.class,             // maxUses
            Holder.class,             // xp
            Holder.class,             // reputationDiscount
            Optional.class,           // merchantPredicate
            Optional.class,           // givenItemModifier
            Optional.class            // doubleTradePriceEnchantments
    );

    private static final Field ENTITY_PREDICATE_PARTS = field(EntityPredicate.class, "parts", Map.class);

    @SuppressWarnings("unchecked")
    static Optional<Holder<LootItemCondition>> merchantPredicate(VillagerTrade trade) {
        return (Optional<Holder<LootItemCondition>>) read(MERCHANT_PREDICATE, trade);
    }

    static Collection<?> parts(EntityPredicate predicate) {
        return ((Map<?, ?>) read(ENTITY_PREDICATE_PARTS, predicate)).values();
    }

    static VillagerTrade withoutMerchantPredicate(VillagerTrade trade) {
        try {
            return CONSTRUCTOR.newInstance(
                    read(WANTS, trade),
                    read(ADDITIONAL_WANTS, trade),
                    read(GIVES, trade),
                    read(MAX_USES, trade),
                    read(XP, trade),
                    read(REPUTATION_DISCOUNT, trade),
                    Optional.empty(),
                    read(GIVEN_ITEM_MODIFIER, trade),
                    read(DOUBLE_TRADE_PRICE_ENCHANTMENTS, trade)
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not copy villager trade", exception);
        }
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not read " + field, exception);
        }
    }

    private static Field field(Class<?> owner, String name, Class<?> type) {
        try {
            final Field field = owner.getDeclaredField(name);
            if (field.getType() != type) {
                throw new IllegalStateException(owner.getName() + "." + name + " is no longer of type, " + type.getName());
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing field " + owner.getName() + "." + name, e);
        }
    }

    private static <T> Constructor<T> constructor(Class<T> owner, Class<?>... parameterTypes) {
        try {
            Constructor<T> constructor = owner.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Missing constructor on " + owner.getName(), exception);
        }
    }
}

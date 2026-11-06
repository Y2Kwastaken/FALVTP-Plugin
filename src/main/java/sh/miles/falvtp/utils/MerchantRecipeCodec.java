package sh.miles.falvtp.utils;

import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public final class MerchantRecipeCodec implements WorldCodec<MerchantRecipe> {
    public static final MerchantRecipeCodec INSTANCE = new MerchantRecipeCodec();

    private static final NamespacedKey RESULT = NamespacedKey.fromString("falvtp:result");
    private static final NamespacedKey INGREDIENTS = NamespacedKey.fromString("falvtp:ingredients");
    private static final NamespacedKey USES = NamespacedKey.fromString("falvtp:uses");
    private static final NamespacedKey MAX_USES = NamespacedKey.fromString("falvtp:max_uses");
    private static final NamespacedKey EXPERIENCE_REWARD = NamespacedKey.fromString("falvtp:experience_reward");
    private static final NamespacedKey VILLAGER_EXPERIENCE = NamespacedKey.fromString("falvtp:villager_experience");
    private static final NamespacedKey PRICE_MULTIPLIER = NamespacedKey.fromString("falvtp:price_multiplier");
    private static final NamespacedKey DEMAND = NamespacedKey.fromString("falvtp:demand");
    private static final NamespacedKey SPECIAL_PRICE = NamespacedKey.fromString("falvtp:special_price");
    private static final NamespacedKey IGNORE_DISCOUNTS = NamespacedKey.fromString("falvtp:ignore_discounts");

    private MerchantRecipeCodec() {
    }

    @Override
    public Option<MerchantRecipe> tryRead(final PersistentDataContainerView host) {
        final byte[] result = host.get(RESULT, PersistentDataType.BYTE_ARRAY);
        if (result == null) {
            return Option.none();
        }
        final MerchantRecipe recipe = new MerchantRecipe(
                ItemStack.deserializeBytes(result),
                host.getOrDefault(USES, PersistentDataType.INTEGER, 0),
                host.getOrDefault(MAX_USES, PersistentDataType.INTEGER, 0),
                host.getOrDefault(EXPERIENCE_REWARD, PersistentDataType.BOOLEAN, false),
                host.getOrDefault(VILLAGER_EXPERIENCE, PersistentDataType.INTEGER, 0),
                host.getOrDefault(PRICE_MULTIPLIER, PersistentDataType.FLOAT, 0F),
                host.getOrDefault(DEMAND, PersistentDataType.INTEGER, 0),
                host.getOrDefault(SPECIAL_PRICE, PersistentDataType.INTEGER, 0),
                host.getOrDefault(IGNORE_DISCOUNTS, PersistentDataType.BOOLEAN, false)
        );

        final byte[] ingredients = host.get(INGREDIENTS, PersistentDataType.BYTE_ARRAY);
        if (ingredients != null) {
            recipe.setIngredients(Arrays.asList(ItemStack.deserializeItemsFromBytes(ingredients)));
        }
        return Option.some(recipe);
    }

    @Override
    public void release(final MerchantRecipe obj, final PersistentDataContainer host) {
        host.set(RESULT, PersistentDataType.BYTE_ARRAY, obj.getResult().serializeAsBytes());
        host.set(INGREDIENTS, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(obj.getIngredients()));
        host.set(USES, PersistentDataType.INTEGER, obj.getUses());
        host.set(MAX_USES, PersistentDataType.INTEGER, obj.getMaxUses());
        host.set(EXPERIENCE_REWARD, PersistentDataType.BOOLEAN, obj.hasExperienceReward());
        host.set(VILLAGER_EXPERIENCE, PersistentDataType.INTEGER, obj.getVillagerExperience());
        host.set(PRICE_MULTIPLIER, PersistentDataType.FLOAT, obj.getPriceMultiplier());
        host.set(DEMAND, PersistentDataType.INTEGER, obj.getDemand());
        host.set(SPECIAL_PRICE, PersistentDataType.INTEGER, obj.getSpecialPrice());
        host.set(IGNORE_DISCOUNTS, PersistentDataType.BOOLEAN, obj.shouldIgnoreDiscounts());
    }
}

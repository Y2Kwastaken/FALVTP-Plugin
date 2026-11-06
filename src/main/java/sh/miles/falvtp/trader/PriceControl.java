package sh.miles.falvtp.trader;

import org.bukkit.entity.Player;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public interface PriceControl {

    PriceControl NONE = (customer, offer) -> 0;
    // copied from vanilla hero of village logic
    PriceControl HERO_OF_VILLAGE = (customer, offer) -> {
        PotionEffect hero = customer.getPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
        if (hero == null) {
            return 0;
        }

        double discount = 0.3F + 0.0625F * hero.getAmplifier();
        int reduction = (int) Math.floor(discount * offer.getIngredients().getFirst().getAmount());
        return -Math.max(reduction, 1);
    };

    /**
     * Computes the change to an offer's first cost for this customer.
     *
     * @param customer the player trading
     * @param offer    the offer being priced
     * @return the amount added to the offer's special price where negative makes it cheaper
     */
    int adjustment(Player customer, MerchantRecipe offer);

    /**
     * Composition of this price control and another one
     *
     * @param other the other control to compose
     * @return the composed controls
     */
    default PriceControl and(PriceControl other) {
        return (customer, offer) -> adjustment(customer, offer) + other.adjustment(customer, offer);
    }
}

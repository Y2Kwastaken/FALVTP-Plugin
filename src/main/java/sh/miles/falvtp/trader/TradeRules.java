package sh.miles.falvtp.trader;

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Server-wide trading numbers, passed into {@link FalvtpTrader} calls rather than stored on each trader.
 *
 * @param maxRestocksPerDay    restocks allowed per day; 0 disables restocking
 * @param restockCooldownTicks minimum game ticks between two restocks on the same day
 * @param levelUpExperience    experience needed to reach levels 2 to 5, strictly ascending
 * @param experienceOrbs       whether trades drop experience orbs for the player at all
 * @param orbBase              orb experience per trade before the random spread
 * @param orbSpread            orb experience is {@code orbBase + random(0 until orbSpread)}
 * @param levelUpOrbBonus      extra orb experience when a trade levels the trader up
 */
public record TradeRules(
        int maxRestocksPerDay,
        long restockCooldownTicks,
        List<Integer> levelUpExperience,
        boolean experienceOrbs,
        int orbBase,
        int orbSpread,
        int levelUpOrbBonus
) {
    // copied from vanilla villagers
    public static final TradeRules VANILLA = new TradeRules(2, 2400L, List.of(10, 70, 150, 250), true, 3, 4, 5);

    public TradeRules {
        levelUpExperience = List.copyOf(levelUpExperience);
        Preconditions.checkArgument(levelUpExperience.size() == FalvtpTrader.MAX_LEVEL - FalvtpTrader.MIN_LEVEL,
                "levelUpExperience needs %s entries, got %s", FalvtpTrader.MAX_LEVEL - FalvtpTrader.MIN_LEVEL, levelUpExperience.size());
        Preconditions.checkArgument(isStrictlyAscending(levelUpExperience), "levelUpExperience must be positive and strictly ascending: %s", levelUpExperience);
        Preconditions.checkArgument(maxRestocksPerDay >= 0, "maxRestocksPerDay cannot be negative");
        Preconditions.checkArgument(restockCooldownTicks >= 0, "restockCooldownTicks cannot be negative");
        Preconditions.checkArgument(orbBase >= 0 && orbSpread >= 0 && levelUpOrbBonus >= 0, "orb experience cannot be negative");
    }

    // experience needed to go from level to level + 1; 0 at the max level
    public int experienceToLevelUp(int level) {
        return level < FalvtpTrader.MAX_LEVEL ? levelUpExperience.get(level - FalvtpTrader.MIN_LEVEL) : 0;
    }

    public static boolean isStrictlyAscending(List<Integer> values) {
        int previous = 0;
        for (final int value : values) {
            if (value <= previous) {
                return false;
            }
            previous = value;
        }
        return true;
    }
}

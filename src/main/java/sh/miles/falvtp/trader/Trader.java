package sh.miles.falvtp.trader;

import io.papermc.paper.math.Position;
import org.bukkit.World;
import org.bukkit.block.Biome;

public interface Trader {
    World world();

    Position pos();

    // a bare location, for rolling a trader before whatever will hold it exists
    static Trader at(World world, Position pos) {
        return new At(world, pos.toBlock());
    }

    record At(World world, Position pos) implements Trader {
    }

    default Biome biome() {
        Position pos = pos();
        return world().getBiome(pos.blockX(), pos.blockY(), pos.blockZ());
    }

    // vanilla villagers only restock during their work hours
    default boolean isWorkTime() {
        return Trades.isWorkTime(this);
    }
}

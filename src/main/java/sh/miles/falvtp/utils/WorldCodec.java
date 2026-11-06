package sh.miles.falvtp.utils;

import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataContainer;

// stores data in PDC
public interface WorldCodec<T> {

    // none when host holds no (valid) T
    Option<T> tryRead(PersistentDataContainerView host);

    // for data that must be there, throws otherwise
    default T read(PersistentDataContainerView host) {
        if (tryRead(host) instanceof Option.Some<T>(T value)) {
            return value;
        }
        throw new IllegalArgumentException("Container does not hold a valid value for " + getClass().getSimpleName());
    }

    void release(T obj, PersistentDataContainer host);
}

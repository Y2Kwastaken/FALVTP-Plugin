package sh.miles.falvtp.utils;

import org.jspecify.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A value that is either present ({@link Some}) or absent ({@link None}).
 * <p>
 * The interface is sealed, so callers can branch with pattern matching instead of presence checks:
 * <pre>{@code
 * if (option instanceof Option.Some(var value)) {
 *     use(value);
 * }
 *
 * String label = switch (option) {
 *     case Option.Some(var value) -> value.toString();
 *     case Option.None<?> none -> "nothing";
 * };
 * }</pre>
 *
 * @param <T> the type of the value
 */
public sealed interface Option<T> permits Option.Some, Option.None {

    /**
     * A present value.
     *
     * @param value the value, never null
     * @param <T>   the type of the value
     */
    record Some<T>(T value) implements Option<T> {

        public Some {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * An absent value. All instances are equal; {@link Option#none()} returns a shared one.
     *
     * @param <T> the type the value would have had
     */
    record None<T>() implements Option<T> {

        private static final None<?> INSTANCE = new None<>();
    }

    /**
     * @param value the value, never null
     * @param <T>   the type of the value
     * @return a present option holding {@code value}
     */
    static <T> Option<T> some(T value) {
        return new Some<>(value);
    }

    /**
     * @param <T> the type the value would have had
     * @return the shared absent option
     */
    @SuppressWarnings("unchecked")
    static <T> Option<T> none() {
        return (Option<T>) None.INSTANCE;
    }

    /**
     * @param value a value that may be null
     * @param <T>   the type of the value
     * @return {@link Some} if {@code value} is non-null, otherwise {@link None}
     */
    static <T> Option<T> ofNullable(@Nullable T value) {
        return value == null ? none() : some(value);
    }

    /**
     * @param optional the optional to convert
     * @param <T>      the type of the value
     * @return an option with the same contents
     */
    static <T> Option<T> fromOptional(Optional<? extends T> optional) {
        return optional.isPresent() ? some(optional.get()) : none();
    }

    /**
     * @param mapper the function to apply to the value; a null result becomes {@link None}
     * @param <R>    the type of the mapped value
     * @return the mapped option
     */
    default <R> Option<R> map(Function<? super T, ? extends @Nullable R> mapper) {
        return switch (this) {
            case Some<T>(T value) -> ofNullable(mapper.apply(value));
            case None<T> ignored -> none();
        };
    }

    default <R> Option<R> flatMap(Function<? super T, Option<R>> mapper) {
        return switch (this) {
            case Some<T>(T value) -> mapper.apply(value);
            case None<T> ignored -> none();
        };
    }

    default Option<T> filter(Predicate<? super T> predicate) {
        return this instanceof Some<T>(T value) && !predicate.test(value) ? none() : this;
    }

    default T orElse(T fallback) {
        return this instanceof Some<T>(T value) ? value : fallback;
    }

    default T orElseGet(Supplier<? extends T> fallback) {
        return this instanceof Some<T>(T value) ? value : fallback.get();
    }

    /**
     * @return the value
     * @throws NoSuchElementException if this is {@link None}
     */
    default T orElseThrow() {
        if (this instanceof Some<T>(T value)) {
            return value;
        }
        throw new NoSuchElementException("Option is None");
    }

    default void ifSome(Consumer<? super T> action) {
        if (this instanceof Some<T>(T value)) {
            action.accept(value);
        }
    }

    default Optional<T> toOptional() {
        return this instanceof Some<T>(T value) ? Optional.of(value) : Optional.empty();
    }
}

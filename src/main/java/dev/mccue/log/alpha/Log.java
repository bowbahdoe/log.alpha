package dev.mccue.log.alpha;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dev.mccue.log.alpha.Globals.GLOBAL_CONTEXT;
import static dev.mccue.log.alpha.Globals.LOCAL_CONTEXT;

public sealed interface Log extends Iterable<Log.Entry> {

    Context context();

    Thread thread();

    Flake flake();

    Occurrence occurrence();

    Level level();

    Category category();

    List<Entry> entries();

    record Event(@Override Context context,
                 @Override Thread thread,
                 @Override Flake flake,
                 @Override Occurrence.PointInTime occurrence,
                 @Override Level level,
                 @Override Category category,
                 @Override List<Entry> entries) implements Log {
        public Event(
                Context context,
                Thread thread,
                Flake flake,
                Occurrence.PointInTime occurrence,
                Level level,
                Category category,
                List<Entry> entries
        ) {
            this.context = Objects.requireNonNull(context, "context must not be null");
            this.thread = Objects.requireNonNull(thread, "thread must not be null");
            this.flake = Objects.requireNonNull(flake, "flake must not be null");
            this.occurrence = Objects.requireNonNull(occurrence, "startedAt must not be null");
            this.level = Objects.requireNonNull(level, "level must not be null");
            this.category = Objects.requireNonNull(category, "category must not be null");
            this.entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
        }

        public Event(
                Level level,
                Category category,
                List<Entry> entries
        ) {
            this(Context.current(), Thread.currentThread(), Flake.create(), new Occurrence.PointInTime(Instant.now()), level, category, entries);
        }
    }

    record Span(
            @Override Context context,
            @Override Thread thread,
            @Override Flake flake,
            Outcome outcome,
            @Override Occurrence.SpanOfTime occurrence,
            @Override Level level,
            @Override Category category,
            @Override List<Entry> entries) implements Log {
        public sealed interface Outcome {
            enum Ok implements Outcome {
                INSTANCE;

                @Override
                public String toString() {
                    return "Ok";
                }
            }

            record Error(Throwable throwable) implements Outcome {
            }
        }

        public Span(
                Context context,
                Thread thread,
                Flake flake,
                Outcome outcome,
                Occurrence.SpanOfTime occurrence,
                Level level,
                Category category,
                List<Entry> entries
        ) {
            this.context = Objects.requireNonNull(context, "context must not be null");
            this.thread = Objects.requireNonNull(thread, "thread must not be null");
            this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
            this.flake = Objects.requireNonNull(flake, "flake must not be null");
            this.occurrence = Objects.requireNonNull(occurrence, "startedAt must not be null");
            this.level = Objects.requireNonNull(level, "level must not be null");
            this.category = Objects.requireNonNull(category, "category must not be null");
            this.entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
        }

        public Span(
                Outcome outcome,
                Occurrence.SpanOfTime occurrence,
                Level level,
                Category category,
                List<Entry> entries
        ) {
            this(Context.current(), Thread.currentThread(), Flake.create(), outcome, occurrence, level, category, entries);
        }
    }

    sealed interface Occurrence {
        record PointInTime(Instant happenedAt) implements Occurrence {
        }

        record SpanOfTime(Instant startedAt, java.time.Duration lasted) implements Occurrence {
        }
    }

    /**
     * Takes a list of log entries and executes a block of code where those entries will
     * be added to any logs.
     *
     * <p>Context is not propagated across threads at this time.</p>
     * <p>
     * {@snippet :
     * var response = Log.withContext(
     *         List.of(
     *                 Log.Entry.of("request-id", UUID.randomUUID()),
     *                 Log.Entry.of("user-id", userId)
     *         ),
     *         () -> {
     *             var user = lookupUser(userId);
     *             if (user == null) {
     *                 // Will have the context of the request being made;
     *                 log.info("no-user-found");
     *                 return fourOhFour();
     *             }
     *             else {
     *                 log.info("found-user", Log.Entry.of("name", user.name()));
     *                 return success();
     *             }
     *         }
     * );
     *}
     *
     * <p>If the block of code being executed contains checked exceptions, then the two
     * options available are to either re-throw as runtime exceptions or wrap them into
     * the return value.</p>
     *
     * <p> The first option will look something like this. </p>
     * {@snippet :
     * var favoriteNumber = Log.withContext(
     *     List.of(Log.Entry.of("person", "bob")),
     *     () -> {
     *         try {
     *             return Integer.parseInt(Files.readString(Path.of("favorite.txt")));
     *         }
     *         catch (IOException e) {
     *             throw new UncheckedIOException(e);
     *         }
     *     }
     * );
     *}
     *
     * <p> And the second something like this. </p>
     * <p>
     * {@snippet :
     * sealed interface FavoriteNumberResult {
     *     record GotNumber(int number) implements FavoriteNumberResult {}
     *     record FailedToReadFile(IOException e) implements FavoriteNumberResult {}
     * }
     *
     * var result = Log.withContext(
     *     List.of(Log.Entry.of("person", "bob")),
     *     () -> {
     *         try {
     *             return Integer.parseInt(Files.readString(Path.of("favorite.txt")));
     *         }
     *         catch (IOException e) {
     *             throw new UncheckedIOException(e);
     *         }
     *     }
     * );
     *
     * if (result instanceof FavoriteNumberResult.FailedToReadFile failedToReadFile) {
     *     throw failedToReadFile.e();
     * }
     *}
     *
     * <p>While the second is more verbose and quite unsavory without destructuring pattern match, it does let you
     * bubble exceptions unaltered if that context matters for the purposes of your computation.</p>
     *
     * @param entries The log entries to add
     * @param code    The block of code to execute.
     * @param <T>     The type returned by the block of code.
     * @return The result of the execution of the block of code.
     */
    static <T> T withContext(List<Entry> entries, Supplier<T> code) {
        var localContext = LOCAL_CONTEXT.get();
        try {
            LOCAL_CONTEXT.set(new Context.Child.Plain(
                    entries,
                    localContext == null ? GLOBAL_CONTEXT.get() : localContext
            ));
            return code.get();
        } finally {
            LOCAL_CONTEXT.set(localContext);
        }
    }

    /**
     * Variant of withContext that doesn't produce a value.
     *
     * @param entries The log entries to add.
     * @param code    The block of code to execute.
     * @see Log#withContext(List, Supplier)
     */
    static void withContext(List<Entry> entries, Runnable code) throws Exception {
        withContext(
                entries,
                () -> {
                    code.run();
                    return null;
                });
    }

    /**
     * Sets the global context for the program.
     *
     * <p>The intent is for this to be used for truly global things such as
     * machine info or info about the running app.</p>
     * <p>
     * {@snippet :
     * Log.setGlobalContext(
     *     Log.Entry.of("app-name", "demo-app"),
     *     Log.Entry.of("version", "0.0.1")
     * );
     *}
     *
     * <p>When used inside of a block of code running in {@link Log#withContext(List, Supplier)}
     * or in parallel to code running in such a context, the global context of those already running
     * scopes will not be affected. In that sense it is "safe" to call from multiple threads.</p>
     *
     * @param entries The log entries to add to the global context.
     */
    static void setGlobalContext(List<Entry> entries) {
        GLOBAL_CONTEXT.set(new Context.Global(entries));
    }

    /**
     * @return An Iterator over all the entries in the log, including entries in parent contexts.
     */
    @Override
    default Iterator<Entry> iterator() {
        return new Iterator<>() {
            Iterator<Entry> iter = entries().iterator();
            Context ctx = context();

            @Override
            public boolean hasNext() {
                if (iter.hasNext()) {
                    return true;
                } else {
                    if (ctx instanceof Context.Child childCtx) {
                        iter = childCtx.entries().iterator();
                        ctx = childCtx.parent();
                        return this.hasNext();
                    } else if (ctx instanceof Context.Global globalCtx) {
                        iter = globalCtx.entries().iterator();
                        ctx = null;
                        return this.hasNext();
                    } else {
                        return false;
                    }
                }
            }

            @Override
            public Entry next() {
                if (iter.hasNext()) {
                    return iter.next();
                } else {
                    if (ctx instanceof Context.Child childCtx) {
                        iter = childCtx.entries().iterator();
                        ctx = childCtx.parent();
                        return this.next();
                    } else if (ctx instanceof Context.Global globalCtx) {
                        iter = globalCtx.entries().iterator();
                        ctx = null;
                        return this.next();
                    } else {
                        throw new NoSuchElementException();
                    }
                }
            }
        };
    }

    enum Level {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    sealed interface Context {
        static Context current() {
            var localContext = LOCAL_CONTEXT.get();
            return localContext == null ? GLOBAL_CONTEXT.get() : localContext;
        }

        Optional<Child.Span> parentSpan();
        default Optional<Child.Span> rootSpan() {
            return this.parentSpan()
                    .map(parent -> parent.rootSpan().orElse(parent));
        }

        record Global(List<Entry> entries) implements Context {
            public Global(List<Entry> entries) {
                this.entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
            }

            @Override
            public Optional<Child.Span> parentSpan() {
                return Optional.empty();
            }
        }

        sealed interface Child extends Context {
            List<Entry> entries();
            Context parent();

            @Override
            default Optional<Span> parentSpan() {
                var parent = parent();
                if (parent instanceof Span parentSpan) {
                    return Optional.of(parentSpan);
                }
                else {
                    return parent.parentSpan();
                }
            }

            record Plain(
                    @Override List<Entry> entries,
                    @Override Context parent
            ) implements Child {
                public Plain(
                        List<Entry> entries,
                        Context parent
                ) {
                    this.entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
                    this.parent = Objects.requireNonNull(parent, "parent must not be null");
                }
            }

            record Span(
                    Thread thread,
                    Instant startedAt,
                    Flake spanId,
                    @Override List<Entry> entries,
                    @Override Context parent
            ) implements Child {
                public Span(
                        Thread thread,
                        Instant startedAt,
                        Flake spanId,
                        List<Entry> entries,
                        Context parent
                ) {
                    this.thread = Objects.requireNonNull(thread, "thread must not be null");
                    this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
                    this.spanId = Objects.requireNonNull(spanId, "spanId must not be null");
                    this.entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
                    this.parent = Objects.requireNonNull(parent, "parent must not be null");
                }
            }
        }
    }

    record Category(String namespace, String name) {
        public Category {
            Objects.requireNonNull(namespace, "namespace must not be null.");
            Objects.requireNonNull(name, "name must not be null.");
        }
    }

    record Entry(String key, Value value) {
        public Entry(String key, Value value) {
            Objects.requireNonNull(key, "Entry key must not be null");
            this.key = key;
            this.value = value == null ? Value.Null.INSTANCE : value;
        }

        public static Entry of(String key, String value) {
            return of(key, value, Value.String::new);
        }

        public static Entry of(String key, boolean value) {
            return new Entry(key, new Value.Boolean(value));
        }

        public static Entry of(String key, byte value) {
            return new Entry(key, new Value.Byte(value));
        }

        public static Entry of(String key, char value) {
            return new Entry(key, new Value.Character(value));
        }

        public static Entry of(String key, short value) {
            return new Entry(key, new Value.Short(value));
        }

        public static Entry of(String key, int value) {
            return new Entry(key, new Value.Integer(value));
        }

        public static Entry of(String key, long value) {
            return new Entry(key, new Value.Long(value));
        }

        public static Entry of(String key, float value) {
            return new Entry(key, new Value.Double(value));
        }

        public static Entry of(String key, double value) {
            return new Entry(key, new Value.Double(value));
        }

        public static Entry of(String key, Boolean value) {
            return of(key, value, Value.Boolean::new);
        }

        public static Entry of(String key, Byte value) {
            return of(key, value, Value.Byte::new);
        }

        public static Entry of(String key, Character value) {
            return of(key, value, Value.Character::new);
        }

        public static Entry of(String key, Short value) {
            return of(key, value, Value.Short::new);
        }

        public static Entry of(String key, Integer value) {
            return of(key, value, Value.Integer::new);
        }

        public static Entry of(String key, Long value) {
            return of(key, value, Value.Long::new);
        }

        public static Entry of(String key, Double value) {
            return of(key, value, Value.Double::new);
        }

        public static Entry of(String key, UUID value) {
            return of(key, value, Value.UUID::new);
        }

        public static Entry of(String key, java.net.URI value) {
            return of(key, value, Value.URI::new);
        }

        public static Entry of(String key, Instant value) {
            return of(key, value, Value.Instant::new);
        }

        public static Entry of(String key, java.time.LocalDateTime value) {
            return of(key, value, Value.LocalDateTime::new);
        }

        public static Entry of(String key, java.time.LocalDate value) {
            return of(key, value, Value.LocalDate::new);
        }

        public static Entry of(String key, java.time.LocalTime value) {
            return of(key, value, Value.LocalTime::new);
        }

        public static Entry of(String key, java.time.Duration value) {
            return of(key, value, Value.Duration::new);
        }

        public static Entry of(String key, Throwable value) {
            return of(key, value, Value.Throwable::new);
        }

        public static Entry of(String key, List<Value> value) {
            return of(key, value, Value.List::new);
        }

        public static Entry of(String key, Map<String, Value> value) {
            return of(key, value, Value.Map::new);
        }

        public static <T> Entry of(String key, T value, Function<T, Value> toValue) {
            return new Entry(key, value == null ? Value.Null.INSTANCE : toValue.apply(value));
        }

        public static Entry of(String key, Supplier<Value> valueSupplier) {
            var value = valueSupplier.get();
            return new Entry(key, value == null ? Value.Null.INSTANCE : value);
        }

        public static Entry ofLazy(String key, Supplier<Value> valueSupplier) {
            return new Entry(key, new Value.Lazy(valueSupplier));
        }

        public static <T> Entry ofLazy(String key, T value, Function<T, Value> toValue) {
            return new Entry(key, new Value.Lazy(() -> {
                var v = toValue.apply(value);
                return v == null ? Value.Null.INSTANCE : v;
            }));
        }

        /**
         * A loggable value.
         *
         * <p>Only supports a subset of "basic" data kinds</p>
         */
        public sealed interface Value {
            /**
             * null
             */
            enum Null implements Value {
                INSTANCE;

                @Override
                public java.lang.String toString() {
                    return "Null";
                }
            }

            /**
             * A String.
             *
             * @param value The {@link java.lang.String} being wrapped. Must not be null.
             */
            record String(java.lang.String value) implements Value {
                public String {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * A boolean.
             *
             * @param value A wrapped boolean.
             */
            record Boolean(boolean value) implements Value {
            }

            /**
             * A byte.
             *
             * @param value A wrapped byte.
             */
            record Byte(byte value) implements Value {
            }

            /**
             * A char.
             *
             * @param value A wrapped char.
             */
            record Character(char value) implements Value {
            }

            /**
             * A short.
             *
             * @param value A wrapped shirt.
             */
            record Short(short value) implements Value {
            }

            /**
             * An int.
             *
             * @param value A wrapped int.
             */
            record Integer(int value) implements Value {
            }

            /**
             * A long.
             *
             * @param value A wrapped long.
             */
            record Long(long value) implements Value {
            }

            /**
             * A float.
             *
             * @param value A wrapped float.
             */
            record Float(float value) implements Value {
            }

            /**
             * A double.
             *
             * @param value A wrapped double.
             */
            record Double(double value) implements Value {
            }

            /**
             * A UUID.
             *
             * @param value A wrapped {@link java.util.UUID}.
             */
            record UUID(java.util.UUID value) implements Value {
                /**
                 * @param value The {@link java.util.UUID} being wrapped. Must not be null.
                 */
                public UUID {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * A URI.
             *
             * @param value A wrapped {@link java.net.URI}. Must not be null.
             */
            record URI(java.net.URI value) implements Value {
                public URI {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * An instant in time.
             *
             * @param value A wrapped {@link java.time.Instant}. Must not be null.
             */
            record Instant(java.time.Instant value) implements Value {
                public Instant {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * A local date-time.
             *
             * @param value A wrapped {@link java.time.LocalDateTime}. Must not be null.
             */
            record LocalDateTime(java.time.LocalDateTime value) implements Value {
                public LocalDateTime {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * A local date without a time component. Must not be null.
             *
             * @param value A wrapped {@link java.time.LocalDate}.
             */
            record LocalDate(java.time.LocalDate value) implements Value {
                public LocalDate {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * A local time without a date component.
             *
             * @param value A wrapped {@link java.time.LocalTime}.
             */
            record LocalTime(java.time.LocalTime value) implements Value {
                /**
                 * @param value The {@link java.time.LocalTime} being wrapped. Must not be null.
                 */
                public LocalTime {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * A duration of time.
             *
             * @param value A wrapped {@link java.time.Duration}.
             */
            record Duration(java.time.Duration value) implements Value {
                /**
                 * @param value The {@link java.time.Duration} being wrapped. Must not be null.
                 */
                public Duration {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * A throwable value. Represents an error that occurred when doing some computation.
             *
             * @param value The throwable that was thrown.
             */
            record Throwable(java.lang.Throwable value) implements Value {
                /**
                 * @param value The {@link java.lang.Throwable} being wrapped.  Must not be null.
                 */
                public Throwable {
                    Objects.requireNonNull(value, "value must not be null");
                }
            }

            /**
             * A list of values.
             *
             * @param value The underlying {@link java.util.List}. Will be unmodifiable.
             */
            record List(java.util.List<Value> value) implements Value {
                /**
                 * Constructs a new List.
                 *
                 * @param value The list of values to use to construct the list. Will copy the list
                 *              and replace stray null values. Should not be null.
                 */
                public List(java.util.List<Value> value) {
                    Objects.requireNonNull(value, "value must not be null");
                    this.value = value.stream()
                            .map(v -> v == null ? Null.INSTANCE : v)
                            .toList();
                }
            }

            /**
             * A map of value to value.
             *
             * @param value The underlying {@link java.util.Map}. Will be unmodifiable.
             */
            record Map(java.util.Map<java.lang.String, Value> value) implements Value {
                /**
                 * Constructs a new Map.
                 *
                 * @param value The map of values to use to construct the map. Will copy the map
                 *              and replace stray null values. Should not be null.
                 */
                public Map(java.util.Map<java.lang.String, Value> value) {
                    Objects.requireNonNull(value, "value must not be null");
                    this.value = value.entrySet()
                            .stream()
                            .collect(Collectors.toUnmodifiableMap(
                                    java.util.Map.Entry::getKey,
                                    entry -> entry.getValue() == null ? Null.INSTANCE : entry.getValue()
                            ));
                }
            }

            /**
             * A set of values.
             *
             * <p>This is different from a list in that it is implied that no duplicates are
             * allowed and order is not assumed.</p>
             *
             * @param value The underlying {@link java.util.Set}. Will be unmodifiable.
             */
            record Set(java.util.Set<Value> value) implements Value {
                /**
                 * Constructs a new Set.
                 *
                 * @param value The set of values to use to construct the set. Will copy the set
                 *              and replace stray null values. Should not be null.
                 */
                public Set(java.util.Set<Value> value) {
                    Objects.requireNonNull(value, "value must not be null");
                    this.value = value.stream()
                            .map(v -> v == null ? Null.INSTANCE : v)
                            .collect(Collectors.toUnmodifiableSet());
                }
            }

            /**
             * A lazily realized value.
             *
             * <p>When its value is requested it will be computed. If any exceptions
             * occur when realizing its Value then a Value.Throwable will be returned.</p>
             *
             * <p>After a value is computed it is stable and will not be recomputed.</p>
             */
            final class Lazy implements Value {
                // Implementation based off of clojure's Delay + vavr's Lazy
                private volatile Supplier<? extends Value> supplier;
                private Value value;

                /**
                 * Constructs a Lazy value from the given supplier.
                 *
                 * @param supplier Code which will be called later to provide a value.
                 */
                public Lazy(Supplier<? extends Value> supplier) {
                    Objects.requireNonNull(supplier, "supplier must not be null");
                    this.supplier = supplier;
                    this.value = null;
                }

                /**
                 * @return The computed {@link Value}. Will return the same value on repeat calls. Safe to call from
                 * multiple threads.
                 */
                public Value value() {
                    if (supplier != null) {
                        synchronized (this) {
                            final var s = supplier;
                            if (s != null) {
                                try {
                                    this.value = Objects.requireNonNullElse(s.get(), Null.INSTANCE);
                                } catch (java.lang.Throwable throwable) {
                                    this.value = new Throwable(throwable);
                                }
                                this.supplier = null;
                            }
                        }
                    }

                    return this.value;
                }

                @Override
                public java.lang.String toString() {
                    if (supplier != null) {
                        return "Lazy[pending]";
                    } else {
                        return "Lazy[realized: value=" + value() + "]";
                    }
                }
            }
        }
    }
}

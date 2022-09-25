package dev.mccue.log.alpha;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static dev.mccue.log.alpha.Globals.GLOBAL_CONTEXT;
import static dev.mccue.log.alpha.Globals.LOCAL_CONTEXT;

/**
 * A logger.
 */
@FunctionalInterface
public interface Logger {
    /**
     * Logs the log.
     *
     * @param log The log to log.
     */
    void log(Log log);

    default void event(
            Log.Level level,
            Log.Category category,
            List<Log.Entry> entries
    ) {
        log(new Log.Event(level, category, entries));
    }

    default void event(Log.Level level, Log.Category category, Log.Entry... entries) {
        event(level, category, List.of(entries));
    }

    default void trace(Log.Category category, List<Log.Entry> entries) {
        event(Log.Level.TRACE, category, entries);
    }

    default void trace(Log.Category category, Log.Entry... entries) {
        event(Log.Level.TRACE, category, entries);
    }

    default void debug(Log.Category category, List<Log.Entry> entries) {
        event(Log.Level.DEBUG, category, entries);
    }

    default void debug(Log.Category category, Log.Entry... entries) {
        event(Log.Level.DEBUG, category, entries);
    }

    default void info(Log.Category category, List<Log.Entry> entries) {
        event(Log.Level.INFO, category, entries);
    }

    default void info(Log.Category category, Log.Entry... entries) {
        event(Log.Level.INFO, category, entries);
    }

    default void warn(Log.Category category, List<Log.Entry> entries) {
        event(Log.Level.WARN, category, entries);
    }

    default void warn(Log.Category category, Log.Entry... entries) {
        event(Log.Level.WARN, category, entries);
    }

    default void error(Log.Category category, List<Log.Entry> entries) {
        event(Log.Level.ERROR, category, entries);
    }

    /**
     * @see Logger#error(Log.Category, List)
     */
    default void error(Log.Category category, Log.Entry... entries) {
        event(Log.Level.ERROR, category, entries);
    }

    default <T> T span(
            Log.Level level,
            Log.Category category,
            List<Log.Entry> entries,
            Supplier<T> code
    ) {
        Log.Span.Outcome outcome = Log.Span.Outcome.Ok.INSTANCE;
        var start = Instant.now();
        var localContext = LOCAL_CONTEXT.get();
        try {
            LOCAL_CONTEXT.set(new Log.Context.Child.Span(
                    Thread.currentThread(),
                    start,
                    Flake.create(),
                    localContext == null ? GLOBAL_CONTEXT.get() : localContext
            ));
            return code.get();
        } catch (Throwable t) {
            outcome = new Log.Span.Outcome.Error(t);
            throw t;
        } finally {
            LOCAL_CONTEXT.set(localContext);
            var end = Instant.now();
            var duration = Duration.between(start, end);
            var occurrence = new Log.Occurrence.SpanOfTime(start, duration);
            log(new Log.Span(
                    outcome,
                    occurrence,
                    level,
                    category,
                    entries
            ));
        }
    }

    default void span(
            Log.Level level,
            Log.Category category,
            List<Log.Entry> entries,
            Runnable code
    ) {
        span(level, category, entries, () -> {
            code.run();
            return null;
        });
    }

    default <T> T traceSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Supplier<T> code
    ) {
        return span(Log.Level.TRACE, category, entries, code);
    }

    default void traceSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Runnable code
    ) {
        span(Log.Level.TRACE, category, entries, code);
    }

    default <T> T debugSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Supplier<T> code
    ) {
        return span(Log.Level.DEBUG, category, entries, code);
    }

    default void debugSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Runnable code
    ) {
        span(Log.Level.DEBUG, category, entries, code);
    }

    default <T> T infoSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Supplier<T> code
    ) {
        return span(Log.Level.INFO, category, entries, code);
    }

    default void infoSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Runnable code
    ) {
        span(Log.Level.INFO, category, entries, code);
    }

    default <T> T warnSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Supplier<T> code
    ) {
        return span(Log.Level.WARN, category, entries, code);
    }

    default void warnSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Runnable code
    ) {
        span(Log.Level.WARN, category, entries, code);
    }

    default <T> T errorSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Supplier<T> code
    ) {
        return span(Log.Level.WARN, category, entries, code);
    }

    default void errorSpan(
            Log.Category category,
            List<Log.Entry> entries,
            Runnable code
    ) {
        span(Log.Level.ERROR, category, entries, code);
    }

    /**
     * @param namespace The namespace for log categories.
     * @return A namespaced logger wrapping this one.
     */
    default Namespaced namespaced(String namespace) {
        return new NamespacedLogger(namespace, this);
    }

    /**
     * A logger with the namespace of its category already filled in.
     *
     * <p>The most common use of this is to have a logger for a particular class.</p>
     */
    sealed interface Namespaced {
        void event(Log.Level level, String name, List<Log.Entry> entries);

        default void event(Log.Level level, String name, Log.Entry... entries) {
            event(level, name, List.of(entries));
        }

        default void trace(String name, List<Log.Entry> entries) {
            event(Log.Level.TRACE, name, entries);
        }

        default void trace(String name, Log.Entry... entries) {
            event(Log.Level.TRACE, name, entries);
        }

        default void debug(String name, List<Log.Entry> entries) {
            event(Log.Level.DEBUG, name, entries);
        }

        default void debug(String name, Log.Entry... entries) {
            event(Log.Level.DEBUG, name, entries);
        }

        default void info(String name, List<Log.Entry> entries) {
            event(Log.Level.INFO, name, entries);
        }

        default void info(String name, Log.Entry... entries) {
            event(Log.Level.INFO, name, entries);
        }

        default void warn(String name, List<Log.Entry> entries) {
            event(Log.Level.WARN, name, entries);
        }

        default void warn(String name, Log.Entry... entries) {
            event(Log.Level.WARN, name, entries);
        }

        default void error(String name, List<Log.Entry> entries) {
            event(Log.Level.ERROR, name, entries);
        }

        default void error(String name, Log.Entry... entries) {
            event(Log.Level.ERROR, name, entries);
        }

        <T> T span(Log.Level level, String name, List<Log.Entry> entries, Supplier<T> code);

        default void span(
                Log.Level level,
                String name,
                List<Log.Entry> entries,
                Runnable code
        ) {
            span(level, name, entries, () -> {
                code.run();
                return null;
            });
        }

        default <T> T traceSpan(
                String name,
                List<Log.Entry> entries,
                Supplier<T> code
        ) {
            return span(Log.Level.TRACE, name, entries, code);
        }

        default void traceSpan(
                String name,
                List<Log.Entry> entries,
                Runnable code
        ) {
            span(Log.Level.TRACE, name, entries, code);
        }

        default <T> T debugSpan(
                String name,
                List<Log.Entry> entries,
                Supplier<T> code
        ) {
            return span(Log.Level.DEBUG, name, entries, code);
        }

        default void debugSpan(
                String name,
                List<Log.Entry> entries,
                Runnable code
        ) {
            span(Log.Level.DEBUG, name, entries, code);
        }

        default <T> T infoSpan(
                String name,
                List<Log.Entry> entries,
                Supplier<T> code
        ) {
            return span(Log.Level.INFO, name, entries, code);
        }

        default void infoSpan(
                String name,
                List<Log.Entry> entries,
                Runnable code
        ) {
            span(Log.Level.INFO, name, entries, code);
        }

        default <T> T warnSpan(
                String name,
                List<Log.Entry> entries,
                Supplier<T> code
        ) {
            return span(Log.Level.WARN, name, entries, code);
        }

        default void warnSpan(
                String name,
                List<Log.Entry> entries,
                Runnable code
        ) {
            span(Log.Level.WARN, name, entries, code);
        }

        default <T> T errorSpan(
                String name,
                List<Log.Entry> entries,
                Supplier<T> code
        ) {
            return span(Log.Level.WARN, name, entries, code);
        }

        default void errorSpan(
                String name,
                List<Log.Entry> entries,
                Runnable code
        ) {
            span(Log.Level.ERROR, name, entries, code);
        }
    }
}

record NamespacedLogger(String namespace, Logger logger) implements Logger.Namespaced {
    @Override
    public void event(Log.Level level, String name, List<Log.Entry> entries) {
        logger.event(level, new Log.Category(namespace, name), entries);
    }

    @Override
    public <T> T span(Log.Level level, String name, List<Log.Entry> entries, Supplier<T> code) {
        return logger.span(level, new Log.Category(namespace, name), entries, code);
    }
}

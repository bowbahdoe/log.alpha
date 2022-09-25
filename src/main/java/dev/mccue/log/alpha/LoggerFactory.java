package dev.mccue.log.alpha;

import java.util.ArrayList;
import java.util.ServiceLoader;
import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface LoggerFactory {
    static LoggerFactory create() {
        var loggerFactories = ServiceLoader.load(LoggerFactory.class).iterator();
        if (!loggerFactories.hasNext()) {
            System.err.println("No logger factory supplied. Falling back to no-op logger");
            return () -> (__) -> {
            };
        } else {
            var service = loggerFactories.next();
            if (loggerFactories.hasNext()) {
                var services = new ArrayList<LoggerFactory>();
                services.add(service);
                while (loggerFactories.hasNext()) {
                    services.add(loggerFactories.next());
                }

                System.err.printf("Multiple logger factories supplied: %s. Picking one at random.%n", services);
                return services.get(ThreadLocalRandom.current().nextInt(0, services.size()));
            } else {
                return service;
            }
        }
    }

    static Logger getLogger() {
        return create().createLogger();
    }

    static Logger.Namespaced getLogger(String namespace) {
        return getLogger().namespaced(namespace);
    }

    static Logger.Namespaced getLogger(Class<?> klass) {
        return getLogger().namespaced(klass.getCanonicalName());
    }

    Logger createLogger();
}

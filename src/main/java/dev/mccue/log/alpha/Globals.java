package dev.mccue.log.alpha;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class Globals {
    static final AtomicReference<Log.Context.Global> GLOBAL_CONTEXT =
            new AtomicReference<>(new Log.Context.Global(List.of()));

    /*
     * This should be an extent local when it is possible to be so.
     */
    static final ThreadLocal<Log.Context.Child> LOCAL_CONTEXT =
            new ThreadLocal<>();
}

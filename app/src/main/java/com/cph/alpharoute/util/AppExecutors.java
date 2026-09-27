package com.cph.alpharoute.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's threading policy, in one place.
 *
 * <p>Architecture decision AD-3: there are no coroutines in this project (they are Kotlin), and
 * {@code AsyncTask} is deprecated, so every background operation runs on one of these executors.
 * Nothing in this app touches the database on the main thread.
 *
 * <p><b>Why {@link #diskIO()} is a SINGLE-threaded executor:</b> it serialises every database write.
 * That is the third and outermost guard on the "one store must never be counted twice" rule, sitting
 * alongside the in-transaction status re-read and the unique index on
 * {@code completion_history.storeId} (AD-5). Two completion requests physically cannot interleave.
 */
public class AppExecutors {

    private final ExecutorService diskIO;
    private final ExecutorService networkIO;
    private final Handler mainHandler;

    public AppExecutors() {
        diskIO = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "alpharoute-disk"));

        networkIO = Executors.newFixedThreadPool(
                2,
                runnable -> new Thread(runnable, "alpharoute-network"));

        mainHandler = new Handler(Looper.getMainLooper());
    }

    /** Single thread. All Room reads and writes, and every transaction. */
    public ExecutorService diskIO() {
        return diskIO;
    }

    /** A small pool for routing calls and, later, Firestore sync. */
    public ExecutorService networkIO() {
        return networkIO;
    }

    /** For delivering a result back to the UI thread. */
    public Handler mainHandler() {
        return mainHandler;
    }

    /** Convenience for the common "post this back to the UI" case. */
    public void postToMain(Runnable runnable) {
        mainHandler.post(runnable);
    }
}

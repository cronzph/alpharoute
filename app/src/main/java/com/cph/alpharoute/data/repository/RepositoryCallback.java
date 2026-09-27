package com.cph.alpharoute.data.repository;

/**
 * A one-method callback for the result of an asynchronous repository write.
 *
 * <p>Repositories always perform their work on {@code AppExecutors.diskIO()} and deliver the result
 * here, <b>on the main thread</b>. That is what lets a Fragment update a button state or show a
 * message without any further thread handling of its own.
 *
 * @param <T> the result type - usually {@link WriteOutcome}, or a row id for an insert
 */
public interface RepositoryCallback<T> {

    /** Always invoked on the main thread. Never invoked more than once. */
    void onResult(T result);
}

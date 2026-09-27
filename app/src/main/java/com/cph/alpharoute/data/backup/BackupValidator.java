package com.cph.alpharoute.data.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.domain.StoreStatus;
import com.cph.alpharoute.domain.StoreValidator;
import com.cph.alpharoute.util.GeoConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether a backup file can be restored.
 *
 * <p><b>A separate step from reading, and that separation is the point.</b> {@code BackupCodec} answers
 * "what did the file say"; this answers "is that usable". Keeping them apart means the rules can be tested
 * without a file, and - more importantly - that a file is never partially applied while it is still being
 * judged.
 *
 * <p><b>It reports every problem, not the first one.</b> A user whose backup has three faults should be
 * told all three, not sent round a loop of fixing one and discovering the next.
 *
 * <p><b>Nothing here touches the database.</b> This is a pure function of the parsed contents, so a file
 * can be checked, shown to the user, and refused without anything having been written.
 *
 * <p>Pure Java apart from the two pure helpers it borrows ({@code GeoConstants}, {@code StoreValidator}).
 */
public final class BackupValidator {

    /** One reason a file cannot be restored. */
    public static final class Problem {

        /** What is wrong, in the user's terms. */
        public final String message;

        public Problem(@NonNull String message) {
            this.message = message;
        }

        @NonNull
        @Override
        public String toString() {
            return message;
        }
    }

    /** Utility class - never instantiated. */
    private BackupValidator() {
    }

    /**
     * Checks a parsed backup.
     *
     * @param contents what the file said, or {@code null} when it could not be read at all
     * @return the problems found, in a stable order. Empty means the file can be restored
     */
    @NonNull
    public static List<Problem> validate(@Nullable BackupContents contents) {
        List<Problem> problems = new ArrayList<>();

        if (contents == null) {
            problems.add(new Problem("This file is not an Alfamart Route Tracker backup."));
            return problems;
        }

        // The format version first: if the shape is unknown, every later complaint could be nonsense.
        if (contents.formatVersion != BackupCodec.FORMAT_VERSION) {
            problems.add(new Problem(
                    "This backup was made by a different version of the app (format "
                            + contents.formatVersion + ", expected " + BackupCodec.FORMAT_VERSION + ")."));
            return problems;
        }

        if (contents.stores.isEmpty()) {
            problems.add(new Problem("This backup contains no stores."));
        }

        validateStores(contents, problems);
        validateCompletions(contents, problems);

        return problems;
    }

    private static void validateStores(@NonNull BackupContents contents,
                                       @NonNull List<Problem> problems) {
        Set<String> seenCodes = new HashSet<>();
        Set<String> seenRoutes = new HashSet<>();

        for (BackupContents.BackupStore store : contents.stores) {
            String label = store.storeCode.isEmpty() ? "(no code)" : store.storeCode;

            if (store.storeCode.trim().isEmpty()) {
                problems.add(new Problem("A store has no store code."));
            } else if (!seenCodes.add(store.storeCode)) {
                // Two stores with the same code would be refused by the unique index on import, after the
                // user had already confirmed. Catching it here means the refusal happens before anything is
                // written.
                problems.add(new Problem("Two stores share the code " + label + "."));
            }

            if (store.routeNumber.trim().isEmpty()) {
                problems.add(new Problem("Store " + label + " has no route number."));
            } else if (!seenRoutes.add(store.routeNumber)) {
                problems.add(new Problem("Two stores share the route number " + store.routeNumber + "."));
            }

            if (store.storeName.trim().isEmpty()) {
                problems.add(new Problem("Store " + label + " has no name."));
            }

            if (!GeoConstants.isValidLatitude(store.latitude)
                    || !GeoConstants.isValidLongitude(store.longitude)) {
                problems.add(new Problem("Store " + label + " has a coordinate that is not on the globe."));
            }

            if (parseStatus(store.status) == null) {
                problems.add(new Problem("Store " + label + " has an unknown status: " + store.status));
            }

            if (store.storeCode.length() > StoreValidator.MAX_STORE_CODE_LENGTH
                    || store.routeNumber.length() > StoreValidator.MAX_ROUTE_NUMBER_LENGTH
                    || store.storeName.length() > StoreValidator.MAX_STORE_NAME_LENGTH
                    || store.area.length() > StoreValidator.MAX_AREA_LENGTH) {
                problems.add(new Problem("Store " + label + " has a field that is longer than the app allows."));
            }
        }
    }

    private static void validateCompletions(@NonNull BackupContents contents,
                                            @NonNull List<Problem> problems) {
        Set<String> codesInFile = new HashSet<>();
        for (BackupContents.BackupStore store : contents.stores) {
            codesInFile.add(store.storeCode);
        }

        // The same guard the unique index enforces, checked here so a bad file is refused before it is
        // written rather than half-applied.
        Set<String> storeRoundPairs = new HashSet<>();

        // Which stores are marked COMPLETED, so the two halves can be checked against each other.
        Map<String, Boolean> completedByCode = new HashMap<>();
        for (BackupContents.BackupStore store : contents.stores) {
            completedByCode.put(store.storeCode, parseStatus(store.status) == StoreStatus.COMPLETED);
        }
        Set<String> codesWithCompletion = new HashSet<>();

        for (BackupContents.BackupCompletion completion : contents.completions) {
            if (!codesInFile.contains(completion.storeCode)) {
                // A completion pointing at a store the file does not contain. Restoring it would create an
                // orphan, which the foreign key refuses - and the user would see a failure after confirming.
                problems.add(new Problem(
                        "A completion refers to store " + completion.storeCode
                                + ", which is not in this backup."));
                continue;
            }

            codesWithCompletion.add(completion.storeCode);

            if (!storeRoundPairs.add(completion.storeCode + "#" + completion.roundNumber)) {
                problems.add(new Problem(
                        "Store " + completion.storeCode + " is completed twice in round "
                                + completion.roundNumber + "."));
            }

            if (completion.dailySequence <= 0) {
                problems.add(new Problem(
                        "A completion for store " + completion.storeCode
                                + " has no position in its day."));
            }

            if (completion.completionDateKey.trim().isEmpty()) {
                problems.add(new Problem(
                        "A completion for store " + completion.storeCode + " has no date."));
            }

            if (completion.completionTimestamp <= 0L) {
                problems.add(new Problem(
                        "A completion for store " + completion.storeCode + " has no timestamp."));
            }
        }

        // The two halves must agree, in both directions. A store marked COMPLETED with no completion row
        // would be counted as remaining while the map shows it green - the disagreement the consistency
        // check exists to find, and there is no reason to import one.
        for (BackupContents.BackupStore store : contents.stores) {
            boolean markedCompleted = Boolean.TRUE.equals(completedByCode.get(store.storeCode));
            boolean hasCompletion = codesWithCompletion.contains(store.storeCode);

            if (markedCompleted && !hasCompletion) {
                problems.add(new Problem(
                        "Store " + store.storeCode + " is marked completed but has no completion record."));
            }
            if (!markedCompleted && hasCompletion) {
                problems.add(new Problem(
                        "Store " + store.storeCode + " has a completion record but is not marked completed."));
            }
        }
    }

    /**
     * The status enum for a name, or {@code null} when it is not one the app knows.
     *
     * <p>{@code valueOf} throws, which is the wrong shape here: an unknown status is a fact about the file
     * to report, not an exception to catch. {@code Converters} relies on the throw for the same reason this
     * does not - a database written by a newer version should fail loudly, while a file being inspected
     * should simply be refused.
     */
    @Nullable
    public static StoreStatus parseStatus(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (StoreStatus status : StoreStatus.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return null;
    }

    /** A one-line summary of what a valid file would restore, for the confirmation dialog. */
    @NonNull
    public static String describe(@Nullable BackupContents contents) {
        if (contents == null) {
            return "";
        }
        return contents.storeCount() + " store(s), " + contents.completionCount() + " completion(s)";
    }

    /** An unmodifiable empty list, for callers that want to say "no problems" without allocating. */
    @NonNull
    public static List<Problem> noProblems() {
        return Collections.emptyList();
    }
}

package com.cph.alpharoute.data.sync;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.data.backup.BackupCodec;
import com.cph.alpharoute.data.backup.BackupContents;
import com.cph.alpharoute.data.backup.BackupValidator;
import com.cph.alpharoute.data.backup.ImportMode;
import com.cph.alpharoute.data.db.BackupTransactionDao;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.data.repository.BackupRepository;
import com.cph.alpharoute.data.repository.CompletionRepository;
import com.cph.alpharoute.data.repository.RepositoryCallback;
import com.cph.alpharoute.data.repository.StoreRepository;
import com.cph.alpharoute.domain.CloudSyncSummary;
import com.cph.alpharoute.util.AppExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saving the whole database to the cloud, and getting it back - <b>by hand, both ways.</b>
 *
 * <h3>Why this is manual and not a background sync</h3>
 *
 * <p>There are two buttons and no automatic anything. That is the design, not a first version:
 *
 * <ul>
 *   <li><b>Nothing happens while the user is driving.</b> A sync that fires on every write can push a
 *       half-finished round, and the user has no way to tell that it did.</li>
 *   <li><b>Both directions are a decision.</b> "Save to cloud" and "Retrieve from cloud" are different
 *       intentions and they are not the same action performed twice. A single "Sync" button would have to
 *       guess which one the user meant, and would guess wrong exactly when it mattered.</li>
 *   <li><b>The dangerous direction is visibly dangerous.</b> Retrieving can add records. It shows the user
 *       what it would do <i>before</i> it does it, and merge and replace are separate choices.</li>
 * </ul>
 *
 * <h3>What it reuses, and why that is the whole safety story</h3>
 *
 * <p><b>A cloud save is a backup.</b> {@link BackupRepository#buildBackupText} already turns the whole
 * database into one JSON document, and it is already tested. Pushing writes that document to Firestore.
 * There is no second definition of "the app's data" to drift from the file export.
 *
 * <p><b>A cloud retrieve is a restore.</b> The document is fed through the same
 * {@link BackupRepository#readAndValidate} and the same
 * {@link BackupTransactionDao#importAll} the file import uses - which means the validation, the merge
 * rule, the single transaction and the rollback are all the ones that already existed and were already
 * tested. Writing a second import path for the cloud would have meant a second set of rules, free to
 * disagree with the first, and the one that ran last would win.
 *
 * <h3>Where the data lives</h3>
 *
 * <pre>
 *   users/{uid}/cloud/backup
 *     json              the whole backup document, exactly as the file export writes it
 *     formatVersion     so a future format can be recognised rather than misread
 *     exportedAt        when the phone built it
 *     storeCount        \
 *     completionCount    > readable in the Firebase console without parsing the json
 *     updatedAt         /
 *     deviceLabel       which phone wrote it, for the case where there are two
 * </pre>
 *
 * <p>The counts and the timestamp are duplicated out of the json on purpose. They are what makes the
 * document legible in the console - which is how the user confirms the save actually happened - and they
 * are never read back as data. The json is the only thing restored from.
 *
 * <p><b>Security.</b> Every operation refuses to run without a signed-in uid, and the Firestore rules are
 * scoped to {@code request.auth.uid}. An unscoped Firestore is readable and writable by anyone who finds
 * the project id.
 */
public class CloudSyncRepository {

    private static final String TAG = "AlphaRouteCloudSync";

    private static final String USERS = "users";
    private static final String CLOUD = "cloud";
    private static final String DOC_BACKUP = "backup";

    private static final String FIELD_JSON = "json";
    private static final String FIELD_FORMAT_VERSION = "formatVersion";
    private static final String FIELD_EXPORTED_AT = "exportedAt";
    private static final String FIELD_STORE_COUNT = "storeCount";
    private static final String FIELD_COMPLETION_COUNT = "completionCount";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_DEVICE = "deviceLabel";

    /** How a cloud operation ended. Each one names the user's next move. */
    public enum Outcome {
        /** It worked. */
        SUCCESS,
        /** Nobody is signed in, so there is no place to put the data. */
        NOT_SIGNED_IN,
        /** Signed in, but the cloud has never been written to from this account. */
        NOTHING_THERE,
        /** The document is there but is not a backup the app can read. */
        UNREADABLE,
        /** It parsed but failed the same validation the file import runs. */
        INVALID,
        /** There is nothing to send - no stores and no completions. */
        EMPTY,
        /** The network or Firestore refused. */
        FAILED
    }

    /** What the cloud currently holds, for the status line. Read without downloading the json. */
    public static final class Status {

        public final Outcome outcome;
        public final int storeCount;
        public final int completionCount;
        public final long exportedAt;
        @Nullable
        public final String deviceLabel;

        Status(Outcome outcome,
               int storeCount,
               int completionCount,
               long exportedAt,
               @Nullable String deviceLabel) {
            this.outcome = outcome;
            this.storeCount = storeCount;
            this.completionCount = completionCount;
            this.exportedAt = exportedAt;
            this.deviceLabel = deviceLabel;
        }

        /** Whether the cloud holds something this app could restore. */
        public boolean hasData() {
            return outcome == Outcome.SUCCESS;
        }
    }

    /** A cloud document that has been fetched, parsed, validated and compared with this phone. */
    public static final class Fetched {

        public final Outcome outcome;
        /** Only on {@link Outcome#SUCCESS}. */
        @Nullable
        public final BackupContents contents;
        /** Only on {@link Outcome#SUCCESS}. What a merge would add, worked out before anything is written. */
        @Nullable
        public final CloudSyncSummary summary;
        public final long exportedAt;

        Fetched(Outcome outcome,
                @Nullable BackupContents contents,
                @Nullable CloudSyncSummary summary,
                long exportedAt) {
            this.outcome = outcome;
            this.contents = contents;
            this.summary = summary;
            this.exportedAt = exportedAt;
        }
    }

    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final BackupRepository backupRepository;
    private final StoreRepository storeRepository;
    private final CompletionRepository completionRepository;
    private final AppExecutors executors;

    public CloudSyncRepository(@NonNull BackupRepository backupRepository,
                               @NonNull StoreRepository storeRepository,
                               @NonNull CompletionRepository completionRepository,
                               @NonNull AppExecutors executors) {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.backupRepository = backupRepository;
        this.storeRepository = storeRepository;
        this.completionRepository = completionRepository;
        this.executors = executors;
    }

    // ------------------------------------------------------------------ the status line

    /**
     * Reads what the cloud holds, without downloading the backup itself.
     *
     * <p>Only the summary fields are asked for, so a status refresh is a few hundred bytes rather than a
     * whole database - which is what makes it reasonable to run every time the Settings screen opens.
     */
    public void refreshStatus(@NonNull RepositoryCallback<Status> callback) {
        DocumentReference document = documentForSignedInUser();
        if (document == null) {
            deliver(callback, new Status(Outcome.NOT_SIGNED_IN, 0, 0, 0L, null));
            return;
        }

        document.get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        deliver(callback, new Status(Outcome.NOTHING_THERE, 0, 0, 0L, null));
                        return;
                    }
                    // The json being absent means this is not a document this app wrote, whatever else is
                    // in it. Reporting "nothing there" would be a lie the user could see in the console.
                    if (!snapshot.contains(FIELD_JSON)) {
                        deliver(callback, new Status(Outcome.UNREADABLE, 0, 0, 0L, null));
                        return;
                    }
                    deliver(callback, new Status(
                            Outcome.SUCCESS,
                            intOf(snapshot, FIELD_STORE_COUNT),
                            intOf(snapshot, FIELD_COMPLETION_COUNT),
                            longOf(snapshot, FIELD_EXPORTED_AT),
                            snapshot.getString(FIELD_DEVICE)));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Could not read the cloud status", e);
                    deliver(callback, new Status(Outcome.FAILED, 0, 0, 0L, null));
                });
    }

    // ------------------------------------------------------------------ save to cloud

    /**
     * Writes this phone's whole database to the cloud, replacing whatever is there.
     *
     * <p><b>It replaces rather than merges, and the screen warns first.</b> The cloud document is one
     * snapshot of one phone; merging two snapshots needs a rule about which side wins per record, and
     * getting that wrong loses data silently. Replacing is the honest operation: after a save, the cloud
     * holds exactly what this phone holds.
     *
     * <p>Nothing is deleted locally, ever. This direction only writes.
     */
    public void saveToCloud(@NonNull RepositoryCallback<Outcome> callback) {
        DocumentReference document = documentForSignedInUser();
        if (document == null) {
            deliver(callback, Outcome.NOT_SIGNED_IN);
            return;
        }

        backupRepository.buildBackupText(json -> {
            if (json == null) {
                // The database held something that would not serialise. Nothing was sent.
                deliver(callback, Outcome.FAILED);
                return;
            }

            BackupContents contents = BackupCodec.parse(json);
            if (contents == null) {
                // The text we just built could not be read back as a backup. Nothing was sent, and the
                // cloud copy - if there is one - is untouched.
                Log.e(TAG, "The backup we just built could not be read back");
                deliver(callback, Outcome.FAILED);
                return;
            }

            if (contents.storeCount() == 0 && contents.completionCount() == 0) {
                // Sending an empty document would replace a good cloud copy with nothing. Refusing is the
                // safe answer, and the screen says why.
                deliver(callback, Outcome.EMPTY);
                return;
            }

            long now = System.currentTimeMillis();

            Map<String, Object> fields = new HashMap<>();
            fields.put(FIELD_JSON, json);
            fields.put(FIELD_FORMAT_VERSION, BackupCodec.FORMAT_VERSION);
            fields.put(FIELD_EXPORTED_AT, contents.exportedAt);
            fields.put(FIELD_STORE_COUNT, contents.storeCount());
            fields.put(FIELD_COMPLETION_COUNT, contents.completionCount());
            fields.put(FIELD_UPDATED_AT, now);
            fields.put(FIELD_DEVICE, Build.MODEL == null ? "" : Build.MODEL);

            document.set(fields, SetOptions.merge())
                    .addOnSuccessListener(unused -> deliver(callback, Outcome.SUCCESS))
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Could not save to the cloud", e);
                        deliver(callback, Outcome.FAILED);
                    });
        });
    }

    // ------------------------------------------------------------------ retrieve from cloud

    /**
     * Downloads the cloud document, checks it, and works out what a merge would add.
     *
     * <p><b>Nothing is written here.</b> The screen needs to show the user the numbers and ask before
     * anything changes - and a preview that had already written something would not be a preview.
     */
    public void fetchFromCloud(@NonNull RepositoryCallback<Fetched> callback) {
        DocumentReference document = documentForSignedInUser();
        if (document == null) {
            deliver(callback, new Fetched(Outcome.NOT_SIGNED_IN, null, null, 0L));
            return;
        }

        document.get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        deliver(callback, new Fetched(Outcome.NOTHING_THERE, null, null, 0L));
                        return;
                    }

                    String json = snapshot.getString(FIELD_JSON);
                    if (json == null || json.trim().isEmpty()) {
                        deliver(callback, new Fetched(Outcome.UNREADABLE, null, null, 0L));
                        return;
                    }

                    long exportedAt = longOf(snapshot, FIELD_EXPORTED_AT);

                    // Parsing, validating and comparing all read the database, so they go to the disk
                    // thread - the same thread every other read in the app uses.
                    executors.diskIO().execute(
                            () -> parseValidateAndCompare(json, exportedAt, callback));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Could not read the cloud backup", e);
                    deliver(callback, new Fetched(Outcome.FAILED, null, null, 0L));
                });
    }

    private void parseValidateAndCompare(@NonNull String json,
                                         long exportedAt,
                                         @NonNull RepositoryCallback<Fetched> callback) {

        BackupContents contents = BackupCodec.parse(json);
        if (contents == null) {
            // Present, but not a backup this app wrote - a different document, or a truncated one.
            Log.w(TAG, "The cloud document is not a backup");
            deliver(callback, new Fetched(Outcome.UNREADABLE, null, null, exportedAt));
            return;
        }

        // The SAME validation the file import runs. A cloud document gets no more trust than a file the
        // user picked - it is data from outside the app either way.
        List<BackupValidator.Problem> problems = BackupValidator.validate(contents);
        if (!problems.isEmpty()) {
            Log.w(TAG, "The cloud backup failed validation: " + problems);
            deliver(callback, new Fetched(Outcome.INVALID, null, null, exportedAt));
            return;
        }

        // Two one-shot reads, then the comparison. Both are the same reads the file import preview uses.
        storeRepository.findAllNow(stores ->
                completionRepository.findAllNow(history -> {
                    CloudSyncSummary summary = compare(contents, stores, history);
                    deliver(callback,
                            new Fetched(Outcome.SUCCESS, contents, summary, exportedAt));
                }));
    }

    /**
     * Applies a fetched cloud document to this phone.
     *
     * <p>One transaction, and a failure rolls the whole thing back - the same guarantee the file restore
     * gives. {@code MERGE} keeps anything already here and adds what is missing; {@code REPLACE} deletes
     * everything first, and the screen makes the user choose it deliberately.
     */
    public void applyFromCloud(@NonNull BackupContents contents,
                               @NonNull ImportMode mode,
                               @NonNull RepositoryCallback<BackupTransactionDao.ImportOutcome> callback) {
        backupRepository.restore(contents, mode, callback);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The cloud document for whoever is signed in, or {@code null} when nobody is.
     *
     * <p>Read fresh on every call rather than cached. A cached uid would keep pointing at the previous
     * account after a sign-out and a sign-in as somebody else - which would put one person's round into
     * another person's cloud.
     */
    @Nullable
    private DocumentReference documentForSignedInUser() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null || user.getUid() == null || user.getUid().isEmpty()) {
            return null;
        }
        return firestore.collection(USERS)
                .document(user.getUid())
                .collection(CLOUD)
                .document(DOC_BACKUP);
    }

    @NonNull
    private static CloudSyncSummary compare(@NonNull BackupContents contents,
                                            @Nullable List<StoreEntity> stores,
                                            @Nullable List<CompletionHistoryEntity> history) {

        List<String> cloudCodes = new ArrayList<>(contents.storeCount());
        for (BackupContents.BackupStore store : contents.stores) {
            cloudCodes.add(store.storeCode);
        }

        List<CloudSyncSummary.CompletionKey> cloudCompletions =
                new ArrayList<>(contents.completionCount());
        for (BackupContents.BackupCompletion completion : contents.completions) {
            cloudCompletions.add(new CloudSyncSummary.CompletionKey(
                    completion.storeCode, completion.roundNumber));
        }

        Set<String> localCodes = new HashSet<>();
        if (stores != null) {
            for (StoreEntity store : stores) {
                if (store != null) {
                    localCodes.add(store.storeCode);
                }
            }
        }

        Set<CloudSyncSummary.CompletionKey> localCompletions = new HashSet<>();
        if (history != null) {
            for (CompletionHistoryEntity completion : history) {
                if (completion == null) {
                    continue;
                }
                // The local rows carry a store id, and the cloud document carries a code. Resolving one to
                // the other needs the store list, so it is built here rather than assumed.
                String code = codeFor(stores, completion.storeId);
                if (code != null) {
                    localCompletions.add(
                            new CloudSyncSummary.CompletionKey(code, completion.roundNumber));
                }
            }
        }

        return CloudSyncSummary.of(cloudCodes, cloudCompletions, localCodes, localCompletions);
    }

    @Nullable
    private static String codeFor(@Nullable List<StoreEntity> stores, long storeId) {
        if (stores == null) {
            return null;
        }
        for (StoreEntity store : stores) {
            if (store != null && store.id == storeId) {
                return store.storeCode;
            }
        }
        return null;
    }

    private static int intOf(@NonNull DocumentSnapshot snapshot, @NonNull String field) {
        Long value = snapshot.getLong(field);
        return value == null ? 0 : value.intValue();
    }

    private static long longOf(@NonNull DocumentSnapshot snapshot, @NonNull String field) {
        Long value = snapshot.getLong(field);
        return value == null ? 0L : value;
    }

    private <T> void deliver(@NonNull RepositoryCallback<T> callback, @NonNull T value) {
        executors.postToMain(() -> callback.onResult(value));
    }
}

package com.cph.alpharoute.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.data.backup.BackupCodec;
import com.cph.alpharoute.data.backup.BackupContents;
import com.cph.alpharoute.data.backup.BackupValidator;
import com.cph.alpharoute.data.backup.ImportMode;
import com.cph.alpharoute.data.db.AppSettingDao;
import com.cph.alpharoute.data.db.BackupTransactionDao;
import com.cph.alpharoute.data.db.CompletionHistoryDao;
import com.cph.alpharoute.data.db.StoreDao;
import com.cph.alpharoute.util.AppExecutors;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writing and restoring a backup.
 *
 * <p><b>Phase 9.1 and 9.2.</b> The export reads the whole database and writes it as JSON; the restore reads
 * a file, has it validated, and then replaces or merges.
 *
 * <p><b>Nothing here touches a path the user did not choose.</b> Both directions work on streams the caller
 * opened - the Settings screen uses the system file picker, so the app never asks for a storage permission
 * and never guesses where a backup should live. That also makes this class testable without a filesystem.
 *
 * <p><b>The restore is the most destructive operation in the app</b>, and it is treated that way: validation
 * happens in a pure class before anything is written, the write is one transaction that throws to roll back,
 * and the mode is chosen explicitly by the user rather than defaulted.
 */
public class BackupRepository {

    private static final String TAG = "AlphaRouteBackup";

    private final StoreDao storeDao;
    private final CompletionHistoryDao completionHistoryDao;
    private final AppSettingDao appSettingDao;
    private final BackupTransactionDao backupTransactionDao;
    private final AppExecutors executors;

    public BackupRepository(@NonNull StoreDao storeDao,
                            @NonNull CompletionHistoryDao completionHistoryDao,
                            @NonNull AppSettingDao appSettingDao,
                            @NonNull BackupTransactionDao backupTransactionDao,
                            @NonNull AppExecutors executors) {
        this.storeDao = storeDao;
        this.completionHistoryDao = completionHistoryDao;
        this.appSettingDao = appSettingDao;
        this.backupTransactionDao = backupTransactionDao;
        this.executors = executors;
    }

    // ------------------------------------------------------------------ export

    /**
     * Writes the whole database to a stream.
     *
     * <p>Runs on the disk thread and reads the three tables in one go, so the file describes a single moment
     * rather than three. A backup taken while a completion was landing would otherwise be able to contain a
     * store marked COMPLETED and no completion row - exactly the state the validator refuses.
     *
     * @param out    where to write. The caller owns it and is responsible for closing it
     * @param callback called on the main thread with the number of bytes written, or {@code -1} on failure
     */
    public void exportTo(@NonNull OutputStream out,
                         @NonNull RepositoryCallback<Integer> callback) {
        long now = System.currentTimeMillis();

        executors.diskIO().execute(() -> {
            int written = -1;
            try {
                String json = BackupCodec.toJson(
                        storeDao.getAllNow(),
                        completionHistoryDao.getAllNow(),
                        appSettingDao.getNow(),
                        now);

                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                out.write(bytes);
                out.flush();
                written = bytes.length;

            } catch (JSONException e) {
                // The database held something that could not be written as JSON. Nothing was produced, and
                // the user is told rather than left with a truncated file they might trust.
                android.util.Log.e(TAG, "Could not build the backup", e);
            } catch (IOException e) {
                // The destination refused the write - a full card, a revoked URI. Same report.
                android.util.Log.e(TAG, "Could not write the backup", e);
            } finally {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Closing a stream that already failed is not worth reporting on top of the failure.
                }
            }

            final int result = written;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    /**
     * Builds the backup as a string, without a file.
     *
     * <p>Used by the export to check what it would write, and by tests. It reads the same three tables the
     * file export does, so the two cannot produce different documents.
     */
    public void buildBackupText(@NonNull RepositoryCallback<String> callback) {
        long now = System.currentTimeMillis();

        executors.diskIO().execute(() -> {
            String json = null;
            try {
                json = BackupCodec.toJson(
                        storeDao.getAllNow(),
                        completionHistoryDao.getAllNow(),
                        appSettingDao.getNow(),
                        now);
            } catch (JSONException e) {
                android.util.Log.e(TAG, "Could not build the backup", e);
            }
            final String result = json;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    // ------------------------------------------------------------------ import

    /**
     * Reads a backup file and validates it, without writing anything.
     *
     * <p><b>The read and the check are one step from the caller's side, and no step writes.</b> The screen
     * needs to show the user what a file contains and whether it can be restored <i>before</i> they decide -
     * and a preview that had already written something would not be a preview.
     *
     * @param callback called on the main thread with the parsed contents, or {@code null} when the file
     *                 could not be read as a backup at all
     */
    public void readAndValidate(@NonNull InputStream in,
                                @NonNull RepositoryCallback<BackupContents> callback) {
        executors.diskIO().execute(() -> {
            BackupContents contents = null;
            try {
                contents = BackupCodec.parse(readFully(in));
            } catch (IOException e) {
                android.util.Log.w(TAG, "Could not read the backup file", e);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Nothing useful to do - the read has already finished or failed.
                }
            }

            final BackupContents result = contents;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    /** Validates parsed contents. Pure, and separate from the read so it can be used on their own. */
    @NonNull
    public static List<BackupValidator.Problem> validate(@Nullable BackupContents contents) {
        return BackupValidator.validate(contents);
    }

    /**
     * Restores a backup.
     *
     * <p><b>The contents must already have been validated.</b> This does not check them again, because a
     * second set of file rules here would be a second definition of "valid" - free to disagree with the
     * first, and the one that ran last would win.
     *
     * <p>The whole restore is one transaction. A failure throws inside it, Room rolls everything back, and
     * the callback reports {@code null} - so the user is told the restore failed and their data is exactly
     * as it was.
     *
     * @param callback called on the main thread with what was restored, or {@code null} on failure
     */
    public void restore(@NonNull BackupContents contents,
                        @NonNull ImportMode mode,
                        @NonNull RepositoryCallback<BackupTransactionDao.ImportOutcome> callback) {
        long now = System.currentTimeMillis();

        executors.diskIO().execute(() -> {
            BackupTransactionDao.ImportOutcome outcome = null;
            try {
                outcome = backupTransactionDao.importAll(contents, mode, now);
            } catch (Exception e) {
                // Rolled back. The message names the table or constraint that refused, which is the only
                // clue about a malformed file that passed validation.
                android.util.Log.e(TAG, "The restore failed and was rolled back", e);
            }

            final BackupTransactionDao.ImportOutcome result = outcome;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    // ------------------------------------------------------------------ helpers

    private static String readFully(@NonNull InputStream in) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}

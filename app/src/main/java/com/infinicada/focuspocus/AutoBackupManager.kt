package com.infinicada.focuspocus

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri

enum class AutoBackupCadence(val intervalMillis: Long) {
    OFF(0L),
    DAILY(24L * 60 * 60 * 1000),
    WEEKLY(7L * 24 * 60 * 60 * 1000);

    companion object {
        fun fromStored(name: String?): AutoBackupCadence =
            entries.find { it.name == name } ?: OFF
    }
}

data class AutoBackupState(
    val treeUri: String? = null,
    val cadence: AutoBackupCadence = AutoBackupCadence.OFF,
    val lastBackupMillis: Long = 0L
)

/**
 * Automatic background backup into a user-selected Storage Access Framework
 * folder (e.g. a Syncthing, Nextcloud, or Google Drive synced folder). Uses raw
 * [DocumentsContract] APIs so no extra androidx.documentfile dependency is needed.
 */
object AutoBackupManager {
    private const val TAG = "AutoBackupManager"
    const val BACKUP_FILENAME = "focus-pocus-auto-backup.json"
    private const val MIME_JSON = "application/json"

    /** After a failed attempt, the next automatic one waits this long. */
    const val RETRY_AFTER_FAILURE_MS = 60L * 60 * 1000

    fun readState(prefs: SharedPreferences): AutoBackupState = AutoBackupState(
        treeUri = prefs.getString(Constants.PrefsKeys.AUTO_BACKUP_TREE_URI, null),
        cadence = AutoBackupCadence.fromStored(prefs.getString(Constants.PrefsKeys.AUTO_BACKUP_CADENCE, null)),
        lastBackupMillis = prefs.getLong(Constants.PrefsKeys.AUTO_BACKUP_LAST_MILLIS, 0L)
    )

    fun setTreeUri(prefs: SharedPreferences, treeUri: String?) {
        prefs.edit {
            if (treeUri == null) remove(Constants.PrefsKeys.AUTO_BACKUP_TREE_URI)
            else putString(Constants.PrefsKeys.AUTO_BACKUP_TREE_URI, treeUri)
        }
    }

    fun setCadence(prefs: SharedPreferences, cadence: AutoBackupCadence) {
        prefs.edit {
            putString(Constants.PrefsKeys.AUTO_BACKUP_CADENCE, cadence.name)
        }
    }

    /** Next instant an automatic backup should run, or null when off / unconfigured. */
    fun nextDueMillis(prefs: SharedPreferences, nowMillis: Long): Long? {
        val state = readState(prefs)
        if (state.cadence == AutoBackupCadence.OFF || state.treeUri.isNullOrBlank()) return null
        val next = dueMillis(prefs, state)
        return if (next > nowMillis) next else nowMillis + 60_000L
    }

    fun isDue(prefs: SharedPreferences, nowMillis: Long): Boolean {
        val state = readState(prefs)
        if (state.cadence == AutoBackupCadence.OFF || state.treeUri.isNullOrBlank()) return false
        return nowMillis >= dueMillis(prefs, state)
    }

    /**
     * When the next automatic backup is due: a cadence after the last
     * success (at once if there has been none), but never sooner than
     * [RETRY_AFTER_FAILURE_MS] after a failed attempt. Without the back-off a
     * revoked folder re-armed an exact alarm every minute.
     */
    private fun dueMillis(prefs: SharedPreferences, state: AutoBackupState): Long {
        val byCadence = if (state.lastBackupMillis <= 0L) 0L
        else state.lastBackupMillis + state.cadence.intervalMillis
        val lastFailure = prefs.getLong(Constants.PrefsKeys.AUTO_BACKUP_LAST_FAILURE_MILLIS, 0L)
        val byRetry = if (lastFailure > state.lastBackupMillis) lastFailure + RETRY_AFTER_FAILURE_MS else 0L
        return maxOf(byCadence, byRetry)
    }

    fun runIfDue(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!isDue(prefs, nowMillis)) return false
        return runNow(context, nowMillis)
    }

    /** Writes a fresh backup JSON into the chosen SAF folder right now. */
    fun runNow(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val treeUriStr = prefs.getString(Constants.PrefsKeys.AUTO_BACKUP_TREE_URI, null) ?: return false
        val error = try {
            if (write(context, prefs, treeUriStr, nowMillis)) null else "could not write the file"
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed", e)
            e.message ?: e.javaClass.simpleName
        }
        if (error == null) {
            prefs.edit {
                putLong(Constants.PrefsKeys.AUTO_BACKUP_LAST_MILLIS, nowMillis)
                remove(Constants.PrefsKeys.AUTO_BACKUP_LAST_FAILURE_MILLIS)
            }
            EnforcementLog.record(prefs, EnforcementLog.CAT_BACKUP, "Auto-backup saved ($BACKUP_FILENAME)", nowMillis)
            return true
        }
        // Log the first failure of a run of them, not every hourly retry.
        val lastFailure = prefs.getLong(Constants.PrefsKeys.AUTO_BACKUP_LAST_FAILURE_MILLIS, 0L)
        val lastSuccess = prefs.getLong(Constants.PrefsKeys.AUTO_BACKUP_LAST_MILLIS, 0L)
        if (lastFailure <= lastSuccess) {
            EnforcementLog.record(prefs, EnforcementLog.CAT_BACKUP, "Auto-backup failed: $error", nowMillis)
        }
        prefs.edit { putLong(Constants.PrefsKeys.AUTO_BACKUP_LAST_FAILURE_MILLIS, nowMillis) }
        return false
    }

    /** Writes the backup file into the chosen folder; true when it was written. */
    private fun write(
        context: Context,
        prefs: SharedPreferences,
        treeUriStr: String,
        nowMillis: Long
    ): Boolean {
        val treeUri = treeUriStr.toUri()
        val json = BackupCodec.export(prefs, BuildConfig.VERSION_CODE, nowMillis)
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        var targetDocUri: Uri? = null
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name == BACKUP_FILENAME) {
                    targetDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                    break
                }
            }
        }

        val docUri = targetDocUri
            ?: DocumentsContract.createDocument(resolver, parentDocUri, MIME_JSON, BACKUP_FILENAME)
            ?: return false

        val stream = try {
            resolver.openOutputStream(docUri, "wt")
        } catch (_: Exception) {
            resolver.openOutputStream(docUri)
        }
        return stream?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
            true
        } ?: false
    }
}

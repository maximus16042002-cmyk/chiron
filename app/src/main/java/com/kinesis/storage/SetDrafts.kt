package com.kinesis.storage

import android.content.Context
import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.core.domain.session.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** What [SetDrafts.recover] did with one draft. */
sealed interface DraftRecovery {
    /** A set the app did not get to end, now saved as a session. */
    data class Recovered(val session: SessionRecord) : DraftRecovery

    /** The draft's session had been saved already; the draft was only left behind. */
    data class AlreadySaved(val sessionId: String) : DraftRecovery

    /** The draft could not be read. It is kept beside, under [keptAs], and never retried. */
    data class Unreadable(val keptAs: File) : DraftRecovery
}

/**
 * Drafts of repetition sets in progress, for one account - DECISIONS.md P3-66.
 *
 * Until P3-66 a set existed only in memory until it ended, so a set under way when the app was
 * killed was lost (P3-16). Now, after every completed repetition record, the set so far is
 * written to [directory] as the session ending it then would save, in the export's format
 * (CONTRACTS.md section 10). Ending the set normally discards its draft; a failed save keeps it;
 * and when the account's storage next opens, [recover] saves whatever is left.
 *
 * **One file per set, one directory per account.** Discarding a finished set's draft can then
 * never touch the draft of the set that replaced it (switching camera ends one set and starts
 * another at once), and one account's recovery never sees another's drafts.
 *
 * Writes go to [executor], a single background thread in the app, so the camera thread never
 * touches a file (CLAUDE.md section 6), and they stay in order with [discard]. Each write is a
 * temporary file renamed over the draft, so a kill mid-write leaves the previous draft whole.
 */
class SetDrafts(
    private val directory: File,
    private val executor: Executor,
) {

    /** Replaces [session]'s draft. Returns at once; the write happens on [executor]. */
    fun write(session: SessionRecord) {
        executor.execute {
            directory.mkdirs()
            val file = fileFor(session.sessionId)
            val text = SessionExport.json(listOf(session), emptyList(), session.startedAtEpochMs)
            val temporary = File(directory, file.name + ".tmp")
            temporary.writeText(text)
            // Atomic on the phone's file system. Where a rename cannot replace a file, delete first.
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
        }
    }

    /** The set ended and was saved, or had nothing to save: its draft is not needed. */
    fun discard(sessionId: String) {
        executor.execute { fileFor(sessionId).delete() }
    }

    /**
     * Saves every draft left by a set the app did not get to end - the app was killed, or its
     * save failed - through [repository], so each syncs like any set. Call when the account's
     * storage opens, before a new set starts.
     */
    suspend fun recover(repository: SessionRepository): List<DraftRecovery> {
        val files = withContext(Dispatchers.IO) {
            directory.listFiles { file -> file.isFile && file.name.endsWith(DRAFT) }.orEmpty().sortedBy { it.name }
        }
        if (files.isEmpty()) return emptyList()
        val stored = repository.allSessions().map { it.sessionId }.toHashSet()
        return files.map { file -> recoverOne(file, stored, repository) }
    }

    private suspend fun recoverOne(file: File, stored: MutableSet<String>, repository: SessionRepository): DraftRecovery {
        val text = withContext(Dispatchers.IO) { file.readText() }
        val session = try {
            SessionExport.readSessions(text).single()
        } catch (error: IllegalArgumentException) {
            return setAside(file)
        } catch (error: NoSuchElementException) {
            return setAside(file)
        }
        if (!stored.add(session.sessionId)) {
            withContext(Dispatchers.IO) { file.delete() }
            return DraftRecovery.AlreadySaved(session.sessionId)
        }
        repository.save(session)
        withContext(Dispatchers.IO) { file.delete() }
        return DraftRecovery.Recovered(session)
    }

    private suspend fun setAside(file: File): DraftRecovery = withContext(Dispatchers.IO) {
        val kept = File(directory, file.name.removeSuffix(DRAFT) + UNREADABLE)
        kept.delete()
        file.renameTo(kept)
        DraftRecovery.Unreadable(kept)
    }

    private fun fileFor(sessionId: String): File = File(directory, safe(sessionId) + DRAFT)

    companion object {
        private const val DRAFT = ".json"
        private const val UNREADABLE = ".unreadable"

        /** Session ids are UUIDs; anything else is reduced to what a file name can safely hold. */
        private fun safe(sessionId: String): String = sessionId.replace(Regex("[^A-Za-z0-9-]"), "_")

        /** The account's directory, named after its database (P3-60), under `set-drafts/`. */
        fun directoryName(databaseName: String): String = "set-drafts/" + databaseName.removeSuffix(".db")

        /** One background writer for every draft in the process. */
        private val writer: Executor by lazy { Executors.newSingleThreadExecutor() }

        fun forAccount(context: Context, uid: String): SetDrafts {
            val name = AccountDatabaseNames.forAccount(uid, PreferencesAccountRegistry(context))
            return SetDrafts(File(context.applicationContext.filesDir, directoryName(name)), writer)
        }
    }
}

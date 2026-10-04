package com.kinesis.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kinesis.storage.KinesisDatabase
import com.kinesis.storage.RoomSessionRepository
import java.util.concurrent.TimeUnit

/**
 * Uploads whatever has not reached the account yet - DECISIONS.md P3-14.
 *
 * ### Why a worker and not a coroutine at the call site
 *
 * The session is already on disk by the time this is scheduled, so the upload
 * can take as long as it needs and can survive the app being killed. A coroutine
 * launched from a screen dies with the screen; a user who records a set and
 * immediately closes the app would silently never sync it.
 *
 * ### Why it reconciles rather than uploading one session
 *
 * The worker is given no session id. It asks what the account is missing and
 * pushes all of it, which makes it idempotent: running it twice, or running it
 * after a crash mid-queue, converges on the same state. A per-session worker
 * would need its own durable queue, and that queue would be a second source of
 * truth about what has been uploaded.
 *
 * A failed push returns [Result.retry] and WorkManager applies exponential
 * backoff. Nothing is lost in the meantime - the local copy is the source of
 * truth and the next run finds it again.
 */
class SessionSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID) ?: return Result.success()

        // This account's own database - DECISIONS.md P3-60.
        val local = RoomSessionRepository(KinesisDatabase.forAccount(applicationContext, uid).sessionDao())
        val merge = SessionMerge(local, FirestoreSessionStore())

        return try {
            val failed = merge.pushMissing(uid)
            if (failed.isEmpty()) Result.success() else Result.retry()
        } catch (error: Exception) {
            // Never Result.failure() for a transient problem: that drops the
            // work permanently. The local session is safe either way.
            Result.retry()
        }
    }

    companion object {
        const val KEY_UID: String = "uid"
        /** One queue per account (P3-60): another account's queued upload never absorbs or blocks this one. */
        private const val WORK_NAME = "kinesis-session-sync"

        /**
         * Schedules a reconciliation.
         *
         * `KEEP` rather than `REPLACE`: the worker uploads everything missing,
         * so a run already queued will pick up this session too, and replacing
         * it would reset the backoff on every save.
         */
        fun schedule(context: Context, uid: String) {
            val request = OneTimeWorkRequestBuilder<SessionSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(androidx.work.Data.Builder().putString(KEY_UID, uid).build())
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME + "-" + uid, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/**
 * The [SyncQueue] the app uses. Scheduling only - it never touches the network
 * itself, which is what keeps a Firestore outage away from the local write path.
 */
class WorkManagerSyncQueue(
    private val context: Context,
    private val uid: () -> String?,
) : SyncQueue {

    override fun enqueue(sessionId: String) {
        val currentUid = uid() ?: return
        SessionSyncWorker.schedule(context, currentUid)
    }
}

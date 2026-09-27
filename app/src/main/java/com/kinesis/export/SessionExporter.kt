package com.kinesis.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kinesis.storage.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes an export to the app's cache and hands it to the Android share sheet.
 *
 * Nothing is uploaded: the files go wherever the user sends them, from the
 * share sheet, and nowhere if they cancel. CLAUDE.md section 16's rule is about
 * the app sending data; a file the user explicitly shares is theirs to send.
 *
 * Each export replaces the last one in `cache/exports/`, so the cache holds at
 * most one export's worth of history and it does not accumulate.
 */
class SessionExporter(
    private val context: Context,
    private val repository: SessionRepository,
) {

    /** Builds the export and returns the chooser to start, or `null` when there is nothing to export. */
    suspend fun shareIntent(format: ExportFormat, nowEpochMs: Long): Intent? {
        val sessions = repository.allSessions()
        if (sessions.isEmpty()) return null

        val files = ExportFiles.build(format, sessions, nowEpochMs.toDouble(), stamp = nowEpochMs.toString())
        val uris = withContext(Dispatchers.IO) { write(files) }
        val mime = files.first().mimeType

        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = mime
        send.putExtra(Intent.EXTRA_SUBJECT, "Kinesis sessions")
        // The grant covers every file only if every URI is in the ClipData too.
        send.clipData = ClipData.newUri(context.contentResolver, "Kinesis export", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Export sessions")
    }

    private fun write(files: List<ExportFile>): List<Uri> {
        val directory = File(context.cacheDir, EXPORT_DIRECTORY)
        directory.deleteRecursively()
        directory.mkdirs()
        return files.map { file ->
            val target = File(directory, file.name)
            target.writeText(file.contents, Charsets.UTF_8)
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, target)
        }
    }

    private companion object {
        /** Matches `res/xml/export_paths.xml`. */
        const val EXPORT_DIRECTORY = "exports"

        /** Matches the provider's authority in the manifest. */
        const val AUTHORITY_SUFFIX = ".exports"
    }
}

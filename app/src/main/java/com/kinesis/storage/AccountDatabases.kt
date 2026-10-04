package com.kinesis.storage

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest

/**
 * Small key-value storage for which account owns which database file. Shared preferences in the
 * app; a map in tests.
 */
interface AccountRegistry {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

/**
 * Which database file each account uses - DECISIONS.md P3-60.
 *
 * Until P3-60 every account that signed in on the phone shared one database, and sign-in
 * uploaded every session in it to whoever had just signed in. Now each account has its own file,
 * so its sessions and baselines, and everything read from them, are its own.
 *
 * The file that already existed, [KinesisDatabase.NAME], is adopted by the first account to sign
 * in after the update and stays that account's. On the owner's phone that is the owner, already
 * signed in, so no session moves. Every later account gets a file named after its id, or after a
 * hash of it when the id is not safe in a file name.
 */
object AccountDatabaseNames {

    /** The account that adopted [KinesisDatabase.NAME]. */
    const val LEGACY_OWNER: String = "legacy_database_owner"

    /** The account signed in most recently. Read by work that runs with nobody on screen. */
    const val CURRENT_ACCOUNT: String = "current_account"

    fun forAccount(uid: String, registry: AccountRegistry): String {
        require(uid.isNotEmpty()) { "an account id is needed to choose a database" }
        val legacyOwner = registry.read(LEGACY_OWNER)
        if (legacyOwner == null) {
            registry.write(LEGACY_OWNER, uid)
            return KinesisDatabase.NAME
        }
        if (legacyOwner == uid) return KinesisDatabase.NAME
        return "kinesis-" + fileSafe(uid) + ".db"
    }

    /** Firebase ids are letters and digits; anything else is hashed rather than trusted in a path. */
    private fun fileSafe(uid: String): String =
        if (SAFE_ID.matches(uid)) uid else sha256Hex(uid)

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,128}")
}

/**
 * The registry in the app's preferences. Writes are committed before returning, because the
 * adoption must be recorded before the database it names is opened.
 */
class PreferencesAccountRegistry(context: Context) : AccountRegistry {

    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        preferences.edit(commit = true) { putString(key, value) }
    }

    private companion object {
        const val FILE = "kinesis-accounts"
    }
}

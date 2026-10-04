package com.kinesis.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which database file each account uses - DECISIONS.md P3-60.
 *
 * The phone used to keep one database for whoever signed in, so sign-in uploaded every session on
 * the phone to the account that had just signed in. Each account now has its own file. The file
 * that already exists stays with the first account to sign in after the update, which on the
 * owner's phone is the owner.
 */
class AccountDatabaseNamesTest {

    /** The stored registry, as a map, so a fresh read of the same storage can be simulated. */
    private class MapRegistry(val stored: MutableMap<String, String> = HashMap()) : AccountRegistry {
        override fun read(key: String): String? = stored[key]
        override fun write(key: String, value: String) {
            stored[key] = value
        }
    }

    private val owner = "Q8ZxL2owner0000000000000000a"
    private val friend = "M3kfriend00000000000000000bb"
    private val third = "Z1third000000000000000000ccc"

    @Test
    fun the_first_account_after_the_update_keeps_the_existing_database() {
        assertEquals(KinesisDatabase.NAME, AccountDatabaseNames.forAccount(owner, MapRegistry()))
    }

    @Test
    fun the_same_account_always_gets_the_same_database_even_from_a_fresh_read() {
        val stored = HashMap<String, String>()
        val first = AccountDatabaseNames.forAccount(owner, MapRegistry(stored))
        AccountDatabaseNames.forAccount(friend, MapRegistry(stored))

        assertEquals(first, AccountDatabaseNames.forAccount(owner, MapRegistry(stored)))
        assertEquals(
            AccountDatabaseNames.forAccount(friend, MapRegistry(stored)),
            AccountDatabaseNames.forAccount(friend, MapRegistry(stored)),
        )
    }

    /** The defect itself: a second account must never open the first one's sessions. */
    @Test
    fun a_second_account_gets_its_own_database_and_never_the_first_ones() {
        val registry = MapRegistry()
        val ownersFile = AccountDatabaseNames.forAccount(owner, registry)
        val friendsFile = AccountDatabaseNames.forAccount(friend, registry)

        assertNotEquals(ownersFile, friendsFile)
        assertNotEquals(KinesisDatabase.NAME, friendsFile)
    }

    @Test
    fun two_later_accounts_never_share_a_database() {
        val registry = MapRegistry()
        AccountDatabaseNames.forAccount(owner, registry)
        assertNotEquals(
            AccountDatabaseNames.forAccount(friend, registry),
            AccountDatabaseNames.forAccount(third, registry),
        )
    }

    @Test
    fun an_id_unsafe_for_a_file_name_is_hashed_never_used_raw() {
        val registry = MapRegistry()
        AccountDatabaseNames.forAccount(owner, registry)
        val a = AccountDatabaseNames.forAccount("../../shared_prefs/x", registry)
        val b = AccountDatabaseNames.forAccount("../../shared_prefs/y", registry)

        for (name in listOf(a, b)) {
            assertTrue(name, name.matches(Regex("kinesis-[A-Za-z0-9_-]+\\.db")))
        }
        assertNotEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun an_empty_account_id_is_refused() {
        AccountDatabaseNames.forAccount("", MapRegistry())
    }
}

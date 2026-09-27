package com.kinesis.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The database version cannot move without a migration and an exported schema -
 * DECISIONS.md P3-22.
 *
 * Room refuses to open a database it has no migration path for, and the builder
 * has no destructive fallback, so a forgotten migration would not wipe anyone's
 * sessions - it would stop the app opening its database at all. This catches it
 * on the JVM, in the change that bumps the version, rather than on a phone.
 */
class DatabaseVersionGuardTest {

    private val schemas = File("schemas/com.kinesis.storage.KinesisDatabase")

    @Test
    fun migrations_run_from_version_1_to_the_current_version_without_a_gap() {
        val steps = KinesisDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until KinesisDatabase.VERSION).map { it to it + 1 }, steps)
    }

    @Test
    fun every_version_has_its_exported_schema() {
        for (version in 1..KinesisDatabase.VERSION) {
            val schema = File(schemas, "$version.json")
            assertTrue("no exported schema for version $version at ${schema.absolutePath}", schema.isFile)
            assertTrue(schema.readText().contains("\"version\": $version,"))
        }
    }

    /** Destructive fallback discards every session on a version it cannot migrate. */
    @Test
    fun the_builder_never_falls_back_to_a_destructive_migration() {
        val source = File("src/main/java/com/kinesis/storage/KinesisDatabase.kt").readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\n]*"""), "")
        assertTrue(!source.contains("fallbackToDestructiveMigration"))
        assertTrue(source.contains(".addMigrations(*MIGRATIONS.toTypedArray())"))
    }
}

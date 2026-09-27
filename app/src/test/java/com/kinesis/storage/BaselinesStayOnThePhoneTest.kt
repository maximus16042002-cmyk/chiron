package com.kinesis.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Baselines stay on the phone - DECISIONS.md P3-22.
 *
 * The sign-in disclosure (P3-14) says completed sessions leave the phone. It
 * stays true only while nothing uploads a baseline, so three independent things
 * are checked, any one of which would be enough on its own:
 *
 * 1. no source file in the sync package refers to a baseline type;
 * 2. the only repository sync wraps is the session repository;
 * 3. the Firestore rules accept writes under a user's `sessions` and nowhere
 *    else, so even an upload that got written would be refused.
 */
class BaselinesStayOnThePhoneTest {

    private val main = File("src/main/java/com/kinesis")

    private val baselineTypes = Regex(
        """\b(CalibrationBaseline|RepetitionBaseline|HoldBaseline|BaselineRepository|RoomBaselineRepository|""" +
            """BaselineEntity|BaselineDao|BaselineMapping|BaselineJson|baselineDao)\b""",
    )

    @Test
    fun nothing_in_the_sync_package_touches_a_baseline() {
        val sync = File(main, "sync").listFiles().orEmpty().filter { it.extension == "kt" }
        assertTrue("the sync package was not found, so this proves nothing", sync.size >= 5)
        val offences = sync.flatMap { file ->
            stripComments(file.readText()).lineSequence().withIndex()
                .filter { baselineTypes.containsMatchIn(it.value) }
                .map { file.name + ":" + (it.index + 1) + "  " + it.value.trim() }
                .toList()
        }
        assertEquals("baseline code in the sync package - it would leave the phone", emptyList<String>(), offences)
    }

    @Test
    fun sync_wraps_the_session_repository_and_nothing_else() {
        val wrapped = Regex("""\bclass\s+(\w+)\s*\(\s*private val delegate:\s*(\w+)""")
        val decorators = File(main, "sync").listFiles().orEmpty().filter { it.extension == "kt" }
            .flatMap { wrapped.findAll(it.readText()).map { match -> match.groupValues[1] + "(" + match.groupValues[2] + ")" }.toList() }
        assertEquals(listOf("SyncingSessionRepository(SessionRepository)"), decorators)
    }

    @Test
    fun the_firestore_rules_accept_writes_only_under_a_users_sessions() {
        val rules = stripRuleComments(File("../firestore.rules").readText())
        val matches = Regex("""match\s+(/\S+)\s*\{""").findAll(rules).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("/databases/{database}/documents", "/users/{userId}/sessions/{sessionId}", "/{document=**}"),
            matches,
        )
        assertTrue("the catch-all must refuse everything", rules.contains("allow read, write: if false;"))
    }

    /** The disclosure itself: it names sessions, and nothing in it has to change. */
    @Test
    fun the_disclosure_describes_sessions_leaving_and_nothing_more() {
        val screen = File(main, "ui/screens/AuthScreen.kt").readText()
        assertTrue(screen.contains("Your completed sessions"))
        assertTrue(!screen.contains("baseline", ignoreCase = true))
    }

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*[\s\S]*?\*/""")) { match -> "\n".repeat(match.value.count { it == '\n' }) }
            .replace(Regex("""//[^\n]*"""), "")

    private fun stripRuleComments(rules: String): String = rules.replace(Regex("""//[^\n]*"""), "")
}

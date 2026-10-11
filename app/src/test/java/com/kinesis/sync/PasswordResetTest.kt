package com.kinesis.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Forgot password?" request - DECISIONS.md P3-80. */
class PasswordResetTest {

    @Test
    fun a_sent_request_says_so_without_saying_whether_the_account_exists() {
        val line = PasswordReset.messageFor(null)
        assertEquals(PasswordReset.SENT, line)
        assertTrue(line.startsWith("If an account uses that email"))
    }

    @Test
    fun each_failure_says_what_to_do() {
        assertTrue(PasswordReset.messageFor(AuthFailure.MALFORMED_EMAIL).contains("email address"))
        assertTrue(PasswordReset.messageFor(AuthFailure.NETWORK_UNAVAILABLE).startsWith("No network"))
        assertTrue(PasswordReset.messageFor(AuthFailure.UNKNOWN).contains("could not be sent"))
    }

    @Test
    fun an_address_that_cannot_be_one_is_refused_before_anything_is_sent() {
        for (bad in listOf("", "  ", "nitish", "nitish@", "@gmail.com", "a@b", "a@@b.com", "a@b.", "a@.com")) {
            assertFalse("accepted '$bad'", PasswordReset.isPlausibleEmail(bad))
        }
        for (good in listOf("nitish@gmail.com", " nitish@gmail.com ", "a.b@college.edu.in")) {
            assertTrue("refused '$good'", PasswordReset.isPlausibleEmail(good))
        }
    }

    /** A gateway that does not implement it - a test fake - reports a failure, never a false "sent". */
    @Test
    fun a_gateway_without_reset_never_claims_it_sent_one() = runBlocking {
        val gateway = object : AuthGateway {
            override val state: AuthState = AuthState.SignedOut
            override suspend fun signIn(email: String, password: String) = AuthResult.Failed(AuthFailure.UNKNOWN)
            override suspend fun signUp(email: String, password: String) = AuthResult.Failed(AuthFailure.UNKNOWN)
            override suspend fun signOut() = Unit
        }
        assertEquals(AuthFailure.UNKNOWN, gateway.sendPasswordReset("nitish@gmail.com"))
    }
}

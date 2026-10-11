package com.kinesis.sync

/**
 * Account state and sign-in - DECISIONS.md P3-14.
 *
 * ### Named states, not exceptions
 *
 * The same discipline P3-11 applied to Health Connect and P3-13 to the pose
 * model: everything that can realistically go wrong is a value the caller has to
 * handle, not something thrown past them. Authentication fails for mundane
 * reasons constantly - a typo, an address already registered, no signal on a gym
 * wifi - and none of those should be able to take a screen down.
 */

/** Who is signed in, if anyone. */
sealed interface AuthState {

    /** Not yet determined. Firebase restores a session asynchronously on launch. */
    data object Unknown : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val uid: String, val email: String?) : AuthState
}

/** Why an attempt failed. Each is a different sentence to the user. */
enum class AuthFailure {

    /** Wrong password, or no account for that address. */
    INVALID_CREDENTIALS,

    /** Sign-up against an address that already has an account. */
    EMAIL_ALREADY_IN_USE,

    /** The address is not a valid email. */
    MALFORMED_EMAIL,

    /** Firebase's own minimum, which the UI does not duplicate or guess at. */
    WEAK_PASSWORD,

    /**
     * No usable network.
     *
     * Kept distinct from every other failure because it is the one the user can
     * do nothing about and the one where retrying later genuinely helps.
     */
    NETWORK_UNAVAILABLE,

    /** Anything else. Reported honestly rather than guessed at. */
    UNKNOWN,
}

/** The outcome of a sign-in or sign-up attempt. */
sealed interface AuthResult {
    data class Success(val uid: String) : AuthResult
    data class Failed(val reason: AuthFailure) : AuthResult
}

/**
 * Sign-in, behind an interface so the screens and the sync logic can be tested
 * without Firebase, a network or a device.
 */
interface AuthGateway {

    /** The current state. `Unknown` until Firebase has restored any session. */
    val state: AuthState

    suspend fun signIn(email: String, password: String): AuthResult

    suspend fun signUp(email: String, password: String): AuthResult

    suspend fun signOut()

    /**
     * Asks for a password-reset email to [email] - DECISIONS.md P3-80. `null` when the request was
     * made, or a reason it could not be. An address with no account is reported as sent, as Firebase
     * does, so the screen cannot be used to find out which addresses are registered.
     */
    suspend fun sendPasswordReset(email: String): AuthFailure? = AuthFailure.UNKNOWN
}

/** What the sign-in screen says after a reset request - DECISIONS.md P3-80. */
object PasswordReset {

    const val SENT: String =
        "If an account uses that email, a link to choose a new password is on its way. Check the inbox and spam."

    /** The line for an outcome: [SENT] when the request was made, otherwise why not. */
    fun messageFor(failure: AuthFailure?): String = when (failure) {
        null -> SENT
        AuthFailure.MALFORMED_EMAIL -> "Type the email address of your account first, then tap Forgot password."
        AuthFailure.NETWORK_UNAVAILABLE -> "No network. Sending a reset link needs one."
        else -> "The reset link could not be sent. Try again in a moment."
    }

    /** An address that cannot be one is refused before anything is sent. */
    fun isPlausibleEmail(email: String): Boolean {
        val trimmed = email.trim()
        val at = trimmed.indexOf('@')
        return at > 0 && at == trimmed.lastIndexOf('@') && trimmed.indexOf('.', at) > at + 1 && !trimmed.endsWith(".")
    }
}

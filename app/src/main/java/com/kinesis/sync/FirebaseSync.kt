package com.kinesis.sync

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.kinesis.core.domain.session.SessionRecord
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed [AuthGateway] - DECISIONS.md P3-14.
 *
 * Every realistic failure is mapped to a named [AuthFailure] rather than thrown,
 * because a typo in a password should not be able to take a screen down. The
 * mapping is exhaustive over the exceptions Firebase documents and falls back to
 * [AuthFailure.UNKNOWN] rather than guessing.
 */
class FirebaseAuthGateway(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthGateway {

    override val state: AuthState
        get() = auth.currentUser
            ?.let { AuthState.SignedIn(it.uid, it.email) }
            ?: AuthState.SignedOut

    override suspend fun signIn(email: String, password: String): AuthResult = attempt {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun signUp(email: String, password: String): AuthResult = attempt {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private suspend fun attempt(block: suspend () -> Any?): AuthResult = try {
        block()
        val uid = auth.currentUser?.uid
        if (uid == null) AuthResult.Failed(AuthFailure.UNKNOWN) else AuthResult.Success(uid)
    } catch (error: FirebaseNetworkException) {
        // The one the user can do nothing about, and the one worth retrying.
        AuthResult.Failed(AuthFailure.NETWORK_UNAVAILABLE)
    } catch (error: FirebaseAuthWeakPasswordException) {
        AuthResult.Failed(AuthFailure.WEAK_PASSWORD)
    } catch (error: FirebaseAuthUserCollisionException) {
        AuthResult.Failed(AuthFailure.EMAIL_ALREADY_IN_USE)
    } catch (error: FirebaseAuthInvalidCredentialsException) {
        // Firebase uses this for both a malformed address and a wrong password.
        val malformed = error.errorCode == "ERROR_INVALID_EMAIL"
        AuthResult.Failed(
            if (malformed) AuthFailure.MALFORMED_EMAIL else AuthFailure.INVALID_CREDENTIALS,
        )
    } catch (error: FirebaseAuthInvalidUserException) {
        // No account for that address. Reported as invalid credentials rather
        // than "no such user", which would let anyone probe for registered
        // addresses.
        AuthResult.Failed(AuthFailure.INVALID_CREDENTIALS)
    } catch (error: Exception) {
        AuthResult.Failed(AuthFailure.UNKNOWN)
    }
}

/**
 * Firestore-backed [RemoteSessionStore] - DECISIONS.md P3-14.
 *
 * ### The document path is the security boundary
 *
 * Everything is written under `users/{uid}/sessions/{sessionId}`. That shape is
 * what the security rules lock down - a user can read and write only under their
 * own uid - and it is why the rules in `firestore.rules` are short enough to
 * read in one sitting. **Those rules are not deployed by this code and must be
 * pasted into the console by hand**; until then the project is in test mode and
 * this path is not actually enforced.
 *
 * ### `NaN` again
 *
 * The same sentinel problem P3-12 hit with SQLite. `NaN` means "not measured"
 * throughout Kinesis, and it is written here as `null` and read back as `NaN`,
 * for the same reason and by the same convention - so a value that survives the
 * Room round trip survives this one identically.
 */
class FirestoreSessionStore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : RemoteSessionStore {

    override suspend fun push(uid: String, session: SessionRecord): SyncResult = try {
        firestore.collection(USERS).document(uid)
            .collection(SESSIONS).document(session.sessionId)
            .set(SessionDocument.toMap(session))
            .await()
        SyncResult.Pushed
    } catch (error: FirebaseFirestoreException) {
        SyncResult.Failed(
            when (error.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                -> SyncFailure.NETWORK_UNAVAILABLE

                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
                -> SyncFailure.NOT_PERMITTED

                else -> SyncFailure.UNKNOWN
            },
        )
    } catch (error: Exception) {
        SyncResult.Failed(SyncFailure.UNKNOWN)
    }

    override suspend fun pull(uid: String): List<SessionRecord> = try {
        firestore.collection(USERS).document(uid).collection(SESSIONS)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.data?.let(SessionDocument::fromMap) }
    } catch (error: Exception) {
        // A failed pull is an empty pull. The merge is a union, so nothing is
        // lost by returning less than everything - it is retried next sign-in.
        emptyList()
    }

    private companion object {
        const val USERS = "users"
        const val SESSIONS = "sessions"
    }
}

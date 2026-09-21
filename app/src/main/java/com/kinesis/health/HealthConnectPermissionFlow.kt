package com.kinesis.health

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.health.connect.client.PermissionController

/**
 * Asking the user for Health Connect access - DECISIONS.md P3-12.
 *
 * P3-11 built the read path and deliberately stopped short of this, because the
 * thing that should launch a permission request is a screen and there was none.
 * There is one now.
 *
 * ### Denial surfaces through the existing model, not a parallel one
 *
 * This flow does not invent a "permission denied" state. It re-asks
 * [ActivityContextSource.availability], and whatever that reports -
 * `PERMISSIONS_DENIED`, `NOT_INSTALLED`, `PLATFORM_TOO_OLD` - is what the screen
 * renders, through the same `ContextAvailability` and `DailyMetric` types P3-11
 * defined. A second path describing the same conditions in different words is
 * how two parts of an app start disagreeing about what the user granted.
 *
 * ### Per-metric, because the platform is per-metric
 *
 * Health Connect grants each read permission independently. A user can allow
 * steps and refuse heart rate, and that is an ordinary outcome rather than a
 * failure: the request asks for all three, and whatever comes back is
 * intersected with what Kinesis asked for. The metrics that were refused arrive
 * at the UI as `DailyMetric.NotPermitted`, which is already distinct from
 * `NoData`.
 *
 * Nothing here retries or nags. A refusal is an answer.
 */

/** What the permission screen needs to know. */
sealed interface PermissionUiState {

    /** Still asking the platform. */
    data object Checking : PermissionUiState

    /**
     * Health Connect is usable. [granted] may be empty, partial or complete -
     * all three are ordinary.
     */
    data class Usable(val granted: Set<String>) : PermissionUiState {
        val isComplete: Boolean get() = granted.containsAll(ActivityContextPermissions.ALL)
        val isEmpty: Boolean get() = granted.isEmpty()
    }

    /** Nothing to ask for. The reason is the one P3-11 already defined. */
    data class Unusable(val reason: ContextUnavailable) : PermissionUiState
}

/**
 * Tracks what Kinesis is allowed to read, and can ask for more.
 *
 * Held by the screen rather than a singleton, because permissions can change
 * while the app is backgrounded - the user can revoke them in system settings -
 * so the answer is re-read on resume rather than cached for the process.
 */
class HealthConnectPermissionState internal constructor(
    private val source: ActivityContextSource,
) {

    var current: PermissionUiState by mutableStateOf(PermissionUiState.Checking)
        private set

    /** Re-reads what is granted. Safe to call on every resume. */
    suspend fun refresh() {
        current = when (val availability = source.availability()) {
            is ContextAvailability.Available -> PermissionUiState.Usable(
                availability.grantedPermissions,
            )

            is ContextAvailability.Unavailable -> PermissionUiState.Unusable(availability.reason)
        }
    }

    /**
     * Called with whatever the system dialog returned.
     *
     * The result is not trusted on its own - [refresh] re-asks the platform,
     * because the contract reports what the dialog returned and the platform is
     * the authority on what is actually granted.
     */
    suspend fun onRequestResult() = refresh()
}

/**
 * The permission request contract, or `null` where Health Connect cannot exist.
 *
 * Kinesis supports API 24 and the library needs 26 (P3-11), so this is one of
 * the places the platform check has to happen before a Health Connect class is
 * touched at all.
 */
@RequiresApi(Build.VERSION_CODES.O)
fun healthPermissionContract(): ActivityResultContract<Set<String>, Set<String>> =
    PermissionController.createRequestPermissionResultContract()

/**
 * Remembers permission state for a screen, refreshing when it first appears.
 *
 * Returns `null` on a platform too old for Health Connect, so a caller cannot
 * accidentally build a request launcher that could never work.
 */
@Composable
fun rememberHealthConnectPermissions(
    source: ActivityContextSource,
): State<PermissionUiState> {
    val state = remember(source) { HealthConnectPermissionState(source) }
    return produceState<PermissionUiState>(
        initialValue = PermissionUiState.Checking,
        key1 = source,
    ) {
        state.refresh()
        value = state.current
    }
}

/**
 * A launcher for the system permission dialog, or `null` when there is nothing
 * to launch - too old a platform, or no provider.
 *
 * Deliberately returns `null` rather than a no-op launcher: a button that does
 * nothing when pressed is worse than a button that is not offered.
 */
@Composable
fun rememberHealthPermissionLauncher(
    onResult: (Set<String>) -> Unit,
): ((Set<String>) -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val launcher = rememberLauncherForActivityResult(
        contract = healthPermissionContract(),
        onResult = onResult,
    )
    return remember(launcher) { { permissions: Set<String> -> launcher.launch(permissions) } }
}

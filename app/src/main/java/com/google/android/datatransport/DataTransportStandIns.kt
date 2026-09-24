package com.google.android.datatransport

/*
 * Inert stand-ins for Google's datatransport API - DECISIONS.md P3-18.
 *
 * Kinesis's own code, not Google's: nothing here is copied from the library, only
 * the names and signatures MediaPipe's bytecode links against. Creating any
 * MediaPipe task builds a usage-stats logger (`RemoteLoggingClient`) that sends
 * through this API to Google's Clearcut service. P3-13 excluded the real library
 * so the app would send nothing, and without it task creation crashed on the
 * device with NoClassDefFoundError. These types let the logger construct and
 * give everything it logs nowhere to go: no uploader, no event store, no
 * scheduled job, no network - the code that would do any of that is not in the
 * APK at all.
 *
 * Exactly the members MediaPipe 0.10.21 links against, read from its bytecode.
 * `MediaPipeTelemetryStandInTest` re-reads that bytecode, so an upgrade that needs
 * more fails a JVM test, and fails the on-device test, before it crashes a user.
 *
 * If the real datatransport ever comes back - the P3-13 exclusion removed, or a
 * new transitive path - a **debug build still succeeds**: its app and library
 * code are dexed separately, so the duplicate classes are not detected, and
 * which copy a phone loads is undefined. Measured in P3-18. What does catch it:
 * `MediaPipeTelemetryStandInTest`'s absence check and `OfflineManifestGuardTest`
 * (both fail), and a release build's dex merge ("defined multiple times").
 */

/** A named payload encoding. Only the name MediaPipe asks for is kept. */
class Encoding private constructor(val name: String) {
    companion object {
        @JvmStatic
        fun of(name: String): Encoding = Encoding(name)
    }
}

/** An event to log. The payload is dropped here, the first point it could be kept. */
class Event<T> private constructor() {
    companion object {
        private val DISCARDED = Event<Any?>()

        @JvmStatic
        @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
        fun <T> ofData(payload: T): Event<T> = DISCARDED as Event<T>
    }
}

/** Turns a payload into bytes. Never called: nothing is ever encoded. */
fun interface Transformer<T, U> {
    fun apply(input: T): U
}

/** Where MediaPipe sends its events. */
interface Transport<T> {
    fun send(event: Event<T>)
}

interface TransportFactory {
    fun <T> getTransport(
        name: String,
        payloadType: Class<T>,
        payloadEncoding: Encoding,
        payloadTransformer: Transformer<T, ByteArray>,
    ): Transport<T>
}

/** The only factory and transport there are: every event is discarded. */
internal object DiscardingTransport : Transport<Any?>, TransportFactory {

    override fun send(event: Event<Any?>) = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <T> getTransport(
        name: String,
        payloadType: Class<T>,
        payloadEncoding: Encoding,
        payloadTransformer: Transformer<T, ByteArray>,
    ): Transport<T> = this as Transport<T>
}

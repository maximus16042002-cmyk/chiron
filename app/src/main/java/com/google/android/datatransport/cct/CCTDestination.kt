package com.google.android.datatransport.cct

import com.google.android.datatransport.runtime.Destination

/**
 * Inert stand-in - see DataTransportStandIns.kt and DECISIONS.md P3-18. In
 * Google's library this names the Clearcut endpoint MediaPipe reports to. Here
 * it names nothing: no endpoint, no uploader, and no code that could reach one.
 */
class CCTDestination private constructor() : Destination {
    companion object {
        @JvmField
        val INSTANCE: CCTDestination = CCTDestination()
    }
}

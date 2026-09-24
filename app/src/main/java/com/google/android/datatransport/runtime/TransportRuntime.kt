package com.google.android.datatransport.runtime

import android.content.Context
import com.google.android.datatransport.DiscardingTransport
import com.google.android.datatransport.TransportFactory

/*
 * Inert stand-ins - see DataTransportStandIns.kt and DECISIONS.md P3-18. Kinesis's
 * own code: nothing is initialised, scheduled, stored or sent.
 */

/** Where events would go. The only one there is, `CCTDestination`, goes nowhere. */
interface Destination

class TransportRuntime private constructor() {

    /** Every destination gets the same factory, whose transport discards. */
    @Suppress("UNUSED_PARAMETER")
    fun newFactory(destination: Destination): TransportFactory = DiscardingTransport

    companion object {
        private val INSTANCE = TransportRuntime()

        /** Nothing to set up: no event store, no scheduler, no uploader. */
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun initialize(context: Context?) = Unit

        @JvmStatic
        fun getInstance(): TransportRuntime = INSTANCE
    }
}

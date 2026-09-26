package com.kinesis.diagnostics

/**
 * Release builds: no pose model switch. They run the lite model only -
 * DECISIONS.md P3-21. The switch exists only in `src/debug`.
 */
@Suppress("UNUSED_PARAMETER")
fun switchablePoseModels(presentAssets: Set<String>): List<String> = emptyList()

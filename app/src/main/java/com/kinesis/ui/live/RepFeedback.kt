package com.kinesis.ui.live

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.View
import java.util.Locale

/**
 * Whether a published count is a newly counted repetition - DECISIONS.md P3-49.
 *
 * The live state is republished several times a second with the same count (CLAUDE.md section 6
 * keeps that rate low; this keeps the cue lower still): only a rise is a repetition. Two counted
 * between publications fire once, for the latest. A lower count is a new set starting.
 */
class RepCountWatcher {

    private var last = 0

    fun counted(count: Int): Boolean {
        val rose = count > last
        last = count
        return rose
    }
}

/**
 * The cue on a counted repetition, and at the end of a rest - DECISIONS.md P3-49: a short tone, a
 * vibration, and the count spoken aloud, each switched on separately in Profile and all off by
 * default.
 *
 * Platform APIs only, no dependency: `ToneGenerator`, the view's haptic feedback (no permission
 * needed), and `TextToSpeech`, Android's own on-device speech. A voice that needs the network is
 * not used: if the phone has no offline voice for its language, counts are not spoken.
 *
 * Driven by the repetition count changing, never by a frame (CLAUDE.md section 6).
 */
class RepFeedback(
    context: Context,
    private val tone: Boolean,
    private val vibrate: Boolean,
    speak: Boolean,
) {

    private val tones: ToneGenerator? = if (tone) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull() else null

    private var speech: TextToSpeech? = null
    private var speechReady = false

    init {
        if (speak) {
            speech = TextToSpeech(context.applicationContext) { status ->
                speechReady = status == TextToSpeech.SUCCESS && useOfflineVoice()
            }
        }
    }

    /** Called with the new count, once per counted repetition. */
    fun onRepetition(count: Int, view: View?) {
        if (tone) tones?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
        if (vibrate) view?.performHapticFeedback(confirmation())
        if (speechReady) speech?.speak(count.toString(), TextToSpeech.QUEUE_FLUSH, null, "count")
    }

    fun release() {
        tones?.release()
        speech?.shutdown()
        speech = null
    }

    /** Keep only a voice that works offline, for the phone's language; otherwise say nothing. */
    private fun useOfflineVoice(): Boolean {
        val engine = speech ?: return false
        if (engine.setLanguage(Locale.getDefault()) < TextToSpeech.LANG_AVAILABLE) return false
        val current = engine.voice
        if (current != null && !current.isNetworkConnectionRequired) return true
        val offline = engine.voices.orEmpty().firstOrNull {
            !it.isNetworkConnectionRequired && it.locale.language == Locale.getDefault().language
        } ?: return false
        return engine.setVoice(offline) == TextToSpeech.SUCCESS
    }

    companion object {
        private const val VOLUME = 80
        private const val BEEP_MS = 120

        /** The end of a rest: always a tone and a vibration, whatever the repetition cues are set to. */
        fun restOver(view: View?) {
            view?.performHapticFeedback(confirmation())
            runCatching {
                val generator = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
                generator.startTone(ToneGenerator.TONE_PROP_ACK, 400)
                view?.postDelayed({ generator.release() }, 600) ?: generator.release()
            }
        }

        private fun confirmation(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
    }
}

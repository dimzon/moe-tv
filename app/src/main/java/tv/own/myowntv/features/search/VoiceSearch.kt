package tv.own.myowntv.features.search
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/**
 * Voice search through the system's speech recogniser ([RecognizerIntent]). The recogniser app does the
 * listening, so OwnTV needs no microphone permission. Fire TV and many boxes ship no recogniser at all,
 * so the microphone shows only where one answers the intent.
 */
internal object VoiceSearch {

    /** The recogniser request: free-form speech, one best guess first, in the app's language. */
    fun intent(prompt: String, languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)

    /** Whether any installed app answers the recogniser intent. */
    fun isAvailable(context: Context): Boolean = isAvailable {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
    }

    /**
     * [resolves] asks the package manager. Some OEM builds throw from it instead of answering; that
     * counts as "no recogniser", so a broken box loses the button, never the screen.
     */
    fun isAvailable(resolves: () -> Boolean): Boolean =
        try {
            resolves()
        } catch (_: RuntimeException) {
            false
        }

    /**
     * The search text from the recogniser's answer: its first non-blank guess, trimmed and with runs of
     * spaces collapsed. Null when the user backed out, nothing was heard, or the answer was empty.
     */
    fun spokenQuery(resultCode: Int, matches: List<String?>?): String? {
        if (resultCode != Activity.RESULT_OK) return null
        return matches.orEmpty()
            .firstNotNullOfOrNull { it?.trim()?.replace(Whitespace, " ")?.takeIf(String::isNotEmpty) }
    }

    private const val MAX_RESULTS = 5
    private val Whitespace = Regex("\\s+")
}

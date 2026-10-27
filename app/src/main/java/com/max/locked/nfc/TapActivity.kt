package com.max.locked.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.max.locked.R
import com.max.locked.data.BrickRepository
import com.max.locked.data.BrickRepository.TapResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Transparent activity for everyday puck taps. Verifies the tag (UID AND secret hash), toggles the
 * lock, buzzes, shows a short toast and finishes. Unknown tags only get "Not a paired puck."
 */
@AndroidEntryPoint
class TapActivity : ComponentActivity() {

    @Inject lateinit var repository: BrickRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) {
            finish()
            return
        }
        val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        val secret = intent
            .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            ?.firstNotNullOfOrNull { message ->
                message.records.firstNotNullOfOrNull { KeyUri.parse(it.toUri()?.toString()) }
            }

        lifecycleScope.launch {
            val uid = tag?.id?.toHex()
            val key = uid?.let { repository.findKeyByUid(it) }
            val verified = key != null && secret != null &&
                TagSecret.hashesMatch(TagSecret.hash(secret), key.secretHash)

            if (!verified) {
                Log.d(TAG, "tap rejected (uid known=${key != null}, secret present=${secret != null})")
                feedback(R.string.tap_not_paired, HapticFeedbackConstants.REJECT)
                return@launch
            }

            val result = repository.toggleFromKey(key)
            Log.d(TAG, "tap from puck ${key.id}: $result")
            when (result) {
                TapResult.Locked -> feedback(getString(R.string.tap_locked), HapticFeedbackConstants.CONFIRM)
                TapResult.Unlocked -> feedback(getString(R.string.tap_unlocked), HapticFeedbackConstants.CONFIRM)
                is TapResult.WrongPuck -> feedback(
                    result.lockingPuckName?.let { getString(R.string.tap_wrong_puck, it) }
                        ?: getString(R.string.tap_wrong_puck_unnamed),
                    HapticFeedbackConstants.REJECT,
                )
                is TapResult.WrongMode -> feedback(
                    if (result.modeNames.isEmpty()) {
                        getString(R.string.tap_wrong_mode_unnamed)
                    } else {
                        getString(R.string.tap_wrong_mode, joinOr(result.modeNames))
                    },
                    HapticFeedbackConstants.REJECT,
                )
                TapResult.StillLocked -> feedback(getString(R.string.tap_still_locked), HapticFeedbackConstants.CONFIRM)
            }
        }
    }

    private fun feedback(message: Int, haptic: Int) = feedback(getString(message), haptic)

    /** "Work", "Work or Sleep", "Work, Sleep or Study". */
    private fun joinOr(names: List<String>): String =
        if (names.size == 1) names.single()
        else getString(R.string.names_or, names.dropLast(1).joinToString(", "), names.last())

    /** Buzz once the window is attached (post waits for that), then toast and finish. */
    private fun feedback(message: String, haptic: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        window.decorView.post {
            window.decorView.performHapticFeedback(haptic)
            finish()
        }
    }

    private companion object {
        const val TAG = "Locked"
    }
}

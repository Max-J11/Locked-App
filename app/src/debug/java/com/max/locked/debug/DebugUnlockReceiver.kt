package com.max.locked.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.max.locked.data.BrickRepository
import com.max.locked.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DEBUG BUILDS ONLY (lives in src/debug). Force-unlocks from ADB so testing can't lock me out:
 *
 *   adb shell am broadcast -a com.max.locked.DEBUG_UNLOCK -p com.max.locked
 *
 * Guarded by the DUMP permission, which the ADB shell has and normal apps don't.
 */
@AndroidEntryPoint
class DebugUnlockReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: BrickRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Log.d("Locked", "debug force-unlock received")
        val pending = goAsync()
        scope.launch {
            try {
                repository.unlock(source = "debug_adb")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.max.locked.DEBUG_UNLOCK"
    }
}

package com.max.locked.ui.pairing

import android.content.Context
import android.nfc.Tag
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.domain.DEFAULT_MODE_ID
import com.max.locked.domain.isModeInUse
import com.max.locked.nfc.TagSecret
import com.max.locked.nfc.TagWriter
import com.max.locked.nfc.WriteError
import com.max.locked.nfc.WriteResult
import com.max.locked.nfc.toHex
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PairingStep {
    data object EnterName : PairingStep
    data object Waiting : PairingStep
    data object TapAgain : PairingStep
    data object Writing : PairingStep
    /** The puck already has a Locked key (probably from another phone): use it, or write a new one? */
    data class FoundExisting(val tagUid: String, val secret: String) : PairingStep
    /** The puck is already paired with this phone, under [name]. Nothing to do. */
    data class AlreadyPaired(val name: String) : PairingStep
    data class Done(val name: String) : PairingStep
    data class Failed(val error: WriteError) : PairingStep
}

/** A mode the new puck can be set to. [inUse]: a lock or schedule is using it, so it can't be picked yet. */
data class ModeChoice(val id: Long, val name: String, val inUse: Boolean)

data class PairingUiState(
    val name: String = "",
    val step: PairingStep = PairingStep.EnterName,
    /** Chose "Use it only on this phone": overwrite whatever key the puck has. */
    val writeNew: Boolean = false,
    /** The mode she tapped, or null to use the first one that's free. */
    val pickedModeId: Long? = null,
    /** The chosen mode came into use before the puck was saved; she needs to pick another. */
    val modeTaken: Boolean = false,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BrickRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    val modeChoices: StateFlow<List<ModeChoice>> =
        combine(repository.modes, repository.policy) { modes, policy ->
            val now = System.currentTimeMillis()
            modes.map { ModeChoice(it.id, it.name, isModeInUse(policy, it.id, now)) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The mode the puck will get: her pick if it's free, else Default if free, else the first free one. */
    fun chosenMode(choices: List<ModeChoice> = modeChoices.value, picked: Long? = _state.value.pickedModeId): Long? {
        val free = choices.filterNot { it.inUse }
        return free.firstOrNull { it.id == picked }?.id
            ?: free.firstOrNull { it.id == DEFAULT_MODE_ID }?.id
            ?: free.firstOrNull()?.id
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(30)) }

    fun pickMode(id: Long) = _state.update { it.copy(pickedModeId = id, modeTaken = false) }

    fun startWaiting() {
        if (chosenMode() == null) return
        _state.update { it.copy(step = PairingStep.Waiting, modeTaken = false) }
    }

    /** Saves the key already on the puck, so it keeps working on other phones too. */
    fun useExisting() {
        val found = _state.value.step as? PairingStep.FoundExisting ?: return
        viewModelScope.launch { save(found.tagUid, TagSecret.hash(found.secret)) }
    }

    /** Overwrites the puck's key on the next tap. Other phones stop accepting it. */
    fun writeNewKey() = _state.update { it.copy(step = PairingStep.Waiting, writeNew = true) }

    /**
     * After a failed write. Only writes fail, so the puck was either blank or she'd already chosen
     * a fresh setup, and whatever half-finished key is on it now is ours: write again without asking.
     */
    fun tryAgain() = _state.update { it.copy(step = PairingStep.Waiting, writeNew = true) }

    /** Called from the NFC reader thread. Tags arriving outside Waiting/TapAgain are ignored. */
    fun onTag(tag: Tag) {
        val current = _state.value
        if (current.step != PairingStep.Waiting && current.step != PairingStep.TapAgain) return

        if (current.step == PairingStep.Waiting && !current.writeNew) {
            val existing = TagWriter.readKey(tag)
            if (existing != null) {
                val uid = tag.id.toHex()
                // This runs on the NFC reader's background thread, so a quick lookup here is fine
                // and keeps a second tap from slipping in mid-check.
                val here = runBlocking { repository.pairedPuck(uid, TagSecret.hash(existing)) }
                Log.d(TAG, "tag $uid already has a key (paired here: ${here != null})")
                _state.update {
                    it.copy(step = here?.let { p -> PairingStep.AlreadyPaired(p.name) } ?: PairingStep.FoundExisting(uid, existing))
                }
                return
            }
        }
        _state.update { it.copy(step = PairingStep.Writing) }

        viewModelScope.launch {
            val secret = TagSecret.generate()
            val result = withContext(Dispatchers.IO) {
                TagWriter.write(tag, TagWriter.buildMessage(context, secret))
            }
            when (result) {
                is WriteResult.Success -> save(result.tagUid, TagSecret.hash(secret))
                WriteResult.FormattedTapAgain -> _state.update { it.copy(step = PairingStep.TapAgain) }
                is WriteResult.Failure -> _state.update { it.copy(step = PairingStep.Failed(result.reason)) }
            }
        }
    }

    private suspend fun save(tagUid: String, secretHash: String) {
        val name = _state.value.name.trim().ifEmpty { DEFAULT_NAME }
        val modeId = chosenMode()
        if (modeId != null && repository.savePairedKey(name, tagUid, secretHash, modeId)) {
            _state.update { it.copy(step = PairingStep.Done(name)) }
            return
        }
        // The mode came into use while she was pairing. Back to the choice; the key now on the
        // puck is ours, so the next tap writes over it without asking.
        Log.d(TAG, "pairing refused: mode $modeId is in use now")
        _state.update { it.copy(step = PairingStep.EnterName, modeTaken = true, writeNew = true) }
    }

    private companion object {
        const val TAG = "Locked"
        const val DEFAULT_NAME = "Puck"
    }
}

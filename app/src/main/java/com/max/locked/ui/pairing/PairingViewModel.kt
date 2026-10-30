package com.max.locked.ui.pairing

import android.content.Context
import android.nfc.Tag
import android.util.Log
import com.max.locked.nfc.toHex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import com.max.locked.nfc.TagSecret
import com.max.locked.nfc.TagWriter
import com.max.locked.nfc.WriteError
import com.max.locked.nfc.WriteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PairingStep {
    data object EnterName : PairingStep
    data object Waiting : PairingStep
    data object TapAgain : PairingStep
    data object Writing : PairingStep
    /** The puck already has a Locked key (probably from another phone): use it, or write a new one? */
    data class FoundExisting(val tagUid: String, val secret: String) : PairingStep
    data class Done(val name: String) : PairingStep
    data class Failed(val error: WriteError) : PairingStep
}

data class PairingUiState(
    val name: String = "",
    val step: PairingStep = PairingStep.EnterName,
    /** Chose "Write a new key": overwrite whatever key the puck has. */
    val writeNew: Boolean = false,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BrickRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(30)) }

    fun startWaiting() = _state.update { it.copy(step = PairingStep.Waiting) }

    /** Saves the key already on the puck, so it keeps working on other phones too. */
    fun useExisting() {
        val found = _state.value.step as? PairingStep.FoundExisting ?: return
        viewModelScope.launch {
            val name = _state.value.name.trim().ifEmpty { DEFAULT_NAME }
            repository.savePairedKey(name, found.tagUid, TagSecret.hash(found.secret))
            Log.d(TAG, "paired existing key on tag ${found.tagUid}")
            _state.update { it.copy(step = PairingStep.Done(name)) }
        }
    }

    /** Overwrites the puck's key on the next tap. Other phones stop accepting it. */
    fun writeNewKey() = _state.update { it.copy(step = PairingStep.Waiting, writeNew = true) }

    /** Called from the NFC reader thread. Tags arriving outside Waiting/TapAgain are ignored. */
    fun onTag(tag: Tag) {
        val current = _state.value
        if (current.step != PairingStep.Waiting && current.step != PairingStep.TapAgain) return

        if (current.step == PairingStep.Waiting && !current.writeNew) {
            val existing = TagWriter.readKey(tag)
            if (existing != null) {
                _state.update { it.copy(step = PairingStep.FoundExisting(tag.id.toHex(), existing)) }
                return
            }
        }
        _state.update { it.copy(step = PairingStep.Writing) }

        viewModelScope.launch {
            val secret = TagSecret.generate()
            val result = withContext(Dispatchers.IO) {
                TagWriter.write(tag, TagWriter.buildMessage(context, secret))
            }
            val next = when (result) {
                is WriteResult.Success -> {
                    val name = current.name.trim().ifEmpty { DEFAULT_NAME }
                    repository.savePairedKey(name, result.tagUid, TagSecret.hash(secret))
                    PairingStep.Done(name)
                }
                WriteResult.FormattedTapAgain -> PairingStep.TapAgain
                is WriteResult.Failure -> PairingStep.Failed(result.reason)
            }
            _state.update { it.copy(step = next) }
        }
    }

    private companion object {
        const val TAG = "Locked"
        const val DEFAULT_NAME = "Puck"
    }
}

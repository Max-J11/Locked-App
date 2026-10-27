package com.max.locked.ui.apppicker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max.locked.data.BrickRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val selected: Boolean,
    /** Chosen and the mode is in use: can't be unchecked until unlocked. */
    val locked: Boolean,
)

/** Loaded once per screen visit, off the main thread. */
private class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

data class AppPickerUiState(
    val loading: Boolean = true,
    val modeName: String = "",
    val isDefaultMode: Boolean = true,
    /** A lock is in effect using this mode: settings here can only get stricter. */
    val modeActive: Boolean = false,
    val query: String = "",
    val apps: List<AppItem> = emptyList(),
    val hideNotifications: Boolean = false,
    /** While the mode is in use, an "on" notification setting can't be switched off. */
    val hideNotificationsLocked: Boolean = false,
)

/** Edits one mode: its apps, its notification setting, its name. */
@HiltViewModel
class AppPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BrickRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val modeId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_MODE_ID))

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<AppPickerUiState> =
        combine(
            installed,
            query,
            repository.selectedApps(modeId),
            repository.mode(modeId),
            repository.policy,
        ) { apps, q, selected, mode, policy ->
            val active = policy.isActive(System.currentTimeMillis()) && modeId in policy.activeModeIds
            val hideNotifications = mode?.blockNotifications == true
            val needle = q.trim()
            AppPickerUiState(
                loading = apps == null,
                modeName = mode?.name.orEmpty(),
                isDefaultMode = modeId == BrickRepository.DEFAULT_MODE_ID,
                modeActive = active,
                query = q,
                apps = apps.orEmpty()
                    .filter { needle.isEmpty() || it.label.contains(needle, ignoreCase = true) }
                    .map {
                        val isSelected = it.packageName in selected
                        AppItem(it.packageName, it.label, it.icon, isSelected, locked = isSelected && active)
                    },
                hideNotifications = hideNotifications,
                hideNotificationsLocked = hideNotifications && active,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPickerUiState())

    init {
        viewModelScope.launch {
            installed.value = withContext(Dispatchers.IO) { loadLaunchableApps() }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setHideNotifications(on: Boolean) {
        viewModelScope.launch { repository.setBlockNotifications(modeId, on) }
    }

    fun setSelected(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            if (selected) repository.addApp(modeId, packageName) else repository.removeApp(modeId, packageName)
        }
    }

    fun rename(name: String) {
        viewModelScope.launch { repository.renameMode(modeId, name) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch { if (repository.deleteMode(modeId)) onDeleted() }
    }

    /** Apps with a launcher icon, minus the never-block list, sorted by name, icons pre-rendered. */
    private fun loadLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val neverBlock = repository.neverBlockPackages()
        val iconPx = (ICON_DP * context.resources.displayMetrics.density).toInt()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName !in neverBlock }
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching {
                        info.loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap()
                    }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    companion object {
        const val ARG_MODE_ID = "modeId"
        private const val ICON_DP = 36
    }
}

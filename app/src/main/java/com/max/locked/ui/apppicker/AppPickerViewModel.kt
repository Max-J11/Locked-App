package com.max.locked.ui.apppicker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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
)

/** Loaded once per screen visit, off the main thread. */
private class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

data class AppPickerUiState(
    val loading: Boolean = true,
    val query: String = "",
    val apps: List<AppItem> = emptyList(),
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BrickRepository,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<AppPickerUiState> =
        combine(installed, query, repository.selectedApps) { apps, q, selected ->
            val needle = q.trim()
            AppPickerUiState(
                loading = apps == null,
                query = q,
                apps = apps.orEmpty()
                    .filter { needle.isEmpty() || it.label.contains(needle, ignoreCase = true) }
                    .map { AppItem(it.packageName, it.label, it.icon, it.packageName in selected) },
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

    fun setSelected(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            if (selected) repository.addApp(packageName) else repository.removeApp(packageName)
        }
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

    private companion object {
        const val ICON_DP = 36
    }
}

package com.max.locked.ui.apppicker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    val selected: Boolean,
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

    private val installed = MutableStateFlow<List<Pair<String, String>>?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<AppPickerUiState> =
        combine(installed, query, repository.selectedApps) { apps, q, selected ->
            AppPickerUiState(
                loading = apps == null,
                query = q,
                apps = apps.orEmpty()
                    .filter { (_, label) -> q.isBlank() || label.contains(q.trim(), ignoreCase = true) }
                    .map { (pkg, label) -> AppItem(pkg, label, pkg in selected) },
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

    /** Apps with a launcher icon, minus the never-block list, sorted by name. */
    private fun loadLaunchableApps(): List<Pair<String, String>> {
        val pm = context.packageManager
        val neverBlock = repository.neverBlockPackages()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first !in neverBlock }
            .sortedBy { it.second.lowercase() }
    }
}

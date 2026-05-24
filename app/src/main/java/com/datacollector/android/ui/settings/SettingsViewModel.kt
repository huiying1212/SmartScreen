package com.datacollector.android.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datacollector.android.data.config.AppPreferences
import com.datacollector.android.managers.WallpaperGenerationManager
import com.datacollector.android.services.FloatingOverlayService
import com.datacollector.android.utils.CollectionConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val overlayEnabled: Boolean = true,
    val wallpaperGenEnabled: Boolean = true,
    val faceStyle: String = "CLASSIC",
    val wallpaperStyle: String = "唯美艺术",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val cfg get() = CollectionConfig.getInstance(context)

    val state = combine(
        prefs.overlayEnabled,
        prefs.wallpaperGenerationEnabled,
        prefs.faceStyle,
        prefs.wallpaperStyle,
    ) { overlay, wp, face, wpStyle ->
        SettingsUiState(
            overlayEnabled = overlay,
            wallpaperGenEnabled = wp,
            faceStyle = face,
            wallpaperStyle = wpStyle,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setOverlayEnabled(value: Boolean) = persist {
        prefs.setOverlayEnabled(value)
        cfg.setBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, value)
        if (value) {
            if (cfg.getBoolean(CollectionConfig.KEY_RI4SU_ENABLED, true)) startOverlayServiceIfPermitted()
        } else {
            stopService(FloatingOverlayService::class.java)
        }
    }

    fun setWallpaperGenEnabled(value: Boolean) = persist {
        prefs.setWallpaperGenerationEnabled(value)
        cfg.setBoolean(CollectionConfig.KEY_WALLPAPER_GENERATION_ENABLED, value)
        if (!value) {
            withContext(Dispatchers.IO) {
                runCatching { WallpaperGenerationManager(context).restoreOriginalWallpaperIfExists() }
            }
        }
    }

    fun setFaceStyle(value: String) = persist {
        prefs.setFaceStyle(value)
        cfg.setString(CollectionConfig.KEY_FACE_STYLE, value)
    }

    fun setWallpaperStyle(value: String) = persist {
        prefs.setWallpaperStyle(value)
        cfg.setString(CollectionConfig.KEY_WALLPAPER_STYLE, value)
    }

    private fun startOverlayServiceIfPermitted() {
        runCatching {
            if (!Settings.canDrawOverlays(context)) return
            val i = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }
    }

    private fun stopService(clazz: Class<*>) =
        runCatching { context.stopService(Intent(context, clazz)) }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

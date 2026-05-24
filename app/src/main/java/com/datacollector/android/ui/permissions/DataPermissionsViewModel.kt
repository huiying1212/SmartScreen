package com.datacollector.android.ui.permissions

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datacollector.android.data.config.AppPreferences
import com.datacollector.android.managers.WallpaperGenerationManager
import com.datacollector.android.services.DataCollectionService
import com.datacollector.android.services.FloatingOverlayService
import com.datacollector.android.utils.CollectionConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DataPermissionsUiState(
    val ri4suEnabled: Boolean = true,
    val overlayEnabled: Boolean = true,
    val wallpaperGenEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val activityEnabled: Boolean = true,
    val screenUsageEnabled: Boolean = true,
    val calendarEnabled: Boolean = true,
    val wifiEnabled: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val weatherEnabled: Boolean = true,
)

@HiltViewModel
class DataPermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val cfg get() = CollectionConfig.getInstance(context)

    private data class SensorFlags(
        val location: Boolean,
        val activity: Boolean,
        val screenUsage: Boolean,
        val calendar: Boolean,
        val wifi: Boolean,
        val bluetooth: Boolean,
        val weather: Boolean,
    )

    private val _sensors = MutableStateFlow(
        SensorFlags(
            location    = cfg.getBoolean(CollectionConfig.KEY_LOCATION_ENABLED, true),
            activity    = cfg.getBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, true),
            screenUsage = cfg.getBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, true),
            calendar    = cfg.getBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, true),
            wifi        = cfg.getBoolean(CollectionConfig.KEY_WIFI_ENABLED, true),
            bluetooth   = cfg.getBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, true),
            weather     = cfg.getBoolean(CollectionConfig.KEY_WEATHER_ENABLED, true),
        )
    )

    val state = combine(
        prefs.ri4suEnabled,
        prefs.overlayEnabled,
        prefs.wallpaperGenerationEnabled,
        _sensors,
    ) { ri4su, overlay, wp, s ->
        DataPermissionsUiState(
            ri4suEnabled      = ri4su,
            overlayEnabled    = overlay,
            wallpaperGenEnabled = wp,
            locationEnabled   = s.location,
            activityEnabled   = s.activity,
            screenUsageEnabled = s.screenUsage,
            calendarEnabled   = s.calendar,
            wifiEnabled       = s.wifi,
            bluetoothEnabled  = s.bluetooth,
            weatherEnabled    = s.weather,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataPermissionsUiState())

    // ── Service-backed toggles ────────────────────────────────────────────

    fun setRi4suEnabled(value: Boolean) = persist {
        prefs.setRi4suEnabled(value)
        cfg.setBoolean(CollectionConfig.KEY_RI4SU_ENABLED, value)
        if (value) {
            startDataCollectionService()
            if (cfg.getBoolean(CollectionConfig.KEY_OVERLAY_ENABLED, true)) {
                startOverlayServiceIfPermitted()
            }
        } else {
            stopService(DataCollectionService::class.java)
            stopService(FloatingOverlayService::class.java)
            withContext(Dispatchers.IO) {
                runCatching { WallpaperGenerationManager(context).restoreOriginalWallpaperIfExists() }
            }
        }
    }

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

    // ── Sensor-only toggles (CollectionConfig only, no service restart) ──

    fun setLocationEnabled(value: Boolean) {
        _sensors.value = _sensors.value.copy(location = value)
        cfg.setBoolean(CollectionConfig.KEY_LOCATION_ENABLED, value)
    }

    fun setActivityEnabled(value: Boolean) {
        _sensors.value = _sensors.value.copy(activity = value)
        cfg.setBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, value)
    }

    fun setScreenUsageEnabled(value: Boolean) {
        _sensors.value = _sensors.value.copy(screenUsage = value)
        cfg.setBoolean(CollectionConfig.KEY_SCREEN_USAGE_ENABLED, value)
    }

    fun setCalendarEnabled(value: Boolean) {
        _sensors.value = _sensors.value.copy(calendar = value)
        cfg.setBoolean(CollectionConfig.KEY_CALENDAR_ENABLED, value)
    }

    fun setWifiEnabled(value: Boolean) {
        _sensors.value = _sensors.value.copy(wifi = value)
        cfg.setBoolean(CollectionConfig.KEY_WIFI_ENABLED, value)
    }

    fun setBluetoothEnabled(value: Boolean) {
        _sensors.value = _sensors.value.copy(bluetooth = value)
        cfg.setBoolean(CollectionConfig.KEY_BLUETOOTH_ENABLED, value)
    }

    fun setWeatherEnabled(value: Boolean) {
        _sensors.value = _sensors.value.copy(weather = value)
        cfg.setBoolean(CollectionConfig.KEY_WEATHER_ENABLED, value)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun startDataCollectionService() {
        runCatching {
            val i = Intent(context, DataCollectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }
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

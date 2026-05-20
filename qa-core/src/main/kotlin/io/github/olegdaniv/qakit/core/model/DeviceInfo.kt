package io.github.olegdaniv.qakit.core.model

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Вся інформація про пристрій та білд — одне місце, один виклик.
 *
 * Використання:
 * ```kotlin
 * val info = DeviceInfo.collect(context)
 * println(info.appVersion)   // "1.2.3 (42)"
 * println(info.memoryUsage)  // "128 MB / 512 MB"
 * ```
 */
data class DeviceInfo(
    // --- App ---
    val appName: String,
    val packageName: String,
    val appVersion: String,       // "1.2.3 (42)"
    val buildType: String,        // "debug" / "release"
    val buildTime: Long,          // ms epoch

    // --- Device ---
    val manufacturer: String,
    val model: String,
    val androidVersion: String,   // "14"
    val sdkInt: Int,              // 34
    val board: String,
    val supportedAbis: List<String>,

    // --- Memory ---
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowMemory: Boolean,

    // --- Display ---
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val screenDensity: Float,     // dpi
    val screenDensityDpi: Int,
) {
    /** "Samsung Galaxy S24" */
    val deviceName: String get() = "$manufacturer $model"

    /** "1.2.3 (42)" */
    val usedRamMb: Long get() = totalRamMb - availableRamMb

    /** "128 MB used / 512 MB total" */
    val memoryUsage: String get() = "${usedRamMb} MB used / ${totalRamMb} MB total"

    /** "${widthPx} × ${heightPx} @ ${densityDpi} dpi" */
    val screenInfo: String get() = "${screenWidthPx} × ${screenHeightPx} @ ${screenDensityDpi} dpi"

    companion object {
        fun collect(context: Context): DeviceInfo {
            val appContext = context.applicationContext
            val pm = appContext.packageManager
            val packageInfo = pm.getPackageInfo(appContext.packageName, 0)

            val am = appContext.getSystemService<ActivityManager>()
            val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }

            val metrics = appContext.resources.displayMetrics

            return DeviceInfo(
                // App
                appName = pm.getApplicationLabel(
                    pm.getApplicationInfo(appContext.packageName, 0)
                ).toString(),
                packageName = appContext.packageName,
                appVersion = "${packageInfo.versionName} (${
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        packageInfo.longVersionCode
                    else
                        @Suppress("DEPRECATION") packageInfo.versionCode
                })",
                buildType = if (isDebugBuild(appContext)) "debug" else "release",
                buildTime = packageInfo.lastUpdateTime,

                // Device
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                board = Build.BOARD,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),

                // Memory
                totalRamMb = memInfo.totalMem / 1024 / 1024,
                availableRamMb = memInfo.availMem / 1024 / 1024,
                isLowMemory = memInfo.lowMemory,

                // Display
                screenWidthPx = metrics.widthPixels,
                screenHeightPx = metrics.heightPixels,
                screenDensity = metrics.density,
                screenDensityDpi = metrics.densityDpi,
            )
        }

        private fun isDebugBuild(context: Context): Boolean = try {
            val flags = context.packageManager
                .getApplicationInfo(context.packageName, 0).flags
            (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }
}
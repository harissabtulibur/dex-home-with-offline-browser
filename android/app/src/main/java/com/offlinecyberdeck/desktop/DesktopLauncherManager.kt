package com.offlinecyberdeck.desktop

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

data class DesktopPermissionStatus(
    val isDefaultHomeLauncher: Boolean,
    val canDrawOverlays: Boolean,
    val isFreeformEnabledInSettings: Boolean,
    val isForceResizableEnabled: Boolean,
    val hasFreeformFeature: Boolean,
    val notificationsGranted: Boolean,
    val notificationAccessGranted: Boolean,
    val canWriteSecureSettings: Boolean
) {
    val freeformReady: Boolean
        get() = isFreeformEnabledInSettings || hasFreeformFeature

    val allCoreReady: Boolean
        get() = isDefaultHomeLauncher && canDrawOverlays && freeformReady
}

data class BatteryStatusInfo(
    val percent: Int?,
    val isCharging: Boolean
)

object DesktopLauncherManager {

    // Capped at 64 app icons (~1.3 MB max) so App Drawer stays ultra-light on 4 GB RAM tablets
    private val iconCache = LruCache<String, ImageBitmap>(64)

    fun inspectPermissions(context: Context): DesktopPermissionStatus {
        val pm = context.packageManager
        val isHome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_HOME) == true || isResolvedAsDefaultHome(context)
        } else {
            isResolvedAsDefaultHome(context)
        }

        val canOverlay = Settings.canDrawOverlays(context)
        val devFreeform = runCatching {
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
        }.getOrDefault(false)
        val forceResizable = runCatching {
            Settings.Global.getInt(context.contentResolver, "force_resizable_activities", 0) == 1
        }.getOrDefault(false)
        val sysFreeform = pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val notifAccessOk = NotificationCenterStore.isNotificationAccessEnabled(context)
        val writeSecureOk = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

        return DesktopPermissionStatus(
            isDefaultHomeLauncher = isHome,
            canDrawOverlays = canOverlay,
            isFreeformEnabledInSettings = devFreeform,
            isForceResizableEnabled = forceResizable,
            hasFreeformFeature = sysFreeform,
            notificationsGranted = notifOk,
            notificationAccessGranted = notifAccessOk,
            canWriteSecureSettings = writeSecureOk
        )
    }

    fun tryEnableFreeformFlagsDirectly(context: Context): Result<String> {
        return runCatching {
            val granted = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                error("Izin sistem WRITE_SECURE_SETTINGS belum aktif. Aktifkan manual di Developer Options atau jalankan ADB sekali.")
            }
            Settings.Global.putInt(context.contentResolver, "enable_freeform_support", 1)
            Settings.Global.putInt(context.contentResolver, "force_resizable_activities", 1)
            "Freeform & Resizable berhasil diaktifkan! Restart tablet / buka ulang app agar One UI menerapkannya."
        }
    }

    fun buildFreeformAdbCommand(packageName: String): String =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS && " +
            "adb shell settings put global enable_freeform_support 1 && " +
            "adb shell settings put global force_resizable_activities 1"

    private fun isResolvedAsDefaultHome(context: Context): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun buildDefaultHomeRoleIntentOrNull(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return null
    }

    fun openHomeLauncherSettings(context: Context): String {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Pilih 'Offline Cyber Deck' sebagai Aplikasi Utama / Home"
        )
    }

    fun openOverlayPermissionSettings(context: Context): String {
        val candidates = listOf(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Aktifkan izin 'Tampil di atas aplikasi lain / Appear on top'"
        )
    }

    fun openDeveloperOptionsSettings(context: Context): String {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Di Opsi Pengembang: aktifkan 'Enable freeform windows' & 'Force activities to be resizable'"
        )
    }

    fun openSystemMultiWindowSettings(context: Context): String {
        val candidates = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$AdvancedAppsActivity"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Di Samsung One UI: buka Fitur Lanjutan → Labs → aktifkan Multi-jendela untuk semua aplikasi"
        )
    }

    fun openAppDetailsSettings(context: Context): String {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Atur Baterai ke 'Tidak dibatasi' agar launcher selalu responsif"
        )
    }

    private fun launchFirstWorkingSettings(
        context: Context,
        candidates: List<Intent>,
        successMessage: String
    ): String {
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return successMessage
        }
        return "Buka Pengaturan Android secara manual."
    }

    suspend fun loadInstalledApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val byPackage = LinkedHashMap<String, InstalledAppInfo>()
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            if (byPackage.containsKey(pkg)) continue
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg).ifBlank { pkg }
            val appFlags = ri.activityInfo?.applicationInfo?.flags ?: 0
            val isSystem = (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0
            byPackage[pkg] = InstalledAppInfo(
                packageName = pkg,
                label = label,
                isSystemApp = isSystem
            )
        }
        byPackage.values.sortedBy { it.label.lowercase() }
    }

    fun loadAppIconBitmap(context: Context, packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = drawable.toBitmap(width = 72, height = 72, config = Bitmap.Config.ARGB_8888)
                .asImageBitmap()
            iconCache.put(packageName, bmp)
            bmp
        }.getOrNull()
    }

    fun clearIconCache() {
        iconCache.evictAll()
    }

    fun launchExternalApp(
        context: Context,
        packageName: String,
        floating: Boolean,
        cascadeIndex: Int = 0
    ): Result<String> {
        return runCatching {
            val pm = context.packageManager
            val baseIntent = pm.getLaunchIntentForPackage(packageName)
                ?: error("Aplikasi tidak memiliki launcher activity.")

            baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (floating) {
                // IMPORTANT: Do NOT set FLAG_ACTIVITY_LAUNCH_ADJACENT — that flag forces Android/One UI into Split-Screen!
                val dm = context.resources.displayMetrics
                val w = dm.widthPixels.coerceAtLeast(600)
                val h = dm.heightPixels.coerceAtLeast(600)
                val offset = (cascadeIndex % 5) * (w / 40).coerceAtLeast(20)
                val winW = (w * 0.64f).toInt().coerceAtLeast(420)
                val winH = (h * 0.74f).toInt().coerceAtLeast(440)
                val left = ((w - winW) / 2 + offset).coerceIn(24, (w - winW - 24).coerceAtLeast(24))
                val top = ((h - winH) / 2 + offset).coerceIn(24, (h - winH - 64).coerceAtLeast(24))
                val bounds = Rect(left, top, left + winW, top + winH)

                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { options.setLaunchBounds(bounds) }
                }
                // WINDOWING_MODE_FREEFORM = 5 (requires Freeform Windows enabled in Developer Options or One UI Labs)
                runCatching {
                    ActivityOptions::class.java
                        .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                        .invoke(options, 5)
                }

                val perm = inspectPermissions(context)
                val startedFloating = runCatching {
                    context.startActivity(baseIntent, options.toBundle())
                    true
                }.getOrDefault(false)

                when {
                    !startedFloating -> {
                        context.startActivity(baseIntent)
                        "Dibuka normal (sistem menolak launch bounds)."
                    }
                    !perm.freeformReady -> {
                        "Mode Freeform belum aktif di sistem — buka Izin DeX → Developer Options untuk mengaktifkan 'Enable freeform windows'."
                    }
                    else -> {
                        "Membuka jendela floating freeform (gunakan bar atas jendela untuk Minimize/Maximize/Close)."
                    }
                }
            } else {
                context.startActivity(baseIntent)
                "Aplikasi dibuka."
            }
        }
    }

    fun readBatteryStatus(context: Context): BatteryStatusInfo {
        return runCatching {
            val bm = context.getSystemService(BatteryManager::class.java)
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            val charging = bm?.isCharging == true
            BatteryStatusInfo(percent = pct, isCharging = charging)
        }.getOrDefault(BatteryStatusInfo(percent = null, isCharging = false))
    }

    fun readBatteryPercent(context: Context): Int? = readBatteryStatus(context).percent
}

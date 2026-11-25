package com.offlinecyberdeck.desktop

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

data class DeckNotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val isClearable: Boolean,
    val contentIntent: PendingIntent?
)

object NotificationCenterStore {
    private val _notifications = MutableStateFlow<List<DeckNotificationItem>>(emptyList())
    val notifications: StateFlow<List<DeckNotificationItem>> = _notifications.asStateFlow()

    @Volatile
    private var serviceRef: WeakReference<CyberDeckNotificationService>? = null

    internal fun attachService(service: CyberDeckNotificationService) {
        serviceRef = WeakReference(service)
    }

    internal fun detachService(service: CyberDeckNotificationService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }

    internal fun updateFromActive(items: List<DeckNotificationItem>) {
        _notifications.value = items
            .sortedByDescending { it.postedAtMs }
            .take(25)
    }

    fun dismiss(key: String) {
        val svc = serviceRef?.get()
        if (svc != null) {
            runCatching { svc.cancelNotification(key) }
        }
        _notifications.value = _notifications.value.filterNot { it.key == key }
    }

    fun clearAllClearable() {
        val svc = serviceRef?.get()
        val clearableKeys = _notifications.value.filter { it.isClearable }.map { it.key }
        if (svc != null) {
            clearableKeys.forEach { key -> runCatching { svc.cancelNotification(key) } }
        }
        _notifications.value = _notifications.value.filterNot { it.isClearable }
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        if (flat.isBlank()) return false
        val expected = ComponentName(context, CyberDeckNotificationService::class.java)
        return flat.split(':').any { token ->
            ComponentName.unflattenFromString(token) == expected ||
                token.contains(context.packageName)
        }
    }

    fun openNotificationAccessSettings(context: Context): String {
        val candidates = listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
                return "Aktifkan 'Offline Cyber Deck' di Akses Notifikasi agar notif WA/YouTube/sistem tampil di taskbar."
            }
        }
        return "Buka Setelan Android → Akses Notifikasi."
    }
}

class CyberDeckNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationCenterStore.attachService(this)
        refreshSnapshot()
    }

    override fun onListenerDisconnected() {
        NotificationCenterStore.detachService(this)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        val raw = runCatching { activeNotifications }.getOrNull() ?: return
        val pm = packageManager
        val list = ArrayList<DeckNotificationItem>(25)
        for (sbn in raw) {
            val notif = sbn.notification ?: continue
            if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            if (sbn.packageName == packageName && sbn.isOngoing) continue

            val extras = notif.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
            val body = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().trim()
            if (title.isBlank() && body.isBlank()) continue

            val appLabel = runCatching {
                val ai = pm.getApplicationInfo(sbn.packageName, 0)
                pm.getApplicationLabel(ai).toString()
            }.getOrDefault(sbn.packageName)

            list += DeckNotificationItem(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = title.ifBlank { appLabel },
                text = body,
                postedAtMs = sbn.postTime,
                isClearable = sbn.isClearable,
                contentIntent = notif.contentIntent
            )
            if (list.size >= 25) break
        }
        NotificationCenterStore.updateFromActive(list)
    }
}

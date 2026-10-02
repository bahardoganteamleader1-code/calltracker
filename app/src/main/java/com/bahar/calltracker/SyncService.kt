package com.bahar.calltracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Status { val text = MutableStateFlow("Hazirlaniyor...") }

class SyncService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val intervalMs = 5 * 60_000L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ch = "sync"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "Sync", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("Bahar Dogan Dis Klinigi")
            .setContentText("Arama bilgileri senkronize ediliyor")
            .setSmallIcon(android.R.drawable.stat_notify_sync).build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)

        val p = getSharedPreferences("p", MODE_PRIVATE)
        val name = p.getString("name", null); val pass = p.getString("pass", null)
        if (name == null || pass == null) { stopSelf(); return START_NOT_STICKY }

        scope.coroutineContext.cancelChildren()
        scope.launch {
            while (isActive) {
                try {
                    val s = readTodayStats(this@SyncService)
                    val r = Api.post(JSONObject().apply {
                        put("action", "upsert"); put("password", pass)
                        put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                        put("name", name)
                        put("total", s.total); put("missed", s.missed)
                        put("answeredOut", s.answeredOut); put("unansweredOut", s.unansweredOut)
                        put("seconds", s.seconds)
                    })
                    Status.text.value = if (r.optBoolean("ok")) "Bagli - bilgiler kaydediliyor"
                    else "Bagli degil - ${r.optString("error")}"
                } catch (e: SecurityException) {
                    Status.text.value = "Bagli degil - arama kaydi izni verilmedi"
                } catch (e: Exception) {
                    Status.text.value = "Bagli degil - internet yok veya sunucu yanit vermiyor"
                }
                delay(intervalMs)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

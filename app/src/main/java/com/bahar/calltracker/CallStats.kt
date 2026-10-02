package com.bahar.calltracker

import android.content.Context
import android.provider.CallLog
import java.time.LocalDate
import java.time.ZoneId

data class Stats(
    val total: Int, val missed: Int, val answeredOut: Int,
    val unansweredOut: Int, val seconds: Long
)

fun readTodayStats(ctx: Context): Stats {
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    var total = 0; var missed = 0; var ansOut = 0; var unansOut = 0; var secs = 0L
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.TYPE, CallLog.Calls.DURATION),
        "${CallLog.Calls.DATE} >= ?", arrayOf(start.toString()), null
    )?.use { c ->
        while (c.moveToNext()) {
            val type = c.getInt(0); val dur = c.getLong(1)
            when (type) {
                CallLog.Calls.OUTGOING_TYPE -> {
                    total++
                    if (dur > 0) { ansOut++; secs += dur } else unansOut++
                }
                CallLog.Calls.INCOMING_TYPE -> { total++; secs += dur }
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> { total++; missed++ }
            }
        }
    }
    return Stats(total, missed, ansOut, unansOut, secs)
}

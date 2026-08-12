package de.dariatech.softphone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Eigene Anrufliste (verpasst/eingehend/ausgehend) – als JSON in den
 * SharedPreferences, unabhängig vom Liblinphone-internen Verlauf.
 */
data class CallEntry(
    val number: String,
    /** "in" | "out" | "missed" */
    val direction: String,
    val at: Long,
    val durationSec: Int
)

object CallLogStore {

    private const val PREFS = "calllog"
    private const val KEY = "entries"
    private const val MAX = 200

    fun add(context: Context, entry: CallEntry) {
        val list = list(context).toMutableList()
        list.add(0, entry)
        while (list.size > MAX) list.removeAt(list.size - 1)
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("number", e.number)
                    .put("direction", e.direction)
                    .put("at", e.at)
                    .put("durationSec", e.durationSec)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    fun list(context: Context): List<CallEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CallEntry(
                    number = o.optString("number"),
                    direction = o.optString("direction"),
                    at = o.optLong("at"),
                    durationSec = o.optInt("durationSec")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Verpasste Anrufe seit Mitternacht (für die Verlaufs-Kopfzeile). */
    fun missedToday(context: Context): Int {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        val midnight = cal.timeInMillis
        return list(context).count { it.direction == "missed" && it.at >= midnight }
    }
}

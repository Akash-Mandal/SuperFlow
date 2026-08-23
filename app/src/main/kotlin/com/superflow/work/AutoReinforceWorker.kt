package com.superflow.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.superflow.data.Prefs
import com.superflow.data.db.SuperFlowDatabase
import com.superflow.data.model.Requirement
import com.superflow.data.model.RequirementStatus
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import org.json.JSONObject

class AutoReinforceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val prefs = Prefs.get(applicationContext)
        if (!prefs.autoReinforceEnabled) return Result.success()
        val db = SuperFlowDatabase.get(applicationContext).db
        val cursor = db.query("SELECT id, projectId, whatJson, whenExpr, whereKind, howOp FROM blueprint_auto_plan WHERE status='PENDING'")
        val pending = mutableListOf<Map<String, String>>()
        cursor.use {
            while (it.moveToNext()) pending.add(mapOf("id" to it.getString(0), "projectId" to it.getString(1), "whatJson" to it.getString(2), "whenExpr" to it.getString(3)))
        }
        if (pending.isEmpty()) return Result.success()
        val bus = CommandBus.get(applicationContext)
        for (p in pending) {
            val whenExpr = p["whenExpr"] ?: continue
            val week = whenExpr.removePrefix("WEEK:").toIntOrNull() ?: continue
            val todayWeek = java.time.LocalDate.now().let { (it.dayOfYear / 7) + 1 }
            if (todayWeek < week) continue
            if (prefs.autoReinforceMode == "propose") {
                try {
                    db.execSQL("INSERT OR REPLACE INTO proactive_suggestion VALUES (?,?,?,?,?,?,?,?)",
                        arrayOf(java.util.UUID.randomUUID().toString(), "GROWTH", "Auto Reinforce ready: phase $week — apply ${p["whatJson"]?.let { JSONObject(it).optString("command") }}", "MEDIUM", p["whatJson"] ?: "", null, 0, 0, System.currentTimeMillis()))
                } catch (_: Exception) {}
                continue
            }
            try {
                val what = JSONObject(p["whatJson"] ?: continue)
                val cmd = what.optString("command"); val args = what.optJSONObject("args") ?: JSONObject()
                val res = bus.execute(cmd, args, Actor.SYSTEM)
                if (res.ok) db.execSQL("UPDATE blueprint_auto_plan SET status='APPLIED', appliedAt=? WHERE id=?", arrayOf(System.currentTimeMillis(), p["id"]))
                else db.execSQL("UPDATE blueprint_auto_plan SET status='FAILED' WHERE id=?", arrayOf(p["id"]))
            } catch (e: Exception) { Log.w("AutoReinforce", "apply failed", e) }
        }
        try { com.superflow.widget.TodayWidget.refresh(applicationContext) } catch (_: Exception) {}
        try { com.superflow.notify.Reminders.rescheduleAllNow(applicationContext) } catch (_: Exception) {}
        Result.success()
    } catch (e: Exception) { Log.w("AutoReinforce", "worker failed", e); Result.retry() }
    companion object { const val NAME = "superflow_auto_reinforce" }
}

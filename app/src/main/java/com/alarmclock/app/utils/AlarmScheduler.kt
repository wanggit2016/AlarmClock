package com.alarmclock.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.alarmclock.app.data.Alarm
import com.alarmclock.app.receiver.AlarmReceiver

/**
 * 系统闹钟调度器
 *
 * 负责：向系统 AlarmManager 注册/取消闹钟
 * 注意：
 *   - Android 12+ (API 31+) 需要 SCHEDULE_EXACT_ALARM 权限
 *   - Android 13+ (API 33+) 需要 POST_NOTIFICATIONS 权限
 *   - 使用 FLAG_UPDATE_CURRENT 确保同一 id 的 PendingIntent 被正确更新
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val ACTION_ALARM_TRIGGER = "com.alarmclock.app.ACTION_ALARM_TRIGGER"
    const val EXTRA_ALARM_ID = "extra_alarm_id"

    /**
     * 调度闹钟
     *
     * @param context 上下文
     * @param alarm 闹钟数据
     * @return true = 调度成功，false = 未到调度时机或失败
     */
    fun schedule(context: Context, alarm: Alarm): Boolean {
        if (!alarm.enabled) {
            Log.d(TAG, "Alarm ${alarm.id} is disabled, skip scheduling")
            cancel(context, alarm.id)
            return false
        }

        val triggerTime = WorkdayUtils.getNextAlarmTime(
            alarm.hour, alarm.minute,
            alarm.repeatType, alarm.customDays, alarm.workdayMode
        ) ?: run {
            Log.d(TAG, "Alarm ${alarm.id} has no next trigger time (e.g., one-time past)")
            return false
        }

        Log.d(TAG, "Scheduling alarm ${alarm.id} at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(triggerTime))}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 检查精确闹钟权限（Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "No exact alarm permission, using inexact alarm")
                // 降级为非精确闹钟
                setInexactAlarm(context, alarmManager, triggerTime, alarm.id)
                return true
            }
        }

        setExactAlarm(context, alarmManager, triggerTime, alarm.id)
        return true
    }

    /**
     * 精确闹钟
     * 优先使用 setAlarmClock()（API 21+，Android 专门给闹钟 App 的最高优先级 API）
     * 其次回退到 setExactAndAllowWhileIdle()（API 23+，Doze 模式也能触发）
     */
    private fun setExactAlarm(context: Context, alarmManager: AlarmManager, triggerTime: Long, alarmId: Long) {
        val pendingIntent = createPendingIntent(context, alarmId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API 21+: setAlarmClock() —— 最高优先级，状态栏显示闹钟图标，Doze/省电均不受影响
            // 这是 Android 官方推荐闹钟 App 使用的 API
            val showIntent = PendingIntent.getActivity(
                context,
                (alarmId and 0xFFFFFFFFL).toInt(),
                Intent(context, com.alarmclock.app.ui.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
            Log.d(TAG, "Scheduled with setAlarmClock() (highest priority)")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+: setExactAndAllowWhileIdle，Doze 模式下也能触发
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d(TAG, "Scheduled with setExactAndAllowWhileIdle()")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // API 19+: setExact
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * 非精确闹钟（降级方案）
     */
    private fun setInexactAlarm(context: Context, alarmManager: AlarmManager, triggerTime: Long, alarmId: Long) {
        val pendingIntent = createPendingIntent(context, alarmId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * 取消闹钟
     */
    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            val pendingIntent = createPendingIntent(context, alarmId)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm $alarmId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm $alarmId", e)
        }
    }

    /**
     * 为所有已启用的闹钟重新调度（开机/设置变更时调用）
     */
    suspend fun rescheduleAll(context: Context, getEnabledAlarms: suspend () -> List<Alarm>) {
        val alarms = getEnabledAlarms()
        Log.d(TAG, "Rescheduling ${alarms.size} enabled alarms")
        // 先取消所有（防止重复）
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (alarm in alarms) {
            try {
                alarmManager.cancel(createPendingIntent(context, alarm.id))
            } catch (_: Exception) {}
        }
        // 再逐个调度
        for (alarm in alarms) {
            schedule(context, alarm)
        }
    }

    /**
     * 创建 PendingIntent
     * 使用 alarm.id 作为 requestCode 确保唯一性
     */
    private fun createPendingIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarmId)
            // 使用 data 区分不同闹钟
            data = android.net.Uri.parse("alarmclock://alarm/$alarmId")
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }

        // requestCode 使用 alarm.id 的低 32 位
        val requestCode = (alarmId and 0xFFFFFFFFL).toInt()

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flags
        )
    }
}

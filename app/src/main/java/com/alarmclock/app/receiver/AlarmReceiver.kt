package com.alarmclock.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alarmclock.app.AlarmApplication
import com.alarmclock.app.data.Alarm
import com.alarmclock.app.service.AlarmRingingService
import com.alarmclock.app.utils.AlarmScheduler
import com.alarmclock.app.utils.WorkdayUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 闹钟触发广播接收器
 *
 * 当系统 AlarmManager 触发时，启动响铃服务
 * 同时判断：闹钟在"非工作日"模式下，如果今天实际上是工作日，应该跳过
 */
class AlarmReceiver : BroadcastReceiver() {

    private val TAG = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) {
            Log.e(TAG, "No alarm ID in intent")
            return
        }

        Log.d(TAG, "Received alarm trigger for ID: $alarmId")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as AlarmApplication
                val alarm = app.repository.getAlarmById(alarmId)

                if (alarm == null) {
                    Log.w(TAG, "Alarm $alarmId not found in database, cancel scheduling")
                    AlarmScheduler.cancel(context, alarmId)
                    return@launch
                }

                if (!alarm.enabled) {
                    Log.d(TAG, "Alarm $alarmId is disabled")
                    return@launch
                }

                // 检查：对于 REPEAT_WORKDAYS / REPEAT_NON_WORKDAYS 模式
                // 虽然我们已经用 WorkdayUtils 计算了下次响铃时间，
                // 但出于安全考虑，这里再次校验今天是否应该响
                if (!shouldRingToday(alarm)) {
                    Log.d(TAG, "Alarm $alarmId should NOT ring today (workday check), skip and reschedule")
                    // 跳到下一次符合条件的日期
                    AlarmScheduler.schedule(context, alarm)
                    return@launch
                }

                // 启动响铃服务
                val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                    action = AlarmRingingService.ACTION_START_RINGING
                    putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // 对于非重复闹钟，自动设为已触发（禁用）
                if (alarm.repeatType == Alarm.REPEAT_NEVER) {
                    app.repository.updateEnabled(alarmId, false)
                    AlarmScheduler.cancel(context, alarmId)
                } else {
                    // 重复闹钟：调度下一次
                    AlarmScheduler.schedule(context, alarm)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 再次校验今天是否应该响（双重保险） */
    private fun shouldRingToday(alarm: Alarm): Boolean {
        val today = Date()
        return when (alarm.repeatType) {
            Alarm.REPEAT_WORKDAYS -> WorkdayUtils.isWorkday(today, alarm.workdayMode)
            Alarm.REPEAT_NON_WORKDAYS -> WorkdayUtils.isNonWorkday(today, alarm.workdayMode)
            Alarm.REPEAT_CUSTOM -> {
                val cal = java.util.Calendar.getInstance().apply { time = today }
                val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                val index = when (dayOfWeek) {
                    java.util.Calendar.MONDAY -> 0
                    java.util.Calendar.TUESDAY -> 1
                    java.util.Calendar.WEDNESDAY -> 2
                    java.util.Calendar.THURSDAY -> 3
                    java.util.Calendar.FRIDAY -> 4
                    java.util.Calendar.SATURDAY -> 5
                    java.util.Calendar.SUNDAY -> 6
                    else -> -1
                }
                index >= 0 && (alarm.customDays and (1 shl index)) != 0
            }
            else -> true
        }
    }
}

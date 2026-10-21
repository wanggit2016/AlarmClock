package com.alarmclock.app

import android.app.Application
import com.alarmclock.app.data.AlarmDatabase
import com.alarmclock.app.data.AlarmRepository
import com.alarmclock.app.utils.AlarmScheduler
import com.alarmclock.app.utils.HolidayRemoteData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口类
 *
 * 负责初始化全局依赖（数据库、Repository），并在启动时同步调度闹钟
 */
class AlarmApplication : Application() {

    val database by lazy { AlarmDatabase.getInstance(this) }
    val repository by lazy { AlarmRepository(database.alarmDao()) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            // 先恢复节假日缓存，保证后续闹钟调度使用最新数据
            HolidayRemoteData.restoreFromCache(this@AlarmApplication)

            // 应用启动时，重新调度所有启用的闹钟
            // （防止系统在应用未运行时取消了 AlarmManager）
            AlarmScheduler.rescheduleAll(this@AlarmApplication) {
                repository.getEnabledAlarms()
            }

            // 后台联网刷新当年和明年的节假日数据（失败自动用缓存/内置数据兜底）
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            HolidayRemoteData.refreshYearIfNeeded(this@AlarmApplication, year)
            HolidayRemoteData.refreshYearIfNeeded(this@AlarmApplication, year + 1)
        }
    }
}

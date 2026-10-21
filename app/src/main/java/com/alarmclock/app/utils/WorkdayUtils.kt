package com.alarmclock.app.utils

import java.util.Calendar
import java.util.Date

/**
 * 工作日/非工作日判断工具类
 *
 * 提供两种判定模式：
 * 1. 简单模式（SIMPLE）：周一至周五为工作日，周六周日为非工作日
 * 2. 中国法定节假日模式（CHINA_HOLIDAY）：基于国务院节假日安排，自动识别调休和补班
 */
object WorkdayUtils {

    const val MODE_SIMPLE = 0
    const val MODE_CHINA_HOLIDAY = 1

    /**
     * 判断指定日期是否是工作日
     *
     * @param date 要判断的日期
     * @param mode 判定模式
     * @return true = 工作日，false = 非工作日
     */
    fun isWorkday(date: Date, mode: Int): Boolean {
        return when (mode) {
            MODE_SIMPLE -> isWorkdaySimple(date)
            MODE_CHINA_HOLIDAY -> isWorkdayChina(date)
            else -> isWorkdaySimple(date)
        }
    }

    /**
     * 判断指定日期是否是非工作日
     */
    fun isNonWorkday(date: Date, mode: Int): Boolean {
        return !isWorkday(date, mode)
    }

    /**
     * 简单模式：周一至周五为工作日
     */
    private fun isWorkdaySimple(date: Date): Boolean {
        val cal = Calendar.getInstance().apply { time = date }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Calendar.SUNDAY = 1, MONDAY = 2, ... SATURDAY = 7
        return dayOfWeek != Calendar.SUNDAY && dayOfWeek != Calendar.SATURDAY
    }

    /**
     * 中国法定节假日模式
     * 规则：
     *   1. 如果是调休上班日（extraWorkday）→ 工作日
     *   2. 如果是法定节假日（holiday）→ 非工作日
     *   3. 如果是普通周末（周六/周日）→ 非工作日
     *   4. 其他（周一至周五）→ 工作日
     */
    private fun isWorkdayChina(date: Date): Boolean {
        // 调休上班日优先判定（即使是周末也要上班）
        if (ChinaHolidayData.isExtraWorkday(date)) {
            return true
        }
        // 法定节假日（即使是周中也放假）
        if (ChinaHolidayData.isHoliday(date)) {
            return false
        }
        // 普通周末
        val cal = Calendar.getInstance().apply { time = date }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return false
        }
        // 周一至周五且不是节假日 → 工作日
        return true
    }

    /**
     * 获取下一个符合条件的响铃日期
     *
     * @param hour 小时
     * @param minute 分钟
     * @param repeatType 重复类型
     * @param customDays 自定义重复日期（Bitmap）
     * @param workdayMode 工作日判定模式
     * @return 下一次响铃的时间戳，如果没有则返回 null
     */
    fun getNextAlarmTime(
        hour: Int,
        minute: Int,
        repeatType: Int,
        customDays: Int,
        workdayMode: Int
    ): Long? {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // 如果今天的时间已经过了，从明天开始找
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val searchLimit = 365 // 最多搜索365天

        for (i in 0 until searchLimit) {
            val candidateDate = cal.time
            val candidateDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // SUNDAY=1, MONDAY=2, ...

            val shouldRing = when (repeatType) {
                com.alarmclock.app.data.Alarm.REPEAT_NEVER -> {
                    // 只找一次，且仅当今天是目标日期时响
                    if (i == 0) {
                        // 如果只响一次，那下一次就是今天（如果时间未过）
                        true
                    } else {
                        // 过了今天就不再响
                        return null
                    }
                }
                com.alarmclock.app.data.Alarm.REPEAT_DAILY -> true
                com.alarmclock.app.data.Alarm.REPEAT_WORKDAYS -> isWorkday(candidateDate, workdayMode)
                com.alarmclock.app.data.Alarm.REPEAT_NON_WORKDAYS -> isNonWorkday(candidateDate, workdayMode)
                com.alarmclock.app.data.Alarm.REPEAT_CUSTOM -> {
                    // 自定义日期：dayOfWeek 转换为 index (周一=0, 周日=6)
                    val index = when (candidateDayOfWeek) {
                        Calendar.MONDAY -> 0
                        Calendar.TUESDAY -> 1
                        Calendar.WEDNESDAY -> 2
                        Calendar.THURSDAY -> 3
                        Calendar.FRIDAY -> 4
                        Calendar.SATURDAY -> 5
                        Calendar.SUNDAY -> 6
                        else -> -1
                    }
                    if (index >= 0) (customDays and (1 shl index)) != 0 else false
                }
                else -> true
            }

            if (shouldRing) {
                return cal.timeInMillis
            }

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return null
    }
}

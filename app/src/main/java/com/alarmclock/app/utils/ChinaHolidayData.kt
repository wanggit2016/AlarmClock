package com.alarmclock.app.utils

import java.util.Calendar
import java.util.Date

/**
 * 中国法定节假日数据（内置2025-2026年节假日安排）
 * 用于准确判断某天是工作日还是非工作日
 *
 * 数据来源：国务院办公厅关于节假日安排的通知
 * 格式说明：
 *   holidays: 放假日期（即使是周末也放假）
 *   extraWorkdays: 调休上班日期（即使是周末也要上班）
 */
object ChinaHolidayData {

    // 节假日列表（格式：yyyy-MM-dd，这些日期即使落在周末也放假日）
    private val holidays = mutableSetOf<String>()

    // 调休上班日（格式：yyyy-MM-dd，这些日期即使落在周末也要上班）
    private val extraWorkdays = mutableSetOf<String>()

    // 联网获取的远程数据（按年份），优先级高于内置数据
    private val remoteHolidays = mutableMapOf<Int, Set<String>>()
    private val remoteExtraWorkdays = mutableMapOf<Int, Set<String>>()

    /** 设置某年份的远程节假日数据（由 HolidayRemoteData 调用） */
    fun setRemoteYearData(year: Int, holidaySet: Set<String>, extraWorkdaySet: Set<String>) {
        remoteHolidays[year] = holidaySet
        remoteExtraWorkdays[year] = extraWorkdaySet
    }

    init {
        // ==================== 2025年 ====================
        holidays += listOf(
            // 元旦
            "2025-01-01",
            // 春节（2025.1.28 - 2025.2.4）
            "2025-01-28", "2025-01-29", "2025-01-30", "2025-01-31",
            "2025-02-01", "2025-02-02", "2025-02-03", "2025-02-04",
            // 清明节
            "2025-04-04", "2025-04-05", "2025-04-06",
            // 劳动节
            "2025-05-01", "2025-05-02", "2025-05-03", "2025-05-04", "2025-05-05",
            // 端午节
            "2025-05-31", "2025-06-01", "2025-06-02",
            // 国庆节 + 中秋节（合并放假 2025.10.1 - 2025.10.8）
            "2025-10-01", "2025-10-02", "2025-10-03", "2025-10-04",
            "2025-10-05", "2025-10-06", "2025-10-07", "2025-10-08"
        )

        extraWorkdays += listOf(
            // 春节调休
            "2025-01-26", "2025-02-08",
            // 清明节调休
            "2025-04-27",
            // 劳动节调休（与清明共用 4月27日，Set 自动去重）
            // "2025-04-27", // 已在上方
            "2025-05-11",
            // 端午节调休
            "2025-09-28",
            // 国庆节调休（与端午共用 9月28日）
            // "2025-09-28", // 已在上方
            "2025-10-11"
        )

        // ==================== 2026年（预填，待官方公布后更新） ====================
        holidays += listOf(
            // 元旦
            "2026-01-01", "2026-01-02", "2026-01-03",
            // 春节（预计 2026.2.16 - 2026.2.22）
            "2026-02-16", "2026-02-17", "2026-02-18", "2026-02-19",
            "2026-02-20", "2026-02-21", "2026-02-22",
            // 清明节
            "2026-04-04", "2026-04-05", "2026-04-06",
            // 劳动节
            "2026-05-01", "2026-05-02", "2026-05-03", "2026-05-04", "2026-05-05",
            // 端午节
            "2026-06-19", "2026-06-20", "2026-06-21",
            // 中秋节
            "2026-09-25", "2026-09-26", "2026-09-27",
            // 国庆节（2026.10.1 - 2026.10.7）
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04",
            "2026-10-05", "2026-10-06", "2026-10-07"
        )

        extraWorkdays += listOf(
            // 元旦调休
            "2025-12-28", "2026-01-04",
            // 春节调休
            "2026-02-14", "2026-02-28",
            // 劳动节调休
            "2026-04-26", "2026-05-09"
        )
    }

    /** 判断指定日期是否是中国法定节假日 */
    fun isHoliday(date: Date): Boolean {
        val key = formatDate(date)
        val year = key.substring(0, 4).toInt()
        // 该年份有远程数据 → 以远程为准；否则用内置数据
        remoteHolidays[year]?.let { return it.contains(key) }
        return holidays.contains(key)
    }

    /** 判断指定日期是否是调休上班日（周末但要上班） */
    fun isExtraWorkday(date: Date): Boolean {
        val key = formatDate(date)
        val year = key.substring(0, 4).toInt()
        remoteExtraWorkdays[year]?.let { return it.contains(key) }
        return extraWorkdays.contains(key)
    }

    private fun formatDate(date: Date): String {
        val cal = Calendar.getInstance().apply { time = date }
        return String.format(
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}

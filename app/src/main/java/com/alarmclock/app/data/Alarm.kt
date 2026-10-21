package com.alarmclock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 闹钟实体
 *
 * @param id 主键，自增
 * @param hour 小时 0-23
 * @param minute 分钟 0-59
 * @param label 闹钟标签（可选）
 * @param enabled 是否启用
 * @param repeatType 重复类型：
 *   NEVER = 0, DAILY = 1, WORKDAYS = 2, NON_WORKDAYS = 3, CUSTOM = 4
 * @param customDays 自定义重复日期（仅当 repeatType = CUSTOM 时生效），
 *   使用 Bitmap 存储，bit 0 = 周一, bit 1 = 周二, ... bit 6 = 周日
 * @param workdayMode 工作日判定模式：SIMPLE = 0, CHINA_HOLIDAY = 1
 * @param createTime 创建时间戳
 * @param updateTime 更新时间戳
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String? = null,
    val enabled: Boolean = true,
    val repeatType: Int = REPEAT_NEVER,
    val customDays: Int = 0,
    val workdayMode: Int = WORKDAY_MODE_CHINA_HOLIDAY,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
) {
    companion object {
        // 重复类型常量
        const val REPEAT_NEVER = 0
        const val REPEAT_DAILY = 1
        const val REPEAT_WORKDAYS = 2
        const val REPEAT_NON_WORKDAYS = 3
        const val REPEAT_CUSTOM = 4

        // 工作日判定模式
        const val WORKDAY_MODE_SIMPLE = 0        // 简单模式：周一至周五
        const val WORKDAY_MODE_CHINA_HOLIDAY = 1  // 中国法定节假日模式

        /** 将自定义日期 Bitmap 转换为可读字符串 */
        fun customDaysToString(customDays: Int): String {
            val dayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
            val selected = mutableListOf<String>()
            for (i in 0..6) {
                if (customDays and (1 shl i) != 0) {
                    selected.add(dayNames[i])
                }
            }
            return "周${selected.joinToString("、")}"
        }
    }

    /** 获取重复规则的可读描述 */
    fun getRepeatDescription(): String {
        return when (repeatType) {
            REPEAT_NEVER -> "仅响一次"
            REPEAT_DAILY -> "每天"
            REPEAT_WORKDAYS -> if (workdayMode == WORKDAY_MODE_CHINA_HOLIDAY) {
                "工作日（含调休）"
            } else {
                "工作日（周一至周五）"
            }
            REPEAT_NON_WORKDAYS -> if (workdayMode == WORKDAY_MODE_CHINA_HOLIDAY) {
                "非工作日（含节假日）"
            } else {
                "周末（周六、周日）"
            }
            REPEAT_CUSTOM -> customDaysToString(customDays)
            else -> "仅响一次"
        }
    }

    /** 格式化时间 HH:mm */
    fun getTimeText(): String {
        return String.format("%02d:%02d", hour, minute)
    }
}

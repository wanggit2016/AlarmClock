package com.alarmclock.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 闹钟数据访问对象
 */
@Dao
interface AlarmDao {

    /** 获取所有闹钟（按时间排序） */
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<Alarm>>

    /** 获取所有闹钟（一次性查询） */
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    suspend fun getAllAlarmsOnce(): List<Alarm>

    /** 根据ID查询闹钟 */
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): Alarm?

    /** 获取所有启用的闹钟 */
    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabledAlarms(): List<Alarm>

    /** 插入闹钟，返回自增ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: Alarm): Long

    /** 更新闹钟 */
    @Update
    suspend fun update(alarm: Alarm)

    /** 删除闹钟 */
    @Delete
    suspend fun delete(alarm: Alarm)

    /** 根据ID删除 */
    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 更新启用状态 */
    @Query("UPDATE alarms SET enabled = :enabled, updateTime = :updateTime WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean, updateTime: Long = System.currentTimeMillis())
}

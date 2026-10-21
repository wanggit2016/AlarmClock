package com.alarmclock.app.data

import kotlinx.coroutines.flow.Flow

/**
 * 闹钟数据仓库
 */
class AlarmRepository(private val alarmDao: AlarmDao) {

    /** 获取所有闹钟（Flow） */
    fun getAllAlarms(): Flow<List<Alarm>> = alarmDao.getAllAlarms()

    /** 获取所有闹钟（一次性查询） */
    suspend fun getAllAlarmsOnce(): List<Alarm> = alarmDao.getAllAlarmsOnce()

    /** 根据ID查询 */
    suspend fun getAlarmById(id: Long): Alarm? = alarmDao.getAlarmById(id)

    /** 获取所有启用的闹钟 */
    suspend fun getEnabledAlarms(): List<Alarm> = alarmDao.getEnabledAlarms()

    /** 插入闹钟 */
    suspend fun insert(alarm: Alarm): Long = alarmDao.insert(alarm)

    /** 更新闹钟 */
    suspend fun update(alarm: Alarm) = alarmDao.update(alarm.copy(updateTime = System.currentTimeMillis()))

    /** 删除闹钟 */
    suspend fun delete(alarm: Alarm) = alarmDao.delete(alarm)

    /** 根据ID删除 */
    suspend fun deleteById(id: Long) = alarmDao.deleteById(id)

    /** 更新启用状态 */
    suspend fun updateEnabled(id: Long, enabled: Boolean) = alarmDao.updateEnabled(id, enabled)
}

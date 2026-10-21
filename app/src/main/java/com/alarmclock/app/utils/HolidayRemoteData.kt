package com.alarmclock.app.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 节假日远程数据获取与缓存
 *
 * 数据源：timor.tech 免费节假日 API（国务院公布放假安排后会同步更新）
 * 策略：
 *  - 每年数据缓存 7 天，避免频繁请求
 *  - 网络失败时自动使用上次缓存；无缓存则用内置数据兜底
 */
object HolidayRemoteData {

    private const val PREFS_NAME = "holiday_cache"
    private const val KEY_DATA_PREFIX = "data_"
    private const val KEY_TIME_PREFIX = "time_"
    private const val CACHE_VALID_MS = 7L * 24 * 60 * 60 * 1000 // 缓存 7 天
    private const val API_URL = "https://timor.tech/api/holiday/year/"

    /**
     * 缓存过期时联网刷新指定年份的节假日数据
     * @return true = 已有可用数据（缓存有效或刷新成功）
     */
    suspend fun refreshYearIfNeeded(context: Context, year: Int): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasCache = prefs.contains(KEY_DATA_PREFIX + year)
        val fresh = System.currentTimeMillis() - prefs.getLong(KEY_TIME_PREFIX + year, 0L) < CACHE_VALID_MS
        if (hasCache && fresh) return@withContext true

        val json = fetchYearJson(year) ?: return@withContext hasCache
        if (parseAndApply(year, json)) {
            prefs.edit()
                .putString(KEY_DATA_PREFIX + year, json)
                .putLong(KEY_TIME_PREFIX + year, System.currentTimeMillis())
                .apply()
            true
        } else {
            hasCache
        }
    }

    /** 从 SharedPreferences 恢复全部已缓存年份（App 启动时调用） */
    fun restoreFromCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.all.keys
            .filter { it.startsWith(KEY_DATA_PREFIX) }
            .forEach { key ->
                val year = key.removePrefix(KEY_DATA_PREFIX).toIntOrNull() ?: return@forEach
                val json = prefs.getString(key, null) ?: return@forEach
                parseAndApply(year, json)
            }
    }

    /** 解析 API 返回的 JSON 并写入 ChinaHolidayData */
    private fun parseAndApply(year: Int, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            if (root.optInt("code", -1) != 0) return false
            val holidayObj = root.optJSONObject("holiday") ?: return false

            val holidays = mutableSetOf<String>()
            val extraWorkdays = mutableSetOf<String>()
            val keys = holidayObj.keys()
            while (keys.hasNext()) {
                val item = holidayObj.optJSONObject(keys.next()) ?: continue
                val date = item.optString("date", "")
                if (date.isEmpty()) continue
                // holiday=true → 放假；holiday=false → 调休上班
                if (item.optBoolean("holiday")) holidays += date else extraWorkdays += date
            }
            if (holidays.isEmpty()) return false

            ChinaHolidayData.setRemoteYearData(year, holidays, extraWorkdays)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchYearJson(year: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(API_URL + year).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

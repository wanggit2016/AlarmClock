package com.alarmclock.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alarmclock.app.AlarmApplication
import com.alarmclock.app.R
import com.alarmclock.app.data.Alarm
import com.alarmclock.app.databinding.ActivityAlarmEditBinding
import com.alarmclock.app.utils.AlarmScheduler
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 添加/编辑闹钟界面
 *
 * 核心功能：
 * - 时间选择
 * - 标签设置
 * - 重复规则选择（含"非工作日"——本项目核心新功能）
 * - 工作日判定模式（简单模式 vs 中国法定节假日模式）
 * - 自定义重复日期
 */
class AlarmEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }

    private lateinit var binding: ActivityAlarmEditBinding
    private var editAlarm: Alarm? = null
    private var editingId: Long = -1L

    // 当前设置
    private var currentRepeatType = Alarm.REPEAT_NEVER
    private var currentCustomDays = 0
    private var currentWorkdayMode = Alarm.WORKDAY_MODE_CHINA_HOLIDAY

    // 星期信息：index 0-6 = 周一-周日
    private val weekDayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
    private val weekDayKeys = arrayOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)

        initViews()
        setupWeekdayChips()

        if (editingId > 0) {
            lifecycleScope.launch { loadAlarm() }
        } else {
            // 新建默认值
            val now = java.util.Calendar.getInstance()
            binding.timePicker.hour = now.get(java.util.Calendar.HOUR_OF_DAY)
            binding.timePicker.minute = now.get(java.util.Calendar.MINUTE)
        }
    }

    private fun initViews() {
        // 标题
        binding.tvTitle.text = if (editingId > 0) getString(R.string.edit_alarm) else getString(R.string.add_alarm)

        // 删除按钮（仅编辑模式可见）
        binding.btnDelete.visibility = if (editingId > 0) View.VISIBLE else View.GONE

        // 返回
        binding.btnBack.setOnClickListener { finish() }

        // 删除
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }

        // 保存
        binding.btnSave.setOnClickListener {
            saveAlarm()
        }

        // 重复类型单选
        binding.rbNever.setOnClickListener { selectRepeatType(Alarm.REPEAT_NEVER) }
        binding.rbDaily.setOnClickListener { selectRepeatType(Alarm.REPEAT_DAILY) }
        binding.rbWorkdays.setOnClickListener { selectRepeatType(Alarm.REPEAT_WORKDAYS) }
        binding.rbNonWorkdays.setOnClickListener { selectRepeatType(Alarm.REPEAT_NON_WORKDAYS) }
        binding.rbCustom.setOnClickListener { selectRepeatType(Alarm.REPEAT_CUSTOM) }

        // 工作日判定模式
        binding.rbModeSimple.setOnClickListener { currentWorkdayMode = Alarm.WORKDAY_MODE_SIMPLE }
        binding.rbModeChina.setOnClickListener { currentWorkdayMode = Alarm.WORKDAY_MODE_CHINA_HOLIDAY }

        // 时间选择器 - Material 3 默认使用 hour/minute 属性
        binding.timePicker.setIs24HourView(true)

        // 默认选中：不重复 + 中国法定节假日模式
        selectRepeatType(Alarm.REPEAT_NEVER)
        binding.rbModeChina.isChecked = true
    }

    private fun setupWeekdayChips() {
        val chipGroup = binding.chipGroupWeekdays
        chipGroup.removeAllViews()

        for (i in weekDayNames.indices) {
            val chip = Chip(this).apply {
                text = weekDayNames[i]
                isCheckable = true
                isClickable = true
                tag = i

                setOnCheckedChangeListener { compoundButton, isChecked ->
                    val dayIndex = compoundButton.tag as Int
                    if (isChecked) {
                        currentCustomDays = currentCustomDays or (1 shl dayIndex)
                    } else {
                        currentCustomDays = currentCustomDays and (1 shl dayIndex).inv()
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun selectRepeatType(type: Int) {
        currentRepeatType = type
        // ChipGroup 启用/禁用状态
        val enabled = type == Alarm.REPEAT_CUSTOM
        binding.chipGroupWeekdays.isEnabled = enabled
        for (i in 0 until binding.chipGroupWeekdays.childCount) {
            binding.chipGroupWeekdays.getChildAt(i).isEnabled = enabled
        }

        // 工作日判定模式仅在 WORKDAYS / NON_WORKDAYS 模式下有意义
        val modeEnabled = type == Alarm.REPEAT_WORKDAYS || type == Alarm.REPEAT_NON_WORKDAYS
        binding.rbModeSimple.isEnabled = modeEnabled
        binding.rbModeChina.isEnabled = modeEnabled
    }

    private suspend fun loadAlarm() {
        val app = applicationContext as AlarmApplication
        val alarm = withContext(Dispatchers.IO) { app.repository.getAlarmById(editingId) }
        if (alarm != null) {
            editAlarm = alarm

            binding.timePicker.hour = alarm.hour
            binding.timePicker.minute = alarm.minute

            if (!alarm.label.isNullOrBlank()) {
                binding.etLabel.setText(alarm.label)
            }

            // 重复类型
            when (alarm.repeatType) {
                Alarm.REPEAT_NEVER -> binding.rbNever.isChecked = true
                Alarm.REPEAT_DAILY -> binding.rbDaily.isChecked = true
                Alarm.REPEAT_WORKDAYS -> binding.rbWorkdays.isChecked = true
                Alarm.REPEAT_NON_WORKDAYS -> binding.rbNonWorkdays.isChecked = true
                Alarm.REPEAT_CUSTOM -> binding.rbCustom.isChecked = true
            }
            selectRepeatType(alarm.repeatType)

            // 自定义日期
            currentCustomDays = alarm.customDays
            for (i in 0 until binding.chipGroupWeekdays.childCount) {
                val chip = binding.chipGroupWeekdays.getChildAt(i) as Chip
                val dayIndex = chip.tag as Int
                chip.isChecked = (alarm.customDays and (1 shl dayIndex)) != 0
            }

            // 工作日判定模式
            currentWorkdayMode = alarm.workdayMode
            when (alarm.workdayMode) {
                Alarm.WORKDAY_MODE_SIMPLE -> binding.rbModeSimple.isChecked = true
                Alarm.WORKDAY_MODE_CHINA_HOLIDAY -> binding.rbModeChina.isChecked = true
            }
        } else {
            // 闹钟不存在，关闭编辑
            finish()
        }
    }

    private fun saveAlarm() {
        val hour = binding.timePicker.hour
        val minute = binding.timePicker.minute
        val label = binding.etLabel.text?.toString()?.trim()?.ifBlank { null }

        val alarm = Alarm(
            id = editAlarm?.id ?: 0,
            hour = hour,
            minute = minute,
            label = label,
            enabled = editAlarm?.enabled ?: true,
            repeatType = currentRepeatType,
            customDays = currentCustomDays,
            workdayMode = currentWorkdayMode
        )

        lifecycleScope.launch {
            val app = applicationContext as AlarmApplication
            val id = if (editingId > 0) {
                app.repository.update(alarm)
                editingId
            } else {
                app.repository.insert(alarm)
            }

            Toast.makeText(this@AlarmEditActivity, R.string.alarm_saved, Toast.LENGTH_SHORT).show()

            // 重新调度系统闹钟
            val savedAlarm = alarm.copy(id = id, enabled = true)
            AlarmScheduler.schedule(this@AlarmEditActivity, savedAlarm)

            finish()
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    val app = applicationContext as AlarmApplication
                    app.repository.deleteById(editingId)
                    AlarmScheduler.cancel(this@AlarmEditActivity, editingId)
                    Toast.makeText(this@AlarmEditActivity, R.string.alarm_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

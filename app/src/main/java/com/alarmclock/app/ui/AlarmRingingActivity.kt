package com.alarmclock.app.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alarmclock.app.AlarmApplication
import com.alarmclock.app.R
import com.alarmclock.app.data.Alarm
import com.alarmclock.app.databinding.ActivityAlarmRingingBinding
import com.alarmclock.app.service.AlarmRingingService
import com.alarmclock.app.utils.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闹钟响铃全屏界面
 *
 * 显示响铃闹钟的时间和标签，提供"关闭"和"稍后提醒"操作
 */
class AlarmRingingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingingBinding
    private var alarm: Alarm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 保持屏幕常亮 + 全屏显示
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // 音量调到最大（如果是静音模式）
        adjustVolume()

        val alarmId = intent.getLongExtra(AlarmRingingService.EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) {
            finish()
            return
        }

        loadAlarm(alarmId)
        setupClickListeners()
        setupPulseAnimation()
        updateTimeClock()
    }

    private fun adjustVolume() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (current < max) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    private fun loadAlarm(alarmId: Long) {
        lifecycleScope.launch {
            val app = applicationContext as AlarmApplication
            alarm = withContext(Dispatchers.IO) { app.repository.getAlarmById(alarmId) }

            alarm?.let { a ->
                binding.tvTime.text = a.getTimeText()
                if (!a.label.isNullOrBlank()) {
                    binding.tvLabel.visibility = View.VISIBLE
                    binding.tvLabel.text = a.label
                } else {
                    binding.tvLabel.visibility = View.GONE
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnDismiss.setOnClickListener { dismissAlarm() }
        binding.btnSnooze.setOnClickListener { snoozeAlarm() }
    }

    /** 关闭闹钟 */
    private fun dismissAlarm() {
        stopService()
        lifecycleScope.launch {
            alarm?.let { a ->
                val app = applicationContext as AlarmApplication
                if (a.repeatType == Alarm.REPEAT_NEVER) {
                    app.repository.updateEnabled(a.id, false)
                    AlarmScheduler.cancel(this@AlarmRingingActivity, a.id)
                }
            }
        }
        finish()
    }

    /** 稍后提醒（5分钟后再响） */
    private fun snoozeAlarm(minutes: Int = 5) {
        stopService()

        val snoozeTime = System.currentTimeMillis() + minutes * 60 * 1000L

        // 使用一次性 PendingIntent 来实现稍后提醒
        alarm?.let { a ->
            val intent = Intent(this, com.alarmclock.app.receiver.AlarmReceiver::class.java).apply {
                action = AlarmScheduler.ACTION_ALARM_TRIGGER
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, a.id)
                data = android.net.Uri.parse("alarmclock://snooze/${a.id}/${snoozeTime}")
            }

            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    } else 0

            val pi = android.app.PendingIntent.getBroadcast(
                this,
                (a.id and 0xFFFFFFFFL).toInt() + 100000, // 用不同的 requestCode 避免冲突
                intent,
                flags
            )

            val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, snoozeTime, pi)
            } else {
                am.setExact(android.app.AlarmManager.RTC_WAKEUP, snoozeTime, pi)
            }

            Toast.makeText(this, "${getString(R.string.snooze)} ${minutes}${getString(R.string.snooze_minutes)}", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun stopService() {
        val intent = Intent(this, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_STOP_RINGING
        }
        startService(intent)
    }

    /** 脉冲动画 */
    private fun setupPulseAnimation() {
        val animator = ObjectAnimator.ofFloat(binding.root, "alpha", 1f, 0.7f, 1f)
        animator.duration = 1500
        animator.repeatCount = ValueAnimator.INFINITE
        animator.start()
    }

    /** 更新时钟显示 */
    private fun updateTimeClock() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.tvTime.post(object : Runnable {
            override fun run() {
                val now = sdf.format(Date())
                // 只显示一次闹钟设置的时间，不实时更新
                binding.tvTime.postDelayed(this, 1000)
            }
        })
    }

    // 拦截返回键
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
            // 返回键和 Home 键不做处理，让默认行为保持屏幕常亮
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保服务在界面关闭后也停止
        stopService()
    }
}

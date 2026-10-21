package com.alarmclock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alarmclock.app.R
import com.alarmclock.app.ui.AlarmRingingActivity

/**
 * 闹钟响铃前台服务
 *
 * 使用前台服务确保闹钟在后台也能稳定响铃
 * 同时播放铃声和振动
 */
class AlarmRingingService : Service() {

    companion object {
        private const val TAG = "AlarmRingingService"
        private const val FOREGROUND_ID = 1001
        private const val CHANNEL_ID_FOREGROUND = "alarm_foreground"
        private const val CHANNEL_ID_ALARM = "alarm_notification"

        const val ACTION_START_RINGING = "com.alarmclock.app.START_RINGING"
        const val ACTION_STOP_RINGING = "com.alarmclock.app.STOP_RINGING"
        const val EXTRA_ALARM_ID = "extra_alarm_id"

        private var ringtone: Ringtone? = null
        private var vibrator: Vibrator? = null
        private var mediaPlayer: MediaPlayer? = null
    }

    private var currentAlarmId: Long = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RINGING -> {
                currentAlarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                startRinging(currentAlarmId)
            }
            ACTION_STOP_RINGING -> {
                stopRinging()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(alarmId: Long) {
        Log.d(TAG, "Start ringing for alarm $alarmId")

        // 启动前台通知
        startForeground(FOREGROUND_ID, buildNotification(alarmId))

        // 播放铃声
        playRingtone()

        // 启动振动
        startVibration()

        // 启动响铃Activity（全屏）
        val activityIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        startActivity(activityIntent)
    }

    private fun buildNotification(alarmId: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            (alarmId and 0xFFFFFFFFL).toInt(),
            Intent(this, AlarmRingingActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setContentTitle(getString(R.string.ringing_title))
            .setContentText("点击关闭闹钟")
            .setSmallIcon(R.drawable.ic_alarm_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val fgChannel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                getString(R.string.channel_name_foreground),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_desc_foreground)
                setShowBadge(false)
            }
            nm.createNotificationChannel(fgChannel)

            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                getString(R.string.channel_name_alarm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_desc_alarm)
                enableVibration(true)
            }
            nm.createNotificationChannel(alarmChannel)
        }
    }

    private fun playRingtone() {
        try {
            // 优先使用系统默认闹钟铃声
            val ringtoneUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (ringtoneUri != null) {
                ringtone = RingtoneManager.getRingtone(this, ringtoneUri).apply {
                    // Android 8+ 设置音频属性
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    }
                    isLooping = true
                }
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ringtone", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            vibrator?.let { v ->
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }

    private fun stopRinging() {
        Log.d(TAG, "Stop ringing")
        try {
            ringtone?.stop()
            ringtone = null
            vibrator?.cancel()
            vibrator = null
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringing", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }
}

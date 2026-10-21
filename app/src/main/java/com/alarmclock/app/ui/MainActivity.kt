package com.alarmclock.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.alarmclock.app.AlarmApplication
import com.alarmclock.app.R
import com.alarmclock.app.data.Alarm
import com.alarmclock.app.databinding.ActivityMainBinding
import com.alarmclock.app.utils.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主界面 - 闹钟列表
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter

    // 通知权限请求（Android 13+）
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 处理结果可后续扩展 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeAlarms()
        setupFab()

        // 请求必要权限
        requestNecessaryPermissions()
        // 检查精确闹钟权限
        checkExactAlarmPermission()
    }

    private fun setupRecyclerView() {
        adapter = AlarmAdapter(
            onItemClick = { alarm ->
                openEditActivity(alarm.id)
            },
            onToggle = { alarm, enabled ->
                toggleAlarm(alarm, enabled)
            }
        )
        binding.rvAlarms.layoutManager = LinearLayoutManager(this)
        binding.rvAlarms.adapter = adapter
    }

    private fun observeAlarms() {
        val app = applicationContext as AlarmApplication
        lifecycleScope.launch {
            app.repository.getAllAlarms().collectLatest { alarms ->
                adapter.submitList(alarms)
                binding.layoutEmpty.visibility = if (alarms.isEmpty()) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
            }
        }
    }

    private fun setupFab() {
        binding.fabAddAlarm.setOnClickListener {
            openEditActivity()
        }
    }

    private fun openEditActivity(alarmId: Long = -1L) {
        val intent = Intent(this, AlarmEditActivity::class.java).apply {
            if (alarmId > 0) putExtra(AlarmEditActivity.EXTRA_ALARM_ID, alarmId)
        }
        startActivity(intent)
    }

    private fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        val app = applicationContext as AlarmApplication
        lifecycleScope.launch {
            app.repository.updateEnabled(alarm.id, enabled)
            if (enabled) {
                AlarmScheduler.schedule(this@MainActivity, alarm.copy(enabled = true))
            } else {
                AlarmScheduler.cancel(this@MainActivity, alarm.id)
            }
        }
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // POST_NOTIFICATIONS（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(this, R.string.need_exact_alarm_permission, Toast.LENGTH_LONG).show()
                // 引导用户开启
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    // 从编辑页面返回后刷新列表
    override fun onResume() {
        super.onResume()
        // 列表数据通过 Flow 自动更新
    }
}

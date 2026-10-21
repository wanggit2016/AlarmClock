# 智能闹钟 App

一款支持"选择非工作日响"的 Android 闹钟应用。

## 核心功能

- ✅ **基础闹钟功能**：设置时间、标签、开关
- ✅ **多种重复规则**：不重复 / 每天 / 工作日 / **非工作日** / 自定义
- ✅ **智能工作日判定（核心新功能）**：
  - **简单模式**：周一至周五为工作日
  - **中国法定节假日模式**：自动识别国务院公布的调休和补班日
- ✅ **开机自启**：重启后自动恢复所有闹钟
- ✅ **稍后提醒**：响铃时可以暂停5分钟
- ✅ **Material Design 3**：现代化界面设计

## 项目结构

```
app/src/main/java/com/alarmclock/app/
├── AlarmApplication.kt          // 应用入口
├── data/                        // 数据层
│   ├── Alarm.kt                 // 闹钟实体
│   ├── AlarmDao.kt              // 数据访问接口
│   ├── AlarmDatabase.kt         // Room 数据库
│   └── AlarmRepository.kt       // 数据仓库
├── ui/                          // UI 层
│   ├── MainActivity.kt          // 主界面（闹钟列表）
│   ├── AlarmEditActivity.kt     // 添加/编辑闹钟
│   ├── AlarmRingingActivity.kt  // 响铃全屏界面
│   └── AlarmAdapter.kt          // 列表适配器
├── receiver/                    // 广播接收器
│   ├── AlarmReceiver.kt         // 闹钟触发接收
│   └── BootReceiver.kt          // 开机自启
├── service/                     // 服务
│   └── AlarmRingingService.kt   // 响铃前台服务
└── utils/                       // 工具类
    ├── AlarmScheduler.kt        // AlarmManager 调度
    ├── WorkdayUtils.kt          // 工作日/非工作日判断
    └── ChinaHolidayData.kt      // 中国法定节假日数据
```

## 技术栈

- Kotlin + Jetpack Compose (ViewBinding)
- Room 持久化存储
- AlarmManager + BroadcastReceiver
- Material Design 3

## 关键实现：非工作日判断

核心逻辑在 `WorkdayUtils.kt` 中：

```kotlin
// 计算下一次响铃时间时，判断是否符合重复规则
fun getNextAlarmTime(hour, minute, repeatType, customDays, workdayMode): Long? {
    // ...
    val shouldRing = when (repeatType) {
        REPEAT_NON_WORKDAYS -> isNonWorkday(candidateDate, workdayMode)
        REPEAT_WORKDAYS -> isWorkday(candidateDate, workdayMode)
        // ...
    }
    // ...
}
```

### 简单模式 vs 中国法定节假日模式

| 场景 | 简单模式 | 中国法定节假日模式 |
|------|---------|-----------------|
| 周一-周五（非节假日） | 工作日 | 工作日 |
| 周六、周日 | 非工作日 | 非工作日 |
| 国庆放假（10月1日 周三） | 工作日 ❌ | **非工作日** ✅ |
| 春节调休上班（1月26日 周日） | 非工作日 ❌ | **工作日** ✅ |

## 权限说明

| 权限 | 用途 |
|------|------|
| VIBRATE | 振动提醒 |
| RECEIVE_BOOT_COMPLETED | 开机自启 |
| WAKE_LOCK | 保持唤醒 |
| FOREGROUND_SERVICE | 前台服务（响铃） |
| POST_NOTIFICATIONS | 通知（Android 13+） |
| SCHEDULE_EXACT_ALARM | 精确闹钟（Android 12+） |
| USE_EXACT_ALARM | 精确闹钟（Android 12+） |

## 使用方法

### 在 Android Studio 中构建

1. 打开 Android Studio
2. File → Open → 选择项目根目录
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器
5. Run → Run 'app'

### Gradle 构建

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

## 注意事项

1. **Android 12+ 精确闹钟权限**：首次使用时需要手动授予，应用会引导您到系统设置
2. **Android 13+ 通知权限**：首次启动会自动请求
3. **电池优化**：建议关闭电池优化以确保闹钟准时响铃
4. **节假日数据**：内置了 2025-2026 年数据，后续可通过网络更新或扩展

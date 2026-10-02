# 体重记（WeightNote）

纯本地的安卓体重记录 App：按分组（如早晨 / 晚上）记录体重、体脂率和围度，并在同一张图上对比多条曲线。数据只保存在本机，不联网、不需要账号。

## 功能

- **多身份**：每个身份的分组、指标、记录、提醒、单位互相独立
- **分组**：默认有「早晨」「晚上」两个分组，可以增删、改名、换颜色、调整顺序；每个分组可以单独设置体重单位
- **按时间自动归组**（默认关闭）：给分组配置时间段（支持跨午夜），开启后记录时自动选中对应分组
- **记录**
  - 体重：数字键盘输入，默认带出该分组上次的值；可选填体脂率和备注；时间可以修改，也可以补录
  - 围度：腰围、臀围、胸围、大腿围、上臂围，也可以添加自定义项目
  - 同一天同组已有记录时弹出提示，默认**覆盖**，也可以**保留两条**；图表只取最新的一条
- **图表**：可选体重、BMI、体脂率、各项围度；分组可以多选，每组一条曲线；时间范围可选 7 天到全部，也可以自定义；支持拖动、缩放和点按查看数值；可显示 7 日均线、目标线，以及两个分组的差值曲线（如晚 − 早）
- **统计**：首页显示当前体重、较上次变化、BMI（中国标准）、目标进度；图表页显示区间内的最新、最高、最低、平均值和变化量
- **提醒**：每个分组可以设多个提醒时间；当天该分组已记录体重就不再提醒
- **备份**：导出 JSON（完整备份，可恢复）和 CSV（可用 Excel 打开）；从备份导入时可选追加或覆盖；还可以定期自动备份到指定文件夹（gzip 压缩，保留最近 N 份）

## 技术栈

Kotlin · Jetpack Compose（Material 3）· Room · DataStore · WorkManager · kotlinx.serialization · Navigation Compose。曲线图用 Compose Canvas 自己绘制。最低支持 Android 8.0（API 26）。

```
app/src/main/java/com/weightnote/
├── data/            单位换算、Repository、设置（DataStore）
│   ├── db/          Room 实体与 DAO
│   └── backup/      导入导出、自动备份 Worker
├── domain/          图表数据计算（同日取最新、移动平均、差值、BMI、时间规则）
├── reminder/        提醒闹钟、通知、开机重新安排
└── ui/              Compose 页面（首页、趋势、记录、设置、录入面板等）
```

## 构建

需要 JDK 17 和 Android SDK（platform 35、build-tools 35）。

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./gradlew assembleDebug          # 生成 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 运行单元测试
```

## 安装到手机

1. 手机开启「开发者选项 → USB 调试」，用数据线连接电脑
2. 执行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`

也可以把 APK 文件发到手机上直接安装（需要允许安装未知来源应用）。

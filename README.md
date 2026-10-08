# 体重记（WeightNote）

纯本地的安卓体重记录 App：按分组（如早晨 / 晚上）记录体重、体脂率和围度，并在同一张图上对比多条曲线。数据只保存在本机，不联网、不需要账号。

## 功能

- **多身份**：每个身份的分组、指标、记录、提醒、单位互相独立
- **分组**：默认有「早晨」「晚上」两个分组，可以增删、改名、换颜色、调整顺序；每个分组可以单独设置体重单位
- **按时间自动归组**（默认开启）：给分组配置时间段（支持跨午夜），记录时按当前时间自动选中分组，也可以在面板里手动改
- **记录**
  - 体重：数字键盘输入，默认带出该分组上次的值；可选填体脂率和备注；时间可以修改，也可以补录
  - 围度：腰围、臀围、胸围、大腿围、上臂围，也可以添加自定义项目
  - 同一天同组已有记录时弹出提示，默认**覆盖**，也可以**保留两条**；图表只取最新的一条
- **图表**：可选体重、BMI、体脂率、各项围度；分组可以多选，每组一条曲线；时间范围可选 7 天到全部，也可以自定义；支持拖动、缩放和点按查看数值；可显示 7 日均线、目标线，以及差值曲线
  - 差值曲线默认是**隔夜变化**：第二天早晨 − 前一天晚上，点落在第二天；也可以切换成同一天内的变化
- **数据洞察**：首页自动算出隔夜平均变化、一天内的变化、最近两周的周对比，以及按趋势预计达到目标的日期
- **统计**：首页显示当前体重、较上次变化、BMI（中国标准）、目标进度；图表页显示区间内的最新、最高、最低、平均值和变化量
- **提醒**：每个分组可以设多个提醒时间；当天该分组已记录体重就不再提醒；通知上可以直接「去记录」或「30 分钟后提醒」
- **桌面小组件**：显示当前身份今天各分组是否已记录，点分组直接打开对应的记录面板
- **回收站**：删除的记录保留 7 天，可以恢复或彻底删除；被覆盖的旧记录也会放进来
- **备份**：导出 JSON（完整备份，可恢复）和 CSV（可用 Excel 打开）；从备份导入时可选追加或覆盖；还可以定期自动备份到指定文件夹（gzip 压缩，保留最近 N 份）

## 技术栈

Kotlin · Jetpack Compose（Material 3）· Room · DataStore · WorkManager · Glance（桌面小组件）· kotlinx.serialization · Navigation Compose。曲线图用 Compose Canvas 自己绘制。最低支持 Android 8.0（API 26）。

```
app/src/main/java/com/weightnote/
├── data/            单位换算、Repository、设置（DataStore）
│   ├── db/          Room 实体、DAO、数据库升级
│   └── backup/      导入导出、自动备份 Worker
├── domain/          图表数据计算、洞察与目标预测、时间规则
├── reminder/        提醒闹钟、通知、开机重新安排
├── widget/          桌面小组件
└── ui/              Compose 页面（首页、趋势、记录、设置、录入面板等）
app/src/test/         单元测试 + 数据库升级测试（Robolectric）
```

## 构建

需要 JDK 17 和 Android SDK（platform 35、build-tools 35）。

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./gradlew assembleDebug          # 调试包
./gradlew assembleRelease        # 正式包（体积小得多）
./gradlew testDebugUnitTest      # 单元测试 + 数据库升级测试
```

推送代码后，GitHub Actions 会自动跑测试并编译两种安装包，可以在 Actions 页面下载。

## 签名

正式包和调试包都使用**同一把密钥签名**，所以两种包可以互相覆盖安装，不会丢失数据。

- 密钥文件放在项目外：`~/.weightnote/weightnote-release.keystore`
- 密码等信息放在项目根目录的 `keystore.properties`（已加入 `.gitignore`，不会提交）

**请务必备份 `~/.weightnote/` 目录**。密钥一旦丢失，以后就无法覆盖安装，只能卸载重装（本机数据会丢失）。

新电脑上编译时，把这两个文件放回原位即可。CI 也可以改用环境变量提供：
`WEIGHTNOTE_KEYSTORE`、`WEIGHTNOTE_STORE_PASSWORD`、`WEIGHTNOTE_KEY_ALIAS`、`WEIGHTNOTE_KEY_PASSWORD`。

## 安装到手机

1. 手机开启「开发者选项 → USB 调试」，用数据线连接电脑
2. 执行 `adb install -r dist/weightnote-1.1.0-release.apk`

也可以把 APK 文件发到手机上直接安装（需要允许安装未知来源应用）。

# 零终端 · ZERO://TERMINAL

宿迁学院双端课程表终端 —— Kotlin / Compose Multiplatform（Android + Windows 桌面）。

自动爬取教务系统课表，终端机能风 UI，配套安卓桌面小组件与小米澎湃「超级岛」上课提醒。

## 功能

**课表核心**
- 教务系统（正方 URP，`jwgl.squ.edu.cn`）CAS 统一身份认证登录，支持触发风控时的图形验证码
- 课表自动爬取 + 本地缓存，约一天自动重爬更新（Android WorkManager / 桌面前台调度）
- **真实节次时间**：从 `print-data` 接口的 `timeTableLayout.courseUnitList` 解析（40 分钟小课，共 12 节），不再使用猜的占位作息
- 周视图网格：大节/小节一键折叠切换（自然 morph 动画）、左右滑动切周、一键回本周
- 课程状态识别：课前 40 分钟预警（黄）/ 上课中（绿脉冲）/ 课间指向下一节
- 深/浅色双主题，切周/切折叠/切主题带故障（glitch）转场特效
- 备忘录：点表头进入当天时间轴编辑，事件与课程重叠时直接替换卡片节次标签

**Android**
- 桌面小组件（Glance）：今日课程列表 + NEXT 条，30 分钟自刷 + 同步即推
- **澎湃超级岛 / MIUI 焦点通知**：上课开始时弹出（课名 + 教室 + 环形进度），20 分钟自动消失
- 校园卡余额查询（ehall 办事大厅，复用教务 CAS 凭据，低于 5 元变红）
- 开机自启后自动重排提醒

**桌面（Windows）**
- 同款周视图 + 右侧今日栏 + 校园卡余额
- 鼠标光源、CAD 十字准星 + 坐标、卡片悬停信息浮层、等高线/噪点纹理

## 技术栈

- Kotlin 2.1.20 / Compose Multiplatform 1.8.0 / AGP 8.9.1
- Ktor 3 手写会话层（CAS 登录、cookie 维护）
- Android：Glance AppWidget、WorkManager、AlarmManager
- 序列化：kotlinx.serialization；存储：双端 PlatformStorage（JSON 文件）

## 构建

仓库不存放二进制文件。clone 后先生成应用图标（需要 Python + Pillow）：

```bash
pip install pillow
python tool/gen_icons.py   # 生成 13 个图标 + icon.ico，已存在则跳过
```

另外仓库未包含 `gradle-wrapper.jar`，请使用本机 Gradle 8.13+，或先执行一次 `gradle wrapper` 生成。

```bash
# Android APK
gradle :composeApp:assembleDebug

# Windows 桌面运行 / 打包 MSI+EXE
gradle :composeApp:run
gradle :composeApp:packageDistributionForCurrentOS
```

## 超级岛实现说明

本项目「超级岛 / 焦点通知」的实现方式（`miui.focus.param` 的 `param_v2` JSON 结构、`param_island` 摘要态、LOW 重要性渠道 + `Notification.Builder` extras 注入等）**参考自开源项目 [轻屿课表 mikcb](https://github.com/Mutx163/mikcb)（作者 Mutx163，GPL-3.0）**，在此表示感谢。代码为本项目按自身架构重新实现，未直接复制其源码。

## 免责声明

仅供学习交流。课表数据来自学校教务系统，请合理使用爬虫频率，勿用于商业用途。登录凭据仅存储在本机应用私有目录。

# 海信 75E3F（国内版）适配

本 fork 把归藏电视适配到一台国内版海信 75E3F。目标行为：

1. **开机直接进入 HDMI 2**（IPTV 机顶盒）。
2. 在 IPTV 画面按 **返回**，回到归藏电视首页；Home 键同样回到归藏电视。
3. 首页默认：**IPTV（HDMI 2）**、**PS5 / Switch（HDMI 1）**、**哔哩哔哩**、**投屏**、**斗鱼直播**、**原桌面**。
   应用类入口只在电视上装了对应应用时出现，缺的不留空位。首次在设置里编辑首页后，以保存的顺序为准。

包名为 `io.github.sunsetwarrior.guizangtv`，与上游正式版可以并存。

以上行为已在实机（2026-10-21）上验证，见文末「实机记录」。

## 实现方式

- **开机进入 HDMI 2 用电视自带的「开机信号源」**（电视设置里设为 HDMI 2/ARC）。这台固件开机时 system_server 总是显式拉起自带桌面 `com.jamdeo.tv.vod`，不管默认桌面是谁，所以归藏电视没法在开机时接管；固件随后按开机信号源进入 HDMI 2。
- **归藏电视设为默认桌面（HOME）**。LiveTV（信号源播放器）按返回时发 HOME，于是回到归藏电视；遥控器 Home 键同理。
- **首页切 HDMI**：这台电视的 `TvInputManager` 是空的，标准接口不可用。改为和系统信号源面板相同的做法：
  1. 反射调用 boot classpath 里 `com.jamdeo.flow.jar` 的 `com.jamdeo.tv.TvManager.getInstance().getSourceManager(ctx)`（异步绑定 `com.jamdeo.tv.service`，`IManagerStateListener.onServiceAvailable` 后可用）；
  2. `setCurrentInputSource(1280 + n)`（HDMI 1 = 1281，HDMI 2/ARC = 1282）；
  3. 启动 `com.jamdeo.tv.livetv/.LiveTvActivity`，extra `switch_by_trans=true`（表示信号源已切好，只负责显示）。
  
  直接启动 LiveTvActivity 只会恢复「上一次的信号源」，各种指定信号源的 extra 都被忽略；`SILO_LAUNCH_EVENT` 等 tiletransition 广播是受保护广播，普通应用发不了。

代码位置：

| 文件 | 作用 |
| --- | --- |
| `TvControls.java` | 按机型分派：小米走 `XiaomiControls`，海信先走 `HisenseControls.openHdmi`，其他机型用标准 Android TV 接口切 HDMI；`prepare()` 在首页创建时预先绑定厂商服务 |
| `HisenseControls.java` | 海信识别、`SourceManager` 反射（切源、读开机信号源）、原桌面与投屏包名 |
| `BootStart.java` | 海信：设置页显示固件开机信号源与默认桌面状态（只读）；`HDMI_PORT` 为 IPTV 端口 |
| `HomeItems.java` | 首页默认项和名称；`defaults()` 按候选包名挑第一个已安装的 |

## 实机记录（75E3F）

| 项目 | 结果 |
| --- | --- |
| 机型识别 | `ro.product.manufacturer=Hisense`，`ro.product.model=VIDAA_TV`（不含 75E3F），Android 9 / SDK 28，`ro.product.device=NOVA72691` |
| HDMI 切换 | `dumpsys tv_input` 为空；用上面的 `SourceManager` 方案，实测 HDMI 1 / HDMI 2 均可切，返回回到归藏电视 |
| 信号源列表 | `HDMI 1=1281`、`HDMI 2/ARC=1282`、`AV=1025`、`DVBC=2561`（`SignalSourceModule` 日志） |
| 信号源面板 | `com.hisense.tv.inputs` 不存在；系统面板是 `com.jamdeo.tv.vod` 里的 `SignalViewService`，只由受保护广播 `com.jamdeo.tv.policy.PANEL_EVENT` 拉起，应用打不开（首页设置里的「信号源」会提示暂时无法打开） |
| 原桌面 | `com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity`（聚好看）。从它按返回不会退出（它本身是桌面），按 Home 回归藏电视 |
| 投屏 | `com.hisense.multiscreen.screenhelper`（Hi投屏）；`com.ms.weshare` 已装但无启动入口；`com.hisense.tv.mirror` 是「拍照」 |
| 斗鱼直播 | 未安装，首页不显示该入口；装的是虎牙 TV 版 `com.huya.nftv` |
| 返回键 | LiveTV 按返回会发 HOME；默认桌面是归藏电视时回到首页 |
| 开机方式 | 遥控器电源键关机再开机是**完整开机**（有开机广告），会进入 HDMI 2 |
| ADB | 首次连接一直 `unauthorized` 且不弹授权框，电视重启后才授权成功 |
| 注入按键 | `adb shell input keyevent KEYCODE_HOME` 会被拦（`tv_user_setup_complete` 未设，`Not starting activity because user setup is in progress`）；遥控器 Home 正常，无需改这个设置 |

开机过程：开机广告 → 聚好看一闪 → HDMI 2。广告和聚好看这一步由固件控制。

## 步骤（在和电视同一 Wi-Fi 的电脑上）

### 1. 探测（只读）

```sh
adb connect 192.168.3.11:5555
adb shell getprop ro.product.model
adb shell getprop ro.product.manufacturer
adb shell getprop ro.build.version.release
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm list packages | grep -i -E "hisense|jamdeo|vidaa|juhaokan|keylab|xiaodianshi|douyu|cast|share|screen"
adb shell dumpsys tv_input | head -80
```

### 2. 构建并安装

```sh
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以从 GitHub Actions 的 `guizang-tv-debug` 产物下载 APK。

从电视应用列表打开归藏电视，查看应用看到的状态：

```sh
adb shell dumpsys activity io.github.sunsetwarrior.guizangtv | grep -A6 -E "BootStart|TvControls"
```

应看到 `hisense sourceManager=true ready=true`，`startup=` 为当前开机信号源。

### 3. 设为默认桌面

先记录原桌面（第 1 步 `resolve-activity` 的输出，75E3F 上是 `com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity`），然后：

```sh
adb shell cmd package set-home-activity io.github.sunsetwarrior.guizangtv/io.github.op7418.guizangtv.FrameHomeActivity
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

不需要禁用原桌面（禁用后首页「原桌面」入口就打不开了，开机时固件仍会先拉起它）。

**不要禁用** `com.hisense.tv.tiletransition`，否则信号源切换不再生效。

### 4. 设开机信号源并验证

在电视 **设置** 中把「开机信号源」设为 **HDMI 2/ARC**。归藏电视 **设置 → 电视与启动 → 开机进入 HDMI 2** 会显示读到的开机信号源和默认桌面状态。然后完整重启：

```sh
adb shell reboot
```

开机后应自动进入 IPTV；按返回回到归藏电视首页。

### 恢复

```sh
adb shell cmd package set-home-activity com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity
adb uninstall io.github.sunsetwarrior.guizangtv
```

开机信号源在电视设置里改回即可。

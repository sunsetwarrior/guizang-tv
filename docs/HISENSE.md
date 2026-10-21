# 海信 75E3F（国内版）适配

本 fork 把归藏电视适配到一台国内版海信 75E3F。目标行为：

1. **开机直接进入 HDMI 2**（IPTV 机顶盒）。
2. 在 IPTV 画面按 **返回**，回到归藏电视首页；Home 键同样回到归藏电视。
3. 首页默认：**IPTV（HDMI 2）**、**PS5 / Switch（HDMI 1）**、**哔哩哔哩**、**投屏**、**斗鱼直播**、**原桌面**。
   应用类入口只在电视上装了对应应用时出现，缺的不留空位。首次在设置里编辑首页后，以保存的顺序为准。

包名为 `io.github.sunsetwarrior.guizangtv`，与上游正式版可以并存。

## 实现方式

小米版靠小米桌面的 `start_3rd_app` 开机拉起应用。海信没有这个接口，所以改为：

- **归藏电视设为默认桌面（HOME）**。开机后系统启动的第一个界面就是它。
- 作为 HOME 首次启动时，`BootStart.fromHome()` 判断这是本次开机的第一次，若是则打开 HDMI 2。电视输入服务可能比桌面晚就绪，所以失败时每 2.5 秒重试一次，最多 8 次；用户离开首页时停止重试。
- HDMI 画面是从归藏电视打开的，按返回会回到它。

代码位置：

| 文件 | 作用 |
| --- | --- |
| `TvControls.java` | 按机型分派：小米走 `XiaomiControls`，其他机型用标准 Android TV 接口（`TvInputManager` + `TvContract.buildChannelUriForPassthroughInput`）切 HDMI |
| `HisenseControls.java` | 海信识别、信号源面板、原桌面包名 |
| `BootStart.java` | `fromHome()`：海信开机进入 HDMI 2；`HDMI_PORT` 改端口 |
| `HomeItems.java` | 首页默认项和名称；`defaults()` 按候选包名挑第一个已安装的 |

## 待在电视上确认的事项

以下内容**尚未在 75E3F 上验证**。`HisenseControls` 里的包名取自上游 cleanyourtv 的 Vidda / 海信清单（`examples/vidda-mt9653`），75E3F 可能不同。

| 项目 | 当前做法 | 如何确认 |
| --- | --- | --- |
| 机型识别 | `Build.MANUFACTURER` / `BRAND` 为 hisense，或型号含 75E3F | `getprop ro.product.manufacturer` 等 |
| HDMI 切换 | `TvInputManager` 中标签为 “HDMI 2” 的输入 | `dumpsys tv_input`；`dumpsys activity <包名>` 中的 `TvControls:` 段列出应用看到的 HDMI 输入 |
| 信号源面板 | `com.hisense.tv.inputs/com.hisense.tv.InputsUIService`，action `com.hisense.tv.start_inputs` | `pm list packages`、`dumpsys package com.hisense.tv.inputs` |
| 原桌面 | `com.jamdeo.tv.vod`，否则任意其他 HOME | `cmd package resolve-activity` |
| 投屏 | `HisenseControls.CAST` 中第一个有启动入口的包（Vidda 上的 `com.ms.ucast.ucastapp` 等） | 电视上投屏应用的包名，见下方命令 |
| 斗鱼直播 | `com.douyu.xl.douyutv`（斗鱼 TV 版） | 应用商店装的是否就是这个包 |
| 返回键 | 海信 IPTV / 信号源播放器按返回是否结束自身 | 实机按键 |
| 开机方式 | 依赖完整开机（`BOOT_COUNT` 变化） | 遥控器关机是**待机**还是**真关机**？若是快速待机，唤醒不会触发开机进入，需改为监听亮屏或使用电视自带“开机信号源”设置 |

若 `dumpsys tv_input` 为空（Vidda 固件上就是空的），标准接口无法切 HDMI，需要找海信自己的切换入口，写进 `HisenseControls`，再由 `TvControls.openHdmi()` 调用。

## 步骤（在和电视同一 Wi-Fi 的电脑上）

### 1. 探测（只读）

```sh
adb connect 192.168.3.11:5555
adb shell getprop ro.product.model
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.brand
adb shell getprop ro.build.version.release
adb shell getprop ro.build.display.id
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm list packages | grep -i -E "hisense|jamdeo|vidaa|juhaokan|keylab|xiaodianshi|douyu|cast|share|screen"
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER
adb shell dumpsys tv_input | head -80
adb shell dumpsys package com.hisense.tv.inputs | grep -i -E "service|exported|permission" | head -20
```

### 2. 构建并安装

```sh
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以从 GitHub Actions 的 `guizang-tv-debug` 产物下载 APK。

先从电视应用列表打开归藏电视，逐项试：IPTV、PS5 / Switch、哔哩哔哩、信号源、设置。查看应用看到的状态：

```sh
adb shell dumpsys activity io.github.sunsetwarrior.guizangtv | grep -A6 -E "BootStart|TvControls"
```

### 3. 设为默认桌面

先记录原桌面（第 1 步 `resolve-activity` 的输出），然后：

```sh
adb shell cmd package set-home-activity io.github.sunsetwarrior.guizangtv/io.github.op7418.guizangtv.FrameHomeActivity
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

如果原桌面仍会抢回 Home，再禁用它（只禁用，不卸载）。**禁用后首页的“原桌面”入口就打不开了**，能不禁用就不禁用：

```sh
adb shell pm disable-user --user 0 com.jamdeo.tv.vod   # 以第 1 步看到的原桌面包名为准
```

上游 Vidda 清单记录过两个坑，75E3F 上若出现同样现象可参考：

- 遥控器 Home 键被映射为 `KEYCODE_F12`，由厂商应用切回“上一个应用”；原桌面禁用后 Home 键可能失效。
- `com.keylab.speech.view.vidaa` 开机时按组件名拉起第二个厂商首页，会盖在默认桌面上。
- **不要禁用** `com.hisense.tv.tiletransition`，否则信号源面板选 HDMI 不再生效。

### 4. 验证

在归藏电视 **设置 → 电视与启动 → 开机进入 HDMI 2** 中确认“默认桌面：已是归藏电视”，然后完整重启：

```sh
adb shell reboot
```

开机后应自动进入 IPTV；按返回回到归藏电视首页。用遥控器关机再开机也试一次，确认是完整开机还是待机唤醒。

### 恢复

```sh
adb shell pm enable com.jamdeo.tv.vod   # 若第 3 步禁用过
adb shell cmd package set-home-activity <第 1 步记录的原桌面组件>
adb uninstall io.github.sunsetwarrior.guizangtv
```

package io.github.op7418.guizangtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Once-per-full-boot entry. Xiaomi: opt-in launch through the stock launcher's start_3rd_app hook.
 * Hisense: the firmware always starts its own launcher at boot, so booting into HDMI_PORT is the
 * TV's own 开机信号源 setting; this app is the default Home, so Back from that input lands here.
 * Never handles screen-on or the Home key itself.
 */
public final class BootStart {
    private static final String PREFS = "boot-start";
    private static final String EXTRA = "guizang.boot-count";
    /** The IPTV box. */
    static final int HDMI_PORT = 2;
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static boolean enabled(Context c) {
        if (XiaomiControls.enabled()) return prefs(c).getBoolean("enabled", false);
        return HisenseControls.enabled() && HisenseControls.startupSource(c) == HisenseControls.hdmiSource(HDMI_PORT);
    }
    static String title() { return XiaomiControls.enabled() ? "开机自动进入" : "开机进入 HDMI " + HDMI_PORT; }
    private static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    static void enter(Activity a) {
        int boot = bootCount(a);
        String reason = BootStartPolicy.skipReason(XiaomiControls.enabled(), enabled(a), boot, prefs(a).getInt("attempted", -1),
                SystemClock.elapsedRealtime());
        if (reason != null) { Log.i("GuiZangBoot", reason); return; }
        if (!prefs(a).edit().putInt("attempted", boot).putString("status", "系统已调用启动入口，等待界面确认").commit()) return;
        try {
            a.startActivity(new Intent(a, FrameHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA, boot));
        } catch (RuntimeException error) {
            status(a, "启动失败：" + error.getClass().getSimpleName());
            Log.w("GuiZangBoot", "Launch rejected", error);
        }
    }
    private static boolean configured(Context c) {
        return c.getPackageName().equals(Settings.System.getString(c.getContentResolver(), "start_3rd_app"));
    }
    private static void status(Context c, String value) {
        prefs(c).edit().putString("status", value).apply();
        Log.i("GuiZangBoot", value);
    }
    static void confirm(Activity activity) {
        Intent intent = activity.getIntent();
        int boot = intent.getIntExtra(EXTRA, -1);
        if (boot < 0) return;
        intent.removeExtra(EXTRA);
        if (boot == bootCount(activity) && prefs(activity).getInt("attempted", -1) == boot && enabled(activity)) {
            status(activity, "已在本次开机后进入桌面");
        }
    }
    static void settings(Activity a, Runnable systemSettings) {
        new AlertDialog.Builder(a).setTitle("设置")
                .setItems(new String[]{"电视系统设置", title() + "：" + (enabled(a) ? "已开启" : "已关闭")},
                        (d, i) -> { if (i == 0) systemSettings.run(); else show(a); })
                .setNegativeButton("返回", null).show();
    }
    static void show(Activity a) {
        if (HisenseControls.enabled() && !XiaomiControls.enabled()) {
            String startup = HisenseControls.sourceName(a, HisenseControls.startupSource(a));
            new AlertDialog.Builder(a).setTitle(title())
                    .setMessage("海信开机总是先启动自带桌面，开机进入哪个信号源由电视设置里的「开机信号源」决定。"
                            + "设为 HDMI " + HDMI_PORT + " 后，开机直接进入 IPTV；按返回回到归藏电视首页。\n\n"
                            + "开机信号源：" + (startup == null ? "暂时读不到" : startup)
                            + (enabled(a) ? "" : "（请在电视设置中改为 HDMI " + HDMI_PORT + "）")
                            + "\n默认桌面：" + (TvControls.isDefaultHome(a) ? "已是归藏电视" : "还不是，需电脑设置（docs/HISENSE.md）"))
                    .setNegativeButton("返回", null).show();
            return;
        }
        if (!XiaomiControls.enabled()) {
            new AlertDialog.Builder(a).setTitle("开机自动进入（实验）")
                    .setMessage("目前仅适配 MiTV-MTSU0。其他机型请先使用普通应用入口。")
                    .setNegativeButton("返回", null).show();
            return;
        }
        String state = prefs(a).getString("status", "尚未进行完整重启测试");
        new AlertDialog.Builder(a).setTitle("开机自动进入（实验）")
                .setMessage("仅在完整开机后尝试一次，不接管 Home 键，同次开机的后续调用直接跳过。\n\n"
                        + "由已适配的小米系统调用启动入口；不需要悬浮窗权限。\n\n"
                        + "系统入口：" + (configured(a) ? "已配置" : "尚未配置，需电脑设置") + "\n上次状态：" + state)
                .setPositiveButton(enabled(a) ? "关闭自动进入" : "开启自动进入", (d, which) -> {
                    boolean value = !enabled(a);
                    prefs(a).edit().putBoolean("enabled", value).apply();
                    Toast.makeText(a, value ? (configured(a) ? "已开启，下次完整重启时尝试进入" : "开关已开启，还需按项目文档配置系统入口") : "已关闭开机自动进入", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("返回", null).show();
    }
    static void dump(Context c, java.io.PrintWriter out) {
        out.println("BootStart: enabled=" + enabled(c) + " configured=" + configured(c)
                + " boot=" + bootCount(c) + " attempted=" + prefs(c).getInt("attempted", -1)
                + " status=" + prefs(c).getString("status", "none"));
    }
}

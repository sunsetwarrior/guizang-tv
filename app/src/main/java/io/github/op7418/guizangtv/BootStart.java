package io.github.op7418.guizangtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Once-per-full-boot entry. Xiaomi: opt-in launch through the stock launcher's start_3rd_app hook.
 * Hisense: this app is the default Home, and its first Home launch after boot opens HDMI_PORT, so
 * Back from that input lands on this home. Never handles screen-on or the Home key itself.
 */
public final class BootStart {
    private static final String PREFS = "boot-start";
    private static final String EXTRA = "guizang.boot-count";
    /** The IPTV box. */
    static final int HDMI_PORT = 2;
    /** The input service can register after Home first starts, so a boot switch retries briefly. */
    private static final int HDMI_TRIES = 8;
    private static final long HDMI_RETRY_MS = 2500;
    private static final Handler retry = new Handler(Looper.getMainLooper());
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static boolean enabled(Context c) {
        if (XiaomiControls.enabled()) return prefs(c).getBoolean("enabled", false);
        return HisenseControls.enabled() && prefs(c).getBoolean("hdmi-enabled", true);
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
    /** Called with the Intent that created the home; only a HOME launch can switch inputs. */
    static void fromHome(Activity a) {
        Intent intent = a.getIntent();
        if (XiaomiControls.enabled() || intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        int boot = bootCount(a);
        String reason = BootStartPolicy.skipReason(HisenseControls.enabled(), enabled(a), boot,
                prefs(a).getInt("hdmi-attempted", -1), SystemClock.elapsedRealtime());
        if (reason != null) { Log.i("GuiZangBoot", reason); return; }
        if (!prefs(a).edit().putInt("hdmi-attempted", boot).commit()) return;
        openHdmi(a, 1);
    }
    private static void openHdmi(Activity a, int attempt) {
        if (a.isFinishing() || a.isDestroyed()) return;
        if (TvControls.openHdmi(a, HDMI_PORT)) { status(a, "已在本次开机后进入 HDMI " + HDMI_PORT + "（第 " + attempt + " 次尝试）"); return; }
        if (attempt >= HDMI_TRIES) { status(a, "开机切换 HDMI " + HDMI_PORT + " 失败，已停留在首页"); return; }
        retry.postDelayed(() -> openHdmi(a, attempt + 1), HDMI_RETRY_MS);
    }
    /** The user left or picked something else; a late boot switch must not take the screen. */
    static void cancel() { retry.removeCallbacksAndMessages(null); }
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
            String state = prefs(a).getString("status", "尚未进行完整重启测试");
            new AlertDialog.Builder(a).setTitle(title())
                    .setMessage("完整开机后，归藏电视作为默认桌面启动时会切到 HDMI " + HDMI_PORT + "。"
                            + "在信号源中按返回，回到归藏电视首页。每次开机只切换一次。\n\n"
                            + "默认桌面：" + (TvControls.isDefaultHome(a) ? "已是归藏电视" : "还不是，需电脑设置（docs/HISENSE.md）")
                            + "\n上次状态：" + state)
                    .setPositiveButton(enabled(a) ? "关闭" : "开启", (d, which) -> {
                        boolean value = !enabled(a);
                        prefs(a).edit().putBoolean("hdmi-enabled", value).apply();
                        Toast.makeText(a, value ? "已开启，下次完整开机时进入 HDMI " + HDMI_PORT : "已关闭开机进入 HDMI " + HDMI_PORT, Toast.LENGTH_LONG).show();
                    })
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
                + " hdmiAttempted=" + prefs(c).getInt("hdmi-attempted", -1)
                + " status=" + prefs(c).getString("status", "none"));
    }
}

package io.github.op7418.guizangtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import java.lang.reflect.Proxy;
import java.util.Locale;

/**
 * China-market Hisense profile, target 75E3F. Package names below come from the Hisense / Vidda
 * JuUI lists in Gathin23/cleanyourtv (examples/vidda-mt9653) and still need checking on this set;
 * see docs/HISENSE.md for the probe commands.
 */
final class HisenseControls {
    private HisenseControls() { }

    /** Stock JuUI launcher on the Vidda set; the first candidate tried by startStockHome. */
    static final String STOCK_HOME = "com.jamdeo.tv.vod";
    /** Casting apps kept on the Vidda set, in the order the home tries them; Xiaomi's comes last. */
    static final String[] CAST = {"com.ms.ucast.ucastapp", "com.hisense.multiscreen.screenhelper",
            "com.ms.weshare", "com.hisense.pcm.share", "com.xiaomi.mitv.smartshare"};

    /** JuUI source ids: HDMI n is 1280 + n ("HDMI 1" = 1281, "HDMI 2/ARC" = 1282 on the 75E3F). */
    private static final int HDMI_SOURCE_BASE = 1280;
    private static final ComponentName LIVE_TV =
            new ComponentName("com.jamdeo.tv.livetv", "com.jamdeo.tv.livetv.LiveTvActivity");
    /** com.jamdeo.tv.SourceManager from the boot-classpath com.jamdeo.flow.jar; it binds asynchronously. */
    private static Object sourceManager;
    private static volatile boolean sourceReady;

    static boolean enabled() {
        return "hisense".equalsIgnoreCase(Build.MANUFACTURER) || "hisense".equalsIgnoreCase(Build.BRAND)
                || Build.MODEL.toUpperCase(Locale.ROOT).contains("75E3F");
    }

    /**
     * The 75E3F registers no TvInputManager inputs. Mirrors the stock source panel: the vendor
     * SourceManager switches the input, then LiveTV shows it (switch_by_trans: already switched).
     * False until the source service has bound, so callers retry or report.
     */
    static boolean openHdmi(Context context, int port) {
        if (!enabled() || port < 1 || port > 4 || !sources(context)) return false;
        try {
            Object done = sourceManager.getClass().getMethod("setCurrentInputSource", int.class)
                    .invoke(sourceManager, HDMI_SOURCE_BASE + port);
            if (!Boolean.TRUE.equals(done)) return false;
            context.startActivity(new Intent().setComponent(LIVE_TV).putExtra("switch_by_trans", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            android.util.Log.w("TvHome.Hisense", "HDMI " + port + " switch rejected", unavailable);
            return false;
        }
    }

    /** Starts binding the vendor source service; true once it is usable. */
    static boolean sources(Context context) {
        if (!enabled()) return false;
        if (sourceManager != null) return sourceReady;
        try {
            Class<?> tv = Class.forName("com.jamdeo.tv.TvManager");
            Object manager = tv.getMethod("getSourceManager", Context.class)
                    .invoke(tv.getMethod("getInstance").invoke(null), context.getApplicationContext());
            if (manager == null) return false;
            Class<?> listener = Class.forName("com.jamdeo.tv.IManagerStateListener");
            Object callback = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "onServiceAvailable": sourceReady = true; return null;
                            case "onServiceUnavailable": sourceReady = false; return null;
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals": return proxy == args[0];
                            case "toString": return "GuiZangSourceListener";
                            default: return null;
                        }
                    });
            manager.getClass().getMethod("addManagerStateListener", listener).invoke(manager, callback);
            sourceManager = manager;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            android.util.Log.w("TvHome.Hisense", "Source service unavailable", unavailable);
        }
        return sourceReady;
    }

    static int hdmiSource(int port) { return HDMI_SOURCE_BASE + port; }

    /** The firmware's 开机信号源 (TV settings); -1 until the source service is ready. */
    static int startupSource(Context context) { return sourceInt(context, "getStartupInputSource"); }

    /** Vendor display name such as "HDMI 2/ARC", or null when unknown. */
    static String sourceName(Context context, int source) {
        if (source < 0 || !sources(context)) return null;
        try {
            Object name = sourceManager.getClass().getMethod("getInputSourceName", int.class).invoke(sourceManager, source);
            return name == null ? null : name.toString();
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    private static int sourceInt(Context context, String getter) {
        if (!sources(context)) return -1;
        try {
            Object value = sourceManager.getClass().getMethod(getter).invoke(sourceManager);
            return value instanceof Integer ? (Integer) value : -1;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            android.util.Log.w("TvHome.Hisense", getter + " failed", unavailable);
            return -1;
        }
    }

    static void dump(Context context, java.io.PrintWriter out) {
        out.println("  hisense sourceManager=" + (sourceManager != null) + " ready=" + sourceReady
                + " current=" + sourceInt(context, "getCurrentInputSource") + " startup=" + startupSource(context)
                + " (" + sourceName(context, startupSource(context)) + ")");
    }

    /** Vendor source panel; it performs the switch itself when an input is picked. */
    static boolean showInputs(Context context) {
        ComponentName panel = new ComponentName("com.hisense.tv.inputs", "com.hisense.tv.InputsUIService");
        try {
            ServiceInfo info = context.getPackageManager().getServiceInfo(panel, 0);
            if (!info.enabled || !info.exported) return false;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                return false;
            return context.startService(new Intent("com.hisense.tv.start_inputs").setComponent(panel)) != null;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            android.util.Log.w("TvHome.Hisense", "Source panel unavailable", unavailable);
            return false;
        }
    }
}

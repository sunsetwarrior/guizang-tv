package io.github.op7418.guizangtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Routes TV-specific entries to the matching vendor profile; other sets use plain Android TV APIs. */
final class TvControls {
    private TvControls() { }

    static String vendor() {
        return XiaomiControls.enabled() ? "xiaomi" : HisenseControls.enabled() ? "hisense" : "generic";
    }

    static boolean openHdmi(Context context, int port) {
        if (XiaomiControls.enabled()) return XiaomiControls.openHdmi(context, port);
        if (HisenseControls.openHdmi(context, port)) return true;
        TvInputInfo input = hdmiInput(context, port);
        if (input == null) return false;
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(input.getId()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException unavailable) {
            android.util.Log.w("TvHome.Hdmi", "Passthrough input " + input.getId() + " rejected", unavailable);
            return false;
        }
    }

    /** Hardware HDMI inputs only (CEC devices carry a parent id), matched by their "HDMI n" label. */
    private static TvInputInfo hdmiInput(Context context, int port) {
        for (TvInputInfo info : hdmiInputs(context)) {
            String label = String.valueOf(info.loadLabel(context)).replace(" ", "").toUpperCase(Locale.ROOT);
            if (label.equals("HDMI" + port)) return info;
        }
        return null;
    }

    private static List<TvInputInfo> hdmiInputs(Context context) {
        List<TvInputInfo> found = new ArrayList<>();
        TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (manager == null) return found;
        for (TvInputInfo info : manager.getTvInputList())
            if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() == null) found.add(info);
        return found;
    }

    /** Vendor services that bind asynchronously; called early so the first HDMI pick finds them ready. */
    static void prepare(Context context) {
        HisenseControls.sources(context);
    }

    static boolean showInputs(Context context) {
        if (XiaomiControls.enabled()) return XiaomiControls.showInputs(context);
        return HisenseControls.enabled() && HisenseControls.showInputs(context);
    }

    static Intent settings(Context context) {
        Intent vendor = XiaomiControls.settings(context, Settings.ACTION_SETTINGS);
        return vendor != null ? vendor : new Intent(Settings.ACTION_SETTINGS);
    }

    static String stockHomeName() {
        return XiaomiControls.enabled() ? "小米原桌面" : HisenseControls.enabled() ? "海信原桌面" : "原桌面";
    }

    static boolean startStockHome(Context context) {
        if (XiaomiControls.enabled())
            return XiaomiControls.startStockHome(context, new ComponentName("com.mitv.tvhome", "com.mitv.tvhome.MainActivity"));
        ComponentName home = stockHome(context);
        if (home == null) return false;
        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(home)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException unavailable) {
            android.util.Log.w("TvHome.Stock", "Stock home " + home + " rejected", unavailable);
            return false;
        }
    }

    static String stockHomePackage(Context context) {
        if (XiaomiControls.enabled()) return "com.mitv.tvhome";
        ComponentName home = stockHome(context);
        return home == null ? null : home.getPackageName();
    }

    /** Another enabled HOME activity, the vendor launcher first; null once it has been disabled. */
    private static ComponentName stockHome(Context context) {
        ComponentName fallback = null;
        for (ResolveInfo r : context.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || r.activityInfo.name.endsWith("FallbackHome")) continue;
            ComponentName home = new ComponentName(pkg, r.activityInfo.name);
            if (pkg.equals(HisenseControls.STOCK_HOME)) return home;
            if (fallback == null) fallback = home;
        }
        return fallback;
    }

    /** True when the HOME button resolves to this app rather than the vendor launcher. */
    static boolean isDefaultHome(Context context) {
        ResolveInfo home = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        return home != null && context.getPackageName().equals(home.activityInfo.packageName);
    }

    static void dump(Context context, java.io.PrintWriter out) {
        out.println("TvControls: vendor=" + vendor() + " defaultHome=" + isDefaultHome(context)
                + " stockHome=" + stockHome(context));
        for (TvInputInfo info : hdmiInputs(context))
            out.println("  hdmi id=" + info.getId() + " label=" + info.loadLabel(context));
        if (HisenseControls.enabled()) HisenseControls.dump(context, out);
    }
}

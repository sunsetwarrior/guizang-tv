package io.github.op7418.guizangtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
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

    static boolean enabled() {
        return "hisense".equalsIgnoreCase(Build.MANUFACTURER) || "hisense".equalsIgnoreCase(Build.BRAND)
                || Build.MODEL.toUpperCase(Locale.ROOT).contains("75E3F");
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

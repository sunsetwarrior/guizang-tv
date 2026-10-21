package io.github.op7418.guizangtv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import org.json.JSONArray;
import java.util.*;

/** Stable app/source identities; removing an entry never uninstalls an app. */
final class HomeItems {
    static final String DOUYU = "com.douyu.xl.douyutv";
    final Context context;
    final LinkedHashMap<String,String> names = new LinkedHashMap<>();
    final List<String> order = new ArrayList<>();
    HomeItems(Context c) {
        context = c;
        List<String> defaults = defaults();
        discover();
        try {
            JSONArray data = new JSONArray(c.getSharedPreferences("home-items",0).getString("order","[]"));
            for (int i=0;i<data.length() && order.size()<9;i++) {
                String id = data.getString(i);
                if (validId(id) && !order.contains(id)) order.add(id);
            }
        } catch (Exception ignored) { order.clear(); }
        if (order.isEmpty()) order.addAll(defaults);
    }
    /** IPTV, the console port, then the apps this TV actually has; a missing app leaves no gap. */
    private List<String> defaults() {
        List<String> ids = new ArrayList<>();
        add(ids, "hdmi:2", "IPTV");
        add(ids, "hdmi:1", "PS5 / Switch");
        if (launchable("com.xiaodianshi.tv.yst")) add(ids, "com.xiaodianshi.tv.yst", "哔哩哔哩");
        for (String cast : HisenseControls.CAST) if (launchable(cast)) { add(ids, cast, "投屏"); break; }
        if (launchable(DOUYU)) add(ids, DOUYU, "斗鱼直播");
        String stock = TvControls.stockHomePackage(context);
        if (stock != null) add(ids, stock, "原桌面");
        return ids;
    }
    private void add(List<String> ids, String id, String label) { ids.add(id); names.put(id, label); }
    private boolean launchable(String pkg) {
        return context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null
                || context.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }
    void discover() {
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String id = r.activityInfo.packageName;
                if (!id.equals(context.getPackageName()) && r.activityInfo.enabled && r.activityInfo.exported)
                    names.putIfAbsent(id, XiaomiControls.label(id, r.loadLabel(context.getPackageManager()).toString()));
            }
        }
    }
    String name(String id) { return names.getOrDefault(id, context.getSharedPreferences("home-items",0).getString("name:"+id,id)); }
    static boolean validId(String id) { return id.equals("hdmi:1") || id.equals("hdmi:2") || id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+"); }
    boolean save(List<String> next) {
        if (next.isEmpty() || next.size()>9 || new HashSet<>(next).size()!=next.size()) return false;
        for(String id : next) if(!validId(id)) return false;
        android.content.SharedPreferences.Editor e = context.getSharedPreferences("home-items",0).edit()
                .putString("order",new JSONArray(next).toString());
        for(String id:next) e.putString("name:"+id,name(id));
        if(!e.commit()) return false;
        order.clear(); order.addAll(next); return true;
    }
}

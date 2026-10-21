package io.github.op7418.guizangtv;

import android.content.Context;
import android.graphics.drawable.Drawable;
import java.util.Arrays;

/** Monochrome SVG marks, converted losslessly to Android vectors. See assets/icons/SOURCES.json. */
final class HomeIcons {
    static Drawable get(Context context,String id,int color){
        int resource;
        if(Arrays.asList(HisenseControls.CAST).contains(id))id="cast";
        else if(id.equals(TvControls.stockHomePackage(context)))id="stock-home";
        switch(id){
            case "hdmi:2":resource=R.drawable.mark_monitor_play;break;
            case "hdmi:1":resource=R.drawable.mark_playstation;break;
            case "com.xiaodianshi.tv.yst":resource=R.drawable.mark_bilibili;break;
            case "cast":resource=R.drawable.mark_broadcast;break;
            case "com.mitv.gallery":resource=R.drawable.mark_image;break;
            case "stock-home":resource=R.drawable.mark_house;break;
            case "control:settings":resource=R.drawable.mark_gear_six;break;
            case "control:inputs":resource=R.drawable.mark_monitor_play;break;
            case "control:apps":resource=R.drawable.mark_squares_four;break;
            default:resource=id.startsWith("hdmi:")?R.drawable.mark_monitor_play:R.drawable.mark_app_window;
        }
        Drawable mark=context.getDrawable(resource).mutate();mark.setTint(color);return mark;
    }
}

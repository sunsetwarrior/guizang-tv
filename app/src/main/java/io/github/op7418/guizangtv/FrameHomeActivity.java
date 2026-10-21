package io.github.op7418.guizangtv;

import android.app.*;
import android.content.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

/** Native TV home and one grouped Settings entry. */
public final class FrameHomeActivity extends Activity implements HomeSurface.Actions {
    final Handler handler = new Handler(Looper.getMainLooper());
    HomeAppearance appearance;
    HomeItems items;
    FrameWallpapers wallpapers;
    HomeSettings settings;
    HomeWeather weather;
    HomeSurface surface;
    private FrameLayout root;
    private ImageView photo, transition;
    private AlertDialog library;
    private boolean resumed;
    private int selected;
    private final Runnable tick = new Runnable(){public void run(){
        String time=new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date());
        if(surface!=null)surface.clock.setText(time);
        if(settings!=null)settings.time(time);
        handler.postDelayed(this,60000-System.currentTimeMillis()%60000);
    }};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        appearance=new HomeAppearance(this);items=new HomeItems(this);
        selected=getSharedPreferences("frame-home",0).getInt("selected",0);
        root=new FrameLayout(this);photo=new ImageView(this);transition=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);transition.setScaleType(ImageView.ScaleType.CENTER_CROP);transition.setAlpha(0);
        root.addView(photo,new FrameLayout.LayoutParams(-1,-1));root.addView(transition,new FrameLayout.LayoutParams(-1,-1));
        wallpapers=new FrameWallpapers(this,photo,transition);
        weather=new HomeWeather(this,this::weatherChanged);
        settings=new HomeSettings(this);
        wallpapers.changed=()->{if(surface!=null)surface.wallpaper(wallpaperDrawable());};
        setContentView(root);refreshHome();surface.focusApp();BootStart.fromHome(this);
    }
    void refreshHome(){
        int restore=Math.max(0,Math.min(items.order.size()-1,selected));
        selected=restore;
        if(surface!=null)root.removeView(surface);
        root.setBackgroundColor(appearance.light()?0xFFF0EFE9:0xFF11151B);
        photo.setVisibility(appearance.get("background")==0?View.VISIBLE:View.INVISIBLE);
        transition.setVisibility(photo.getVisibility());
        surface=new HomeSurface(this,appearance,items,wallpaperDrawable(),restore,false,this);
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        surface.apps.get(restore).requestFocus();
        selected=restore;
        handler.removeCallbacks(tick);tick.run();weatherChanged();updateRotation();
    }
    HomeSurface preview(){HomeSurface view=new HomeSurface(this,appearance,items,wallpaperDrawable(),selected,true,this);view.weather(weather.current());return view;}
    void weatherChanged(){if(surface!=null)surface.weather(weather.current());if(settings!=null)settings.weatherChanged();}
    Drawable wallpaperDrawable(){return photo.getDrawable();}
    void updateRotation(){if(weather!=null)weather.active(resumed&&!settings.showing()&&(library==null||!library.isShowing()));if(resumed&&appearance.get("background")==0&&!settings.showing()&&(library==null||!library.isShowing()))wallpapers.resume();else wallpapers.pause();}
    @Override public void selected(int index){selected=index;getSharedPreferences("frame-home",0).edit().putInt("selected",index).apply();}
    @Override public void open(String id){
        if(id.startsWith("hdmi:")){if(!TvControls.openHdmi(this,Integer.parseInt(id.substring(5))))message("暂时无法切换信号源");return;}
        if("com.mitv.tvhome".equals(id)||HisenseControls.STOCK_HOME.equals(id)){stockHome();return;}
        Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(id);
        if(intent==null)intent=getPackageManager().getLaunchIntentForPackage(id);
        launch(intent,"应用暂时不可用");
    }
    @Override public void control(int index){
        if(index==0)showLibrary();
        else if(index==1){if(!TvControls.showInputs(this))message("暂时无法打开信号源");}
        else settings.show();
    }
    void stockHome(){if(!TvControls.startStockHome(this))message("暂时无法打开"+TvControls.stockHomeName());}
    void systemSettings(){launch(TvControls.settings(this),"暂时无法打开设置");}
    private void launch(Intent i,String error){if(i==null){message(error);return;}try{startActivity(i);}catch(RuntimeException unavailable){message(error);}}
    private void showLibrary(){
        items.discover();List<String> ids=new ArrayList<>(items.names.keySet());ids.removeIf(id->id.startsWith("hdmi:") || (getPackageManager().getLeanbackLaunchIntentForPackage(id)==null && getPackageManager().getLaunchIntentForPackage(id)==null));
        Collator collator=Collator.getInstance(Locale.CHINA);ids.sort((a,b)->collator.compare(items.name(a),items.name(b)));
        String[] labels=new String[ids.size()];for(int i=0;i<labels.length;i++)labels[i]=items.name(ids.get(i));
        library=new AlertDialog.Builder(this).setTitle("全部应用").setItems(labels,(d,i)->open(ids.get(i))).setNegativeButton("返回",null).create();
        library.setOnDismissListener(d->{surface.controls.get(0).requestFocus();updateRotation();});library.show();updateRotation();
    }
    @Override protected void onResume(){super.onResume();resumed=true;BootStart.confirm(this);handler.removeCallbacks(tick);tick.run();updateRotation();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);}
    @Override protected void onPause(){resumed=false;BootStart.cancel();weather.active(false);handler.removeCallbacks(tick);wallpapers.pause();if(library!=null)library.dismiss();super.onPause();}
    @Override protected void onDestroy(){settings.close();weather.destroy();wallpapers.destroy();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onBackPressed(){if(surface.controls.contains(getCurrentFocus()))surface.focusApp();else if(XiaomiControls.enabled())stockHome();else if(!TvControls.isDefaultHome(this))super.onBackPressed();}
    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        super.dump(prefix,fd,out,args);wallpapers.dump(out);BootStart.dump(this,out);TvControls.dump(this,out);weather.dump(out);
        out.println("HomeAppearance: "+appearance.prefs.getAll()+" items="+items.order+" settings="+settings.page());
    }
    void message(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}

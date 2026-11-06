package com.fieldview.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, instrumentedPackages="com.fieldview.tv", shadows={PhoneUiTest.Memory.class,PhoneUiTest.NoVideo.class})
public class PhoneUiTest {
    @Implements(value=ConfigStore.class,isInAndroidSdk=false) public static class Memory {
        static AppConfig config;
        @Implementation protected AppConfig load(){return config;}
        @Implementation protected void save(AppConfig next){config=next;}
    }
    @Implements(value=CameraTile.class,isInAndroidSdk=false) public static class NoVideo extends org.robolectric.shadows.ShadowViewGroup {
        @RealObject CameraTile tile;
        @Implementation protected void startPlayer() throws Exception {
            field(CameraTile.class,"player").set(tile,Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),new Class[]{ExoPlayer.class},(p,m,a)->null));
        }
    }
    static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static void invoke(MainActivity activity,String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private ActivityController<MainActivity> open(){
        Memory.config=new AppConfig();Memory.config.visibleCount=2;Memory.config.useMainStream=false;
        for(int i=0;i<3;i++){AppConfig.Camera c=new AppConfig.Camera();c.id="camera-"+i;c.name="Camera "+i;c.host="192.0.2.10";Memory.config.cameras.add(c);}
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
    }
    static void measure(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    @Test public void portraitHasTouchControlsAndVerticallyStackedTwoCameraViews(){
        ActivityController<MainActivity> c=open();try{MainActivity a=c.get();measure(a.findViewById(R.id.viewer_root),412,915);
            assertFalse(BuildConfig.TV_INTERFACE);assertNotNull(a.findViewById(R.id.phone_more));CameraWall wall=a.findViewById(R.id.camera_wall);
            assertEquals(2,wall.getChildCount());assertTrue(wall.getChildAt(1).getTop()>=wall.getChildAt(0).getBottom());
            assertTrue(wall.getChildAt(0).getWidth()>wall.getWidth()*0.9);
        }finally{c.pause().stop().destroy();}
    }
    @Test public void pinnedCameraAndTouchBrowsingSurviveActivityRecreation()throws Exception{
        ActivityController<MainActivity> c=open();try{MainActivity a=c.get();((CameraWall)a.findViewById(R.id.camera_wall)).getChildAt(0).performClick();
            a.findViewById(R.id.phone_next).performClick();ViewSelector selected=(ViewSelector)field(MainActivity.class,"selector").get(a);
            assertEquals("camera-1",selected.snapshot(System.currentTimeMillis()).visible.get(0));c.recreate();a=c.get();
            selected=(ViewSelector)field(MainActivity.class,"selector").get(a);assertEquals(ViewSelector.PIN,selected.snapshot(System.currentTimeMillis()).mode);
            assertEquals("camera-1",selected.snapshot(System.currentTimeMillis()).visible.get(0));a.onBackPressed();assertFalse(a.isFinishing());
        }finally{c.pause().stop().destroy();}
    }
    @Test public void editButtonsFitNarrowScreenWithRemoveVisible(){
        ActivityController<MainActivity> c=open();TvFormDialog dialog=null;try{
            LinearLayout fields=new LinearLayout(c.get());fields.addView(new EditText(c.get()));dialog=new TvFormDialog(c.get(),"Edit camera",fields);dialog.setRemoveAction(()->{});dialog.show();
            View actions=dialog.findViewById(R.id.form_actions);measure(actions,360,60);
            assertTrue(dialog.findViewById(R.id.form_save).getRight()<=360);assertTrue(dialog.findViewById(R.id.form_save).getWidth()>=100);
            assertEquals(View.VISIBLE,dialog.findViewById(R.id.form_remove).getVisibility());
        }finally{if(dialog!=null)dialog.dismiss();c.pause().stop().destroy();}
    }
}

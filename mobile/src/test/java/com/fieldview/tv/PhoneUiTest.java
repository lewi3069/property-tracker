package com.fieldview.tv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={35,36}, instrumentedPackages="com.fieldview.tv", shadows={PhoneUiTest.Memory.class,PhoneUiTest.NoVideo.class,PhoneUiTest.FakeNative.class})
public class PhoneUiTest {
    @org.junit.Before public void resetNativeFake(){FakeNative.sessions.clear();FakeNative.holdRetirement=false;}
    @Implements(value=ConfigStore.class,isInAndroidSdk=false) public static class Memory {
        static AppConfig config;
        @Implementation protected AppConfig load(){return config;}
        @Implementation protected void save(AppConfig next){config=next;}
    }
    @Implements(value=CameraTile.class,isInAndroidSdk=false) public static class NoVideo extends org.robolectric.shadows.ShadowViewGroup {
        @RealObject CameraTile tile;
        static int starts;
        @Implementation protected void startPlayer() throws Exception {
            starts++;
            // The pinned native main runs the real start path against FakeNative; host tests never load LibVLC.
            if(tile.usesPhoneNativeMain()){org.robolectric.util.reflector.Reflector.reflector(TileStart.class,tile).startPlayer();return;}
            field(CameraTile.class,"player").set(tile,Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),new Class[]{ExoPlayer.class},(p,m,a)->null));
            // As a first frame would, free the shared startup slot for the next queued camera.
            Method ready=CameraTile.class.getDeclaredMethod("finishStartup");ready.setAccessible(true);ready.invoke(tile);
        }
    }
    static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static void invoke(MainActivity activity,String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private ActivityController<MainActivity> open(){
        Memory.config=new AppConfig();Memory.config.visibleCount=2;Memory.config.useMainStream=false;
        for(int i=0;i<3;i++){AppConfig.Camera c=new AppConfig.Camera();c.id="camera-"+i;c.name="Camera "+i;c.host="192.0.2.10";Memory.config.cameras.add(c);}
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
    }
    /** A fresh phone launch opens Browse/Grid, so tests of Auto select it explicitly. */
    private ActivityController<MainActivity> openAuto()throws Exception{ActivityController<MainActivity> c=open();autoButton(c.get()).performClick();return c;}
    static Button autoButton(MainActivity a)throws Exception{return (Button)field(MainActivity.class,"autoButton").get(a);}
    static void measure(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    @Test @Config(qualifiers="w360dp-h480dp-port-mdpi")
    public void portraitHasTouchControlsAndVerticallyStackedTwoCameraViews()throws Exception{
        ActivityController<MainActivity> c=openAuto();try{MainActivity a=c.get();settle(a);
            assertFalse(BuildConfig.TV_INTERFACE);assertNotNull(a.findViewById(R.id.phone_more));CameraWall wall=a.findViewById(R.id.camera_wall);
            assertEquals("The selector ranks three; the measured wall decides how many show",3,selector(a).portraitAuto());
            assertTrue("Three 16:9 rows overflow this compact wall",wall.getHeight()>0&&wall.stackedNaturalHeight(wall.getWidth(),new double[3],3)>wall.getHeight());
            assertEquals(Arrays.asList("camera-0","camera-1"),assertStackedRows(wall,2));
        }finally{c.pause().stop().destroy();}
    }
    @Test public void pinnedCameraAndTouchBrowsingSurviveActivityRecreation()throws Exception{
        ActivityController<MainActivity> c=openAuto();try{MainActivity a=c.get();((CameraWall)a.findViewById(R.id.camera_wall)).getChildAt(0).performClick();
            assertTrue("An upward swipe takes the next camera",swipe(a,0,-120));ViewSelector selected=(ViewSelector)field(MainActivity.class,"selector").get(a);
            assertEquals("camera-1",selected.snapshot(System.currentTimeMillis()).visible.get(0));c.recreate();a=c.get();
            selected=(ViewSelector)field(MainActivity.class,"selector").get(a);assertEquals(ViewSelector.PIN,selected.snapshot(System.currentTimeMillis()).mode);
            assertEquals("camera-1",selected.snapshot(System.currentTimeMillis()).visible.get(0));a.onBackPressed();assertFalse(a.isFinishing());
        }finally{c.pause().stop().destroy();}
    }
    @Test public void deniedNotificationPermissionContinuesToOverlayChoiceWithoutRepeatedPrompt() throws Exception {
        ActivityController<MainActivity> controller=open();
        try {
            MainActivity activity=controller.get();
            Method start=MainActivity.class.getDeclaredMethod("startFloatingCamera",String.class);start.setAccessible(true);start.invoke(activity,"camera-0");
            assertEquals("camera-0",field(MainActivity.class,"pendingNotificationCamera").get(activity));
            activity.onRequestPermissionsResult(42,new String[]{android.Manifest.permission.POST_NOTIFICATIONS},new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNull(field(MainActivity.class,"pendingNotificationCamera").get(activity));
            assertTrue(activity.getSharedPreferences("phone-ui",0).getBoolean("notificationPrompted",false));
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());dialog.dismiss();start.invoke(activity,"camera-0");
            assertNull(field(MainActivity.class,"pendingNotificationCamera").get(activity));
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().dismiss();
        } finally {controller.pause().stop().destroy();}
    }
    @Test public void phoneAutoUsesSubstreamsAndManualFullscreenCanUseTheMainStream() throws Exception {
        ActivityController<MainActivity> controller = openAuto();
        try {
            MainActivity activity = controller.get();
            Memory.config.textureGrid = true; Memory.config.denseGrid = true; Memory.config.useMainStream = true;
            ViewSelector selected = (ViewSelector) field(MainActivity.class, "selector").get(activity);
            long now = System.currentTimeMillis(); selected.activity("camera-1", now, now + 30_000);
            invoke(activity, "render");
            CameraWall wall = activity.findViewById(R.id.camera_wall);
            CameraTile featured = null;
            for (int i = 0; i < wall.getChildCount(); i++) {
                CameraTile tile = (CameraTile) wall.getChildAt(i); assertFalse(tile.usesNativeGrid());
                if ("Enlarge Camera 1".contentEquals(tile.getContentDescription())) featured = tile;
            }
            assertNotNull(featured);
            assertEquals(Memory.config.cameras.get(1).streamUri(false), field(CameraTile.class, "activeUri").get(featured));
            featured.performClick(); settle(activity);
            CameraTile enlarged = tiles(activity).get("camera-1");
            assertEquals(ViewSelector.PIN, selected.snapshot(now()).mode);
            assertFalse(enlarged.usesNativeGrid());
            assertEquals(Memory.config.cameras.get(1).streamUri(true), field(CameraTile.class, "activeUri").get(enlarged));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void privacyPolicyIsBundledAndReadableWithoutAConfiguredCamera() throws Exception {
        ActivityController<MainActivity> controller=open();
        try {
            MainActivity activity=controller.get(); invoke(activity,"showPrivacy");
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            String text=allText(dialog.findViewById(android.R.id.custom));
            assertTrue(text.contains("Property Tracker Privacy Policy")); assertTrue(text.contains("Connections you choose"));
            assertTrue(text.contains("not necessarily encrypted")); dialog.dismiss();
        } finally {controller.pause().stop().destroy();}
    }
    @Test public void openSourceNoticesAreIncludedAndReadableOffline() throws Exception {
        ActivityController<MainActivity> controller=open();
        try {
            MainActivity activity=controller.get(); invoke(activity,"showLicenses");
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            String text=allText(dialog.findViewById(android.R.id.custom));
            assertTrue(text.contains("Apache License")); assertTrue(text.contains("Eclipse Distribution License"));
            assertTrue(text.contains("Classpath")); dialog.dismiss();
        } finally {controller.pause().stop().destroy();}
    }
    private String allText(View view) {
        if(view instanceof android.widget.TextView)return ((android.widget.TextView)view).getText().toString();
        StringBuilder text=new StringBuilder();
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)text.append(allText(group.getChildAt(i)));}
        return text.toString();
    }
    @Test public void editButtonsFitNarrowScreenWithRemoveVisible(){
        ActivityController<MainActivity> c=open();TvFormDialog dialog=null;try{
            LinearLayout fields=new LinearLayout(c.get());fields.addView(new EditText(c.get()));dialog=new TvFormDialog(c.get(),"Edit camera",fields);dialog.setRemoveAction(()->{});dialog.show();
            View actions=dialog.findViewById(R.id.form_actions);measure(actions,360,60);
            assertTrue(dialog.findViewById(R.id.form_save).getRight()<=360);assertTrue(dialog.findViewById(R.id.form_save).getWidth()>=100);
            assertEquals(View.VISIBLE,dialog.findViewById(R.id.form_remove).getVisibility());
        }finally{if(dialog!=null)dialog.dismiss();c.pause().stop().destroy();}
    }

    // Phone portrait Browse: every enabled camera in saved order; only rows in view own a stream.
    @SuppressWarnings("unchecked") static Map<String,CameraTile> tiles(MainActivity a)throws Exception{return (Map<String,CameraTile>)field(MainActivity.class,"tiles").get(a);}
    static ViewSelector selector(MainActivity a)throws Exception{return (ViewSelector)field(MainActivity.class,"selector").get(a);}
    static Button gridButton(MainActivity a)throws Exception{return (Button)field(MainActivity.class,"gridButton").get(a);}
    static int budget(MainActivity a)throws Exception{return ((PlaybackLimits)field(MainActivity.class,"playbackLimits").get(a)).maxVisible;}
    static Object player(CameraTile tile)throws Exception{return field(CameraTile.class,"player").get(tile);}
    /** Fifteen saved cameras with camera-5 disabled. A model name applies that device's playback budget. */
    private ActivityController<MainActivity> openCatalog(String model)throws Exception{
        Memory.config=new AppConfig();Memory.config.visibleCount=4;Memory.config.useMainStream=false;
        for(int i=0;i<15;i++){AppConfig.Camera c=new AppConfig.Camera();c.id="camera-"+i;c.name="Camera "+i;c.host="192.0.2.10";c.enabled=i!=5;Memory.config.cameras.add(c);}
        NoVideo.starts=0;ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        if(model!=null){field(MainActivity.class,"playbackLimits").set(c.get(),PlaybackLimits.forDevice(model,false));invoke(c.get(),"resetSelector");}
        return c.start().resume().visible();
    }
    /** Runs posted viewport work and the layout passes it causes. Each test idles well under the 20 s stall timeout. */
    static void settle(MainActivity a){
        View decor=a.getWindow().getDecorView();
        for(int i=0;i<4;i++){
            if(decor.isLayoutRequested()||decor.getWidth()==0){android.util.DisplayMetrics m=a.getResources().getDisplayMetrics();
                measure(decor,decor.getWidth()>0?decor.getWidth():m.widthPixels,decor.getHeight()>0?decor.getHeight():m.heightPixels);}
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        }
        if(decor.isLayoutRequested())measure(decor,decor.getWidth(),decor.getHeight());
    }
    static List<View> rows(CameraWall wall){List<View> rows=new ArrayList<>();for(int i=0;i<wall.getChildCount();i++)rows.add(wall.getChildAt(i));rows.sort((x,y)->Integer.compare(x.getTop(),y.getTop()));return rows;}
    static String id(View row){return "camera-"+row.getContentDescription().toString().substring("Enlarge Camera ".length());}
    static boolean inView(CameraWall wall,View row){return row.getBottom()>wall.getScrollY()&&row.getTop()<wall.getScrollY()+wall.getHeight();}
    /** Streams belong only to rows overlapping the viewport, as many as the budget allows. Returns the overlapping row count. */
    static int assertBoundedOwnership(MainActivity a,CameraWall wall,int budget)throws Exception{
        int overlapping=0,owned=0;
        for(View row:rows(wall)){
            if(inView(wall,row))overlapping++;
            if(row instanceof CameraTile){owned++;assertTrue("Offscreen rows release their stream",inView(wall,row));assertSame(row,tiles(a).get(id(row)));}
        }
        assertEquals(owned,tiles(a).size());assertEquals(Math.min(budget,overlapping),owned);return overlapping;
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void portraitBrowseListsEveryEnabledCameraAndStreamsOnlyRowsInView()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();Button grid=gridButton(a);
            settle(a);assertEquals("A fresh launch opens Browse","● Browse",grid.getText().toString());
            CameraWall wall=a.findViewById(R.id.camera_wall);List<View> rows=rows(wall);List<String> listed=new ArrayList<>(),saved=new ArrayList<>();
            for(View row:rows)listed.add(row.getContentDescription().toString());
            for(int i=0;i<15;i++)if(i!=5)saved.add("Enlarge Camera "+i);
            assertEquals("Enabled cameras in saved order",saved,listed);
            assertNull(a.findViewById(R.id.viewer_footer));
            View first=rows.get(0),last=rows.get(rows.size()-1);int gap=first.getTop();
            assertEquals(wall.getWidth()-2*gap,first.getWidth());assertEquals(first.getWidth(),last.getWidth());
            assertEquals(first.getWidth()*9/16.0,first.getHeight(),1); // 16:9 until the camera reports its picture size
            assertTrue(wall.canScrollVertically(1));assertFalse(wall.canScrollVertically(-1));
            assertBoundedOwnership(a,wall,budget(a));assertTrue(tiles(a).containsKey("camera-0"));
            // Half a row down: rows still on screen keep the same player; only newly visible rows connect.
            Map<String,CameraTile> before=new HashMap<>(tiles(a));Map<String,Object> players=new HashMap<>();
            for(Map.Entry<String,CameraTile> e:before.entrySet())players.put(e.getKey(),player(e.getValue()));
            int starts=NoVideo.starts,step=first.getHeight()/2;wall.scrollTo(0,step);settle(a);assertEquals(step,wall.getScrollY());
            assertBoundedOwnership(a,wall,budget(a));int retained=0;
            for(View row:rows(wall)){String id=id(row);if(!before.containsKey(id))continue;
                if(inView(wall,row)){retained++;assertSame(before.get(id),tiles(a).get(id));assertSame(players.get(id),player(before.get(id)));}
                else{assertFalse(tiles(a).containsKey(id));assertNull(player(before.get(id)));}
            }
            assertEquals(tiles(a).size()-retained,NoVideo.starts-starts);
            // End of the list: the last camera streams and the first row's stream has been released.
            wall.scrollTo(0,Integer.MAX_VALUE);settle(a);rows=rows(wall);last=rows.get(rows.size()-1);
            assertFalse(wall.canScrollVertically(1));assertEquals(last.getBottom()+gap,wall.getScrollY()+wall.getHeight());
            assertBoundedOwnership(a,wall,budget(a));assertTrue(tiles(a).containsKey("camera-14"));
            assertFalse(tiles(a).containsKey("camera-0"));assertNull(player(before.get("camera-0")));
            assertTrue(field(CameraTile.class,"released").getBoolean(before.get("camera-0")));
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void browseNeverOwnsMoreStreamsThanThePlaybackBudget()throws Exception{
        ActivityController<MainActivity> c=openCatalog("AFTT");try{MainActivity a=c.get();assertEquals(2,budget(a));
            gridButton(a).performClick();settle(a);CameraWall wall=a.findViewById(R.id.camera_wall);
            assertEquals(14,wall.getChildCount());assertBoundedOwnership(a,wall,2);
            int row=rows(wall).get(0).getHeight();
            wall.scrollTo(0,row/2);settle(a);assertTrue("More rows are visible than the budget allows",assertBoundedOwnership(a,wall,2)>2);
            wall.scrollTo(0,row*6+row/3);settle(a);assertBoundedOwnership(a,wall,2);
            wall.scrollTo(0,Integer.MAX_VALUE);settle(a);assertBoundedOwnership(a,wall,2);
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void tappingABrowseRowOpensManualFullscreenAndBackRestoresTheOffset()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();gridButton(a).performClick();settle(a);
            CameraWall wall=a.findViewById(R.id.camera_wall);List<View> rows=rows(wall);
            int offset=rows.get(6).getTop()-rows.get(0).getHeight()/3;wall.scrollTo(0,offset);settle(a);assertEquals(offset,wall.getScrollY());
            CameraTile opened=null;for(View row:rows(wall))if(row instanceof CameraTile&&row.getTop()>=offset){opened=(CameraTile)row;break;}
            assertNotNull(opened);String id=id(opened);Object stream=player(opened);assertNotNull(stream);
            opened.performClick();settle(a);
            assertEquals(ViewSelector.PIN,selector(a).snapshot(System.currentTimeMillis()).mode);
            assertEquals(View.GONE,a.findViewById(R.id.viewer_toolbar).getVisibility());
            assertEquals(1,wall.getChildCount());assertSame(opened,wall.getChildAt(0));assertEquals(1,tiles(a).size());assertEquals(0,wall.getScrollY());
            a.onBackPressed();settle(a);
            assertEquals(ViewSelector.GRID,selector(a).snapshot(System.currentTimeMillis()).mode);
            assertEquals(View.VISIBLE,a.findViewById(R.id.viewer_toolbar).getVisibility());assertEquals("● Browse",gridButton(a).getText().toString());
            assertEquals("Back returns to the same place in the list",offset,wall.getScrollY());assertEquals(14,wall.getChildCount());
            assertSame("The opened camera keeps its stream through fullscreen and back",opened,tiles(a).get(id));assertSame(stream,player(opened));
            assertBoundedOwnership(a,wall,budget(a));
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void stoppedBrowseIgnoresLateScrollAndLayoutCallbacks()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);boolean stopped=false;
        try{MainActivity a=c.get();gridButton(a).performClick();settle(a);CameraWall wall=a.findViewById(R.id.camera_wall);
            int offset=rows(wall).get(4).getTop();
            CameraWall.ViewportListener late=(CameraWall.ViewportListener)field(CameraWall.class,"viewportListener").get(wall);
            Runnable start=(Runnable)field(MainActivity.class,"browseStart").get(a);
            wall.scrollTo(0,offset); // Queues a release and a settle-delayed start.
            c.pause().stop();stopped=true;int starts=NoVideo.starts;
            assertTrue(tiles(a).isEmpty());assertEquals(0,wall.getChildCount());
            late.viewportChanged(true);late.viewportChanged(false);start.run();
            View decor=a.getWindow().getDecorView();measure(decor,decor.getWidth(),decor.getHeight());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertTrue("A stopped viewer must not reconnect",tiles(a).isEmpty());assertEquals(0,wall.getChildCount());assertEquals(starts,NoVideo.starts);
            c.restart().resume();stopped=false;settle(a);
            assertEquals(offset,wall.getScrollY());assertEquals(14,wall.getChildCount());assertBoundedOwnership(a,wall,budget(a));
        }finally{if(stopped)c.destroy();else c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w740dp-h360dp-land-mdpi")
    public void phoneLandscapeGridKeepsTheBudgetedGrid()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();Button grid=gridButton(a);
            settle(a);assertEquals("A fresh launch opens the existing Grid","● Grid",grid.getText().toString());
            CameraWall wall=a.findViewById(R.id.camera_wall);assertEquals(4,wall.getChildCount());assertEquals(4,tiles(a).size());
            assertFalse(wall.canScrollVertically(1));assertNull(wall.browseFrames());
            for(int i=0;i<wall.getChildCount();i++){View tile=wall.getChildAt(i);assertTrue(tile instanceof CameraTile);
                assertTrue(tile.getBottom()<=wall.getHeight());assertTrue(tile.getRight()<=wall.getWidth());}
            assertEquals(0,selector(a).portraitAuto());
            autoButton(a).performClick();settle(a);
            assertNull(a.findViewById(R.id.viewer_footer));
            assertFalse("Landscape Auto keeps its grid geometry",field(CameraWall.class,"stacked").getBoolean(wall));assertEquals(4,wall.getChildCount());
            invoke(a,"showLayout");android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View form=dialog.findViewById(android.R.id.custom);
            assertNull("Portrait Auto is offered only in portrait",afterLabel(form,"Auto cameras in portrait"));
            assertEquals(View.VISIBLE,afterLabel(form,"Cameras visible on this screen").getVisibility());dialog.dismiss();
        }finally{c.pause().stop().destroy();}
    }

    // Phone portrait Auto: 2 (default) or 3 stacked full-width feeds, driven through the validated router.
    static long now(){return org.robolectric.shadows.ShadowSystemClock.currentTimeMillis();}
    static String activity(String camera,boolean active){
        return "{\"type\":\""+(active?"activity":"activity_end")+"\",\"camera_id\":\""+camera+"\",\"event_id\":\""+(active?"on-":"off-")+camera+"\",\"expires_at\":"+(now()+30_000)+"}";
    }
    /** Accepts each command at the app's clock, then renders once, as the message inbox or the next tick would. */
    static void commands(MainActivity a,String... payloads)throws Exception{
        CommandRouter router=(CommandRouter)field(MainActivity.class,"router").get(a);
        for(String payload:payloads)assertTrue(payload,router.accept(payload,false,now()));
        invoke(a,"render");settle(a);
    }
    @SuppressWarnings("unchecked") static Map<String,Double> videoAspects(MainActivity a)throws Exception{return (Map<String,Double>)field(MainActivity.class,"videoAspects").get(a);}
    static void reportAspect(CameraTile tile,int width,int height)throws Exception{Method m=CameraTile.class.getDeclaredMethod("reportAspect",int.class,int.class);m.setAccessible(true);m.invoke(tile,width,height);}
    /**
     * Full-width rows stacked from the top, each at its camera's cached substream shape (16:9 if unknown), shrunk
     * together only when they overflow the wall. Returns the cameras from top to bottom.
     */
    static List<String> assertStackedRows(CameraWall wall,int count)throws Exception{
        List<View> rows=rows(wall);assertEquals(count,rows.size());List<String> ids=new ArrayList<>();int gap=rows.get(0).getLeft();
        Map<String,Double> aspects=videoAspects((MainActivity)wall.getContext());int rowWidth=wall.getWidth()-2*gap;long natural=0;int[] expected=new int[count];
        for(int i=0;i<count;i++){String id=id(rows.get(i));ids.add(id);Double aspect=aspects.get(id);
            expected[i]=(int)Math.round(rowWidth/(aspect==null?16.0/9.0:aspect));natural+=expected[i];}
        int available=wall.getHeight()-2*gap*count;
        for(int i=0;i<count;i++){View row=rows.get(i);assertTrue(row instanceof CameraTile);assertEquals("Full width",rowWidth,row.getWidth());
            if(natural>available)assertEquals("Scaled with the others",expected[i]*(double)available/natural,row.getHeight(),1);
            else assertEquals("Natural height for its picture shape",expected[i],row.getHeight());
            assertEquals(i==0?gap:rows.get(i-1).getBottom()+2*gap,row.getTop());}
        assertTrue(rows.get(count-1).getBottom()<=wall.getHeight());assertEquals(0,wall.getScrollY());
        return ids;
    }
    /** The view after the form label with exactly this text, or null when the label is absent. */
    static View afterLabel(View root,String text){
        if(!(root instanceof android.view.ViewGroup))return null;
        android.view.ViewGroup group=(android.view.ViewGroup)root;
        for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);
            if(child instanceof TextView&&text.contentEquals(((TextView)child).getText()))return i+1<group.getChildCount()?group.getChildAt(i+1):null;
            View found=afterLabel(child,text);if(found!=null)return found;}
        return null;
    }
    /** Resize the wall through the toolbar's height, as an inset or chrome change would, and let layout settle. */
    static void setWallHeight(MainActivity a,CameraWall wall,int height){
        View toolbar=a.findViewById(R.id.viewer_toolbar);toolbar.getLayoutParams().height+=wall.getHeight()-height;toolbar.requestLayout();settle(a);
        assertEquals(height,wall.getHeight());
    }
    @Test @Config(qualifiers="w360dp-h840dp-port-mdpi")
    public void phoneViewerKeepsTheCameraSpaceAndMovesStatusToSettings()throws Exception{
        ActivityController<MainActivity> controller=openCatalog(null);
        try{
            MainActivity activity=controller.get();
            settle(activity);
            CameraWall wall=activity.findViewById(R.id.camera_wall);
            assertNull(activity.findViewById(R.id.viewer_footer));
            int height=wall.stackedNaturalHeight(wall.getWidth(),new double[3],3);
            setWallHeight(activity,wall,height);

            autoButton(activity).performClick();
            settle(activity);
            assertNull(activity.findViewById(R.id.viewer_footer));
            assertEquals(height,wall.getHeight());
            assertEquals(Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));

            gridButton(activity).performClick();
            settle(activity);
            assertNull(activity.findViewById(R.id.viewer_footer));
            assertEquals(height,wall.getHeight());

            activity.findViewById(R.id.phone_more).performClick();
            PopupMenu menu=org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
            assertNotNull(menu);
            MenuItem settings=null;
            for(int i=0;i<menu.getMenu().size();i++){
                MenuItem item=menu.getMenu().getItem(i);
                if("Settings".contentEquals(item.getTitle())) settings=item;
            }
            assertNotNull(settings);
            assertTrue(menu.getMenu().performIdentifierAction(settings.getItemId(),0));
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            try{
                TextView status=dialog.findViewById(R.id.phone_settings_status);
                assertTrue(status.getText().toString().contains("browse  ·  14/15 cameras"));
                assertTrue(status.getText().toString().contains("1 disabled"));
                assertNotNull(dialog.findViewById(R.id.layout_fullscreen));
                field(MainActivity.class,"mqttState").set(activity,"Home Assistant control connected");
                invoke(activity,"render");
                assertTrue(status.getText().toString().contains("Home Assistant control connected"));
            }finally{dialog.dismiss();}

            tiles(activity).get("camera-0").performClick();
            settle(activity);
            assertNull(activity.findViewById(R.id.viewer_footer));
        }finally{controller.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h840dp-port-mdpi")
    public void portraitAutoFitsMixedPictureShapesAndRestoresTheThirdRow()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();autoButton(a).performClick();settle(a);
            CameraWall wall=a.findViewById(R.id.camera_wall);
            assertEquals("Unknown shapes count as 16:9",Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));
            for(Map.Entry<String,CameraTile> e:tiles(a).entrySet()){assertFalse("Auto plays substreams",e.getValue().playingMainStream());
                assertEquals(Memory.config.cameras.get(Integer.parseInt(e.getKey().substring(7))).streamUri(false),field(CameraTile.class,"activeUri").get(e.getValue()));}
            // Doorbell 4:3, regular 16:9 and Duo 8:3 substreams: each row takes its natural full-width height.
            CameraTile door=tiles(a).get("camera-0"),regular=tiles(a).get("camera-1"),duo=tiles(a).get("camera-2");
            reportAspect(door,640,480);reportAspect(regular,640,360);reportAspect(duo,1536,576);settle(a);
            assertEquals(Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));
            assertTrue(door.getHeight()>regular.getHeight()&&regular.getHeight()>duo.getHeight());
            int natural=wall.stackedNaturalHeight(wall.getWidth(),new double[]{4/3.0,16/9.0,1536/576.0},3);assertTrue(natural<=wall.getHeight());
            Object doorPlayer=player(door),regularPlayer=player(regular);int starts=NoVideo.starts;
            Object duoPlayer=player(duo);
            for(boolean fullscreen:new boolean[]{true,false}){
                field(MainActivity.class,"layoutFullscreen").setBoolean(a,fullscreen);invoke(a,"render");settle(a);
                assertEquals(Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));
                assertSame("Changing fullscreen chrome preserves a third feed that still fits",duoPlayer,player(tiles(a).get("camera-2")));
                assertEquals(starts,NoVideo.starts);
            }
            // A compact viewport for the same three keeps the top two at natural heights and releases only the third.
            setWallHeight(a,wall,natural-3);
            assertEquals(Arrays.asList("camera-0","camera-1"),assertStackedRows(wall,2));
            assertNull("One release",player(duo));assertEquals("No start",starts,NoVideo.starts);
            assertSame(door,tiles(a).get("camera-0"));assertSame(doorPlayer,player(door));assertSame(regularPlayer,player(regular));
            // Exactly tall enough again: the third returns with one start; the top two keep their players.
            setWallHeight(a,wall,natural);
            assertEquals(Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));
            assertEquals("One start",starts+1,NoVideo.starts);assertSame(doorPlayer,player(door));assertSame(regularPlayer,player(regular));
            // A changed picture shape alone also drops the third, and restores it when the shape fits again.
            reportAspect(regular,640,480);settle(a);
            assertEquals(Arrays.asList("camera-0","camera-1"),assertStackedRows(wall,2));assertEquals(starts+1,NoVideo.starts);
            reportAspect(regular,640,360);settle(a);
            assertEquals(Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));assertEquals(starts+2,NoVideo.starts);
            assertSame(doorPlayer,player(door));assertSame(regularPlayer,player(regular));
            // Negligible jitter does not re-fit: the rows shrink a pixel together and nothing restarts.
            setWallHeight(a,wall,natural-1);
            assertEquals(Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));assertEquals(starts+2,NoVideo.starts);
            // Even two natural rows overflowing: both scale down together, with no crop or stretch.
            setWallHeight(a,wall,(door.getHeight()+regular.getHeight())/2);
            assertEquals(Arrays.asList("camera-0","camera-1"),assertStackedRows(wall,2));assertSame(doorPlayer,player(door));
            // No manual portrait count; the landscape grid size is kept.
            invoke(a,"showLayout");android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View form=dialog.findViewById(android.R.id.custom);
            assertNull("No manual portrait count",afterLabel(form,"Auto cameras in portrait"));
            assertEquals("Grid size is kept for landscape",View.GONE,afterLabel(form,"Cameras visible on this screen").getVisibility());
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();settle(a);
            assertEquals(4,((AppConfig)field(MainActivity.class,"config").get(a)).visibleCount);
            assertEquals("Saving settings keeps the cached shapes and the fit",Arrays.asList("camera-0","camera-1"),assertStackedRows(wall,2));
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h840dp-port-mdpi")
    public void portraitAutoFitsActiveCamerasWithoutResettingTheirHold()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();autoButton(a).performClick();settle(a);
            CameraWall wall=a.findViewById(R.id.camera_wall);
            int tallRows=wall.stackedNaturalHeight(wall.getWidth(),new double[]{4/3.0,4/3.0,4/3.0},3);
            setWallHeight(a,wall,Math.min(wall.getHeight(),tallRows-4));
            // Cached substream shapes: three doorbells cannot share this wall, so the top two ranked active cameras show.
            for(String id:new String[]{"camera-7","camera-9","camera-11"})videoAspects(a).put(id,4/3.0);
            commands(a,activity("camera-7",true),activity("camera-9",true),activity("camera-11",true));
            assertEquals("The selector still ranks all three",3,selector(a).snapshot(now()).featured.size());
            assertTrue(wall.stackedNaturalHeight(wall.getWidth(),new double[]{4/3.0,4/3.0,4/3.0},3)>wall.getHeight());
            assertEquals(Arrays.asList("camera-11","camera-7"),assertStackedRows(wall,2));
            commands(a,"{\"type\":\"layout\",\"max_visible\":9,\"expires_at\":"+(now()+30_000)+"}");
            assertEquals("A layout override cannot raise portrait Auto",Arrays.asList("camera-11","camera-7"),assertStackedRows(wall,2));
            commands(a,activity("camera-7",false),activity("camera-9",false),activity("camera-11",false));
            assertEquals("Idle returns to three",Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));
            commands(a,activity("camera-3",true));
            ViewSelector selection=selector(a);
            assertEquals(Arrays.asList("camera-3"),selection.snapshot(now()).featured);
            Field current=field(ViewSelector.class,"current");
            Object held=current.get(selection);
            setWallHeight(a,wall,wall.getHeight()/2);
            assertEquals(Arrays.asList("camera-3","camera-0"),assertStackedRows(wall,2));
            assertSame("A fit change keeps the selection that owns the motion hold",held,current.get(selection));
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h840dp-port-mdpi")
    public void normalPortraitStacksTheDefaultCameraFirstAndThreeActiveFeeds()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();
            AppConfig config=(AppConfig)field(MainActivity.class,"config").get(a);
            config.spotlightCamera="camera-9"; config.useMainStream=true;
            invoke(a,"resetSelector");autoButton(a).performClick();settle(a);
            CameraWall wall=a.findViewById(R.id.camera_wall);assertEquals(3,selector(a).portraitAuto());
            assertEquals(Arrays.asList("camera-9","camera-0","camera-1"),assertStackedRows(wall,3));
            commands(a,activity("camera-2",true),activity("camera-3",true),activity("camera-4",true));
            assertEquals("Three active feeds share the stack",Arrays.asList("camera-2","camera-3","camera-4"),assertStackedRows(wall,3));
            assertEquals(3,selector(a).snapshot(now()).featured.size());
            Field main=field(CameraTile.class,"mainStream");
            for (CameraTile tile : tiles(a).values())
                assertFalse("Phone Auto keeps active cameras on substreams",main.getBoolean(tile));
            commands(a,activity("camera-2",false),activity("camera-3",false),activity("camera-4",false));
            assertEquals(Arrays.asList("camera-9","camera-0","camera-1"),assertStackedRows(wall,3));
            assertEquals(4,((AppConfig)field(MainActivity.class,"config").get(a)).visibleCount);
        }finally{c.pause().stop().destroy();}
    }

    // A fresh phone launch opens Browse; phone tiles never draw an outline.
    static void assertNoOutline(MainActivity a)throws Exception{
        for(CameraTile tile:tiles(a).values()){tile.requestFocus();
            assertTrue(tile.getBackground() instanceof android.graphics.drawable.ColorDrawable);
            assertEquals(android.graphics.Color.BLACK,((android.graphics.drawable.ColorDrawable)tile.getBackground()).getColor());}
    }
    @Test @Config(qualifiers="w360dp-h840dp-port-mdpi")
    public void freshPhoneLaunchOpensBrowseAndAnExplicitAutoSurvivesRecreation()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();settle(a);CameraWall wall=a.findViewById(R.id.camera_wall);
            assertEquals(ViewSelector.GRID,selector(a).snapshot(now()).mode);
            assertEquals("● Browse",gridButton(a).getText().toString());assertEquals("Auto",autoButton(a).getText().toString());
            assertEquals(View.VISIBLE,a.findViewById(R.id.viewer_toolbar).getVisibility());
            assertNotNull(wall.browseFrames());assertEquals(14,wall.getChildCount());assertBoundedOwnership(a,wall,budget(a));
            assertFalse(tiles(a).isEmpty());assertNoOutline(a);
            autoButton(a).performClick();settle(a);assertEquals("● Auto",autoButton(a).getText().toString());
            assertEquals("Measured during Browse, so Auto opens with three",Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(wall,3));
            commands(a,activity("camera-0",true));assertTrue(selector(a).snapshot(now()).featured.contains("camera-0"));
            assertNoOutline(a); // Featured feeds included.
            c.recreate();a=c.get();settle(a);
            assertEquals("A restored mode wins over the fresh-launch default",ViewSelector.AUTO,selector(a).snapshot(now()).mode);
            assertEquals(Arrays.asList("camera-0","camera-1","camera-2"),assertStackedRows(a.findViewById(R.id.camera_wall),3));
        }finally{c.pause().stop().destroy();}
    }
    @Test public void freshPhoneLaunchWithoutCamerasKeepsTheSetupPrompt()throws Exception{
        Memory.config=new AppConfig();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        try{MainActivity a=c.get();settle(a);
            assertEquals(ViewSelector.GRID,selector(a).snapshot(now()).mode);
            TextView empty=(TextView)field(MainActivity.class,"empty").get(a);
            assertEquals(View.VISIBLE,empty.getVisibility());assertTrue(empty.getText().toString().startsWith("Add a camera to start."));
            assertEquals(View.VISIBLE,a.findViewById(R.id.viewer_toolbar).getVisibility());
            assertEquals(0,((CameraWall)a.findViewById(R.id.camera_wall)).getChildCount());
        }finally{c.pause().stop().destroy();}
    }

    // Phone native main: FakeNative replaces the compile-time LibVLC backend, so host tests never load native video.
    @org.robolectric.util.reflector.ForType(CameraTile.class) public interface TileStart{@org.robolectric.util.reflector.Direct void startPlayer();}
    /**
     * Sessions retire at once; with holdRetirement only when a test calls retireAll(). Never posted, so the shared
     * startup coordinator cannot stay blocked when Robolectric clears the looper between tests.
     */
    @Implements(value=GridVideoFactory.class,isInAndroidSdk=false) public static class FakeNative{
        static final List<FakeSession> sessions=new ArrayList<>();
        static boolean holdRetirement;
        @Implementation protected static GridVideoSession create(android.content.Context context){FakeSession s=new FakeSession(context);sessions.add(s);return s;}
        static void retireAll(){for(FakeSession s:new ArrayList<>(sessions))s.retire();}
    }
    static final class FakeSession implements GridVideoSession{
        final View view;String uri;Listener listener;int closes;final List<Runnable> pending=new ArrayList<>();
        FakeSession(android.content.Context context){view=new View(context);}
        @Override public View view(){return view;}
        @Override public void start(String uri,Listener listener){this.uri=uri;this.listener=listener;}
        float pixelRatio = 1f;
        @Override public Stats stats() {
            return new Stats(25, 24, 0, 1_000_000, 4512, 2512, 0, -1, pixelRatio);
        }
        @Override public void close(Runnable complete){
            closes++;listener=null;
            if(FakeNative.holdRetirement)pending.add(complete);else complete.run();
        }
        void retire(){List<Runnable> done=new ArrayList<>(pending);pending.clear();for(Runnable r:done)r.run();}
    }
    static String playerLogs(){
        StringBuilder logs=new StringBuilder();
        for(org.robolectric.shadows.ShadowLog.LogItem item:org.robolectric.shadows.ShadowLog.getLogsForTag("FieldviewPlayer")){
            assertNull("No exceptions in diagnostics",item.throwable);logs.append(item.msg).append('\n');}
        assertFalse("No stream addresses",logs.indexOf("rtsp:")>=0||logs.indexOf("192.0.2.10")>=0);
        return logs.toString();
    }
    static String liveStatus(CameraTile tile)throws Exception{Method m=CameraTile.class.getDeclaredMethod("liveStatus");m.setAccessible(true);return (String)m.invoke(tile);}
    @Test public void phoneNativeMainIsLimitedToOnePinnedFullscreenRtspMain(){
        String rtsp="rtsp://192.0.2.10:554/main";
        assertTrue(CameraTile.phoneNativeMain(false,false,true,true,false,rtsp));
        assertFalse("Never TV",CameraTile.phoneNativeMain(true,false,true,true,false,rtsp));
        assertFalse("Never Float",CameraTile.phoneNativeMain(false,true,true,true,false,rtsp));
        assertFalse("Never substreams",CameraTile.phoneNativeMain(false,false,false,true,false,rtsp));
        assertFalse("Never Browse or Auto tiles",CameraTile.phoneNativeMain(false,false,true,false,false,rtsp));
        assertFalse("Never multi-camera fullscreen",CameraTile.phoneNativeMain(false,false,true,true,true,rtsp));
        assertFalse("RTSP only",CameraTile.phoneNativeMain(false,false,true,true,false,"http://192.0.2.10/main"));
        assertFalse(CameraTile.phoneNativeMain(false,false,true,true,false,null));
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void pinnedMainPlaysNativelyAndRetiresBeforeMedia3Returns()throws Exception{
        FakeNative.holdRetirement=true;ActivityController<MainActivity> c=openCatalog(null);
        try{MainActivity a=c.get();((AppConfig)field(MainActivity.class,"config").get(a)).useMainStream=true;settle(a);
            assertFalse(tiles(a).isEmpty());assertTrue("Browse stays on Media3",FakeNative.sessions.isEmpty());
            autoButton(a).performClick();commands(a,activity("camera-1",true));
            assertTrue("Auto, including a featured camera, stays on Media3",FakeNative.sessions.isEmpty());
            reportAspect(tiles(a).get("camera-1"),1536,576);settle(a); // Its Duo substream shape, observed in Auto.
            CameraTile pinned=tiles(a).get("camera-1");pinned.performClick();settle(a);
            assertEquals(ViewSelector.PIN,selector(a).snapshot(now()).mode);
            assertEquals(1,FakeNative.sessions.size());FakeSession session=FakeNative.sessions.get(0);
            assertEquals(Memory.config.cameras.get(1).streamUri(true),session.uri);
            assertSame(pinned,session.view.getParent());assertNull("No Media3 player alongside",player(pinned));
            View media3=(View)field(CameraTile.class,"surface").get(pinned);assertEquals(View.GONE,media3.getVisibility());
            session.listener.frames();session.listener.frames();
            assertTrue(field(CameraTile.class,"firstFrame").getBoolean(pinned));assertEquals("Live",liveStatus(pinned));
            assertEquals("The pinned main's 4512x2512 picture does not replace the substream shape",1536/576.0,videoAspects(a).get("camera-1"),1e-9);
            // Back detaches at once; no feed is admitted until native retirement completes.
            int starts=NoVideo.starts;a.onBackPressed();settle(a);
            assertEquals(ViewSelector.AUTO,selector(a).snapshot(now()).mode);
            assertEquals(1,session.closes);assertNull(session.view.getParent());assertEquals(View.VISIBLE,media3.getVisibility());
            assertEquals("Replacement waits for native retirement",starts,NoVideo.starts);
            session.retire();settle(a);
            assertTrue(NoVideo.starts>starts);assertEquals("Auto returns to Media3",1,FakeNative.sessions.size());
            assertNotNull(player(tiles(a).get("camera-1")));
            CameraWall wall=a.findViewById(R.id.camera_wall);
            assertEquals("Back in Auto, its row uses the Duo substream shape",Arrays.asList("camera-1"),assertStackedRows(wall,rows(wall).size()).subList(0,1));
            String logs=playerLogs();
            assertTrue(logs,logs.contains("event=start renderer=surface engine=libvlc decoder=hardware_first"));
            assertTrue(logs,logs.contains("engine=libvlc size=4512x2512"));
            assertTrue(logs,logs.contains("event=frames_advancing engine=libvlc displayed=24 decoded=25 lost=0 size=4512x2512"));
        }finally{c.pause().stop().destroy();FakeNative.holdRetirement=false;FakeNative.retireAll();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void sameShapeSubstreamIsLearnedAfterMainPlayback()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);
        try{
            MainActivity a=c.get();((AppConfig)field(MainActivity.class,"config").get(a)).useMainStream=true;settle(a);
            CameraTile tile=tiles(a).get("camera-0");tile.performClick();settle(a);
            assertTrue(tile.playingMainStream());
            reportAspect(tile,4512,2512);
            assertFalse(videoAspects(a).containsKey("camera-0"));
            a.onBackPressed();settle(a);
            assertSame(tile,tiles(a).get("camera-0"));
            assertFalse(tile.playingMainStream());
            reportAspect(tile,2256,1256);
            assertEquals(4512/2512.0,videoAspects(a).get("camera-0"),1e-9);
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void failedPinnedMainFallsBackOnceToItsSubstreamUntilOpenedAgain()throws Exception{
        FakeNative.holdRetirement=true;ActivityController<MainActivity> c=openCatalog(null);
        try{MainActivity a=c.get();((AppConfig)field(MainActivity.class,"config").get(a)).useMainStream=true;settle(a);
            AppConfig.Camera camera=Memory.config.cameras.get(1);assertNotEquals(camera.streamUri(true),camera.streamUri(false));
            CameraTile pinned=tiles(a).get("camera-1");pinned.performClick();settle(a);
            assertEquals(1,FakeNative.sessions.size());FakeSession main=FakeNative.sessions.get(0);
            // A fatal main error before the first frame releases the native session, then tries the distinct substream once.
            int starts=NoVideo.starts;main.listener.error();
            assertEquals("Main unavailable · switching to grid stream…",((TextView)field(CameraTile.class,"status").get(pinned)).getText().toString());
            assertEquals(1,main.closes);assertNull(main.view.getParent());settle(a);
            assertEquals("The substream waits for native retirement",starts,NoVideo.starts);
            main.retire();settle(a);
            assertEquals(starts+1,NoVideo.starts);assertEquals("No second main attempt",1,FakeNative.sessions.size());
            assertEquals(camera.streamUri(false),field(CameraTile.class,"activeUri").get(pinned));
            assertTrue(field(CameraTile.class,"fallbackActive").getBoolean(pinned));assertFalse(field(CameraTile.class,"mainStream").getBoolean(pinned));
            assertNotNull("Media3 plays the substream",player(pinned));
            assertEquals("Truthful quality once live","Grid stream · main unavailable",liveStatus(pinned));
            // Rerendering keeps the fallback rather than reopening the failed main.
            field(MainActivity.class,"signature").set(a,"");invoke(a,"render");settle(a);
            assertSame(pinned,tiles(a).get("camera-1"));assertEquals(camera.streamUri(false),field(CameraTile.class,"activeUri").get(pinned));
            assertEquals(starts+1,NoVideo.starts);assertEquals(1,FakeNative.sessions.size());
            // A substream failure retries the substream through the normal backoff, never the main.
            Method retry=CameraTile.class.getDeclaredMethod("scheduleRetry",String.class);retry.setAccessible(true);retry.invoke(pinned,"Camera stream unavailable");
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));settle(a);
            assertEquals(starts+2,NoVideo.starts);assertEquals(1,FakeNative.sessions.size());
            assertEquals(camera.streamUri(false),field(CameraTile.class,"activeUri").get(pinned));
            // A new explicit open retries the main natively.
            a.onBackPressed();settle(a);tiles(a).get("camera-1").performClick();settle(a);
            assertEquals(2,FakeNative.sessions.size());assertEquals(camera.streamUri(true),FakeNative.sessions.get(1).uri);
            assertFalse(field(CameraTile.class,"fallbackActive").getBoolean(tiles(a).get("camera-1")));
            String logs=playerLogs();
            assertTrue(logs,logs.contains("event=failure code=LIBVLC_STREAM_ERROR"));
            assertTrue(logs,logs.contains("event=fallback code=MAIN_STREAM_UNAVAILABLE reason=Camera stream unavailable"));
        }finally{c.pause().stop().destroy();FakeNative.holdRetirement=false;FakeNative.retireAll();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void rapidSwitchAndStopRetireEachNativeMainBeforeTheNextStarts()throws Exception{
        FakeNative.holdRetirement=true;ActivityController<MainActivity> c=openCatalog(null);boolean stopped=false;
        try{MainActivity a=c.get();((AppConfig)field(MainActivity.class,"config").get(a)).useMainStream=true;settle(a);
            tiles(a).get("camera-1").performClick();settle(a);assertEquals(1,FakeNative.sessions.size());
            // Two quick upward swipes: the first native main detaches; nothing else starts until it has retired.
            assertTrue(swipe(a,0,-120));assertTrue(swipe(a,0,-120));settle(a);
            assertEquals(1,FakeNative.sessions.size());assertEquals(1,FakeNative.sessions.get(0).closes);
            FakeNative.retireAll();settle(a);
            assertEquals("Only the final camera opens",2,FakeNative.sessions.size());
            assertEquals(Memory.config.cameras.get(3).streamUri(true),FakeNative.sessions.get(1).uri);
            // Stopping detaches the active native main; a late callback cannot restart it.
            c.pause().stop();stopped=true;FakeSession active=FakeNative.sessions.get(1);
            assertEquals(1,active.closes);assertNull(active.listener);assertTrue(tiles(a).isEmpty());
            FakeNative.retireAll();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertEquals(2,FakeNative.sessions.size());
            c.restart().resume();stopped=false;settle(a);
            assertEquals("Reopening after stop starts one new native main",3,FakeNative.sessions.size());
            assertEquals(Memory.config.cameras.get(3).streamUri(true),FakeNative.sessions.get(2).uri);
        }finally{if(stopped)c.destroy();else c.pause().stop().destroy();FakeNative.holdRetirement=false;FakeNative.retireAll();}
    }

    // Pinned-camera swipes: real MotionEvents dispatched to the activity; offsets in dp from mid-screen.
    static float px(MainActivity a,float dp){return dp*a.getResources().getDisplayMetrics().density;}
    static MotionEvent touch(long down,long at,int action,float x,float y){return MotionEvent.obtain(down,at,action,x,y,0);}
    /** Down, two moves, then the end action; returns whether the activity consumed the final event. */
    static boolean drag(MainActivity a,float dxDp,float dyDp,int end){
        float x=px(a,180),y=px(a,360),dx=px(a,dxDp),dy=px(a,dyDp);long t=SystemClock.uptimeMillis();
        a.dispatchTouchEvent(touch(t,t,MotionEvent.ACTION_DOWN,x,y));
        a.dispatchTouchEvent(touch(t,t+30,MotionEvent.ACTION_MOVE,x+dx/2,y+dy/2));
        a.dispatchTouchEvent(touch(t,t+60,MotionEvent.ACTION_MOVE,x+dx,y+dy));
        return a.dispatchTouchEvent(touch(t,t+80,end,x+dx,y+dy));
    }
    static boolean swipe(MainActivity a,float dxDp,float dyDp){return drag(a,dxDp,dyDp,MotionEvent.ACTION_UP);}
    static MotionEvent twoFingers(long down,long at,int action,float x,float y){
        MotionEvent.PointerProperties[] ids=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] at2=new MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++){ids[i]=new MotionEvent.PointerProperties();ids[i].id=i;ids[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            at2[i]=new MotionEvent.PointerCoords();at2[i].x=x+i*100;at2[i].y=y;}
        return MotionEvent.obtain(down,at,action,2,ids,at2,0,0,1f,1f,0,0,0,0);
    }
    /** A vertical drag that gains a second finger before release. */
    static void twoFingerSwipe(MainActivity a){
        float x=px(a,180),y=px(a,360),dy=px(a,-150);long t=SystemClock.uptimeMillis();
        int second=1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        a.dispatchTouchEvent(touch(t,t,MotionEvent.ACTION_DOWN,x,y));
        a.dispatchTouchEvent(touch(t,t+20,MotionEvent.ACTION_MOVE,x,y+dy/3));
        a.dispatchTouchEvent(twoFingers(t,t+40,MotionEvent.ACTION_POINTER_DOWN|second,x,y+dy/3));
        a.dispatchTouchEvent(twoFingers(t,t+60,MotionEvent.ACTION_MOVE,x,y+dy));
        a.dispatchTouchEvent(twoFingers(t,t+70,MotionEvent.ACTION_POINTER_UP|second,x,y+dy));
        a.dispatchTouchEvent(touch(t,t+80,MotionEvent.ACTION_UP,x,y+dy));
    }
    static String pinnedCamera(MainActivity a)throws Exception{
        ViewSelector.Selection s=selector(a).snapshot(now());assertEquals(ViewSelector.PIN,s.mode);return s.visible.get(0);
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void pinnedCameraHasNoBarAndOnlyDeliberateVerticalSwipesChangeCamera()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();settle(a);
            CameraWall wall=a.findViewById(R.id.camera_wall);int offset=rows(wall).get(2).getTop();wall.scrollTo(0,offset);settle(a);
            tiles(a).get("camera-2").performClick();settle(a);
            View bar=a.findViewById(R.id.phone_controls);
            assertEquals("camera-2",pinnedCamera(a));assertEquals("No bar on entry",View.GONE,bar.getVisibility());
            tiles(a).get("camera-2").performClick();settle(a);
            assertEquals("A tap neither shows a bar nor changes camera",View.GONE,bar.getVisibility());assertEquals("camera-2",pinnedCamera(a));
            assertTrue(swipe(a,0,-120));settle(a);assertEquals("Up: next camera","camera-3",pinnedCamera(a));
            assertTrue(swipe(a,0,-120));settle(a);assertEquals("camera-4",pinnedCamera(a));
            assertTrue(swipe(a,0,-120));settle(a);assertEquals("Disabled camera-5 is skipped","camera-6",pinnedCamera(a));
            assertTrue(swipe(a,0,120));settle(a);assertEquals("Down: previous camera","camera-4",pinnedCamera(a));
            assertEquals(View.GONE,bar.getVisibility());
            // Not a camera swipe: horizontal, too short, not vertical enough, canceled, or two fingers.
            swipe(a,150,10);settle(a);
            assertTrue("A short vertical drag is still kept from the tile",swipe(a,0,-20));
            swipe(a,100,-120);
            assertTrue(drag(a,0,-120,MotionEvent.ACTION_CANCEL));
            twoFingerSwipe(a);settle(a);
            assertEquals("camera-4",pinnedCamera(a));assertEquals(View.GONE,bar.getVisibility());
            // Long-press keeps the existing More menu reachable without a bar.
            assertTrue(tiles(a).get("camera-4").performLongClick());
            assertNotNull(org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu());org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().dismiss();
            assertEquals("camera-4",pinnedCamera(a));
            // System Back returns to Browse at the same scroll position.
            a.onBackPressed();settle(a);
            assertEquals(ViewSelector.GRID,selector(a).snapshot(now()).mode);assertEquals(offset,wall.getScrollY());
            assertEquals(View.GONE,bar.getVisibility());
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void fullscreenGridKeepsBackAndMoreWhilePinningFromItHidesTheBar()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();
            autoButton(a).performClick();field(MainActivity.class,"layoutFullscreen").setBoolean(a,true);invoke(a,"render");settle(a);
            LinearLayout bar=a.findViewById(R.id.phone_controls);
            assertEquals("A fullscreen multi-camera layout offers the bar",View.VISIBLE,bar.getVisibility());
            List<String> labels=new ArrayList<>();for(int i=0;i<bar.getChildCount();i++)labels.add(((Button)bar.getChildAt(i)).getText().toString());
            assertEquals("Previous/Next are gone; Back and More remain",Arrays.asList("Back","More"),labels);
            tiles(a).get("camera-1").performClick();settle(a);
            assertEquals("camera-1",pinnedCamera(a));assertEquals("Entering Pin from a fullscreen grid hides the bar at once",View.GONE,bar.getVisibility());
            a.onBackPressed();settle(a);
            assertEquals(ViewSelector.AUTO,selector(a).snapshot(now()).mode);
            assertEquals("Back to the fullscreen grid offers the bar again",View.VISIBLE,bar.getVisibility());
        }finally{c.pause().stop().destroy();}
    }

    // Pinned-camera zoom: real multi-pointer MotionEvents in window coordinates; geometry is read back from the video view.
    static final int[] ONE={0},TWO={0,1};
    static final int SECOND=1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    static MotionEvent fingers(long down,long at,int action,int[] ids,float... xy){
        MotionEvent.PointerProperties[] p=new MotionEvent.PointerProperties[ids.length];MotionEvent.PointerCoords[] c=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){p[i]=new MotionEvent.PointerProperties();p[i].id=ids[i];p[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            c[i]=new MotionEvent.PointerCoords();c[i].x=xy[2*i];c[i].y=xy[2*i+1];}
        return MotionEvent.obtain(down,at,action,ids.length,p,c,0,0,1f,1f,0,0,0,0);
    }
    /** Two fingers side by side about (x, y), spread from one separation to another; true when the release was claimed. */
    static boolean pinch(MainActivity a,float x,float y,float from,float to){
        long t=SystemClock.uptimeMillis();
        a.dispatchTouchEvent(fingers(t,t,MotionEvent.ACTION_DOWN,ONE,x-from/2,y));
        a.dispatchTouchEvent(fingers(t,t+10,MotionEvent.ACTION_POINTER_DOWN|SECOND,TWO,x-from/2,y,x+from/2,y));
        for(int i=1;i<=4;i++){float half=(from+(to-from)*i/4)/2;a.dispatchTouchEvent(fingers(t,t+10+16*i,MotionEvent.ACTION_MOVE,TWO,x-half,y,x+half,y));}
        a.dispatchTouchEvent(fingers(t,t+90,MotionEvent.ACTION_POINTER_UP|SECOND,TWO,x-to/2,y,x+to/2,y));
        return a.dispatchTouchEvent(fingers(t,t+100,MotionEvent.ACTION_UP,ONE,x-to/2,y));
    }
    /** A stationary press held past the long-press timeout, then released. */
    static void hold(MainActivity a){
        float x=px(a,180),y=px(a,360);long t=SystemClock.uptimeMillis();
        a.dispatchTouchEvent(touch(t,t,MotionEvent.ACTION_DOWN,x,y));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout()+300));
        a.dispatchTouchEvent(touch(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y));
    }
    static View media3(CameraTile tile)throws Exception{return (View)field(CameraTile.class,"surface").get(tile);}
    static int[] window(View view){int[] at=new int[2];view.getLocationInWindow(at);return at;}
    /** Where a video-view content point appears in the window, through the view's own pivot, scale and translation. */
    static float[] shown(View video,float x,float y){
        int[] tile=window((View)video.getParent());
        return new float[]{tile[0]+video.getLeft()+video.getPivotX()+(x-video.getPivotX())*video.getScaleX()+video.getTranslationX(),
                tile[1]+video.getTop()+video.getPivotY()+(y-video.getPivotY())*video.getScaleY()+video.getTranslationY()};
    }
    /** The video-view content point shown at a window point. */
    static float[] content(View video,float x,float y){
        int[] tile=window((View)video.getParent());
        return new float[]{(x-tile[0]-video.getLeft()-video.getPivotX()-video.getTranslationX())/video.getScaleX()+video.getPivotX(),
                (y-tile[1]-video.getTop()-video.getPivotY()-video.getTranslationY())/video.getScaleY()+video.getPivotY()};
    }
    /** The picture fitted at its shape in the view's measured size, centered, then transformed: left, top, right, bottom in the window. */
    static float[] picture(View video,double aspect){
        float w=video.getWidth(),h=video.getHeight(),pw=(float)Math.min(w,h*aspect),ph=(float)Math.min(h,w/aspect);
        float[] start=shown(video,(w-pw)/2,(h-ph)/2),end=shown(video,(w+pw)/2,(h+ph)/2);
        return new float[]{start[0],start[1],end[0],end[1]};
    }
    static void assertIdentity(View view){
        assertEquals(1f,view.getScaleX(),0f);assertEquals(1f,view.getScaleY(),0f);
        assertEquals(0f,view.getTranslationX(),0f);assertEquals(0f,view.getTranslationY(),0f);
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void pinchZoomsOnlyThePinnedPictureAboutTheFingersAndPansWithinIt()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();settle(a);
            tiles(a).get("camera-2").performClick();settle(a);
            CameraTile tile=tiles(a).get("camera-2");View video=media3(tile),status=(View)field(CameraTile.class,"status").get(tile);
            assertEquals("A Media3 substream",View.VISIBLE,video.getVisibility());assertTrue(FakeNative.sessions.isEmpty());
            Object stream=player(tile);int starts=NoVideo.starts,width=tile.getWidth(),height=tile.getHeight();int[] at=window(tile);
            // A picture the shape of the screen has no bars, so the point under the fingers stays there on both axes.
            reportAspect(tile,width,height);settle(a);double full=width/(double)height;
            float fx=at[0]+width*.3f,fy=at[1]+height*.4f;float[] under=content(video,fx,fy);
            assertTrue("The pinch is claimed",pinch(a,fx,fy,px(a,60),px(a,120)));
            assertEquals(2f,video.getScaleX(),1e-4f);assertEquals(2f,video.getScaleY(),1e-4f);
            float[] still=content(video,fx,fy);assertEquals(under[0],still[0],.5f);assertEquals(under[1],still[1],.5f);
            // Only the video transforms: the tile, its layout and its status label stay as they were; the stream plays on.
            assertFalse("No layout pass for a pinch",tile.isLayoutRequested());assertIdentity(tile);assertIdentity(status);
            assertEquals(width,tile.getWidth());assertEquals(height,tile.getHeight());assertEquals(width,video.getWidth());
            assertSame(stream,player(tile));assertEquals(starts,NoVideo.starts);assertEquals("camera-2",pinnedCamera(a));
            // Continuous to 4x, never beyond.
            pinch(a,fx,fy,px(a,60),px(a,100));assertEquals(2*100/60f,video.getScaleX(),1e-3f);
            still=content(video,fx,fy);assertEquals(under[0],still[0],.5f);assertEquals(under[1],still[1],.5f);
            pinch(a,fx,fy,px(a,40),px(a,400));assertEquals(4f,video.getScaleX(),0f);
            // One finger pans while zoomed, even a long vertical drag, and stops at the picture's edges.
            assertTrue(swipe(a,2000,4000));float[] edge=picture(video,full);
            assertEquals(at[0],edge[0],.5f);assertEquals(at[1],edge[1],.5f);
            assertTrue(swipe(a,-2000,-4000));edge=picture(video,full);
            assertEquals(at[0]+width,edge[2],.5f);assertEquals(at[1]+height,edge[3],.5f);
            assertEquals("Panning never changes camera","camera-2",pinnedCamera(a));
            // A wide picture letterboxes: it stays centered vertically and pans only sideways, within itself.
            Method sizeChanged = CameraTile.class.getDeclaredMethod("reportAspect", int.class, int.class, float.class);
            sizeChanged.setAccessible(true);
            sizeChanged.invoke(tile, 1280, 720, 1.5f);
            float[] wide = picture(video, 8.0 / 3);
            assertEquals(at[1]+height/2f,(wide[1]+wide[3])/2,.5f);assertEquals(at[0]+width,wide[2],.5f);
            assertTrue(swipe(a,40,200));float[] panned=picture(video,1536/576.0);
            assertEquals(wide[1],panned[1],.5f);assertEquals(wide[2]+px(a,40),panned[2],.5f);
            // Pinching in returns to the 1x fit, where a vertical swipe changes camera again.
            pinch(a,at[0]+width/2f,at[1]+height/2f,px(a,300),px(a,30));assertIdentity(video);
            assertEquals("camera-2",pinnedCamera(a));assertSame(stream,player(tile));assertEquals(starts,NoVideo.starts);
            assertTrue(swipe(a,0,-120));settle(a);assertEquals("camera-3",pinnedCamera(a));
            assertIdentity(media3(tiles(a).get("camera-3")));
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void aTouchThatGainsAFingerNeverChangesCameraWhileBackAndLongPressStillWork()throws Exception{
        ActivityController<MainActivity> c=openCatalog(null);try{MainActivity a=c.get();settle(a);
            CameraTile tile=tiles(a).get("camera-2");tile.performClick();settle(a);View video=media3(tile);Object stream=player(tile);
            float x=px(a,180),y=px(a,360),top=y-px(a,100),d=px(a,40),focus=x+d/2;long t=SystemClock.uptimeMillis();int[] replaced={1,2};
            // A vertical swipe is captured; then a second finger lands and spreads to twice the distance: a pinch.
            a.dispatchTouchEvent(fingers(t,t,MotionEvent.ACTION_DOWN,ONE,x,y));
            assertTrue(a.dispatchTouchEvent(fingers(t,t+20,MotionEvent.ACTION_MOVE,ONE,x,top)));
            assertTrue(a.dispatchTouchEvent(fingers(t,t+40,MotionEvent.ACTION_POINTER_DOWN|SECOND,TWO,x,top,x+d,top)));
            a.dispatchTouchEvent(fingers(t,t+60,MotionEvent.ACTION_MOVE,TWO,x-d/2,top,x+1.5f*d,top));
            assertEquals(2f,video.getScaleX(),1e-4f);float[] under=content(video,focus,top);
            float scale=video.getScaleX(),panX=video.getTranslationX(),panY=video.getTranslationY();
            // The first finger lifts and another replaces it: the picture does not jump, and the new pair pinches on.
            a.dispatchTouchEvent(fingers(t,t+80,MotionEvent.ACTION_POINTER_UP,TWO,x-d/2,top,x+1.5f*d,top));
            a.dispatchTouchEvent(fingers(t,t+100,MotionEvent.ACTION_POINTER_DOWN|SECOND,replaced,x+1.5f*d,top,x-d/2,top));
            assertEquals(scale,video.getScaleX(),0f);assertEquals(panX,video.getTranslationX(),0f);assertEquals(panY,video.getTranslationY(),0f);
            a.dispatchTouchEvent(fingers(t,t+120,MotionEvent.ACTION_MOVE,replaced,x+2*d,top,x-d,top));
            assertEquals(3f,video.getScaleX(),1e-4f);float[] still=content(video,focus,top);
            assertEquals(under[0],still[0],.5f);assertEquals(under[1],still[1],.5f);
            a.dispatchTouchEvent(fingers(t,t+140,MotionEvent.ACTION_POINTER_UP|SECOND,replaced,x+2*d,top,x-d,top));
            assertTrue(a.dispatchTouchEvent(fingers(t,t+160,MotionEvent.ACTION_UP,new int[]{1},x+2*d,top)));
            assertEquals("Gaining a finger cancels the swipe","camera-2",pinnedCamera(a));
            // A canceled pinch stays claimed and changes nothing else.
            t=SystemClock.uptimeMillis();
            a.dispatchTouchEvent(fingers(t,t,MotionEvent.ACTION_DOWN,ONE,x,y));
            a.dispatchTouchEvent(fingers(t,t+10,MotionEvent.ACTION_POINTER_DOWN|SECOND,TWO,x,y,x+d,y));
            a.dispatchTouchEvent(fingers(t,t+30,MotionEvent.ACTION_MOVE,TWO,x-d,y,x+2*d,y));
            assertEquals("Clamped at 4x",4f,video.getScaleX(),0f);
            assertTrue(a.dispatchTouchEvent(fingers(t,t+40,MotionEvent.ACTION_CANCEL,TWO,x-d,y,x+2*d,y)));
            assertEquals("camera-2",pinnedCamera(a));
            // A stationary long-press still opens More while zoomed, and keeps the camera and zoom.
            PopupMenu before=org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();hold(a);
            PopupMenu menu=org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();assertNotNull(menu);assertNotSame(before,menu);menu.dismiss();
            assertEquals("camera-2",pinnedCamera(a));assertEquals(4f,video.getScaleX(),0f);
            assertSame("Zoom gestures keep the stream",stream,player(tile));
            // Back leaves Pin; the same tile returns to the list at the 1x fit.
            a.onBackPressed();settle(a);
            assertEquals(ViewSelector.GRID,selector(a).snapshot(now()).mode);assertSame(tile,tiles(a).get("camera-2"));
            assertEquals(1f,tile.videoZoom(),0f);assertIdentity(video);assertSame(stream,player(tile));
        }finally{c.pause().stop().destroy();}
    }
    @Test @Config(qualifiers="w360dp-h720dp-port-mdpi")
    public void zoomFollowsTheNativeMainIntoItsSubstreamFallbackAndResetsWithTheCamera()throws Exception{
        FakeNative.holdRetirement=true;ActivityController<MainActivity> c=openCatalog(null);boolean stopped=false;
        try{MainActivity a=c.get();((AppConfig)field(MainActivity.class,"config").get(a)).useMainStream=true;settle(a);
            CameraTile tile=tiles(a).get("camera-1");tile.performClick();settle(a);
            assertEquals(1,FakeNative.sessions.size());FakeSession main=FakeNative.sessions.get(0);View lib=main.view,media3=media3(tile);
            main.pixelRatio = .9f;
            main.listener.frames();
            settle(a);
            int[] at = window(tile);
            int width = tile.getWidth(), height = tile.getHeight();
            double mainShape = 4512.0 / 2512 * main.pixelRatio, subShape = 8.0 / 3;
            pinch(a,at[0]+width/2f,at[1]+height/2f,px(a,50),px(a,200));
            assertEquals("The LibVLC view zooms",4f,lib.getScaleX(),0f);assertEquals(View.GONE,media3.getVisibility());
            // At 4x the main picture is taller than the screen: pan to its top-left corner.
            assertTrue(swipe(a,2000,2000));float[] corner=picture(lib,mainShape);
            assertEquals(at[0],corner[0],.5f);assertEquals(at[1],corner[1],.5f);
            assertEquals("One native session throughout",1,FakeNative.sessions.size());assertEquals(0,main.closes);assertSame(tile,lib.getParent());
            // The main fails: Media3 plays the same camera's substream at the same zoom, re-fitted to the new shape.
            main.listener.error();main.retire();settle(a);
            assertSame(tile,tiles(a).get("camera-1"));assertNull(lib.getParent());assertEquals(View.VISIBLE,media3.getVisibility());
            assertNotNull("Media3 plays the substream",player(tile));assertEquals(1,FakeNative.sessions.size());
            assertEquals(4f,media3.getScaleX(),0f);float[] inherited=picture(media3,mainShape);
            assertEquals(at[0],inherited[0],.5f);assertEquals(at[1],inherited[1],.5f);
            reportAspect(tile,1536,576);float[] fallback=picture(media3,subShape);
            assertEquals("Still at its left edge",at[0],fallback[0],.5f);
            assertEquals("The shorter picture is centered, never panned into its bars",at[1]+height/2f,(fallback[1]+fallback[3])/2,.5f);
            // A camera change (here from a keyboard) retires the zoomed tile and opens the next camera at the 1x fit.
            c.windowFocusChanged(true);assertTrue(a.hasWindowFocus());
            assertTrue(a.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_DOWN)));settle(a);
            assertEquals("camera-2",pinnedCamera(a));assertTrue(field(CameraTile.class,"released").getBoolean(tile));
            assertEquals(1f,tile.videoZoom(),0f);assertIdentity(media3);
            CameraTile next=tiles(a).get("camera-2");assertEquals(1f,next.videoZoom(),0f);
            assertEquals(2,FakeNative.sessions.size());View nextLib=FakeNative.sessions.get(1).view;assertSame(next,nextLib.getParent());assertIdentity(nextLib);
            // Stopping retires the zoomed tile at the 1x fit too.
            pinch(a,at[0]+width/2f,at[1]+height/2f,px(a,50),px(a,100));assertEquals(2f,nextLib.getScaleX(),1e-4f);
            assertEquals(2f,media3(next).getScaleX(),1e-4f);
            c.pause().stop();stopped=true;
            assertEquals(1f,next.videoZoom(),0f);assertIdentity(media3(next));
        }finally{if(stopped)c.destroy();else c.pause().stop().destroy();FakeNative.holdRetirement=false;FakeNative.retireAll();}
    }
}

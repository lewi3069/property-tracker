package com.fieldview.tv;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class CommandInboxTest {
    @Test public void burstIsBoundedAndNewestCommandsKeepTheirOrder() {
        List<String> received = new ArrayList<>(); CommandInbox inbox = new CommandInbox(new Handler(Looper.getMainLooper()), (p,r) -> received.add(p));
        for(int i=0;i<1000;i++) inbox.offer("event-"+i,false);
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(64,received.size());
        assertEquals("event-936",received.get(0)); assertEquals("event-999",received.get(63)); inbox.close();
    }
    @Test public void closedSessionDiscardsPendingAndLateMessages() {
        CommandInbox inbox = new CommandInbox(new Handler(Looper.getMainLooper()), (p,r) -> fail());
        inbox.offer("one",false); inbox.close(); inbox.offer("late",false); shadowOf(Looper.getMainLooper()).idle();
    }
}

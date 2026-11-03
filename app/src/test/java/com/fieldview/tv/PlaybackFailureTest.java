package com.fieldview.tv;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackFailureTest {
    @Test public void nestedNetworkTimeoutExplainsBlockedConnectivity() {
        assertTrue(PlaybackFailure.describe(new IOException("Source failure", new SocketTimeoutException())).contains("timed out"));
    }
    @Test public void networkFailuresAreDistinguished() {
        assertTrue(PlaybackFailure.describe(new ConnectException()).contains("refused"));
        assertTrue(PlaybackFailure.describe(new UnknownHostException()).contains("not found"));
    }
    @Test public void authenticationAndPathsAreDistinguishedWithoutLeakingUris() {
        String auth = PlaybackFailure.describe(new IOException("RTSP 401 rtsp://user:secret@camera/private"));
        assertTrue(auth.contains("login rejected")); assertFalse(auth.contains("secret")); assertFalse(auth.contains("rtsp"));
        assertTrue(PlaybackFailure.describe(new IOException("RTSP 404")).contains("path not found"));
    }
    @Test public void arbitraryExceptionTextIsNeverShown() {
        String reason = PlaybackFailure.describe(new IOException("Password=secret at rtsp://user:secret@camera"));
        assertFalse(reason.contains("secret")); assertFalse(reason.contains("rtsp://"));
    }
}

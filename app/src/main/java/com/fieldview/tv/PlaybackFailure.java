package com.fieldview.tv;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Returns fixed, actionable text without exposing credential-bearing exception messages. */
public final class PlaybackFailure {
    private PlaybackFailure() { }
    public static String describe(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) return "Camera connection timed out · check network / RTSP port";
            if (cause instanceof NoRouteToHostException) return "No route to camera · check network";
            if (cause instanceof ConnectException) return "Camera connection refused · check RTSP port";
            if (cause instanceof UnknownHostException) return "Camera address not found";
            String message = cause.getMessage();
            if (message != null) {
                if (message.matches("(?is).*\\b(401|403)\\b.*")) return "Camera login rejected · check username / password";
                if (message.matches("(?is).*\\b404\\b.*")) return "Stream path not found · check camera preset";
            }
        }
        return "Stream unavailable · check resolution / camera settings";
    }
}

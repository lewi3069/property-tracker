package com.fieldview.tv;

import androidx.media3.common.PlaybackException;
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
        if (error instanceof PlaybackException) {
            switch (((PlaybackException) error).errorCode) {
                case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
                case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
                case PlaybackException.ERROR_CODE_DECODING_FAILED:
                    return "Video decoder unavailable · try fewer cameras or a smaller stream";
                case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
                case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
                    return "Video format unsupported · check codec / resolution";
                case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
                case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:
                    return "Camera stream could not be parsed · check RTSP stream settings";
                default: break;
            }
        }
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

package com.fieldview.tv;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import javax.net.SocketFactory;

/** RTSP's RTP timeout does not cover the initial TCP handshake. */
public final class TimedSocketFactory extends SocketFactory {
    private final int timeoutMs;
    public TimedSocketFactory(int timeoutMs) {
        if (timeoutMs < 1) throw new IllegalArgumentException("Connection timeout must be positive");
        this.timeoutMs = timeoutMs;
    }
    @Override public Socket createSocket() {
        return new Socket() {
            @Override public void connect(SocketAddress address) throws IOException { super.connect(address, timeoutMs); }
            @Override public void connect(SocketAddress address, int requestedTimeout) throws IOException {
                super.connect(address, requestedTimeout <= 0 ? timeoutMs : Math.min(requestedTimeout, timeoutMs));
            }
        };
    }
    private Socket connected(InetSocketAddress remote, InetSocketAddress local) throws IOException {
        Socket socket = createSocket();
        try {
            if (local != null) socket.bind(local);
            socket.connect(remote);
            return socket;
        } catch (IOException | RuntimeException error) {
            try { socket.close(); } catch (IOException ignored) { }
            throw error;
        }
    }
    @Override public Socket createSocket(String host, int port) throws IOException { return connected(new InetSocketAddress(host, port), null); }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException { return connected(new InetSocketAddress(host, port), null); }
    @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
        return connected(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
    @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
        return connected(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
}

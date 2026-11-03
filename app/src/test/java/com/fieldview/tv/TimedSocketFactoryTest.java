package com.fieldview.tv;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimedSocketFactoryTest {
    @Test public void connectedSocketPreservesNormalRtspConnections() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new TimedSocketFactory(1000).createSocket(InetAddress.getLoopbackAddress(), server.getLocalPort());
             Socket accepted = server.accept()) {
            assertTrue(client.isConnected()); assertTrue(accepted.isConnected());
        }
    }
    @Test public void unconnectedSocketCanUseTheBoundedConnectOverload() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new TimedSocketFactory(1000).createSocket()) {
            client.connect(server.getLocalSocketAddress(), 0);
            try (Socket accepted = server.accept()) { assertTrue(client.isConnected()); }
        }
    }
}

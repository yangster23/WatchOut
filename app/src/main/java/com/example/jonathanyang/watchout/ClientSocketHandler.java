package com.example.jonathanyang.watchout;

import android.content.Context;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Necessary to handle a socket for the client of the P2P connection,
 * derived from WiFiDirectServiceDiscovery Demo from Google Samples
 */

public class ClientSocketHandler extends Thread {

    private static final int SOCKET_TIMEOUT = 5000; // Amount of timeout for socket
    private static final String TAG = "ClientSocketHandler";
    LocalBroadcastManager broadcaster;
    private Handler handler;
    private ChatManager chat;
    private InetAddress mAddress;
    private int mPort;

    public ClientSocketHandler(Handler handler, InetAddress groupOwnerAddress, int port,
                               Context context) {
        this.broadcaster = LocalBroadcastManager.getInstance(context);
        this.handler = handler;
        this.mAddress = groupOwnerAddress;
        this.mPort = port;
    }

    @Override
    public void run() {
        Socket socket = new Socket();
        try {
            socket.bind(null);
            socket.connect(new InetSocketAddress(mAddress.getHostAddress(), mPort), SOCKET_TIMEOUT);
            Log.d(TAG, "Launching the I/O handler");
            chat = new ChatManager(socket, handler);
            new Thread(chat).start();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return;
        }
    }

}

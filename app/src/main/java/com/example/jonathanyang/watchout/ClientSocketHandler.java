package com.example.jonathanyang.watchout;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Necessary to handle a socket for the client of the P2P connection,
 * derived from WiFiDirectServiceDiscovery Demo from Google Samples and MyWifiMesh from DrJukka
 */

public class ClientSocketHandler extends Thread {

    static final public String DSS_CLIENT_VALUES = "com.example.jonathanyang.watchout.DSS_CLIENT_VALUES";
    static final public String DSS_CLIENT_MESSAGE = "com.example.jonathanyang.watchout.DSS_CLIENT_MESSAGE";

    LocalBroadcastManager broadcaster;
    private static final String TAG = "ClientSocketHandler";
    private Handler handler;
    private ChatManager chat;
    private String mAddress;
    private int mPort;

    public ClientSocketHandler(Handler handler, String groupOwnerAddress, int port, Context context) {
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
            socket.connect(new InetSocketAddress(mAddress, mPort), 5000);
            Log.d(TAG, "Launching the I/O handler of the client");
            chat = new ChatManager(socket, handler, "Client");
            new Thread(chat).start();
        } catch (Exception e) { // handles exception
            if (broadcaster != null) {
                Intent intent = new Intent(DSS_CLIENT_VALUES);
                intent.putExtra(DSS_CLIENT_MESSAGE, e.toString());
                broadcaster.sendBroadcast(intent);
                ;
            }
            try {
                socket.close();
            } catch (Exception e1) {
                if (broadcaster != null) {
                    Intent intent = new Intent(DSS_CLIENT_VALUES);
                    intent.putExtra(DSS_CLIENT_MESSAGE, e.toString());
                    broadcaster.sendBroadcast(intent);
                }
            }
            return;
        }

    }

    // Retrieves the chat handler
    public ChatManager getChat() {
        return chat;
    }
}

package com.example.jonathanyang.watchout;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Necessary to handle a socket for the group owner of the P2P connection,
 * derived from WiFiDirectServiceDiscovery Demo from Google Samples.
 */

public class GroupOwnerSocketHandler extends Thread {

    private static final String TAG = "GroupOwnerSocketHandler";
    private final int THREAD_COUNT = 10;
    LocalBroadcastManager broadcaster;
    ServerSocket socket = null;
    private Handler handler;
    private ChatManager chat;


    public GroupOwnerSocketHandler(Handler handler, int port, Context context) throws IOException {
        try {
            this.broadcaster = LocalBroadcastManager.getInstance(context);
            socket = new ServerSocket(port);
            this.handler = handler;
            Log.d("GroupOwnerSocketHandler", "Socket Started");
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

    }

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            THREAD_COUNT, THREAD_COUNT, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());

    @Override
    public void run() {
        while (true) {
            try {
                // A blocking operation. We initiate a ChatManager instance when
                // there is a new connection
                Socket  s = socket.accept();
                Log.d(TAG, "Launching the Group I/O handler");
                chat = new ChatManager(s, handler);
                new Thread(chat).start();

            } catch (IOException e) {
                try {
                    if (socket != null && !socket.isClosed())
                        socket.close();
                } catch (IOException ioe) {
                    // No necessary stackTrace.

                }
                e.printStackTrace();

                break;
            }
        }
    }
}

package com.example.jonathanyang.watchout;

import android.os.Handler;
import android.util.Log;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Handles reading and writing of messages with socket buffers. Uses a Handler
 * to post messages to UI thread for UI updates. Derived from WiFiDirectServiceDiscovery Demo from
 * Google Samples
 */

public class ChatManager implements Runnable {

    private Socket socket = null;
    private Handler handler;
    String identity; // determines if either Client or Group Owner

    public ChatManager(Socket socket, Handler handler, String whichUser) {
        this.socket = socket;
        this.handler = handler;
        this.identity = whichUser;
    }

    private InputStream iStream;
    private OutputStream oStream;
    private static final String TAG = "ChatHandler";

    @Override
    public void run() {
        try {
            iStream = socket.getInputStream();
            oStream = socket.getOutputStream();
            byte[] buffer = new byte[1048576]; //Megabyte buffer
            int bytes;
            handler.obtainMessage(MainActivity.MY_HANDLE, this).sendToTarget();

            while (true) {
                try {
                    // Read from the InputStream
                    bytes = iStream.read(buffer);
                    if (bytes == -1) {
                        break;
                    }

                    // Send the obtained bytes to the UI Activity
                    Log.d(TAG, "Rec:" + String.valueOf(buffer));
                    handler.obtainMessage(MainActivity.MESSAGE_READ,bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void write(byte[] buffer) {
        try {
            oStream.write(buffer);
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    String getIdentity(){
        return this.identity;
    }
}
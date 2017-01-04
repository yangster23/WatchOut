package com.example.jonathanyang.watchout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Whenever the connection is established: stop advertising for access point, remove access point,
 * stop searching for additional access points. With Android devices, you can connection only
 * to one WLAN access point.
 */

public class WiFiConnection {

    static final public String DSS_WIFICON_VALUES = "com.example.jonathanyang.watchout.DSS_WIFICON_VALUES";
    static final public String DSS_WIFICON_MESSAGE = "com.example.jonathanyang.watchout.DSS_WIFICON_MESSAGE";

    static final public String DSS_WIFICON_STATUSVAL = "com.example.jonathanyang.watchout.DSS_WIFICON_STATUSVAL";
    static final public String DSS_WIFICON_CONSTATUS = "com.example.jonathanyang.watchout.DSS_WIFICON_CONSTATUS";

    static final public String DSS_WIFICON_SERVERADDRESS = "com.example.jonathanyang.watchout.DSS_WIFICON_SERVERADDRESS";
    static final public String DSS_WIFICON_INETADDRESS = "com.example.jonathanyang.watchout.DSS_WIFICON_INETADDRESS";

    static final public int CONNECTION_STATE_NONE = 0;
    static final public int CONNECTION_STATE_PRECONNECTION = 1;
    static final public int CONNECTION_STATE_CONNECTING = 2;
    static final public int CONNECTION_STATE_CONNECTED = 3;
    static final public int CONNECTION_STATE_DISCONNECTED = 4;

    private int mConnectionState = CONNECTION_STATE_NONE;

    private boolean hadConnection = false;

    WiFiConnection that = this;
    WifiManager wifiManager = null;
    WifiConfiguration wifiConfig = null;
    Context context = null;
    int netId = 0;
    LocalBroadcastManager broadcaster;
    WiFiConnectionReceiver receiver;
    private IntentFilter filter;
    String intetAddress = "";


    public WiFiConnection(Context context, String SSIS, String password) {
        this.context = context;
        broadcaster = LocalBroadcastManager.getInstance(this.context);
        receiver = new WiFiConnectionReceiver();
        filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        this.context.registerReceiver(receiver, filter);
        this.wifiManager = (WifiManager) this.context.getSystemService(this.context.WIFI_SERVICE);
        this.wifiConfig = new WifiConfiguration();
        this.wifiConfig.SSID = String.format("\"%s\"", SSIS);
        this.wifiConfig.preSharedKey = String.format("\"%s\"", password);

        this.netId = this.wifiManager.addNetwork(this.wifiConfig);

        //   this.wifiManager.disconnect();
        this.wifiManager.enableNetwork(this.netId, false);
        this.wifiManager.reconnect();
    }

    public void stop() {
        this.context.unregisterReceiver(receiver);
        this.wifiManager.disconnect();
    }

    public void setInetAddress(String address) {
        this.intetAddress = address;
    }

    public String getInetAddress() {
        return this.intetAddress;
    }


    private class WiFiConnectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info != null) {
                    if (info.isConnected()) {
                        hadConnection = true;
                        mConnectionState = CONNECTION_STATE_CONNECTED;
                    } else if (info.isConnectedOrConnecting()) {
                        mConnectionState = CONNECTION_STATE_CONNECTING;
                    } else {
                        if (hadConnection) {
                            mConnectionState = CONNECTION_STATE_DISCONNECTED;
                        } else {
                            mConnectionState = CONNECTION_STATE_PRECONNECTION;
                        }
                    }
                    if (broadcaster != null) {
                        Intent sndeInt = new Intent(DSS_WIFICON_VALUES);
                        sndeInt.putExtra(DSS_WIFICON_MESSAGE, "DetailedState: " + info.getDetailedState());
                        broadcaster.sendBroadcast(sndeInt);
                    }

                    if (broadcaster != null) {
                        Intent sndeInt = new Intent(DSS_WIFICON_STATUSVAL);
                        sndeInt.putExtra(DSS_WIFICON_CONSTATUS, mConnectionState);
                        broadcaster.sendBroadcast(sndeInt);
                    }
                }
                WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if (wifiInfo != null) {
                    if (broadcaster != null) {
                        Intent snInt = new Intent(DSS_WIFICON_SERVERADDRESS);
                        snInt.putExtra(DSS_WIFICON_INETADDRESS, wifiInfo.getIpAddress());
                        broadcaster.sendBroadcast(snInt);
                    }
                }
            }
        }
    }

}


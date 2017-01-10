package com.example.jonathanyang.watchout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;

/**
 * Establishes connection with another peer through WiFi P2P Manager
 */

public class WiFiServiceConnection implements WifiP2pManager.ConnectionInfoListener,
        WifiP2pManager.GroupInfoListener {

    static final public String DSS_WIFICON_VALUES = "com.example.jonathanyang.watchout.DSS_WIFICON_VALUES";
    static final public String DSS_WIFICON_MESSAGE = "com.example.jonathanyang.watchout.DSS_WIFICON_MESSAGE";
    static final public String DSS_WIFICON_CONINFO = "com.example.jonathanyang.watchout.DSS_WIFICON_CONINFO";
    static final public String DSS_WIFICON_ISGROUP = "com.example.jonathanyang.watchout.DSS_WIFICON_ISGROUP";

    /*
    7 second timer to exit after we get disconnected event
    to prevent us exiting when getting new clients connecting
    if we already have a client, and we get new
    we always get disconnected event first, and then connected
    but if we lose all clients, we just get disconnect event, and not connected following it.
     */
    CountDownTimer exitTimerOnDisconnect = new CountDownTimer(7000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not used
        }

        public void onFinish() {
            disconnectedEvent();
        }
    };

    /*
    If we start connecting same time with other, or if the other party
    does remove service, i.e. got other connection
    our timing out might take minutes, and if we get successful connection
    it should really be established within a minute
    thus we cancel connect, in event of it taking over 60 seconds.
    */
    CountDownTimer cancelConnectTimer = new CountDownTimer(60000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not used
        }

        public void onFinish() {
            disconnectedEvent();
        }
    };

    WiFiServiceConnection that = this;
    LocalBroadcastManager broadcaster;
    Context context;
    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    WifiP2pInfo lastInfo = null;
    WiFiServiceSearcher.ServiceItem selectedItem;

    boolean connecting = false;


    private class WiFiConnectionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // cancel the connection time-out timer here
                cancelConnectTimer.cancel();
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    exitTimerOnDisconnect.cancel();
                    debug_print("We have connection (exit timer cancelled)!!!");
                    p2p.requestConnectionInfo(channel, that);
                } else {
                    debug_print("We are DIS-connected!!!: " + networkInfo.getDetailedState());

                    if (connecting) {
                        if ((lastInfo != null) && lastInfo.isGroupOwner) {
                            debug_print("Started timer for exitting");
                            exitTimerOnDisconnect.start();
                            // new client connects to groupowner
                        } else {
                            disconnectedEvent();
                        }
                    }
                }
            }
        }
    }

    public WiFiServiceConnection(Context Context, WifiP2pManager Manager, WifiP2pManager.Channel Channel) {
        this.context = Context;
        this.p2p = Manager;
        this.channel = Channel;
        this.broadcaster = LocalBroadcastManager.getInstance(this.context);
    }

    // Registers broadcaster to receive connection
    public void start() {
        selectedItem = null;
        receiver = new WiFiConnectionReceiver();
        filter = new IntentFilter();
        filter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
        this.context.registerReceiver(receiver, filter);
    }

    // Get connection info
    public WifiP2pInfo getConnectionInfo() {
        return lastInfo;
    }

    public void stop() {
        this.context.unregisterReceiver(receiver);

        exitTimerOnDisconnect.cancel();
        cancelConnectTimer.cancel();

        if (connecting) {
            connecting = false;
            p2p.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    debug_print("Connecting cancelled");
                }

                @Override
                public void onFailure(int errorCode) {
                    debug_print("Failed cancelling connection: " + errorCode);
                }
            });
        }
        disconnect();
    }

    public void connect(WiFiServiceSearcher.ServiceItem item) {

        selectedItem = item;
        connecting = true;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = item.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        // cancel connection attempt if it took over 60 seconds
        cancelConnectTimer.start();
        p2p.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                debug_print("Connecting to service in " + selectedItem.deviceName);
            }

            @Override
            public void onFailure(int errorCode) {
                debug_print("Failed connecting to service : " + errorCode);
            }
        });
    }

    public void disconnect() {
        if (p2p != null && channel != null) {
            p2p.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && p2p != null && channel != null && group.isGroupOwner()) {
                        p2p.removeGroup(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                debug_print("removeGroup onSuccess -");
                                disconnectedEvent();
                            }

                            @Override
                            public void onFailure(int reason) {
                                debug_print("removeGroup onFailure -" + reason);
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        lastInfo = info;
        connecting = true;
        if (broadcaster != null) {
            Intent intent = new Intent(DSS_WIFICON_CONINFO);
            intent.putExtra(DSS_WIFICON_ISGROUP, info.isGroupOwner);
            broadcaster.sendBroadcast(intent);
        }

        p2p.requestGroupInfo(channel, this);
    }

    public void disconnectedEvent() {
        lastInfo = null;
        if (broadcaster != null) {
            Intent intent = new Intent(DSS_WIFICON_CONINFO);
            intent.putExtra(DSS_WIFICON_ISGROUP, false);
            broadcaster.sendBroadcast(intent);
        }
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {

        int numm = 0;
        for (WifiP2pDevice peer : group.getClientList()) {
            numm++;
            debug_print("Client " + numm + " : " + peer.deviceName + " " + peer.deviceAddress);
        }
    }

    private void debug_print(String buffer) {
        if (broadcaster != null) {
            Intent intent = new Intent(DSS_WIFICON_VALUES);
            if (buffer != null)
                intent.putExtra(DSS_WIFICON_MESSAGE, buffer);
            broadcaster.sendBroadcast(intent);
        }
    }
}


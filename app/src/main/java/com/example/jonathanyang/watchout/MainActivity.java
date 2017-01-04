package com.example.jonathanyang.watchout;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.net.InetAddress;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        ResultCallback<LocationSettingsResult>,
        SensorEventListener { // Main class for application

    // Define a request code to send to Google Play services
    // This code is returned in Activity.onActivityResult
    protected final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    // Constant used in the location settings dialog.
    protected static final int REQUEST_CHECK_SETTINGS = 0x1;

    // Class Name
    protected static final String TAG = MainActivity.class.getSimpleName();

    // Desired interval for location updates in milliseconds.
    public static final long MIN_CHANGE_FOR_UPDATES = 1000;

    // Permission request to access high level location settings
    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 123;

    // Provides entry point to Google Play Services
    protected GoogleApiClient mGoogleApiClient;

    // Stores parameters for requests to the FusedLocationProviderApi.
    protected LocationRequest mLocationRequest;

    //  Stores the types of location services the client is interested in using.
    protected LocationSettingsRequest mLocationSettingsRequest;

    // Tracks the status of the location updates request. Value changes when the user presses the
    // Start Updates and Stop Updates buttons.
    protected Boolean mRequestingLocationUpdates;

    // Provides entry point for Position Sensors
    private SensorManager mSensorManager;

    // Sensors used to get travel direction (0 - 360 degrees)
    private Sensor mAccelerometer;
    private Sensor mMagnetometer;

    // Uses both hardware sensors to provide data for the three orientation angles
    private float[] mAccelerometerReading;
    private float[] mMagnetometerReading;

    // Used to update orientation angles
    private final float[] mRotationMatrix = new float[9];
    private final float[] mOrientationAngles = new float[3];

    // Gets the orientation of the phone to determine bearings in 0 to 360 degrees
    protected int travelDirection;

    public static final String SERVICE_TYPE = "_wdm_p2p._tcp";

    protected MainActivity that = this;

    protected TextSpeech mySpeech = null;

    protected MainBroadcastReceiver mainReceiver;
    private IntentFilter filter;

    // current config is 1 sec, can be changed if needed
    private int mInterval = 1000;

    private Handler timeHandler;
    private int timeCounter;
    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            // call function to update timer
            timeCounter = timeCounter + 1;
            ((TextView) findViewById(R.id.TimeBox)).setText("T: " + timeCounter);
            timeHandler.postDelayed(mStatusChecker, mInterval);
        }
    };

    WiFiServiceSearcher mWifiServiceSearcher = null;
    WiFiAccessPoint mWifiAccessPoint = null;
    WiFiConnection mWifiConnection = null;
    boolean serviceRunning = false;

    //change me to be dynamic!!
    public String CLIENT_PORT_INSTANCE = "38765";
    public String SERVICE_PORT_INSTANCE = "38765";

    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;


    GroupOwnerSocketHandler groupSocket = null;
    ClientSocketHandler clientSocket = null;
    ChatManager chat = null;
    Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:

                    byte[] readBuf = (byte[]) msg.obj;

                    String readMessage = new String(readBuf, 0, msg.arg1);

                    print_line("", "Got message: " + readMessage);

                    mySpeech.speak(readMessage);
                    break;

                case MY_HANDLE:
                    Object obj = msg.obj;
                    chat = (ChatManager) obj;

                    String helloBuffer = "Hello There from " + chat.getIdentity() + " :" + Build.VERSION.SDK_INT;

                    chat.write(helloBuffer.getBytes());
                    print_line("", "Wrote message: " + helloBuffer);
            }
        }
    };

    // Declare our UI widgets
    protected Button mStartUpdatesButton;
    protected Button mStopUpdatesButton;
    protected TextView mLongitudeView;
    protected TextView mLatitudeView;
    protected TextView mLastUpdateView;
    protected TextView mSpeedView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Assign the View from the layout file to the corresponding variables
        mStartUpdatesButton = (Button) findViewById(R.id.start_updates_button);
        mStopUpdatesButton = (Button) findViewById(R.id.stop_updates_button);
        mLongitudeView = (TextView) findViewById(R.id.longitudeView);
        mLatitudeView = (TextView) findViewById(R.id.latitudeView);
        mLastUpdateView = (TextView) findViewById(R.id.lastUpdateView);
        mSpeedView = (TextView) findViewById(R.id.speedView);

        mRequestingLocationUpdates = false;

        // Kick off the process of building the GoogleApiClient, LocationRequest, and
        // LocationSettingsRequest objects.
        buildGoogleApiClient();
        createLocationRequest();
        buildLocationSettingRequest();

        // Builds the sensors to retrieve position of device relative to earth's magnetic north pole
        buildSensors();

        mySpeech = new TextSpeech(this);

        Button showIPButton = (Button) findViewById(R.id.button3);
        showIPButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WiFiP2PHelper.printLocalIpAddresses(that);
            }
        });

        Button clearButton = (Button) findViewById(R.id.button2);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((TextView) findViewById(R.id.debugdataBox)).setText("");
            }
        });

        Button toggleButton = (Button) findViewById(R.id.buttonToggle);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceRunning) { // we elect to turn off the WiFi direct process
                    serviceRunning = false;
                    if (mWifiAccessPoint != null) {
                        mWifiAccessPoint.stop();
                        mWifiAccessPoint = null;
                    }

                    if (mWifiServiceSearcher != null) {
                        mWifiServiceSearcher.stop();
                        mWifiServiceSearcher = null;
                    }

                    if (mWifiConnection != null) {
                        mWifiConnection.stop();
                        mWifiConnection = null;
                    }
                    print_line("", "Stopped");
                } else { // Process has been turned on
                    serviceRunning = true;
                    print_line("", "Started");

                    mWifiAccessPoint = new WiFiAccessPoint(that);
                    mWifiAccessPoint.start();

                    mWifiServiceSearcher = new WiFiServiceSearcher(that);
                    mWifiServiceSearcher.start();
                }
            }
        });

        mainReceiver = new MainBroadcastReceiver();
        filter = new IntentFilter();
        filter.addAction(WiFiAccessPoint.DSS_WIFIAP_VALUES);
        filter.addAction(WiFiAccessPoint.DSS_WIFIAP_SERVERADDRESS);
        filter.addAction(WiFiServiceSearcher.DSS_WIFISS_PEERAPINFO);
        filter.addAction(WiFiServiceSearcher.DSS_WIFISS_PEERCOUNT);
        filter.addAction(WiFiServiceSearcher.DSS_WIFISS_VALUES);
        filter.addAction(WiFiConnection.DSS_WIFICON_VALUES);
        filter.addAction(WiFiConnection.DSS_WIFICON_STATUSVAL);
        filter.addAction(WiFiConnection.DSS_WIFICON_SERVERADDRESS);
        filter.addAction(ClientSocketHandler.DSS_CLIENT_VALUES);
        filter.addAction(GroupOwnerSocketHandler.DSS_GROUP_VALUES);

        LocalBroadcastManager.getInstance(this).registerReceiver((mainReceiver), filter);

        try {
            groupSocket = new GroupOwnerSocketHandler(myHandler,
                    Integer.parseInt(SERVICE_PORT_INSTANCE), this);
            groupSocket.start();
            print_line("", "Group socketserver started.");
        } catch (Exception e) {
            print_line("", "groupseocket error, :" + e.toString());
        }
        timeHandler = new Handler();
        mStatusChecker.run();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWifiConnection != null) {
            mWifiConnection.stop();
            mWifiConnection = null;
        }
        if (mWifiAccessPoint != null) {
            mWifiAccessPoint.stop();
            mWifiAccessPoint = null;
        }

        if (mWifiServiceSearcher != null) {
            mWifiServiceSearcher.stop();
            mWifiServiceSearcher = null;
        }

        timeHandler.removeCallbacks(mStatusChecker);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mainReceiver);
    }

    // Prints the time of each process
    public void print_line(String who, String line) {
        timeCounter = 0;
        ((TextView)findViewById(R.id.debugdataBox)).append(who + " : " + line + "\n");
    }

    // Builds Google Api Client
    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleAPiClient");
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    // Creates Location Request
    protected void createLocationRequest() {
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5 * MIN_CHANGE_FOR_UPDATES) // 5 seconds, in milliseconds
                .setFastestInterval(MIN_CHANGE_FOR_UPDATES); // 1 second, in milliseconds
    }

    // Checks if device has a needed location settings
    protected void buildLocationSettingRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    // Check if the device's location settings are adequate for app's needs
    protected void checkLocationSettings() {
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(
                        mGoogleApiClient,
                        mLocationSettingsRequest
                );
        result.setResultCallback(this);
    }

    // Determines for the user's permission to modify location settings
    @Override
    public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
        final Status status = locationSettingsResult.getStatus();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                Log.i(TAG, "All location settings are satisfied.");
                startLocationUpdates();
                break;
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to" +
                        "upgrade location settings ");
                try {
                    // Show the dialog by calling startResolutionForResult(), and check the result
                    // in onActivityResult().
                    status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException e) {
                    Log.i(TAG, "PendingIntent unable to execute request.");
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog " +
                        "not created.");
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i(TAG, "User agreed to make required location settings changes.");
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "User chose not to make required location settings changes.");
                        break;
                }
                break;
        }
    }

    // Requests location updates from the FusedLocationApi.
    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient,
                mLocationRequest,
                this
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                mRequestingLocationUpdates = true;
                setButtonsEnabledState();
            }
        });

    }

    // Removes location updates from the FusedLocationApi.
    protected void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient,
                this
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                mRequestingLocationUpdates = false;
                setButtonsEnabledState();
            }
        });
    }

    /**
     * Handles the Start Updates button and requests start of location updates. Does nothing if
     * updates have already been requested.
     */
    public void startUpdatesButtonHandler(View view) {
        checkLocationSettings();
    }

    /**
     * Handles the Stop Updates button, and requests removal of location updates.
     */
    public void stopUpdatesButtonHandler(View view) {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        stopLocationUpdates();
    }

    /**
     * Disables both buttons when functionality is disabled due to insufficient location settings.
     * Otherwise ensures that only one button is enabled at any time. The Start Updates button is
     * enabled if the user is not requesting location updates. The Stop Updates button is enabled
     * if the user is requesting location updates.
     */
    private void setButtonsEnabledState() {
        if (mRequestingLocationUpdates) {
            mStartUpdatesButton.setEnabled(false);
            mStopUpdatesButton.setEnabled(true);
        } else {
            mStartUpdatesButton.setEnabled(true);
            mStopUpdatesButton.setEnabled(false);
        }
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();


        // Within {@code onPause()}, we pause location updates, but leave the
        // connection to GoogleApiClient intact.  Here, we resume receiving
        // location updates if the user has requested them.
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            startLocationUpdates();
        }
        // Get updates from accelerometer at a constant rate.
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }

        // Don't receive any more updates from either the magnetometer or accelerometer
        mSensorManager.unregisterListener(this);
    }


    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            handleNewLocation(mLastLocation);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        handleNewLocation(location);
    }

    // Retrieves the location values
    public void handleNewLocation(Location location) {
        if (location != null) {
            Log.d(TAG, location.toString());
            mLongitudeView.setText(String.valueOf(location.getLatitude()));
            mLatitudeView.setText(String.valueOf(location.getLongitude()));
            mLastUpdateView.setText(DateFormat.getTimeInstance().format(new Date()));
            String speed = String.valueOf(location.getSpeed()) + " meters/sec";
            mSpeedView.setText(speed);
            Log.d(TAG, String.valueOf(location.getSpeed()));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                // contacts-related task you need to do.
                Log.d(TAG, "Granted Permission");
                Toast.makeText(MainActivity.this, "ACCESS_FINE_LOCATION Accessed", Toast.LENGTH_SHORT)
                        .show();
            }
        } else {
            // Permission Denied
            Log.d(TAG, "Denied Permission");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    // Sensor Manager and two hardware sensors created
    protected void buildSensors() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    // Get readings from accelerometer and magnetometer. To simplify calculations, consider
    // storing these readings as unit vectors. May need to convert to X and Y components for collision
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccelerometerReading = sensorEvent.values;
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagnetometerReading = sensorEvent.values;
        }

        // Calculate orientation
        if (mAccelerometerReading != null && mMagnetometerReading != null) {
            updateOrientationAngles();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // If sensor accuracy changes, do something here. Need to implement callback in code.
    }

    // Compute the three orientation angles based on most recent readings from sensors.
    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles
        boolean success = SensorManager.getRotationMatrix(mRotationMatrix, null,
                mAccelerometerReading, mMagnetometerReading);

        // mRotationMatrix now has up-to-date info.
        if (success) {
            SensorManager.getOrientation(mRotationMatrix, mOrientationAngles);
            // mOrientationAngles now has up-to-date of 3 angles.
            float azimuthInRadians = mOrientationAngles[0];
            // converts from -180 to 180 degrees to 0-360 degrees
            travelDirection = (int) (Math.toDegrees(azimuthInRadians) + 360) % 360;
            Log.i(TAG, travelDirection + " degrees in 360");
        }
    }


    private class MainBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WiFiAccessPoint.DSS_WIFIAP_VALUES.equals(action)) {
                String s = intent.getStringExtra(WiFiAccessPoint.DSS_WIFIAP_MESSAGE);
                print_line("AP", s);

            } else if (WiFiAccessPoint.DSS_WIFIAP_SERVERADDRESS.equals(action)) {
                InetAddress address = (InetAddress)intent.getSerializableExtra(WiFiAccessPoint.DSS_WIFIAP_INETADDRESS);
                print_line("AP", "inet address" + address.getHostAddress());

            } else if (WiFiServiceSearcher.DSS_WIFISS_VALUES.equals(action)) {
                String s = intent.getStringExtra(WiFiServiceSearcher.DSS_WIFISS_MESSAGE);
                print_line("SS", s);

            } else if (WiFiServiceSearcher.DSS_WIFISS_PEERCOUNT.equals(action)) {
                int s = intent.getIntExtra(WiFiServiceSearcher.DSS_WIFISS_COUNT, -1);
                print_line("SS", "found " + s + " peers");
                mySpeech.speak(s+ " peers discovered.");

            } else if (WiFiServiceSearcher.DSS_WIFISS_PEERAPINFO.equals(action)) {
                String s = intent.getStringExtra(WiFiServiceSearcher.DSS_WIFISS_INFOTEXT);

                String[] separated = s.split(":");
                print_line("SS", "found SSID:" + separated[1] + ", pwd:"  + separated[2]+ "IP: " + separated[3]);

                if(mWifiConnection == null) {
                    if(mWifiAccessPoint != null){
                        mWifiAccessPoint.stop();
                        mWifiAccessPoint = null;
                    }
                    if(mWifiServiceSearcher != null){
                        mWifiServiceSearcher.stop();
                        mWifiServiceSearcher = null;
                    }

                    final String networkSSID = separated[1];
                    final String networkPass = separated[2];
                    final String ipAddress = separated[3];

                    mWifiConnection = new WiFiConnection(that,networkSSID,networkPass);
                    mWifiConnection.setInetAddress(ipAddress);
                    mySpeech.speak("found accesspoint");
                }
            } else if (WiFiConnection.DSS_WIFICON_VALUES.equals(action)) {
                String s = intent.getStringExtra(WiFiConnection.DSS_WIFICON_MESSAGE);
                print_line("CON", s);

            } else if (WiFiConnection.DSS_WIFICON_SERVERADDRESS.equals(action)) {
                int addr = intent.getIntExtra(WiFiConnection.DSS_WIFICON_INETADDRESS, -1);
                print_line("COM", "IP" + Formatter.formatIpAddress(addr));

                if (clientSocket == null &&  mWifiConnection != null) {
                    String IpToConnect = mWifiConnection.getInetAddress();
                    print_line("","Starting client socket connection to : " + IpToConnect);
                    clientSocket = new ClientSocketHandler(myHandler,IpToConnect, Integer.parseInt(CLIENT_PORT_INSTANCE), that);
                    clientSocket.start();
                }
            } else if (WiFiConnection.DSS_WIFICON_STATUSVAL.equals(action)) {
                int status = intent.getIntExtra(WiFiConnection.DSS_WIFICON_CONSTATUS, -1);

                String conStatus = "";
                if(status == WiFiConnection.CONNECTION_STATE_NONE) {
                    conStatus = "NONE";
                }else if(status == WiFiConnection.CONNECTION_STATE_PRECONNECTION) {
                    conStatus = "PreConnecting";
                }else if(status == WiFiConnection.CONNECTION_STATE_CONNECTING) {
                    conStatus = "Connecting";
                    mySpeech.speak("Accesspoint connected");
                }else if(status == WiFiConnection.CONNECTION_STATE_CONNECTED) {
                    conStatus = "Connected";
                }else if(status == WiFiConnection.CONNECTION_STATE_DISCONNECTED) {
                    conStatus = "Disconnected";
                    mySpeech.speak("Accesspoint Disconnected");
                    if(mWifiConnection != null) {
                        mWifiConnection.stop();
                        mWifiConnection = null;
                        // should stop
                        clientSocket = null;
                    }
                    // make sure services are re-started
                    if(mWifiAccessPoint != null){
                        mWifiAccessPoint.stop();
                        mWifiAccessPoint = null;
                    }
                    mWifiAccessPoint = new WiFiAccessPoint(that);
                    mWifiAccessPoint.start();

                    if(mWifiServiceSearcher != null){
                        mWifiServiceSearcher.stop();
                        mWifiServiceSearcher = null;
                    }

                    mWifiServiceSearcher = new WiFiServiceSearcher(that);
                    mWifiServiceSearcher.start();
                }

                print_line("COM", "Status " + conStatus);
            }else if (ClientSocketHandler.DSS_CLIENT_VALUES.equals(action)) {
                String s = intent.getStringExtra(ClientSocketHandler.DSS_CLIENT_MESSAGE);
                print_line("Client", s);

            }else if (GroupOwnerSocketHandler.DSS_GROUP_VALUES.equals(action)) {
                String s = intent.getStringExtra(GroupOwnerSocketHandler.DSS_GROUP_MESSAGE);
                print_line("Group", s);

            }
        }
    }
}
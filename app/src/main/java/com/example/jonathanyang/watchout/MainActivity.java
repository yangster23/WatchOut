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
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import android.text.format.Time;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        ResultCallback<LocationSettingsResult>,
        SensorEventListener,
        WifiP2pManager.ChannelListener { // Main class for application

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

    // Represents a geographical location.
    protected Location mCurrentLocation;

    /**
     * Time when the location was updated represented as a String.
     */
    protected String mLastUpdateTime;

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

    public static final String SERVICE_TYPE = "_wdm_p2p._tcp";
    public static final String SERVICE_INSTANCE = "P2P_test_Nr2";

    protected MainActivity that = this;

    protected TextSpeech mySpeech = null;

    protected MainBroadcastReceiver mainReceiver;
    private IntentFilter filter;

    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;

    private int mInterval = 1000; // 1 second for counter interval, can be changed
    private Handler timeHandler;
    private int timeCounter = 0;
    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            // call function to update timer
            timeCounter = timeCounter + 1;
            ((TextView) findViewById(R.id.TimeBox)).setText("T: " + timeCounter);
            timeHandler.postDelayed(mStatusChecker, mInterval);
        }
    };

    boolean serviceRunning = false;
    WiFiServiceAdvertiser mWiFiServiceAdvertiser = null;
    WiFiServiceSearcher mWiFiServiceSearcher = null;
    WiFiServiceConnection mWiFiServiceConnection = null;

    List<WiFiServiceSearcher.ServiceItem> connectedList =
            new ArrayList<WiFiServiceSearcher.ServiceItem>();

    enum LastConnectionRole {
        NONE,
        GroupOwner,
        Client
    }

    long mPeersDiscovered = 0;
    long ServiceDiscovered = 0;
    long tConnected = 0;

    LastConnectionRole mLastConnectionRole = LastConnectionRole.NONE;
    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;

    // Ports can be dynamic
    public int CLIENT_PORT_INSTANCE = 4545;
    public int SERVICE_PORT_INSTANCE = 4545;

    GroupOwnerSocketHandler groupSocket = null;
    ClientSocketHandler clientSocket = null;
    ChatManager chat = null;
    Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg){

            switch (msg.what) {
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.d(TAG, "MESSAGE READ " + readMessage);
                    String[] separated = readMessage.split(",");

                    // Assign other party's collision information here.
                    collisionLongitudeTwo = Double.parseDouble(separated[0]);
                    collisionLatitudeTwo = Double.parseDouble(separated[1]);
                    collisionSpeedTwo = Double.parseDouble(separated[2]);
                    travelDirectionTwo = Integer.parseInt(separated[3]);
                    print_line("CHAT MESSAGE FOR COLLISION", readMessage);
                    break;

                case MY_HANDLE:
                    Object obj = msg.obj;
                    chat = (ChatManager) obj;
                    // Sends the buffer with collision Object information.
                    String helloBuffer = collisionLongitude + "," + collisionLatitude + ","
                            + collisionSpeed + "," + travelDirection;
                    Log.d(TAG, "SENT BUFFER WITH INFO: " + helloBuffer);
                    chat.write(helloBuffer.getBytes());
            }
        }
    };

    // Determines if there is a collision
    protected CollisionDetection collisionDetector;
    // Collision related information for our device
    protected CollisionObject collisionObject;
    protected double collisionLongitude;
    protected double collisionLatitude;
    protected double collisionSpeed;
    // Gets the orientation of the phone to determine bearings in 0 to 360 degrees
    protected int travelDirection;

    // Collision related information for the other device
    protected CollisionObject collisionObjectTwo;
    protected double collisionLongitudeTwo;
    protected double collisionLatitudeTwo;
    protected double collisionSpeedTwo;
    protected int travelDirectionTwo;

    // Declare our UI widgets
    protected Button mStartUpdatesButton;
    protected Button mStopUpdatesButton;
    protected TextView mLongitudeView;
    protected TextView mLatitudeView;
    protected TextView mLastUpdateView;
    protected TextView mSpeedView;
    protected TextView mTravelDirectionView;

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
        mTravelDirectionView = (TextView) findViewById(R.id.travelDirectionView);

        mRequestingLocationUpdates = false;
        mLastUpdateTime = "";

        // Kick off the process of building the GoogleApiClient, LocationRequest, and
        // LocationSettingsRequest objects.
        buildGoogleApiClient();
        createLocationRequest();
        buildLocationSettingRequest();

        // Builds the sensors to retrieve position of device relative to earth's magnetic north pole
        buildSensors();

        mySpeech = new TextSpeech(this);
        p2p = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        if (p2p == null) {
            print_line("", "This device does not support Wi-Fi Direct");
        } else {
            channel = p2p.initialize(this, getMainLooper(), this);

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
                    if (serviceRunning) {
                        clearAllService();
                    } else {
                        restartService();
                    }
                }
            });

            mainReceiver = new MainBroadcastReceiver();
            filter = new IntentFilter();
            filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            filter.addAction(WiFiServiceAdvertiser.DSS_WIFISA_VALUES);
            filter.addAction(WiFiServiceSearcher.DSS_WIFISS_PEERAPINFO);
            filter.addAction(WiFiServiceSearcher.DSS_WIFISS_PEERCOUNT);
            filter.addAction(WiFiServiceSearcher.DSS_WIFISS_VALUES);
            filter.addAction(WiFiServiceConnection.DSS_WIFICON_VALUES);
            filter.addAction(WiFiServiceConnection.DSS_WIFICON_CONINFO);

            LocalBroadcastManager.getInstance(this).registerReceiver((mainReceiver), filter);

            try {
                groupSocket = new GroupOwnerSocketHandler(myHandler, SERVICE_PORT_INSTANCE, this);
                groupSocket.start();
                print_line("", "Group socket server started.");
            } catch (Exception e) {
                print_line("", "Group socket error, :" + e.toString());
            }
            timeHandler = new Handler();
            mStatusChecker.run();
            collisionDetector = new CollisionDetection();
        }
    }

    public void onDestroy() {

        timeHandler.removeCallbacks(mStatusChecker);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mainReceiver);
        clearAllService();
    }


    private void clearAllService() {

        if (mWiFiServiceAdvertiser != null) {
            mWiFiServiceAdvertiser.stop();
            mWiFiServiceAdvertiser = null;
        }

        if (mWiFiServiceSearcher != null) {
            mWiFiServiceSearcher.stop();
            mWiFiServiceSearcher = null;
        }

        if (mWiFiServiceConnection != null) {
            mWiFiServiceConnection.stop();
            mWiFiServiceConnection = null;
        }
        serviceRunning = false;
        print_line("", "Stopped");
    }

    private void restartService() {
        // just to be sure, all previous services are cleared
        clearAllService();

        //we need this for listening incoming connection already now
        mWiFiServiceConnection = new WiFiServiceConnection(that, p2p, channel);
        mWiFiServiceConnection.start();

        mWiFiServiceAdvertiser = new WiFiServiceAdvertiser(that, p2p, channel);
        mWiFiServiceAdvertiser.start(SERVICE_INSTANCE);

        mWiFiServiceSearcher = new WiFiServiceSearcher(that, p2p, channel);
        mWiFiServiceSearcher.start();

        serviceRunning = true;
        print_line("", "Service started");
    }

    @Override
    public void onChannelDisconnected() {
        // Nothing to do here
    }

    // Prints the time of each process
    public void print_line(String who, String line) {
        timeCounter = 0;
        ((TextView) findViewById(R.id.debugdataBox)).append(who + " : " + line + "\n");
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
    public void onResult(LocationSettingsResult locationSettingsResult) {
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
            public void onResult(Status status) {
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
    public void onConnected(Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            handleNewLocation();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        handleNewLocation();
    }

    // Retrieves the location values
    public void handleNewLocation() {
        if (mCurrentLocation != null) {
            //Log.d(TAG, location.toString());
            collisionLongitude = mCurrentLocation.getLongitude();
            collisionLatitude = mCurrentLocation.getLatitude();
            collisionSpeed = mCurrentLocation.getSpeed();
            mLatitudeView.setText(String.valueOf(collisionLatitude));
            mLongitudeView.setText(String.valueOf(collisionLongitude));
            mLastUpdateView.setText(mLastUpdateTime);
            String speed = String.valueOf(collisionSpeed) + " meters/sec";
            mSpeedView.setText(speed);
            // Log.d(TAG, String.valueOf(collisionSpeed));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
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
    public void onConnectionFailed(ConnectionResult connectionResult) {
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

    // Get readings from accelerometer and magnetometer.
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        handleNewTravelDirection(sensorEvent);
        collisionObject = new CollisionObject(collisionLongitude, collisionLatitude,
                collisionSpeed, travelDirection);
        collisionObjectTwo = new CollisionObject(collisionLongitudeTwo, collisionLatitudeTwo,
                collisionSpeedTwo, travelDirectionTwo);
        // Log.d(TAG, collisionLongitude + " " + " " + collisionLatitude + " " + collisionSpeed + " " + travelDirection);
        // Sends alert for collision
        if (collisionDetector.checkCollision(collisionObject, collisionObjectTwo))
            collisionAlert();
    }

    public void handleNewTravelDirection(SensorEvent sensorEvent) {
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
            //Log.i(TAG, travelDirection + " degrees in 360");
            mTravelDirectionView.setText(String.valueOf(travelDirection));
        }
    }

    // Creates a visual and auditory alert;
    public void collisionAlert() {
        Toast.makeText(MainActivity.this, "COLLISION ALERT: LOOK UP!", Toast.LENGTH_LONG).show();
        mySpeech.speak("COLLISION ALERT: LOOK UP!");
    }

    private WiFiServiceSearcher.ServiceItem SelectServiceToConnect(List<WiFiServiceSearcher.ServiceItem> available) {

        WiFiServiceSearcher.ServiceItem ret = null;

        if (connectedList.size() > 0 && available.size() > 0) {

            int firstNewMatch = -1;
            int firstOldMatch = -1;

            for (int i = 0; i < available.size(); i++) {
                if (firstNewMatch >= 0) {
                    break;
                }
                for (int ii = 0; ii < connectedList.size(); ii++) {
                    if (available.get(i).deviceAddress.equals(connectedList.get(ii).deviceAddress)) {
                        if (firstOldMatch < 0 || firstOldMatch > ii) {
                            //find oldest one available that we have connected previously
                            firstOldMatch = ii;
                        }
                        firstNewMatch = -1;
                        break;
                    } else {
                        if (firstNewMatch < 0) {
                            firstNewMatch = i; // select first not connected device
                        }
                    }
                }
            }

            if (firstNewMatch >= 0) {
                ret = available.get(firstNewMatch);
            } else if (firstOldMatch >= 0) {
                ret = connectedList.get(firstOldMatch);
                // we move this to last position
                connectedList.remove(firstOldMatch);
            }

            //print_line("EEE", "firstNewMatch " + firstNewMatch + ", firstOldMatch: " + firstOldMatch);

        } else if (available.size() > 0) {
            ret = available.get(0);
        }
        if (ret != null) {
            connectedList.add(ret);

            // just to set upper limit for the amount of remembered contacts
            // when we have 101, we remove the oldest (that's the top one)
            // from the array
            if (connectedList.size() > 100) {
                connectedList.remove(0);
            }
        }

        return ret;
    }


    private class MainBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    // we got wifi back, so we can re-start now
                    restartService();
                } else {
                    // no wifi available -> stop all services.
                    clearAllService();
                }
            } else if (WiFiServiceAdvertiser.DSS_WIFISA_VALUES.equals(action)) {
                String s = intent.getStringExtra(WiFiServiceAdvertiser.DSS_WIFISA_MESSAGE);
                print_line("SA", s);

            } else if (WiFiServiceSearcher.DSS_WIFISS_VALUES.equals(action)) {
                String s = intent.getStringExtra(WiFiServiceSearcher.DSS_WIFISS_MESSAGE);
                print_line("SS", s);

            } else if (WiFiServiceConnection.DSS_WIFICON_VALUES.equals(action)) {
                String s = intent.getStringExtra(WiFiServiceConnection.DSS_WIFICON_MESSAGE);
                print_line("CON", s);

            } else if (WiFiServiceSearcher.DSS_WIFISS_PEERCOUNT.equals(action)) {
                int s = intent.getIntExtra(WiFiServiceSearcher.DSS_WIFISS_COUNT, -1);
                print_line("SS", "found " + s + " peers");
                mySpeech.speak(s + " peers discovered.");
                mPeersDiscovered = System.currentTimeMillis();

            } else if (WiFiServiceSearcher.DSS_WIFISS_PEERAPINFO.equals(action)) {
                int s = intent.getIntExtra(WiFiServiceSearcher.DSS_WIFISS_SERVICECNT, -1);

                print_line("SS", "Services found: " + s);
                List<WiFiServiceSearcher.ServiceItem> service = mWiFiServiceSearcher.getServiceList();
                // Select service, save it in a list and start connection with it
                // and do remember to cancel Searching

                if (service.size() > 0) {
                    ServiceDiscovered = System.currentTimeMillis();

                    if (mWiFiServiceConnection == null) {
                        mWiFiServiceConnection = new WiFiServiceConnection(that, p2p, channel);
                        mWiFiServiceConnection.start();
                    }
                    WiFiServiceSearcher.ServiceItem selectItem = SelectServiceToConnect(service);
                    if (selectItem != null) {
                        mWiFiServiceConnection.connect(selectItem);
                        if (mWiFiServiceSearcher != null) {
                            mWiFiServiceSearcher.stop();
                            mWiFiServiceSearcher = null;
                        }
                    } else {
                        // we'll get discovery stopped event soon enough
                        // and it starts the discovery again, so no worries :)
                        print_line("", "No devices selected");
                    }
                }
            } else if (WiFiServiceConnection.DSS_WIFICON_CONINFO.equals(action)) {

                if (mWiFiServiceConnection != null) {
                    WifiP2pInfo pInfo = mWiFiServiceConnection.getConnectionInfo();
                    if (pInfo != null) {
                        tConnected = System.currentTimeMillis();
                        //in-case we did not initiate the connection,
                        // then we are indeed still having discovery on
                        if (mWiFiServiceSearcher != null) {
                            mWiFiServiceSearcher.stop();
                            mWiFiServiceSearcher = null;
                        }

                        String speakout = "";
                        if (pInfo.isGroupOwner) {
                            speakout = "Connected as Group owner.";
                            mLastConnectionRole = LastConnectionRole.GroupOwner;
                        } else {
                            mLastConnectionRole = LastConnectionRole.Client;
                            // as we are client, we can not have more connections,
                            // thus we need to cancel advertising.
                            if (mWiFiServiceAdvertiser != null) {
                                mWiFiServiceAdvertiser.stop();
                                mWiFiServiceAdvertiser = null;
                            }

                            // Client socket connects here.
                            speakout = "Connected as Client, Group IP:" + pInfo.groupOwnerAddress.getHostAddress();
                            clientSocket = new ClientSocketHandler(myHandler, pInfo.groupOwnerAddress,
                                    CLIENT_PORT_INSTANCE, that);
                            clientSocket.start();
                        }

                        mySpeech.speak(speakout);
                        print_line("CON", speakout);

                    } else {
                        //we'll get this when we have disconnection event
                        print_line("CON", "WifiP2pInfo is null, restarting all.");
                        restartService();
                    }
                }

            }
        }
    }
}
package com.example.jonathanyang.watchout;

import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements LocationProvider.LocationCallback { // Main class for application

    public static final String TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 123;
    private LocationProvider mLocationProvider;
    private String speed;
    // Declare our View Variables
    private TextView mLongitudeView;
    private TextView mLatitudeView;
    private TextView mLastUpdateView;
    private TextView mSpeedView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLocationProvider = new LocationProvider(this, this);

        // Assign the View from the layout file to the corresponding variables
        mLongitudeView = (TextView) findViewById(R.id.longitudeView);
        mLatitudeView = (TextView) findViewById(R.id.latitudeView);
        mLastUpdateView = (TextView) findViewById(R.id.lastUpdateView);
        mSpeedView = (TextView) findViewById(R.id.speedView);

    }

    @Override
    protected void onResume() {
        super.onResume();
        mLocationProvider.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocationProvider.disconnect();
    }

    public void handleNewLocation(Location location) {
        Log.d(TAG, location.toString());
        mLongitudeView.setText(String.valueOf(location.getLatitude()));
        mLatitudeView.setText(String.valueOf(location.getLongitude()));
        mLastUpdateView.setText(DateFormat.getTimeInstance().format(new Date()));
        speed = String.valueOf(location.getSpeed()) + " meters/sec";
        mSpeedView.setText(speed);
        Log.d(TAG, String.valueOf(location.getSpeed()));
    }

    // In case location.getSpeed() results in null
    private static long calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        long distanceInMeters = Math.round(6371000 * c);
        return distanceInMeters;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                // contacts-related task you need to do.
                Log.d(TAG, "Granted Permission");
                Toast.makeText(MainActivity.this, "ACCESS_FINE_LOCATION Accessed", Toast.LENGTH_SHORT)
                        .show();
            }
        }
        else {
            // Permission Denied
            Log.d(TAG, "Denied Permission");
        }
    }
}
package com.example.jonathanyang.watchout;


import android.location.Location;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;

/* Main Page of the Application.
 */

public class MainActivity extends AppCompatActivity implements LocationProvider.LocationCallback { // Main class for application

    public static final String TAG = MainActivity.class.getSimpleName();
    private LocationProvider mLocationProvider;
    // Declare our View Variables
    private TextView mLongitudeView;
    private TextView mLatitudeView;
    private TextView mLastUpdateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocationProvider = new LocationProvider(this, this);

        // Assign the View from the layout file to the corresponding variables
        mLongitudeView = (TextView) findViewById(R.id.longitudeView);
        mLatitudeView = (TextView) findViewById(R.id.latitudeView);
        mLastUpdateView = (TextView) findViewById(R.id.lastUpdateView);
    }

    public void handleNewLocation(Location location) {
        Log.d(TAG, location.toString());
        mLongitudeView.setText(String.valueOf(location.getLatitude()));
        mLatitudeView.setText(String.valueOf(location.getLongitude()));
        mLastUpdateView.setText(DateFormat.getTimeInstance().format(new Date()));
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

}
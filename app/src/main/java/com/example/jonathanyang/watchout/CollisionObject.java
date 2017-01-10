package com.example.jonathanyang.watchout;

import android.util.Log;

/**
 * The collision object which will take the location, travel direction, and speed data from the
 * device to built itself. It is represented as a circle for approximation.
 */

public class CollisionObject {
    private final static double QUARTER_PI = Math.PI / 4.0; // Formula for quarter PI

    public double x0; // x position will be longitude converted to x coordinate using mercator projection
    public double y0; // y position will be latitude converted to y coordinate using mercator projection
    public double xt; // velocity vector in x direction will be created based on speed * cos(angle).
    public double yt; // velocity speed in y direction will be created based on speed * sin(angle).

    // This is a fixed radius for each object which we will set right now as 2 meter (~6 feet),
    // but may be changed if it is too big for a radius or too small.
    public double radius = 2.0;

    /* longitude will be in degrees and latitude will be in degrees, speed is in meters/second, and
    direction angle or travel direction will be from 0-360 degrees
     */
    public CollisionObject(double longitude, double latitude, double speed, double directionAngle) {
        // is this necessary at all to convert the longitude and latitude;
        x0 = longitude * Math.PI / 180;
       // Log.d(MainActivity.TAG, "LONGITUDE: " + x0 + " " + longitude);
        y0 = Math.log(Math.tan(QUARTER_PI + 0.5 * (latitude * Math.PI / 180)));
        //Log.d(MainActivity.TAG, "LATITUDE:" + y0 + " " + latitude);
        xt = speed * Math.cos(directionAngle);
        yt = speed * Math.sin(directionAngle);
        //Log.d(MainActivity.TAG, "XVECTOR: " + xt + " YVECTOR: " + yt);
    }
}

/* alternate method to get x and y can be
private static final int    EARTH_RADIUS    = 6371; This is km, should it be in m?
latitude = latitude * Math.PI / 180;
longitude = longitude * Math.PI / 180;
double x = EARTH_RADIUS * Math.sin(latitude) * Math.cos(longitude);
double y = EARTH_RADIUS * Math.sin(latitude) * Math.sin(longitude);
*/
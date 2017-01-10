package com.example.jonathanyang.watchout;

import android.util.Log;

/**
 * To perform the Collision Detection Algorithm, we are given the Collision Objects which are
 * represented as circle for an accurate and efficient approximation. If the minDistance > radius
 * of first Object + radius of second Object, then no need for alert, however, if it is < or =, then
 * there will be a collision within minTime, so you need to alert immediately.
 */

public class CollisionDetection {
    // minimum time is the time in seconds where the two objects will be closest.
    public double minTime;
    // if time of minTime is positive, then you can determine the time of minimum distance and how close it is.
    public double minDistance;


    public CollisionDetection() {
        minTime = 0.0;
        minDistance = 0.0;
    }

    // If true, then there is a collision
    public boolean checkCollision(CollisionObject a, CollisionObject b) {
        minTime = -(a.x0 * a.xt - a.xt * b.x0 - (a.x0 - b.x0) * b.xt + a.y0 * a.yt - a.yt * b.y0
                - (a.y0 - b.y0) * b.yt) / (Math.pow(a.xt, 2) - 2 * a.xt * b.xt + Math.pow(b.xt, 2)
                + Math.pow(a.yt, 2) - 2 * a.yt * b.yt + Math.pow(b.yt, 2));
        minDistance = Math.sqrt(
                Math.pow(minTime * a.xt - minTime * b.xt + a.x0 - b.x0, 2) +
                        Math.pow(minTime * a.yt - minTime * b.yt + a.y0 - b.y0, 2)
        );

        if (minTime <= 0) {
            //Log.d(MainActivity.TAG, "Will never collide (diverging) minimum");
            return false;
        } else {
            if (minDistance <= a.radius + b.radius) {
                //Log.d(MainActivity.TAG, "Will collide");
                return true;
            } else {
                //Log.d(MainActivity.TAG, "Will not collide, but reach minimum distance in");
                return false;
            }
        }
    }
}

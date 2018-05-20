package com.example.carramba;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

class LocationService {
    private static final String TAG = "CARRAMBA: LctnService";
    private Location mLocation;
    private Context mContext;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private LocationRequest mLocationRequest;


    LocationService(final Context mContext) {
        this.mContext = mContext;

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(500);
        updateLocation();

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.d(TAG, "onLocationResult: location result null");
                    return;

                }

                mLocation = locationResult.getLastLocation();
                Log.d(TAG, "onLocationResult: new location " + getLatitude() + " " + getLongitude());
                sendLocationBroadcast();
                //sendErrorBroadcast(getLatitude() + " " + getLongitude());
            }


        };

        startLocationUpdates();
    }

    private void sendLocationBroadcast() {

        Intent locationIntent = new Intent("location");
        String longitude = String.valueOf(getLongitude());
        String latitude = String.valueOf(getLatitude());

        locationIntent.putExtra("location", longitude + " " + latitude);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(locationIntent);

    }

    public void updateLocation() {
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener((Activity) mContext, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            Log.d(TAG, "onSuccess: Location update");
                            mLocation = location;
                            sendLocationBroadcast();

                        } else {
                            Log.d(TAG, "onSuccess: Location null");
                        }

                    }
                });
    }

    String getLongitude() {
        if (mLocation != null) {
            return String.valueOf(mLocation.getLongitude());
        }
        return "0.0";
    }

    String getLatitude() {
        if (mLocation != null) {
            return String.valueOf(mLocation.getLatitude());
        }
        return "0.0";
    }

    private void startLocationUpdates() {
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                null);

    }

    private void sendErrorBroadcast(String errorMessage) {
        Intent errorIntent = new Intent("error");
        errorIntent.putExtra("error", errorMessage);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(errorIntent);

    }


}


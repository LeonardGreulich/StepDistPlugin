package cordova.plugin.stepdist;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import greulich.leonard.stepdist.MainActivity;
import greulich.leonard.stepdist.R;

public class DistanceService extends Service implements LocationListener, StepCounter.StepCounterDelegate {

    private final IBinder mBinder = new LocalBinder();

    private LocationManager locationManager;
    private StepCounter stepCounter;
    private SharedPreferences preferences;
    private DistanceServiceDelegate delegate;

    private List<Location> locationEvents;

    private int distanceFilter;
    private double accuracyFilter;
    private double locationsSequenceDistanceFilter;

    private float stepLength;
    private float calibrationCandidateDistance;
    private int distanceTraveledPersistent;
    private int distanceTraveledProvisional;
    private int stepsTakenPersistent;
    private int stepsTakenProvisional;
    private long lastCalibrated;
    private boolean calibrationInProgress;
    private boolean isTracking;

    @Override
    public IBinder onBind(Intent intent) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        distanceFilter = intent.getIntExtra("distanceFilter", 0);
        accuracyFilter = intent.getDoubleExtra("accuracyFilter", 0);
        locationsSequenceDistanceFilter = intent.getDoubleExtra("locationsSequenceDistanceFilter", 0);

        Notification notification = new NotificationCompat.Builder(this, "stepDistServiceChannel")
                .setContentTitle("Distance Service")
                .setContentText("The service is used to measure the traveled distance in the background.")
                .setSmallIcon(R.drawable.ic_android)
                .setContentIntent(pendingIntent)
                .build();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        stepCounter = new StepCounter(getApplicationContext());
        stepCounter.setDelegate(this);

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, distanceFilter, this);
        } catch (SecurityException securityException) {
            securityException.printStackTrace();
        }

        preferences = getSharedPreferences("sharedPreferences", Context.MODE_PRIVATE);
        isTracking = false;
        loadStepLength();

        startForeground(1, notification);

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        locationManager.removeUpdates(this);
        return super.onUnbind(intent);
    }

    public void startMeasuringDistance() {
        locationEvents = new ArrayList<>();
        distanceTraveledPersistent = 0;
        distanceTraveledProvisional = 0;
        stepsTakenPersistent = 0;
        stepsTakenProvisional = 0;
        calibrationInProgress = false;
        calibrationCandidateDistance = 0;

        stepCounter.startStepCounting();

        isTracking = true;
    }

    public void stopMeasuringDistance() {
        stepCounter.stopStepCounting();

        isTracking = false;
    }

    @Override
    public void onLocationChanged(Location location) {
        sendPluginInfo(location.getAccuracy());

        if (isTracking) {
            processLocationEvent(location);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public void setDelegate(DistanceServiceDelegate distanceServiceDelegate) {
        delegate = distanceServiceDelegate;
    }

    @Override
    public void stepCountDidChange(int count) {
        stepsTakenProvisional = count-stepsTakenPersistent;
        distanceTraveledProvisional = Math.round(stepsTakenProvisional*stepLength);

        delegate.distanceDidChange(distanceTraveledPersistent+distanceTraveledProvisional, stepsTakenPersistent+stepsTakenProvisional);
    }

    private void processLocationEvent(Location location) {
        if (locationEvents.size() >= 3) {
            calibrationCandidateDistance = calculateCumulativeDistance(locationEvents.subList(1, locationEvents.size()));
            if (calibrationCandidateDistance >= locationsSequenceDistanceFilter) {
                calibrationInProgress = true;
                int calibrationCandidateSteps = stepCounter.getStepsBetween(new Date(locationEvents.get(0).getTime()), new Date(locationEvents.get(locationEvents.size()-1).getTime()));
                saveStepLength(calibrationCandidateDistance/calibrationCandidateSteps);
                sendPluginInfo();
            } else if (calibrationInProgress) {
                calibrationInProgress = false;
                stepsTakenPersistent += stepsTakenProvisional;
                distanceTraveledPersistent += stepsTakenProvisional*stepLength;
            }
        }

        if (location.getAccuracy() <= accuracyFilter) {
            locationEvents.add(location);
        } else {
            locationEvents.clear();
            calibrationCandidateDistance = 0;
            sendPluginInfo("Calibr. cancel.: Accuracy (" + String.valueOf(location.getAccuracy()) + ")");
        }
    }

    private float calculateCumulativeDistance(List<Location> locations) {
        Location lastLocation = null;
        float cumulativeDistance = 0;

        for (Location location : locations) {
            if (lastLocation != null) {
                cumulativeDistance += lastLocation.distanceTo(location);
            }
            lastLocation = location;
        }

        return cumulativeDistance;
    }

    public class LocalBinder extends Binder {
        public DistanceService getService(){
            return DistanceService.this;
        }
    }

    public void sendPluginInfo(float accuracy, String debugInfo) {
        boolean isReadyToStart = false;

        // No need to round accuracy on Android
        if (accuracy <= accuracyFilter || stepLength != 0.0) {
            isReadyToStart = true;
        }

        delegate.pluginInfoDidChange(isReadyToStart, debugInfo, lastCalibrated, stepLength);
    }

    public void sendPluginInfo() {
        sendPluginInfo(9999, "");
    }

    public void sendPluginInfo(String debugInfo) {
        sendPluginInfo(9999, debugInfo);
    }

    public void sendPluginInfo(float accuracy) {
        sendPluginInfo(accuracy, "");
    }

    private void loadStepLength() {
        stepLength = preferences.getFloat("stepLength", 0);
        lastCalibrated = preferences.getLong("lastCalibrated", 0);
    }

    private void saveStepLength(float stepLength) {
        this.stepLength = stepLength;
        this.lastCalibrated = new Date().getTime()/1000;

        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat("stepLength", stepLength);
        editor.putLong("lastCalibrated", lastCalibrated);
        editor.apply();
    }

    public interface DistanceServiceDelegate {
        void distanceDidChange(int distanceTraveled, int stepsTaken);
        void pluginInfoDidChange(boolean isReadyToStart, String debugInfo, long lastCalibrated, float stepLength);
    }

}

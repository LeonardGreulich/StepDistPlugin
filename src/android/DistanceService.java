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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DistanceService extends Service implements LocationListener, StepCounter.StepCounterDelegate {

    private final IBinder mBinder = new LocalBinder();

    private LocationManager locationManager;
    private StepCounter stepCounter;
    private SharedPreferences preferences;
    private DistanceServiceDelegate delegate;

    private List<Location> locationEvents;

    private int horizontalDistanceFilter;
    private double horizontalAccuracyFilter;
    private int verticalDistanceFilter;
    private double verticalAccuracyFilter;
    private double distanceTraveledToCalibrate;

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
        Intent notificationIntent = new Intent(this, getMainActivity());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        horizontalDistanceFilter = intent.getIntExtra("horizontalDistanceFilter", 0);
        horizontalAccuracyFilter = intent.getDoubleExtra("horizontalAccuracyFilter", 0);
        verticalDistanceFilter = intent.getIntExtra("verticalDistanceFilter", 0);
        verticalAccuracyFilter = intent.getDoubleExtra("verticalAccuracyFilter", 0);
        distanceTraveledToCalibrate = intent.getDoubleExtra("distanceTraveledToCalibrate", 0);

        JSONObject stepCounterOptions = new JSONObject();
        try {
            stepCounterOptions.put("updateInterval", intent.getDoubleExtra("updateInterval", 0));
            stepCounterOptions.put("betterFragmentFactor", intent.getDoubleExtra("betterFragmentFactor", 0));
            stepCounterOptions.put("deviationLength", intent.getDoubleExtra("deviationLength", 0));
            stepCounterOptions.put("deviationAmplitude", intent.getDoubleExtra("deviationAmplitude", 0));
            stepCounterOptions.put("smoothingTimeframe", intent.getIntExtra("smoothingTimeframe", 0));
        } catch (JSONException e) {
            System.out.println("Error stepCounterOptions");
        }

        try {
            stepCounter = new StepCounter(getApplicationContext(), stepCounterOptions);
            stepCounter.setDelegate(this);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, horizontalDistanceFilter, this);
        } catch (SecurityException securityException) {
            securityException.printStackTrace();
        }

        preferences = getSharedPreferences("sharedPreferences", Context.MODE_PRIVATE);
        isTracking = false;
        loadStepLength();


        Notification notification = new NotificationCompat.Builder(this, "stepDistServiceChannel")
                .setContentTitle("Distance Service")
                .setContentText("The service is used to measure the traveled distance in the background.")
                .setSmallIcon(getApplicationInfo().icon)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        locationManager.removeUpdates(this);
        return super.onUnbind(intent);
    }

    private Class getMainActivity() {
        Context context = getApplicationContext();
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();

        try {
            return Class.forName(className);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
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
            if (calibrationCandidateDistance >= distanceTraveledToCalibrate) {
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

        if (location.getAccuracy() <= horizontalAccuracyFilter) {
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

    public void sendPluginInfo(double accuracy, String debugInfo) {
        boolean isReadyToStart = false;

        // No need to round accuracy on Android
        if (accuracy <= horizontalAccuracyFilter || stepLength != 0.0) {
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

    public void sendPluginInfo(double accuracy) {
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

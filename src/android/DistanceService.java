//
// DistanceService.java
//
// Created by Leonard Greulich on 2/16/19.
// Copyright © 2019 Leonard Greulich. All rights reserved.
//
// Contains main distance estimation class.
// Initializes the GNSS service as well as the step counting service and processes results of both.
//

package cordova.plugin.stepdist;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

public class DistanceService extends Service implements LocationListener, SensorEventListener, StepCounter.StepCounterDelegate {

    private final IBinder mBinder = new LocalBinder();

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private PowerManager powerManager;
    private WakeLock wakeLock;
    private StepCounter stepCounter;
    private SharedPreferences preferences;
    private DistanceServiceDelegate delegate;

    private List<Location> locationEvents;
    private List<Float> altitudeEvents;

    private double sensorUpdateInterval;
    private int horizontalDistanceFilter;
    private double horizontalAccuracyFilter;
    private int verticalDistanceFilter;
    private double verticalAccuracyFilter;
    private double distanceWalkedToCalibrate;
    private double stepLengthFactor;

    private float stepLength;
    private float bodyHeight;
    private float calibrationCandidateDistance;
    private float lastAltitude;
    private int relativeAltitudeGain;
    private float distanceTraveledPersistent;
    private float distanceTraveledProvisional;
    private float distanceTraveledHeuristic;
    private int stepsTakenPersistent;
    private int stepsTakenProvisional;
    private int stepsTakenTotal;
    private long lastCalibrated;
    private boolean calibrationInProgress;
    private boolean enableGPSCalibration;

    private volatile boolean isTracking;

    // Used to compensate for fluctuating sampling rates.
    // DistanceService handles sensor data (as opposed to the iOS implementation), ...
    // ... to ensure that the foreground service consititutes the event listener.
    private volatile double gravityX;
    private volatile double gravityY;
    private volatile double gravityZ;

    // Method that is called when the native interface (stepdistplugin.java) starts and binds to this foreground service.
    // Initializes the background processing and starts the GNSS localization used for the step length calibration.
    // A wake lock is set to prevent the CPU from sleeping when the device is in sleep ...
    // ... registering and running a foreground service is not enough to prevent the CPU from sleeping.
    @Override
    public IBinder onBind(Intent intent) {
        horizontalDistanceFilter = intent.getIntExtra("horizontalDistanceFilter", 0);
        horizontalAccuracyFilter = intent.getDoubleExtra("horizontalAccuracyFilter", 0);
        verticalDistanceFilter = intent.getIntExtra("verticalDistanceFilter", 0);
        verticalAccuracyFilter = intent.getDoubleExtra("verticalAccuracyFilter", 0);
        distanceWalkedToCalibrate = intent.getDoubleExtra("distanceWalkedToCalibrate", 0);
        stepLengthFactor = intent.getDoubleExtra("stepLengthFactor", 0);
        sensorUpdateInterval = intent.getDoubleExtra("updateInterval", 0);

        JSONObject stepCounterOptions = new JSONObject();
        try {
            stepCounterOptions.put("updateInterval", intent.getDoubleExtra("updateInterval", 0));
            stepCounterOptions.put("betterStrideFactor", intent.getDoubleExtra("betterStrideFactor", 0));
            stepCounterOptions.put("deviationLength", intent.getDoubleExtra("deviationLength", 0));
            stepCounterOptions.put("deviationAmplitude", intent.getDoubleExtra("deviationAmplitude", 0));
            stepCounterOptions.put("minStrideAmplitude", intent.getDoubleExtra("minStrideAmplitude", 0));
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
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(0, horizontalDistanceFilter, criteria, this, null);
        } catch (SecurityException securityException) {
            securityException.printStackTrace();
        }

        sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"StepDistPlugin:AllowStepCounting");
        wakeLock.acquire();

        preferences = getSharedPreferences("sharedPreferences", Context.MODE_PRIVATE);
        loadBodyHeight();
        loadStepLength();

        isTracking = false;

        startForeground(1, buildNotification());

        return mBinder;
    }

    // Plugin life cycle method.
    @Override
    public boolean onUnbind(Intent intent) {
        locationManager.removeUpdates(this);
        wakeLock.release();
        return super.onUnbind(intent);
    }

    // Creates a notification that is required to enable a foreground service ...
    // ... and background processing since Android 8 Oreo (see Android documentation).
    // Sets the icon of the parent Cordova application as notification icon.
    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, getMainActivity());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        String applicationName;
        int applicationNameIdentifier = getApplicationInfo().labelRes;
        if (applicationNameIdentifier == 0) {
            applicationName = getApplicationInfo().nonLocalizedLabel.toString();
        } else {
            applicationName = getString(applicationNameIdentifier);
        }

        Notification notification = new NotificationCompat.Builder(this, "stepDistServiceChannel")
                .setContentTitle(applicationName)
                .setContentText("Estimating your walking distance.")
                .setSmallIcon(getApplicationInfo().icon)
                .setContentIntent(pendingIntent)
                .build();

        return notification;
    }

    // Helper method (Android implementation only)
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

    // Starts the main distance estimation and step length calibration.
    // For this, the step counting algorithm is started.
    public void startMeasuringDistance(boolean enableGPSCalibration) {
        locationEvents = new ArrayList<>();
        altitudeEvents = new ArrayList<>();
        distanceTraveledPersistent = 0;
        distanceTraveledProvisional = 0;
        distanceTraveledHeuristic = 0;
        stepsTakenPersistent = 0;
        stepsTakenProvisional = 0;
        stepsTakenTotal = 0;
        calibrationInProgress = false;
        calibrationCandidateDistance = 0;
        lastAltitude = 0;
        relativeAltitudeGain = 0;

        stepCounter.resetData();

        assert sensorManager != null;
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), (int) (sensorUpdateInterval*1000000));

        this.enableGPSCalibration = enableGPSCalibration;

        isTracking = true;

        StepCounterThread stepCounterThread = new StepCounterThread();
        stepCounterThread.start();
    }

    // Stops the main distance estimation and step length calibration.
    public void stopMeasuringDistance() {
        sensorManager.unregisterListener(this);
        isTracking = false;
    }

    // Processes new incoming location events.
    @Override
    public void onLocationChanged(Location location) {
        sendPluginInfo(location.getAccuracy(), "Accuracy: " + String.valueOf(location.getAccuracy()));

        if (isTracking) {
            processLocationEvent(location);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // No need for action
    }

    @Override
    public void onProviderEnabled(String provider) {
        // No need for action
    }

    @Override
    public void onProviderDisabled(String provider) {
        // No need for action
    }

    // Used for the delegation pattern (not directly set as in the iOS implementation).
    public void setDelegate(DistanceServiceDelegate distanceServiceDelegate) {
        delegate = distanceServiceDelegate;
    }

    // Called from within the StepCounter service whenever new steps occured.
    @Override
    public void stepCountDidChange(int count, float frequency) {
        stepsTakenProvisional = count-stepsTakenPersistent;
        // Walking distance based on GNSS-calibrated step length.
        distanceTraveledProvisional = stepsTakenProvisional*stepLength;

        // Walking distance based on heuristica formula based on body height and step frequency.
        int newSteps = count - stepsTakenTotal;
        distanceTraveledHeuristic += newSteps*(stepLengthFactor*bodyHeight*sqrt(frequency));
        stepsTakenTotal = count;

        // Pick on of the two walking distance estimations or calculate the average of both.
        int distanceTraveled = 0;
        if (distanceTraveledProvisional+distanceTraveledPersistent == 0.0 && distanceTraveledHeuristic != 0.0) {
            distanceTraveled = Math.round(distanceTraveledHeuristic);
        } else if (distanceTraveledProvisional+distanceTraveledPersistent != 0.0 && distanceTraveledHeuristic == 0.0) {
            distanceTraveled = Math.round(distanceTraveledProvisional+distanceTraveledPersistent);
        } else if (distanceTraveledProvisional+distanceTraveledPersistent != 0.0 && distanceTraveledHeuristic != 0.0) {
            distanceTraveled = Math.round(((distanceTraveledProvisional+distanceTraveledPersistent)+distanceTraveledHeuristic)/2);
        }

        delegate.distanceDidChange(distanceTraveled, stepsTakenTotal, relativeAltitudeGain);
    }

    private void processLocationEvent(Location location) {
        // Here, not simply take locationEvents.first.time, as this would give the end-time of the 4m walk, not the start, and would neglect steps in this time.
        // Also not use the current locationEvent as we dont have steps for this because of the smoothing timeframe.
        if (locationEvents.size() >= 3 && enableGPSCalibration) {
            calibrationCandidateDistance = calculateCumulativeDistance(locationEvents.subList(1, locationEvents.size()));
            if (calibrationCandidateDistance >= distanceWalkedToCalibrate) {
                calibrationInProgress = true;
                int calibrationCandidateSteps = stepCounter.getStepsBetween(new Date(locationEvents.get(0).getTime()), new Date(locationEvents.get(locationEvents.size()-1).getTime()));
                saveStepLength(calibrationCandidateDistance/calibrationCandidateSteps);
                sendPluginInfo();
            } else if (calibrationInProgress) {
                // As a delegate, this class has the most recent step count data from the step counter.
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy() && location.getVerticalAccuracyMeters() <= verticalAccuracyFilter) {
            updateRelativeAltitude((float) location.getAltitude());
        // If the location event does not have vertical accuracy we simply check the horizontal accuracy
        } else if (location.getAccuracy() <= horizontalAccuracyFilter) {
            updateRelativeAltitude((float) location.getAltitude());
        } else {
            altitudeEvents.clear();
        }
    }

    // Takes multiple location events and returns the total distance between them, used for step length calibration.
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

    // Estimate the elevation based on GNSS location events.
    private void updateRelativeAltitude(float currentApproximateAltitude) {
        altitudeEvents.add(currentApproximateAltitude);
        if (altitudeEvents.size() == verticalDistanceFilter) {
            float sumAltitudes = 0f;
            float sumDiffAltitudes = 0f;
            for (int i = 0; i < verticalDistanceFilter-1; i++) {
                sumAltitudes += altitudeEvents.get(i);
                sumDiffAltitudes += abs(altitudeEvents.get(i+1) - altitudeEvents.get(i));
            }
            sumAltitudes += altitudeEvents.get(verticalDistanceFilter-1);
            if (sumDiffAltitudes >= 1) {
                return;
            }
            float currentAltitude = Math.round(sumAltitudes / verticalDistanceFilter);
            if (lastAltitude != 0.0) {
                float relativeAltitude = currentAltitude - lastAltitude;
                if (relativeAltitude >= 0) {
                    relativeAltitudeGain += Math.round(relativeAltitude);
                }
            }
            lastAltitude = currentAltitude;
            altitudeEvents.remove(0);
        }
    }

    public class LocalBinder extends Binder {
        public DistanceService getService(){
            return DistanceService.this;
        }
    }

    // Sends the current plugin status (isReadyToStart, calibratedStepLength, lastCalibrated, bodyHeight) to the native interface
    // and from there to the plugin interface.
    // DebugInfo only used for testing purposes.
    public void sendPluginInfo(double accuracy, String debugInfo) {
        boolean isReadyToStart = false;

        // No need to round accuracy on Android
        if (accuracy <= horizontalAccuracyFilter || stepLength != 0.0 || bodyHeight != 0.0) {
            isReadyToStart = true;
        }

        delegate.pluginInfoDidChange(isReadyToStart, debugInfo, lastCalibrated, stepLength, bodyHeight);
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

    // Persistence methods.
    private void loadBodyHeight() {
        bodyHeight = preferences.getFloat("bodyHeight", 0);
    }

    public void saveBodyHeight(float bodyHeight) {
        this.bodyHeight = bodyHeight;

        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat("bodyHeight", this.bodyHeight);
        editor.apply();
    }

    private void loadStepLength() {
        stepLength = preferences.getFloat("stepLength", 0);
        lastCalibrated = preferences.getLong("lastCalibrated", 0);
    }

    private void saveStepLength(float stepLength) {
        this.stepLength = stepLength;
        this.lastCalibrated = new Date().getTime()/1000;

        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat("stepLength", this.stepLength);
        editor.putLong("lastCalibrated", this.lastCalibrated);
        editor.apply();
    }

    public void resetData() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();

        loadBodyHeight();
        loadStepLength();
    }

    // Stores the incoming sensor data. Used to compensate for fluctuating sampling rates.
    @Override
    public void onSensorChanged(SensorEvent event) {
        gravityX = event.values[0];
        gravityY = event.values[1];
        gravityZ = event.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No need for action
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    public interface DistanceServiceDelegate {
        void distanceDidChange(int distanceTraveled, int stepsTaken, int relativeAltitudeGain);
        void pluginInfoDidChange(boolean isReadyToStart, String debugInfo, long lastCalibrated, float stepLength, float bodyHeight);
    }

    // Thread to process gravity sensor data and count taken steps.
    // On iOS, sensor data is processed in a background thread automatically, but not on Android.
    // As stated above, the DistanceService handles sensor data (as opposed to the iOS implementation), ...
    // ... to ensure that the foreground service consititutes the event listener.
    class StepCounterThread extends Thread {
        Handler handler = new Handler();

        private Runnable stepCounterRunnable = new Runnable() {
            public void run() {
                if (isTracking) {
                    handler.postDelayed(this, (long) (sensorUpdateInterval*1000));
                }
                stepCounter.processMotionData(gravityX, gravityY, gravityZ);
            }
        };

        @Override
        public void run() {
            if (!isTracking) {
                return;
            }

            handler.postDelayed(stepCounterRunnable, (long) (sensorUpdateInterval*1000));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}

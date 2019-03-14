package cordova.plugin.stepdist;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.Math.abs;

public class DistanceService extends Service implements LocationListener, SensorEventListener, StepCounter.StepCounterDelegate {

    private final IBinder mBinder = new LocalBinder();

    private SensorManager sensorManager;
    private Handler handler;
    private LocationManager locationManager;
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
    private double distanceTraveledToCalibrate;

    private float stepLength;
    private float calibrationCandidateDistance;
    private float lastAltitude;
    private int relativeAltitudeGain;
    private int distanceTraveledPersistent;
    private int distanceTraveledProvisional;
    private int stepsTakenPersistent;
    private int stepsTakenProvisional;
    private long lastCalibrated;
    private boolean calibrationInProgress;
    private boolean isTracking;

    private double gravityX;
    private double gravityY;
    private double gravityZ;

    @Override
    public IBinder onBind(Intent intent) {
        horizontalDistanceFilter = intent.getIntExtra("horizontalDistanceFilter", 0);
        horizontalAccuracyFilter = intent.getDoubleExtra("horizontalAccuracyFilter", 0);
        verticalDistanceFilter = intent.getIntExtra("verticalDistanceFilter", 0);
        verticalAccuracyFilter = intent.getDoubleExtra("verticalAccuracyFilter", 0);
        distanceTraveledToCalibrate = intent.getDoubleExtra("distanceTraveledToCalibrate", 0);
        sensorUpdateInterval = intent.getDoubleExtra("updateInterval", 0);

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

        sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        handler = new Handler();

        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, horizontalDistanceFilter, this);
        } catch (SecurityException securityException) {
            securityException.printStackTrace();
        }

        preferences = getSharedPreferences("sharedPreferences", Context.MODE_PRIVATE);
        isTracking = false;
        loadStepLength();

        startForeground(1, buildNotification(0));

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        locationManager.removeUpdates(this);
        return super.onUnbind(intent);
    }

    private Notification buildNotification(int stepCount) {
        Intent notificationIntent = new Intent(this, getMainActivity());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, "stepDistServiceChannel")
                .setContentTitle("Distance Service")
                .setContentText("Steps: " + String.valueOf(stepCount))
                .setSmallIcon(getApplicationInfo().icon)
                .setContentIntent(pendingIntent)
                .build();

        return notification;
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

    private Runnable stepCounterRunnable = new Runnable() {
        public void run() {
            stepCounter.processMotionData(gravityX, gravityY, gravityZ);
            if (isTracking) {
                handler.postDelayed(this, (long) (sensorUpdateInterval*1000));
            }
        }
    };

    public void startMeasuringDistance() {
        locationEvents = new ArrayList<>();
        altitudeEvents = new ArrayList<>();
        distanceTraveledPersistent = 0;
        distanceTraveledProvisional = 0;
        stepsTakenPersistent = 0;
        stepsTakenProvisional = 0;
        calibrationInProgress = false;
        calibrationCandidateDistance = 0;
        lastAltitude = 0;
        relativeAltitudeGain = 0;

        stepCounter.resetData();

        assert sensorManager != null;
        Sensor gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorManager.registerListener(this, gravitySensor , SensorManager.SENSOR_DELAY_GAME);

        handler.postDelayed(stepCounterRunnable, (long) (sensorUpdateInterval*1000));

        isTracking = true;
    }

    public void stopMeasuringDistance() {
        isTracking = false;
    }

    @Override
    public void onLocationChanged(Location location) {
        sendPluginInfo(location.getAccuracy(), "Accuracy: " + String.valueOf(location.getAccuracy()));

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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.notify(1, buildNotification(count));
        }

        delegate.distanceDidChange(distanceTraveledPersistent+distanceTraveledProvisional, stepsTakenPersistent+stepsTakenProvisional, relativeAltitudeGain);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy() && location.getVerticalAccuracyMeters() <= verticalAccuracyFilter) {
            updateRelativeAltitude((float) location.getAltitude());
        // If the location event does not have vertical accuracy we simply check the horizontal accuracy
        } else if (location.getAccuracy() <= horizontalAccuracyFilter) {
            updateRelativeAltitude((float) location.getAltitude());
        } else {
            altitudeEvents.clear();
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        gravityX = event.values[0];
        gravityY = event.values[1];
        gravityZ = event.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    public interface DistanceServiceDelegate {
        void distanceDidChange(int distanceTraveled, int stepsTaken, int relativeAltitudeGain);
        void pluginInfoDidChange(boolean isReadyToStart, String debugInfo, long lastCalibrated, float stepLength);
    }

}

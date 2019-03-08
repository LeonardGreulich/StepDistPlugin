package cordova.plugin.stepdist;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.SensorsClient;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greulich.leonard.stepdist.MainActivity;
import greulich.leonard.stepdist.R;

public class DistanceService extends Service implements LocationListener, StepCounter.StepCounterDelegate {

    private final IBinder mBinder = new LocalBinder();

    private LocationManager locationManager;
    private StepCounter stepCounter;
    private DistanceServiceDelegate delegate;

    private SensorsClient sensorsClient;
    private OnDataPointListener stepCountListener;

    private List<Location> locationEvents;

    private int distanceFilter;
    private double accuracyFilter;
    private double perpendicularFilter;
    private int locationsSequenceFilter;
    private double locationsSequenceDistanceFilter;

    private double stepLength;
    private double calibrationCandidateDistance;
    private int distanceTraveledPersistent;
    private int distanceTraveledProvisional;
    private int stepsTakenPersistent;
    private int stepsTakenProvisional;
    private int lastCalibrated;
    private boolean calibrationInProgress;

    // Android specific (not on iOS implementation)
    private boolean isTracking;

    @Override
    public IBinder onBind(Intent intent) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        distanceFilter = intent.getIntExtra("distanceFilter", 0);
        accuracyFilter = intent.getDoubleExtra("accuracyFilter", 0);
        perpendicularFilter = intent.getDoubleExtra("perpendicularFilter", 0);
        locationsSequenceFilter = intent.getIntExtra("locationsSequenceFilter", 0);
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
        distanceTraveledProvisional = (int) Math.round(stepsTakenProvisional*stepLength);

        JSONObject distanceInfo = new JSONObject();
        try {
            distanceInfo.put("distanceTraveled", distanceTraveledProvisional + distanceTraveledPersistent);
            distanceInfo.put("stepsTaken", stepsTakenProvisional + stepsTakenPersistent);
        } catch (JSONException e) {
            System.out.println("Error distanceInfo");
        }

        delegate.updateDistanceInfo(distanceInfo);
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
            calibrationCandidateDistance = 0.0;
            sendPluginInfo("Calibr. cancel.: Accuracy (" + String.valueOf(location.getAccuracy()) + ")");
        }
    }

    private double calculateCumulativeDistance(List<Location> locations) {
        Location lastLocation = null;
        double cumulativeDistance = 0;

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

        JSONObject pluginInfo = new JSONObject();
        try {
            pluginInfo.put("isReadyToStart", isReadyToStart);
            pluginInfo.put("debugInfo", debugInfo);
            pluginInfo.put("stepLength", stepLength);
            pluginInfo.put("lastCalibrated", lastCalibrated);
        } catch (JSONException e) {
            System.out.println("Error pluginInfo");
        }

        delegate.updatePluginInfo(pluginInfo);
    }

    public void sendPluginInfo() {
        sendPluginInfo(9999.0f, "");
    }

    public void sendPluginInfo(String debugInfo) {
        sendPluginInfo(9999.0f, debugInfo);
    }

    public void sendPluginInfo(float accuracy) {
        sendPluginInfo(accuracy, "");
    }

    private void identifyStepDataSourcesAndStartCounting() {
        stepCountListener = new OnDataPointListener() {
            boolean firstDataPoint = true;
            int totalSteps = 0;

            @Override
            public void onDataPoint(DataPoint dataPoint) {
                int steps = dataPoint.getValue(Field.FIELD_STEPS).asInt();
                if ((firstDataPoint && steps >= 5) || steps < 0) {
                    firstDataPoint = false;
                    return;
                } else {
                    firstDataPoint = false;
                }
                totalSteps += steps;
                sendPluginInfo("Steps: " + totalSteps);
            }
        };

        sensorsClient = Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this));

        sensorsClient.findDataSources(
                        new DataSourcesRequest.Builder()
                                .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                                .setDataSourceTypes(DataSource.TYPE_RAW, DataSource.TYPE_DERIVED)
                                .build())
                .addOnSuccessListener(
                        dataSources -> {
                            for (DataSource dataSource : dataSources) {
                                startStepCounting(dataSource);
                            }
                        });
    }

    private void startStepCounting(DataSource dataSource) {
        sensorsClient.add(new SensorRequest.Builder()
                                .setDataType(dataSource.getDataType())
                                .setDataSource(dataSource)
                                .setSamplingRate(1, TimeUnit.SECONDS)
                                .setTimeout(1, TimeUnit.HOURS)
                                .build(), stepCountListener)
                .addOnCompleteListener(
                        task -> {
                            if (task.isSuccessful()) {
                                System.out.println("Listener registered!");
                            } else {
                                System.out.println("Listener not registered!");
                            }
                        });
    }

    private void stopStepCounting() {
        assert sensorsClient != null;
        sensorsClient.remove(stepCountListener)
                .addOnCompleteListener(
                        task -> {
                            if (task.isSuccessful() && task.getResult()) {
                                System.out.println("Listener was removed!");
                            } else {
                                System.out.println("Listener was not removed!");
                            }
                        });
    }

    private void loadStepLength() {
        // TODO: To be implemented
        stepLength = 0.78;
        lastCalibrated = 12341234;
    }

    private void saveStepLength(double stepLength) {
        this.stepLength = stepLength;
        this.lastCalibrated = (int) (new Date().getTime());
    }

    public interface DistanceServiceDelegate {
        void updatePluginInfo(JSONObject pluginInfo);
        void updateDistanceInfo(JSONObject distanceInfo);
    }

}

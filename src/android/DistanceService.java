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

import org.json.JSONException;
import org.json.JSONObject;

import greulich.leonard.stepdist.MainActivity;
import greulich.leonard.stepdist.R;

public class DistanceService extends Service implements LocationListener {

    private LocationManager locationManager;
    private DistanceServiceDelegate delegate;
    private final IBinder mBinder = new LocalBinder();

    private Integer distanceFilter;
    private Double accuracyFilter;
    private Double perpendicularFilter;
    private Integer locationsSequenceFilter;
    private Double locationsSequenceDistanceFilter;

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

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, distanceFilter, this);
        } catch (SecurityException securityException) {
            securityException.printStackTrace();
        }

        startForeground(1, notification);

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        locationManager.removeUpdates(this);
        return super.onUnbind(intent);
    }

    public void startMeasuringDistance() {

    }

    public void stopMeasuringDistance() {

    }

    @Override
    public void onLocationChanged(Location location) {
        sendPluginInfo("Accuracy: " + String.valueOf(location.getAccuracy()));
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

    public class LocalBinder extends Binder {
        public DistanceService getService(){
            return DistanceService.this;
        }
    }

    public void sendPluginInfo(Double Accuracy, String debugInfo) {
        JSONObject pluginInfo = new JSONObject();
        try {
            pluginInfo.put("isReadyToStart", true);
            pluginInfo.put("debugInfo", debugInfo);
            pluginInfo.put("stepLength", 0.78);
            pluginInfo.put("lastCalibrated", 123456712);
        } catch (JSONException e) {
            System.out.println("Error");
        }

        delegate.updatePluginInfo(pluginInfo);
    }

    public void sendPluginInfo() {
        sendPluginInfo(9999.0, "");
    }

    public void sendPluginInfo(String debugInfo) {
        sendPluginInfo(9999.0, debugInfo);
    }

    public void sendPluginInfo(Double accuracy) {
        sendPluginInfo(accuracy, "");
    }

    public interface DistanceServiceDelegate {
        void updatePluginInfo(JSONObject pluginInfo);
        void updateDistanceInfo(JSONObject distanceInfo);
    }

}

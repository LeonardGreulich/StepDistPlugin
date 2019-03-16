package cordova.plugin.stepdist;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static android.content.Context.POWER_SERVICE;

public class stepdistplugin extends CordovaPlugin implements DistanceService.DistanceServiceDelegate {

    private DistanceService distanceService;

    private CallbackContext pluginInfoEventCallback;
    private CallbackContext distanceEventCallback;
    
    private Context applicationContext;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            distanceService = ((DistanceService.LocalBinder)service).getService();
            distanceService.setDelegate(stepdistplugin.this);
            distanceService.sendPluginInfo();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            distanceService = null;
        }
    };

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        distanceService = null;
        applicationContext = this.cordova.getActivity().getApplicationContext();
        createNotificationChannel();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("startLocalization")) {
            pluginInfoEventCallback = callbackContext;
            startLocalization(args.getJSONObject(0));
            return true;
        } else if (action.equals("stopLocalization")) {
            stopLocalization();
            pluginInfoEventCallback = null;
            return true;
        } else if (action.equals("startMeasuringDistance")) {
            distanceEventCallback = callbackContext;
            startMeasuringDistance();
            return true;
        } else if (action.equals("stopMeasuringDistance")) {
            stopMeasuringDistance();
            distanceEventCallback = null;
            return true;
        }

        return false;
    }

    private void startLocalization(JSONObject options) throws JSONException {
        if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PermissionHelper.requestPermission(this, 0, Manifest.permission.ACCESS_FINE_LOCATION);

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent();
                String packageName = applicationContext.getPackageName();
                PowerManager pm = (PowerManager) applicationContext.getSystemService(POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    applicationContext.startActivity(intent);
                }
            }

            PluginResult pluginInfoResult = new PluginResult(PluginResult.Status.ERROR);
            pluginInfoEventCallback.sendPluginResult(pluginInfoResult);

            return;
        }

        Intent serviceIntent = new Intent(applicationContext, DistanceService.class);

        serviceIntent.putExtra("horizontalDistanceFilter", options.getInt("horizontalDistanceFilter"));
        serviceIntent.putExtra("horizontalAccuracyFilter", options.getDouble("horizontalAccuracyFilter"));
        serviceIntent.putExtra("verticalDistanceFilter", options.getInt("verticalDistanceFilter"));
        serviceIntent.putExtra("verticalAccuracyFilter", options.getDouble("verticalAccuracyFilter"));
        serviceIntent.putExtra("distanceTraveledToCalibrate", options.getDouble("distanceTraveledToCalibrate"));
        serviceIntent.putExtra("updateInterval", options.getDouble("updateInterval"));
        serviceIntent.putExtra("betterFragmentFactor", options.getDouble("betterFragmentFactor"));
        serviceIntent.putExtra("deviationLength", options.getDouble("deviationLength"));
        serviceIntent.putExtra("deviationAmplitude", options.getDouble("deviationAmplitude"));
        serviceIntent.putExtra("smoothingTimeframe", options.getInt("smoothingTimeframe"));

        applicationContext.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopLocalization() {
        if (distanceService != null) {
            applicationContext.unbindService(serviceConnection);

            PluginResult result = new PluginResult(PluginResult.Status.OK);
            pluginInfoEventCallback.sendPluginResult(result);
        }
    }

    private void startMeasuringDistance() {
        distanceService.startMeasuringDistance();
        
        distanceDidChange(0, 0, 0);
    }

    private void stopMeasuringDistance() {
        distanceService.stopMeasuringDistance();

        PluginResult result = new PluginResult(PluginResult.Status.OK);
        distanceEventCallback.sendPluginResult(result);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "stepDistServiceChannel",
                    "Distance Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = applicationContext.getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void distanceDidChange(int distanceTraveled, int stepsTaken, int relativeAltitudeGain) {
        JSONObject distanceInfo = new JSONObject();
        try {
            distanceInfo.put("distanceTraveled", distanceTraveled);
            distanceInfo.put("stepsTaken", stepsTaken);
            distanceInfo.put("relativeAltitudeGain", relativeAltitudeGain);
        } catch (JSONException e) {
            System.out.println("Error distanceInfo");
        }

        PluginResult distanceInfoResult = new PluginResult(PluginResult.Status.OK, distanceInfo);
        distanceInfoResult.setKeepCallback(true);

        distanceEventCallback.sendPluginResult(distanceInfoResult);
    }

    @Override
    public void pluginInfoDidChange(boolean isReadyToStart, String debugInfo, long lastCalibrated, float stepLength) {
        JSONObject pluginInfo = new JSONObject();
        try {
            pluginInfo.put("isReadyToStart", isReadyToStart);
            pluginInfo.put("debugInfo", debugInfo);
            pluginInfo.put("stepLength", stepLength);
            pluginInfo.put("lastCalibrated", lastCalibrated);
        } catch (JSONException e) {
            System.out.println("Error pluginInfo");
        }

        PluginResult pluginInfoResult = new PluginResult(PluginResult.Status.OK, pluginInfo);
        pluginInfoResult.setKeepCallback(true);

        pluginInfoEventCallback.sendPluginResult(pluginInfoResult);
    }

    @Override
    public void onDestroy() {
        stopMeasuringDistance();
        stopLocalization();
        System.out.println("Plugin destroyed");
        super.onDestroy();
    }

}

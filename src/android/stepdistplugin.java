package cordova.plugin.stepdist;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
            stopLocalization(callbackContext);
            pluginInfoEventCallback = null;
            return true;
        } else if (action.equals("startMeasuringDistance")) {
            distanceEventCallback = callbackContext;
            startMeasuringDistance(callbackContext);
            return true;
        } else if (action.equals("stopMeasuringDistance")) {
            stopMeasuringDistance(callbackContext);
            distanceEventCallback = null;
            return true;
        }

        return false;
    }

    private void startLocalization(JSONObject args) throws JSONException {
        if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PermissionHelper.requestPermission(this, 0, Manifest.permission.ACCESS_FINE_LOCATION);
        }

        Intent serviceIntent = new Intent(applicationContext, DistanceService.class);

        serviceIntent.putExtra("distanceFilter", args.getInt("distanceFilter"));
        serviceIntent.putExtra("accuracyFilter", args.getDouble("distanceFilter"));
        serviceIntent.putExtra("locationsSequenceDistanceFilter", args.getDouble("distanceFilter"));

        applicationContext.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopLocalization(CallbackContext callbackContext) {
        applicationContext.unbindService(serviceConnection);

        PluginResult result = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(result);
    }

    private void startMeasuringDistance(CallbackContext callbackContext) {
        distanceService.startMeasuringDistance();
    }

    private void stopMeasuringDistance(CallbackContext callbackContext) {
        distanceService.stopMeasuringDistance();
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
    public void distanceDidChange(int distanceTraveled, int stepsTaken) {
        JSONObject distanceInfo = new JSONObject();
        try {
            distanceInfo.put("distanceTraveled", distanceTraveled);
            distanceInfo.put("stepsTaken", stepsTaken);
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
        stopMeasuringDistance(distanceEventCallback);
        stopLocalization(pluginInfoEventCallback);
        super.onDestroy();
    }

}

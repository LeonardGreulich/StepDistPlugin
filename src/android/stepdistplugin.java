//
// stepdistplugin.java
//
// Created by Leonard Greulich on 2/16/19.
// Copyright © 2019 Leonard Greulich. All rights reserved.
//
// Contains native interface class.
// Manages the communication with the JavaScript interface and the life cycle of the plugin.
//

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
import android.telecom.Call;

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

    // In order to communicate with the DistanceService which constitutes a foreground service (see Android documentation).
    // Method not present in iOS implementation.
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

    // Initilizaes the notification channel which is later used for the foreground service notification (see Android documentation).
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        distanceService = null;
        applicationContext = this.cordova.getActivity().getApplicationContext();
        createNotificationChannel();
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

    // Manages and propagates the method calls from the plugin interface. Different to iOS implementation where methods are directly called.
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
            startMeasuringDistance(args.getBoolean(0));
            return true;
        } else if (action.equals("stopMeasuringDistance")) {
            stopMeasuringDistance();
            distanceEventCallback = null;
            return true;
        } else if (action.equals("setBodyHeight")) {
            setBodyHeight(args.getDouble(0), callbackContext);
            return true;
        } else if (action.equals("resetData")) {
            resetData(callbackContext);
            return true;
        }

        return false;
    }

    // Plugin life cycle method. Starts the localization in order to get a GNSS fix for the step length calibration.
    // Starts the foreground service (see Android documentation) for the background processing.
    private void startLocalization(JSONObject options) throws JSONException {
        if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PermissionHelper.requestPermission(this, 0, Manifest.permission.ACCESS_FINE_LOCATION);

            PluginResult pluginInfoResult = new PluginResult(PluginResult.Status.ERROR);
            pluginInfoEventCallback.sendPluginResult(pluginInfoResult);

            return;
        }

        Intent serviceIntent = new Intent(applicationContext, DistanceService.class);

        serviceIntent.putExtra("horizontalDistanceFilter", options.getInt("horizontalDistanceFilter"));
        serviceIntent.putExtra("horizontalAccuracyFilter", options.getDouble("horizontalAccuracyFilter"));
        serviceIntent.putExtra("verticalDistanceFilter", options.getInt("verticalDistanceFilter"));
        serviceIntent.putExtra("verticalAccuracyFilter", options.getDouble("verticalAccuracyFilter"));
        serviceIntent.putExtra("distanceWalkedToCalibrate", options.getDouble("distanceWalkedToCalibrate"));
        serviceIntent.putExtra("stepLengthFactor", options.getDouble("stepLengthFactor"));
        serviceIntent.putExtra("updateInterval", options.getDouble("updateInterval"));
        serviceIntent.putExtra("betterStrideFactor", options.getDouble("betterStrideFactor"));
        serviceIntent.putExtra("deviationLength", options.getDouble("deviationLength"));
        serviceIntent.putExtra("deviationAmplitude", options.getDouble("deviationAmplitude"));
        serviceIntent.putExtra("minStrideAmplitude", options.getDouble("minStrideAmplitude"));
        serviceIntent.putExtra("smoothingTimeframe", options.getInt("smoothingTimeframe"));

        applicationContext.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    // Stops the localization and background processing.
    private void stopLocalization() {
        if (distanceService != null) {
            applicationContext.unbindService(serviceConnection);

            PluginResult result = new PluginResult(PluginResult.Status.OK);
            pluginInfoEventCallback.sendPluginResult(result);
        }
    }

    // Starts the main distance estimation and step length calibration.
    private void startMeasuringDistance(boolean enableGPSCalibration) {
        distanceService.startMeasuringDistance(enableGPSCalibration);
        
        distanceDidChange(0, 0, 0);
    }

    // Stops the main distance estimation and step length calibration.
    private void stopMeasuringDistance() {
        distanceService.stopMeasuringDistance();

        PluginResult result = new PluginResult(PluginResult.Status.OK);
        distanceEventCallback.sendPluginResult(result);
    }

    // Sets the body height and enables the heuristic formula to estimate the walking distance based on step frequency and body height.
    private void setBodyHeight(double bodyHeight, CallbackContext callbackContext) {
        distanceService.saveBodyHeight((float) bodyHeight);
        distanceService.sendPluginInfo();

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(pluginResult);
    }

    // Erases all persisted data (step length, calibration date, and body height)
    private void resetData(CallbackContext callbackContext) {
        distanceService.resetData();
        distanceService.sendPluginInfo();

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(pluginResult);
    }

    // Called from within the DistanceService. Sends distance, steps, and elevation to the plugin interface.
    @Override
    public void distanceDidChange(int distanceTraveled, int stepsTaken, int relativeAltitudeGain) {
        JSONObject distanceInfo = new JSONObject();
        try {
            distanceInfo.put("distance", distanceTraveled);
            distanceInfo.put("steps", stepsTaken);
            distanceInfo.put("elevation", relativeAltitudeGain);
        } catch (JSONException e) {
            System.out.println("Error distanceInfo");
        }

        PluginResult distanceInfoResult = new PluginResult(PluginResult.Status.OK, distanceInfo);
        distanceInfoResult.setKeepCallback(true);

        distanceEventCallback.sendPluginResult(distanceInfoResult);
    }

    // Called from within the DistanceService. Sends status information to the plugin interface.
    @Override
    public void pluginInfoDidChange(boolean isReadyToStart, String debugInfo, long lastCalibrated, float stepLength, float bodyHeight) {
        JSONObject pluginInfo = new JSONObject();
        try {
            pluginInfo.put("isReadyToStart", isReadyToStart);
            pluginInfo.put("debugInfo", debugInfo);
            pluginInfo.put("stepLength", stepLength);
            pluginInfo.put("lastCalibrated", lastCalibrated);
            pluginInfo.put("bodyHeight", bodyHeight);
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
        super.onDestroy();
    }
}

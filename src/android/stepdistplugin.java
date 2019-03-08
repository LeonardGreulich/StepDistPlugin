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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataType;

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

    private final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = System.identityHashCode(this) & 0xFFFF;

    private Context applicationContext;
    private CallbackContext pluginInfoEventCallback;
    private CallbackContext distanceEventCallback;
    private DistanceService distanceService;

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
            pluginInfoEventCallback = null;
            stopLocalization(callbackContext);
            return true;
        } else if (action.equals("startMeasuringDistance")) {
            distanceEventCallback = callbackContext;
            startMeasuringDistance(callbackContext);
            return true;
        } else if (action.equals("stopMeasuringDistance")) {
            distanceEventCallback = null;
            stopMeasuringDistance(callbackContext);
            return true;
        }

        return false;
    }

    private void startLocalization(JSONObject args) throws JSONException {
        if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PermissionHelper.requestPermission(this, 0, Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            authorizeGoogleFit();
        }

        Intent serviceIntent = new Intent(applicationContext, DistanceService.class);

        serviceIntent.putExtra("distanceFilter", args.getInt("distanceFilter"));
        serviceIntent.putExtra("accuracyFilter", args.getDouble("distanceFilter"));
        serviceIntent.putExtra("perpendicularDistanceFilter", args.getDouble("distanceFilter"));
        serviceIntent.putExtra("locationsSequenceFilter", args.getInt("distanceFilter"));
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
    public void updatePluginInfo(JSONObject pluginInfo) {
        PluginResult pluginInfoResult = new PluginResult(PluginResult.Status.OK, pluginInfo);
        pluginInfoResult.setKeepCallback(true);

        pluginInfoEventCallback.sendPluginResult(pluginInfoResult);
    }

    @Override
    public void updateDistanceInfo(JSONObject distanceInfo) {
        PluginResult distanceInfoResult = new PluginResult(PluginResult.Status.OK, distanceInfo);
        distanceInfoResult.setKeepCallback(true);

        distanceEventCallback.sendPluginResult(distanceInfoResult);
    }

    @Override
    public void onDestroy() {
        stopMeasuringDistance(distanceEventCallback);
        stopLocalization(pluginInfoEventCallback);
        super.onDestroy();
    }

    void authorizeGoogleFit() {
        FitnessOptions fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .build();

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(applicationContext), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this.cordova.getActivity(),
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(applicationContext),
                    fitnessOptions);
        }
    }

}

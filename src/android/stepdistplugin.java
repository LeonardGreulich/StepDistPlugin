package cordova.plugin.stepdist;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static android.content.Context.LOCATION_SERVICE;

/**
 * This class echoes a string called from JavaScript.
 */
public class stepdistplugin extends CordovaPlugin implements LocationListener {

    private LocationManager locationManager;
    private LocationListener locationListener;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("startLocalization")) {
            String message = args.getString(0);
            this.startLocalization(args, callbackContext);
            return true;
        }

        return false;
    }

    private void startLocalization(JSONArray args, CallbackContext callbackContext) {
        Context context = this.cordova.getActivity().getApplicationContext();
        locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);

        if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PermissionHelper.requestPermission(this, 0, Manifest.permission.ACCESS_FINE_LOCATION);
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        } catch (SecurityException securityException) {
            securityException.printStackTrace();
        }

        JSONObject obj = new JSONObject();
        try {
            obj.put("isReadyToStart", true);
        } catch (JSONException e) {
            System.out.println("Error");
        }

        PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
        result.setKeepCallback(false);

        callbackContext.sendPluginResult(result);
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}

package cordova.plugin.stepdist;

import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static android.content.Context.LOCATION_SERVICE;

/**
 * This class echoes a string called from JavaScript.
 */
public class stepdistplugin extends CordovaPlugin {

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

    private void coolMethod(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }
}

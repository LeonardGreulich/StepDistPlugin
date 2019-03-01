import CoreLocation

@objc(stepdistplugin) class stepdistplugin : CDVPlugin, CLLocationManagerDelegate {
    
    var locationManager: CLLocationManager!
    var pluginInfoEventCallbackId: String!
    var distanceEventCallbackId: String!
    
    @objc(startLocalization:) func startLocalization(command: CDVInvokedUrlCommand) {
        pluginInfoEventCallbackId = command.callbackId

        if locationManager == nil {
            locationManager = CLLocationManager()
        }
        
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK
        )
        pluginResult?.setKeepCallbackAs(true)
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(stopLocalization:) func stopLocalization(command: CDVInvokedUrlCommand) {
        locationManager.stopUpdatingLocation();
        locationManager = nil
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK
        )
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }
    
    @objc(startMeasuringDistance:) func startMeasuringDistance(command: CDVInvokedUrlCommand) {     
        distanceEventCallbackId = command.callbackId
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: "Distance measuring started"
        )
        pluginResult?.setKeepCallbackAs(true)
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: distanceEventCallbackId
        )
    }

    @objc(stopMeasuringDistance:) func stopMeasuringDistance(command: CDVInvokedUrlCommand) {     
        distanceEventCallbackId = nil
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: "Distance measuring stopped"
        )
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        print("Location accuracy: \(locations.last?.horizontalAccuracy ?? 9999.0)")

        if pluginInfoEventCallbackId != nil {
            var isReadyToStart = false;
            
            if (locations.last!.horizontalAccuracy <= 8.1) {
                isReadyToStart = true;
            }
            
            let pluginInfo: [String : Any] = ["isReadyToStart": isReadyToStart,
                                    "lastCalibrated": "Placeholder",
                                    "stepLength": "Placeholder"]
            
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAs: pluginInfo
            )
            pluginResult?.setKeepCallbackAs(true)
            
            self.commandDelegate!.send(
                pluginResult,
                callbackId: pluginInfoEventCallbackId
            )
        }
        
        if distanceEventCallbackId != nil {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAs: "New Distance Event"
            )
            pluginResult?.setKeepCallbackAs(true)
            
            self.commandDelegate!.send(
                pluginResult,
                callbackId: distanceEventCallbackId
            )
        }
    }
    
}

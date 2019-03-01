import CoreLocation

@objc(stepdistplugin) class stepdistplugin : CDVPlugin, CLLocationManagerDelegate {
    
    var locationManager: CLLocationManager!    
    var distanceCallbackId: String!
    
    @objc(startLocalization:) func startLocalization(command: CDVInvokedUrlCommand) {      
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
        distanceCallbackId = command.callbackId
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: "Distance measuring started"
        )
        pluginResult?.setKeepCallbackAs(true)
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: distanceCallbackId
        )
    }

    @objc(stopMeasuringDistance:) func stopMeasuringDistance(command: CDVInvokedUrlCommand) {     
        distanceCallbackId = nil
        
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
        let accuracy = locations.last!.horizontalAccuracy
        
        if distanceCallbackId != nil {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAs: "Accuracy: \(accuracy)."
            )
            pluginResult?.setKeepCallbackAs(true)
            
            self.commandDelegate!.send(
                pluginResult,
                callbackId: distanceCallbackId
            )
        }
    }
    
}

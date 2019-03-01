import CoreLocation
import CoreMotion

@objc(stepdistplugin) class stepdistplugin : CDVPlugin, CLLocationManagerDelegate {
    
    var locationManager: CLLocationManager!
    var pedometer: CMPedometer!
    
    var stepEvents: [CMPedometerData]!
    var locationEvents: [CLLocation]!
    
    var pluginInfoEventCallbackId: String!
    var distanceEventCallbackId: String!
    var distanceFilter: Double!
    var accuracyFilter: Double!
    var distanceTraveled: Int!
    var stepsTaken: Int!
    
    @objc(startLocalization:) func startLocalization(command: CDVInvokedUrlCommand) {
        pluginInfoEventCallbackId = command.callbackId
        
        guard let arguments = command.arguments.first as? [String: Any] else {
            return
        }
        
        if let distanceFilter = arguments["distanceFilter"] as? Double, let accuracyFilter = arguments["accuracyFilter"] as? Double {
            self.distanceFilter = distanceFilter
            self.accuracyFilter = accuracyFilter
        } else {
            return
        }
    
        if locationManager == nil {
            locationManager = CLLocationManager()
        }
        
        if pedometer == nil {
            pedometer = CMPedometer()
        }
        
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = distanceFilter
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: createPluginInfo(accuracy: 9999.0)
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
        
        stepEvents = []
        locationEvents = []
        
        distanceTraveled = 0
        stepsTaken = 0
        
        pedometer.startUpdates(from: Date(), withHandler: { (data, error) in
            if let pedometerData: CMPedometerData = data {
                self.stepEvents.append(pedometerData)
                self.stepsTaken = pedometerData.numberOfSteps.intValue
            }
        })
        
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
        
        pedometer.stopUpdates()
        
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
        guard let locationEvent = locations.last else {
            return
        }
        
        if pluginInfoEventCallbackId != nil {
            let pluginInfo = createPluginInfo(accuracy: locationEvent.horizontalAccuracy)
            
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
            processLocationEvent(locationEvent: locationEvent)
            
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAs: ["distanceTraveled": distanceTraveled, "stepsTaken": stepsTaken]
            )
            pluginResult?.setKeepCallbackAs(true)
            
            self.commandDelegate!.send(
                pluginResult,
                callbackId: distanceEventCallbackId
            )
        }
    }
    
    func createPluginInfo(accuracy: Double) -> [String : Any] {
        var isReadyToStart = false;
        
        if accuracy <= accuracyFilter {
            isReadyToStart = true;
        }
        
        let pluginInfo: [String : Any] = ["isReadyToStart": isReadyToStart, "lastCalibrated": "Placeholder", "stepLength": "Placeholder"]
        return pluginInfo
    }
    
    func processLocationEvent(locationEvent: CLLocation) {
        if locationEvent.horizontalAccuracy <= accuracyFilter {
            locationEvents.append(locationEvent)
        }
        
        if locationEvents.count > 1 {
            distanceTraveled = calculateCumulativeDistance()
        }
    }
    
    func calculateCumulativeDistance() -> Int {
        var lastLocationEvent: CLLocation!
        var cumulativeDistance: Double = 0.0
        
        for locationEvent: CLLocation in locationEvents {
            if lastLocationEvent != nil {
                cumulativeDistance += locationEvent.distance(from: lastLocationEvent)
            }
            lastLocationEvent = locationEvent
        }
        
        return Int(cumulativeDistance)
    }
    
}

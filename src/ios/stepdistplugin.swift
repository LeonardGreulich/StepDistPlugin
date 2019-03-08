import CoreLocation
import CoreMotion

@objc(stepdistplugin) class stepdistplugin : CDVPlugin, CLLocationManagerDelegate, StepCounterDelegate {
    
    var locationManager: CLLocationManager!
    var stepCounter: StepCounter!
    
    var locationEvents: [CLLocation]!
    
    var pluginInfoEventCallbackId: String!
    var distanceEventCallbackId: String!
    
    var distanceFilter: Double!
    var accuracyFilter: Double!
    var locationsSequenceDistanceFilter: Double!
    
    var stepLength: Double!
    var calibrationCandidateDistance: Double!
    var distanceTraveledPersistent: Int!
    var distanceTraveledProvisional: Int!
    var stepsTakenPersistent: Int!
    var stepsTakenProvisional: Int!
    var lastCalibrated: Int!
    var calibrationInProgress: Bool!
    
    @objc(startLocalization:) func startLocalization(command: CDVInvokedUrlCommand) {
        pluginInfoEventCallbackId = command.callbackId
        
        guard let arguments = command.arguments.first as? [String: Any] else {
            return
        }
        
        if let distanceFilter = arguments["distanceFilter"] as? Double,
        let accuracyFilter = arguments["accuracyFilter"] as? Double,
        let locationsSequenceDistanceFilter = arguments["locationsSequenceDistanceFilter"] as? Double {
            self.distanceFilter = distanceFilter
            self.accuracyFilter = accuracyFilter
            self.locationsSequenceDistanceFilter = locationsSequenceDistanceFilter
        } else {
            return
        }
    
        if locationManager == nil {
            locationManager = CLLocationManager()
        }
        
        if stepCounter == nil {
            stepCounter = StepCounter()
            stepCounter.delegate = self
        }
        
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = distanceFilter
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
        
        loadStepLength()
        
        sendPluginInfo()
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
        
        locationEvents = []
        distanceTraveledPersistent = 0
        distanceTraveledProvisional = 0
        stepsTakenPersistent = 0
        stepsTakenProvisional = 0
        calibrationInProgress = false
        calibrationCandidateDistance = 0
        
        stepCounter.startStepCounting()
    }

    @objc(stopMeasuringDistance:) func stopMeasuringDistance(command: CDVInvokedUrlCommand) {     
        distanceEventCallbackId = nil
        
        stepCounter.stopStepCounting()
        
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
            sendPluginInfo(accuracy: locationEvent.horizontalAccuracy)
        }
        
        if distanceEventCallbackId != nil {
            processLocationEvent(locationEvent)
        }
    }
    
    func sendPluginInfo(accuracy: Double = 9999.0, debugInfo: String = "") {
        var isReadyToStart = false;

        if roundAccuracy(accuracy) <= accuracyFilter || stepLength != 0.0 {
            isReadyToStart = true;
        }
        
        let pluginInfo: [String : Any] = ["isReadyToStart": isReadyToStart, "debugInfo": debugInfo, "lastCalibrated": lastCalibrated, "stepLength": stepLength]
        
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
    
    func stepCountDidChange(manager: StepCounter, count: Int) {
        stepsTakenProvisional = count-stepsTakenPersistent
        distanceTraveledProvisional = Int(Double(stepsTakenProvisional)*stepLength)
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: ["distanceTraveled": distanceTraveledProvisional + distanceTraveledPersistent, "stepsTaken": stepsTakenProvisional + stepsTakenPersistent]
        )
        pluginResult?.setKeepCallbackAs(true)
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: distanceEventCallbackId
        )
    }
    
    func processLocationEvent(_ locationEvent: CLLocation) {
        // Here, not simply take locationEvents.first.time, as this would give the end-time of the 4m walk, not the start, and would neglect steps in this time
        // Also not use the current locationEvent as we dont have steps for this because of the smoothing timeframe
        if locationEvents.count >= 3 {
            calibrationCandidateDistance = calculateCumulativeDistance(Array(locationEvents[1...locationEvents.count-1]))
            if calibrationCandidateDistance >= locationsSequenceDistanceFilter {
                calibrationInProgress = true
                let calibrationCandidateSteps: Int = stepCounter.getStepsBetween(startDate: locationEvents.first!.timestamp, endDate: locationEvents.last!.timestamp)
                saveStepLength(calibrationCandidateDistance/Double(calibrationCandidateSteps))
                sendPluginInfo()
            } else if calibrationInProgress {
                // As a delegate, this class has the most recent step count data from the step counter
                calibrationInProgress = false
                stepsTakenPersistent += stepsTakenProvisional
                distanceTraveledPersistent += Int(Double(stepsTakenProvisional)*stepLength)
            }
        }
        
        if roundAccuracy(locationEvent.horizontalAccuracy) <= accuracyFilter {
            locationEvents.append(locationEvent)
        } else {
            locationEvents.removeAll()
            calibrationCandidateDistance = 0.0
            sendPluginInfo(debugInfo: "Calibr. cancel.: Accuracy (\(roundAccuracy(locationEvent.horizontalAccuracy)))");
        }
    }
    
    func calculateCumulativeDistance(_ locations: [CLLocation]) -> Double {
        var lastLocation: CLLocation!
        var cumulativeDistance: Double = 0.0
        
        for location: CLLocation in locations {
            if lastLocation != nil {
                cumulativeDistance += location.distance(from: lastLocation)
            }
            lastLocation = location
        }
        
        return cumulativeDistance
    }

    func roundAccuracy(_ accuracy: Double) -> Double {
        return Double(round(10*accuracy)/10)
    }
    
    func loadStepLength() {
        stepLength = UserDefaults.standard.double(forKey: "stepLength")
        lastCalibrated = UserDefaults.standard.integer(forKey: "lastCalibrated")
    }
    
    func saveStepLength(_ stepLength: Double) {
        self.stepLength = stepLength
        self.lastCalibrated = Int(Date().timeIntervalSince1970)
        
        UserDefaults.standard.set(self.stepLength, forKey: "stepLength")
        UserDefaults.standard.set(self.lastCalibrated, forKey: "lastCalibrated")
    }

}

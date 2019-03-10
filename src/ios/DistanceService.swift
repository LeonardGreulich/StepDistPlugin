//
//  DistanceService.swift
//
//  Created by Leonard Greulich on 2/16/19.
//  Copyright © 2019 Leonard Greulich. All rights reserved.
//

import CoreLocation

class DistanceService: NSObject, CLLocationManagerDelegate, StepCounterDelegate {
    
    private var locationManager: CLLocationManager!
    private var stepCounter: StepCounter!
    var delegate: DistanceServiceDelegate!
    
    private var locationEvents: [CLLocation]!
    
    private var horizontalDistanceFilter: Double!
    private var horizontalAccuracyFilter: Double!
    private var verticalDistanceFilter: Double!
    private var verticalAccuracyFilter: Double!
    private var distanceTraveledToCalibrate: Double!
    
    private var stepLength: Double!
    private var calibrationCandidateDistance: Double!
    private var distanceTraveledPersistent: Int!
    private var distanceTraveledProvisional: Int!
    private var stepsTakenPersistent: Int!
    private var stepsTakenProvisional: Int!
    private var lastCalibrated: Int!
    private var calibrationInProgress: Bool!
    private var isTracking: Bool!
    
    init(_ options: [String: Any]) {
        var stepCounterOptions: [String : Any]!
        
        // Try to parse parameter options into variables
        if let horizontalDistanceFilter = options["horizontalDistanceFilter"] as? Double,
        let horizontalAccuracyFilter = options["horizontalAccuracyFilter"] as? Double,
        let verticalDistanceFilter = options["verticalDistanceFilter"] as? Double,
        let verticalAccuracyFilter = options["verticalAccuracyFilter"] as? Double,
        let distanceTraveledToCalibrate = options["distanceTraveledToCalibrate"] as? Double,
        let updateInterval = options["updateInterval"] as? Double,
        let betterFragmentFactor = options["betterFragmentFactor"] as? Double,
        let deviationLength = options["deviationLength"] as? Double,
        let deviationAmplitude = options["deviationAmplitude"] as? Double,
        let smoothingTimeframe = options["smoothingTimeframe"] as? Int {
            // Store location parameters
            self.horizontalDistanceFilter = horizontalDistanceFilter
            self.horizontalAccuracyFilter = horizontalAccuracyFilter
            self.verticalDistanceFilter = verticalDistanceFilter
            self.verticalAccuracyFilter = verticalAccuracyFilter
            self.distanceTraveledToCalibrate = distanceTraveledToCalibrate

            // Prepare dictionary with step counter parameters
            stepCounterOptions = ["updateInterval": updateInterval,
                                  "betterFragmentFactor": betterFragmentFactor,
                                  "deviationLength": deviationLength,
                                  "deviationAmplitude": deviationAmplitude,
                                  "smoothingTimeframe": smoothingTimeframe]
        } else {
            return
        }
        
        if locationManager == nil {
            locationManager = CLLocationManager()
        }
        
        if stepCounter == nil {
            stepCounter = StepCounter(stepCounterOptions)
        }
    }
    
    func startLocalization() {
        stepCounter.delegate = self
        
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = horizontalDistanceFilter
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    
        loadStepLength()
    
        updatePluginInfo()
        
        isTracking = false;
    }
    
    func stopLocalization() {
        locationManager.stopUpdatingLocation();
    }
    
    func startMeasuringDistance() {
        locationEvents = []
        distanceTraveledPersistent = 0
        distanceTraveledProvisional = 0
        stepsTakenPersistent = 0
        stepsTakenProvisional = 0
        calibrationInProgress = false
        calibrationCandidateDistance = 0
        
        stepCounter.startStepCounting()
        
        isTracking = true;
    }
    
    func stopMeasuringDistance() {
        stepCounter.stopStepCounting()
        
        isTracking = false;
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let locationEvent = locations.last else {
            return
        }
        
        updatePluginInfo(accuracy: locationEvent.horizontalAccuracy)
        
        if isTracking {
            processLocationEvent(locationEvent)
        }
    }
    
    func updatePluginInfo(accuracy: Double = 9999.0, debugInfo: String = "") {
        var isReadyToStart = false;
        
        if roundAccuracy(accuracy) <= horizontalAccuracyFilter || stepLength != 0.0 {
            isReadyToStart = true;
        }
        
        delegate.pluginInfoDidChange(manager: self, isReadyToStart: isReadyToStart, debugInfo: debugInfo, lastCalibrated: lastCalibrated, stepLength: stepLength)
    }
    
    func stepCountDidChange(manager: StepCounter, count: Int) {
        stepsTakenProvisional = count-stepsTakenPersistent
        distanceTraveledProvisional = Int(Double(stepsTakenProvisional)*stepLength)
        
        delegate.distanceDidChange(manager: self, distanceTraveled: distanceTraveledPersistent+distanceTraveledProvisional, stepsTaken: stepsTakenPersistent+stepsTakenProvisional)
    }
    
    func processLocationEvent(_ locationEvent: CLLocation) {
        // Here, not simply take locationEvents.first.time, as this would give the end-time of the 4m walk, not the start, and would neglect steps in this time
        // Also not use the current locationEvent as we dont have steps for this because of the smoothing timeframe
        if locationEvents.count >= 3 {
            calibrationCandidateDistance = calculateCumulativeDistance(Array(locationEvents[1...locationEvents.count-1]))
            if calibrationCandidateDistance >= distanceTraveledToCalibrate {
                calibrationInProgress = true
                let calibrationCandidateSteps: Int = stepCounter.getStepsBetween(startDate: locationEvents.first!.timestamp, endDate: locationEvents.last!.timestamp)
                saveStepLength(calibrationCandidateDistance/Double(calibrationCandidateSteps))
                updatePluginInfo()
            } else if calibrationInProgress {
                // As a delegate, this class has the most recent step count data from the step counter
                calibrationInProgress = false
                stepsTakenPersistent += stepsTakenProvisional
                distanceTraveledPersistent += Int(Double(stepsTakenProvisional)*stepLength)
            }
        }
        
        if roundAccuracy(locationEvent.horizontalAccuracy) <= horizontalAccuracyFilter {
            locationEvents.append(locationEvent)
        } else {
            locationEvents.removeAll()
            calibrationCandidateDistance = 0.0
            updatePluginInfo(debugInfo: "Calibr. cancel.: Accuracy (\(roundAccuracy(locationEvent.horizontalAccuracy)))");
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

protocol DistanceServiceDelegate {
    func distanceDidChange(manager: DistanceService, distanceTraveled: Int, stepsTaken: Int)
    func pluginInfoDidChange(manager: DistanceService, isReadyToStart: Bool, debugInfo: String, lastCalibrated: Int, stepLength: Double)
}

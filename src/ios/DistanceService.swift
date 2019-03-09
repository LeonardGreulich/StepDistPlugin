//
//  DistanceService.swift
//
//  Created by Leonard Greulich on 2/16/19.
//  Copyright © 2019 Leonard Greulich. All rights reserved.
//

import CoreLocation

class DistanceService: NSObject, CLLocationManagerDelegate, StepCounterDelegate {
    
    var locationManager: CLLocationManager!
    var stepCounter: StepCounter!
    var delegate: DistanceServiceDelegate!
    
    var locationEvents: [CLLocation]!
    
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
    var isTracking: Bool!
    
    init(_ options: [String: Any]) {
        if let distanceFilter = options["distanceFilter"] as? Double,
            let accuracyFilter = options["accuracyFilter"] as? Double,
            let locationsSequenceDistanceFilter = options["locationsSequenceDistanceFilter"] as? Double {
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
        }
    }
    
    func startLocalization() {
        stepCounter.delegate = self
        
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = distanceFilter
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    
        loadStepLength()
    
        updatePluginInfo()
        
        isTracking = false;
    }
    
    func stopLocalization() {
        locationManager.stopUpdatingLocation();
        
        locationManager = nil
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
        
        if roundAccuracy(accuracy) <= accuracyFilter || stepLength != 0.0 {
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
            if calibrationCandidateDistance >= locationsSequenceDistanceFilter {
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
        
        if roundAccuracy(locationEvent.horizontalAccuracy) <= accuracyFilter {
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

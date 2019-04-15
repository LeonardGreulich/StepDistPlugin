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
    private var altitudeEvents: [Double]!
    
    private var horizontalDistanceFilter: Double!
    private var horizontalAccuracyFilter: Double!
    private var verticalDistanceFilter: Double!
    private var verticalAccuracyFilter: Double!
    private var distanceWalkedToCalibrate: Double!
    private var stepLengthFactor: Double!
    
    private var stepLength: Double!
    private var bodyHeight: Double!
    private var calibrationCandidateDistance: Double!
    private var lastAltitude: Double!
    private var relativeAltitudeGain: Int!
    private var distanceTraveledPersistent: Double!
    private var distanceTraveledProvisional: Double!
    private var distanceTraveledHeuristic: Double!
    private var stepsTakenPersistent: Int!
    private var stepsTakenProvisional: Int!
    private var stepsTakenTotal: Int!
    private var lastCalibrated: Int!
    private var calibrationInProgress: Bool!
    private var isTracking: Bool!
    private var enableGPSCalibration: Bool!
    
    init(_ options: [String: Any]) {
        var stepCounterOptions: [String : Any]!
        
        // Try to parse parameter options into variables
        if let horizontalDistanceFilter = options["horizontalDistanceFilter"] as? Double,
            let horizontalAccuracyFilter = options["horizontalAccuracyFilter"] as? Double,
            let verticalDistanceFilter = options["verticalDistanceFilter"] as? Double,
            let verticalAccuracyFilter = options["verticalAccuracyFilter"] as? Double,
            let distanceWalkedToCalibrate = options["distanceWalkedToCalibrate"] as? Double,
            let stepLengthFactor = options["stepLengthFactor"] as? Double,
            let updateInterval = options["updateInterval"] as? Double,
            let betterStrideFactor = options["betterStrideFactor"] as? Double,
            let deviationLength = options["deviationLength"] as? Double,
            let deviationAmplitude = options["deviationAmplitude"] as? Double,
            let minStrideAmplitude = options["minStrideAmplitude"] as? Double,
            let smoothingTimeframe = options["smoothingTimeframe"] as? Int {
            // Store location parameters
            self.horizontalDistanceFilter = horizontalDistanceFilter
            self.horizontalAccuracyFilter = horizontalAccuracyFilter
            self.verticalDistanceFilter = verticalDistanceFilter
            self.verticalAccuracyFilter = verticalAccuracyFilter
            self.distanceWalkedToCalibrate = distanceWalkedToCalibrate
            self.stepLengthFactor = stepLengthFactor
            
            // Prepare dictionary with step counter parameters
            stepCounterOptions = ["updateInterval": updateInterval,
                                  "betterStrideFactor": betterStrideFactor,
                                  "deviationLength": deviationLength,
                                  "deviationAmplitude": deviationAmplitude,
                                  "minStrideAmplitude": minStrideAmplitude,
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
        
        loadBodyHeight()
        loadStepLength()
        
        updatePluginInfo()
        
        isTracking = false;
    }
    
    func stopLocalization() {
        locationManager.stopUpdatingLocation();
    }
    
    func startMeasuringDistance(_ enableGPSCalibration: Bool) {
        locationEvents = []
        altitudeEvents = []
        distanceTraveledPersistent = 0
        distanceTraveledProvisional = 0
        distanceTraveledHeuristic = 0
        stepsTakenPersistent = 0
        stepsTakenProvisional = 0
        stepsTakenTotal = 0
        calibrationInProgress = false
        calibrationCandidateDistance = 0
        lastAltitude = 0
        relativeAltitudeGain = 0
        
        self.enableGPSCalibration = enableGPSCalibration
        
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
        
        updatePluginInfo(accuracy: locationEvent.horizontalAccuracy, debugInfo: "Accuracy: \(locationEvent.horizontalAccuracy)")
        
        if isTracking {
            processLocationEvent(locationEvent)
        }
    }
    
    func updatePluginInfo(accuracy: Double = 9999.0, debugInfo: String = "") {
        var isReadyToStart = false;
        
        if roundAccuracy(accuracy) <= horizontalAccuracyFilter || stepLength != 0.0 || bodyHeight != 0.0 {
            isReadyToStart = true;
        }
        
        delegate.pluginInfoDidChange(manager: self,
                                     isReadyToStart: isReadyToStart,
                                     debugInfo: debugInfo,
                                     lastCalibrated: lastCalibrated,
                                     stepLength: stepLength,
                                     bodyHeight: bodyHeight)
    }
    
    func stepCountDidChange(manager: StepCounter, count: Int, frequency: Double) {
        stepsTakenProvisional = count-stepsTakenPersistent
        distanceTraveledProvisional = Double(stepsTakenProvisional)*stepLength
        
        let newSteps: Double = Double(count - stepsTakenTotal)
        distanceTraveledHeuristic += newSteps*(stepLengthFactor*bodyHeight!*frequency.squareRoot())
        stepsTakenTotal = count
        
        var distanceTraveled: Int = 0
        if distanceTraveledProvisional+distanceTraveledPersistent == 0.0 && distanceTraveledHeuristic != 0.0 {
            distanceTraveled = Int(round(distanceTraveledHeuristic))
        } else if distanceTraveledProvisional+distanceTraveledPersistent != 0.0 && distanceTraveledHeuristic == 0.0 {
            distanceTraveled = Int(round(distanceTraveledProvisional+distanceTraveledPersistent))
        } else if distanceTraveledProvisional+distanceTraveledPersistent != 0.0 && distanceTraveledHeuristic != 0.0 {
            distanceTraveled = Int(round(((distanceTraveledProvisional+distanceTraveledPersistent)+distanceTraveledHeuristic)/2))
        }
        
        delegate.distanceDidChange(manager: self,
                                   distanceTraveled: distanceTraveled,
                                   stepsTaken: stepsTakenTotal,
                                   relativeAltitudeGain: relativeAltitudeGain)
    }
    
    func processLocationEvent(_ locationEvent: CLLocation) {
        // Here, not simply take locationEvents.first.time, as this would give the end-time of the 4m walk, not the start, and would neglect steps in this time
        // Also not use the current locationEvent as we dont have steps for this because of the smoothing timeframe
        if locationEvents.count >= 3 && enableGPSCalibration {
            calibrationCandidateDistance = calculateCumulativeDistance(Array(locationEvents[1...locationEvents.count-1]))
            if calibrationCandidateDistance >= distanceWalkedToCalibrate {
                calibrationInProgress = true
                let calibrationCandidateSteps: Int = stepCounter.getStepsBetween(startDate: locationEvents.first!.timestamp, endDate: locationEvents.last!.timestamp)
                saveStepLength(calibrationCandidateDistance/Double(calibrationCandidateSteps))
                updatePluginInfo()
            } else if calibrationInProgress {
                // As a delegate, this class has the most recent step count data from the step counter
                calibrationInProgress = false
                stepsTakenPersistent += stepsTakenProvisional
                distanceTraveledPersistent += Double(stepsTakenProvisional)*stepLength
            }
        }
        
        if roundAccuracy(locationEvent.horizontalAccuracy) <= horizontalAccuracyFilter {
            locationEvents.append(locationEvent)
        } else {
            locationEvents.removeAll()
            calibrationCandidateDistance = 0.0
            updatePluginInfo(debugInfo: "Calibr. cancel.: Accuracy (\(roundAccuracy(locationEvent.horizontalAccuracy)))")
        }
        
        if locationEvent.horizontalAccuracy <= horizontalAccuracyFilter {
            updateRelativeAltitude(locationEvent.altitude)
        } else {
            altitudeEvents.removeAll()
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
    
    func updateRelativeAltitude(_ currentApproximateAltitude: Double) {
        altitudeEvents.append(currentApproximateAltitude);
        if altitudeEvents.count == Int(verticalDistanceFilter) {
            var sumAltitudes: Double = 0.0
            var sumDiffAltitudes: Double = 0.0
            for i in 0...Int(verticalDistanceFilter)-2 {
                sumAltitudes += altitudeEvents[i]
                sumDiffAltitudes += abs(altitudeEvents[i+1] - altitudeEvents[i])
            }
            sumAltitudes += altitudeEvents[Int(verticalDistanceFilter)-1];
            if sumDiffAltitudes >= 1 {
                return
            }
            let currentAltitude: Double = sumAltitudes / verticalDistanceFilter
            if lastAltitude != 0.0 {
                let relativeAltitude: Double = currentAltitude - lastAltitude;
                if (relativeAltitude >= 0) {
                    relativeAltitudeGain += Int(round(relativeAltitude))
                }
            }
            lastAltitude = currentAltitude
            altitudeEvents.remove(at: 0)
        }
    }
    
    func roundAccuracy(_ accuracy: Double) -> Double {
        return Double(round(10*accuracy)/10)
    }
    
    func loadBodyHeight() {
        bodyHeight = UserDefaults.standard.double(forKey: "bodyHeight")
    }
    
    func saveBodyHeight(_ bodyHeight: Double) {
        self.bodyHeight = bodyHeight
        
        UserDefaults.standard.set(self.bodyHeight, forKey: "bodyHeight")
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
    
    func resetData() {
        UserDefaults.standard.removeObject(forKey: "bodyHeight")
        UserDefaults.standard.removeObject(forKey: "stepLength")
        UserDefaults.standard.removeObject(forKey: "lastCalibrated")
        
        loadBodyHeight()
        loadStepLength()
    }
}

protocol DistanceServiceDelegate {
    func distanceDidChange(manager: DistanceService, distanceTraveled: Int, stepsTaken: Int, relativeAltitudeGain: Int)
    func pluginInfoDidChange(manager: DistanceService, isReadyToStart: Bool, debugInfo: String, lastCalibrated: Int, stepLength: Double, bodyHeight: Double)
}

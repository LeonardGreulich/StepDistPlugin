//
//  StepCounter.swift
//
//  Created by Leonard Greulich on 2/16/19.
//  Copyright © 2019 Leonard Greulich. All rights reserved.
//

import Foundation
import CoreMotion

class StepCounter {
    
    private var motionManager: CMMotionManager!
    var delegate: StepCounterDelegate!
    
    // Parameters
    private var updateInterval: Double! // Sets how often new data from the motion sensors should be received
    private var bFF: Double! // Better fragment factor, when a newer fragment is regarded better
    private var dL: Double! // Deviation length, allowed deviation in length to regard fragments as similar
    private var dA: Double! // Deviation amplitude, allowed deviation in amplitude to regard fragments as similar
    private var rT: Int! // Smoothing timeframe
    
    // Raw gravity data and information about maxima and minima
    private var gravityData: [[Double]] = [[], [], []] // Two dimensional array that holds all gravity datapoints, each having a x-, y-, and z-axis
    private var gravityFlag: [[Int8]] = [[0], [0], [0]] // Two dimensional array that holds for each gravity point whether its a maxima (1), a minima(-1), or none (0)
    
    // Supplementary variables
    private var representativeFragment: Fragment! // Holds the representative fragment as soon as one is found — every new incoming fragment is compared to this one
    private var pastThreeExtremaX: [[Int]] = [[], [], []] // Hold the x values of the past three extrema
    private var pastThreeExtremaY: [[Double]] = [[], [], []] // Holds the y values of the past three extrema
    private var fragments: [[Fragment]] = [[], [], []] // Holds all found fragments
    private var similarities: [[Bool]] = [[], [], []] // Holds all information of comparison of fragments and whether they are similar or not
    private var reprFragmentOfAxis: [Int] = [] // If a representative fragment has been found, its axis is appended to this array
    private var i: Int = 0 // Simple running index to performantly know how many data points has been stored and processed
    private var currentStepDates: [Date] = [] // Holds the date and time for each currently found step
    private var precedingStepDates: [Date] = [] // Holds the date and time for each previously found step
    
    init(_ options: [String: Any]) {
        if let updateInterval = options["updateInterval"] as? Double,
        let betterFragmentFactor = options["betterFragmentFactor"] as? Double,
        let deviationLength = options["deviationLength"] as? Double,
        let deviationAmplitude = options["deviationAmplitude"] as? Double,
        let smoothingTimeframe = options["smoothingTimeframe"] as? Int {
            self.updateInterval = updateInterval
            self.bFF = betterFragmentFactor
            self.dL = deviationLength
            self.dA = deviationAmplitude
            self.rT = smoothingTimeframe
        }
    }
    
    func startStepCounting() {
        resetData()
        
        if motionManager == nil {
            motionManager = CMMotionManager()
        }
        
        motionManager.deviceMotionUpdateInterval = updateInterval
        motionManager.startDeviceMotionUpdates(to: OperationQueue.current!) { (data, error) in
            if let motionData = data {
                self.processMotionData(x: motionData.gravity.x, y: motionData.gravity.y, z: motionData.gravity.z)
            }
        }
    }
    
    func stopStepCounting() {
        guard motionManager != nil else {
            return
        }
        
        motionManager.stopDeviceMotionUpdates()
    }
    
    private func resetData() {
        //First, reset motion data and information about maxima and minima
        gravityData = [[], [], []]
        gravityFlag = [[0], [0], [0]]
        
        // Second, reset supplementary variables
        representativeFragment = Fragment()
        pastThreeExtremaX = [[], [], []]
        pastThreeExtremaY = [[], [], []]
        fragments = [[], [], []]
        similarities = [[], [], []]
        reprFragmentOfAxis = []
        i = 0
        currentStepDates = []
        precedingStepDates = []
    }
    
    private func processMotionData(x: Double, y: Double, z: Double) {
        // First, simply store the new incoming data points in the gravity and accelerometer array
        self.gravityData[0].append(x)
        self.gravityData[1].append(y)
        self.gravityData[2].append(z)
        
        // Second, calculate for each new incoming point whether it is an maximina (1), minima(-1), or none(0)
        if i >= 2 {
            for axis in 0...2 {
                gravityFlag[axis].append(setMinimaMaxima(Array(gravityData[axis][i-2...i])))
            }
        }
        
        // If we have enough data points to apply the smoothing algorithm ...
        if i >= rT {
            for axis in 0...2 {
                // ... we apply the smoothing algorithm to this part for every axis
                let result = smoothSubgraph(Array(gravityData[axis][i-rT...i-1]), Array(gravityFlag[axis][i-rT...i-1]))
                gravityData[axis].replaceSubrange(i-rT...i-1, with: result.0)
                gravityFlag[axis].replaceSubrange(i-rT...i-1, with: result.1)
                
                // Now we shift the point of consideration to the left, so that we only look at smoothed data -> (i-rT)
                // If this smoothe point of consideration is a minima or maxima ...
                if gravityFlag[axis][i-rT] != 0 {
                    // ... append it to the respective array
                    pastThreeExtremaX[axis].append(i-rT)
                    pastThreeExtremaY[axis].append(gravityData[axis][i-rT])
                    // If we have gathered three maxima or minima, we can build our first fragment
                    if pastThreeExtremaX[axis].count >= 3 {
                        let fragment: Fragment = createFragment(pastThreeExtremaX[axis], pastThreeExtremaY[axis], gravityFlag[axis][i-rT], axis)
                        fragments[axis].append(fragment)
                        pastThreeExtremaX[axis].removeFirst()
                        pastThreeExtremaY[axis].removeFirst()
                    }
                    // Once we have collected three or more fragments, we can start to compare them (the last vs the third-last to compare the same type)
                    if fragments[axis].count >= 3 {
                        similarities[axis].append(areFragmentsSimilar(fragments[axis][fragments[axis].count-3], fragments[axis][fragments[axis].count-1]))
                    }
                    // Finally, if we have collected the results of 5 or more comparisons, we can check if there is a pattern and, perhaps, ...
                    // ... set the representative fragment (or change it if we find a better one)
                    if !reprFragmentOfAxis.contains(axis) && similarities[axis].count >= 5 {
                        if similarities[axis][similarities[axis].count-5] && similarities[axis][similarities[axis].count-3] && similarities[axis][similarities[axis].count-1] {
                            if fragments[axis][fragments[axis].count-1].amplitude > representativeFragment.amplitude*bFF {
                                representativeFragment = createRepresentativeFragment([fragments[axis][fragments[axis].count-5], fragments[axis][fragments[axis].count-3], fragments[axis][fragments[axis].count-1]])
                                reprFragmentOfAxis.append(axis)
                                initializeStepDates(representativeFragment, 6)
                            }
                        }
                    }
                    // After we have found a representative fragment we compare new incoming fragments of the same axis to it and possibly increase the counter
                    // If there is no similarity, we re-initialize the representative fragment and similarities to look for a new pattern
                    if reprFragmentOfAxis.count != 0 && representativeFragment.axis == axis && representativeFragment.fragmentType == fragments[axis][fragments[axis].count-1].fragmentType {
                        if areFragmentsSimilar(representativeFragment, fragments[axis][fragments[axis].count-1]) {
                            addStepDates(fragments[axis][fragments[axis].count-1], 2)
                            delegate.stepCountDidChange(manager: self, count: getStepsTotal(), frequency: getStepsPerSecond(fragment: fragments[axis][fragments[axis].count-1]))
                        } else {
                            representativeFragment = Fragment()
                            reprFragmentOfAxis = []
                            similarities = [[], [], []]
                            precedingStepDates.append(contentsOf: currentStepDates)
                            currentStepDates.removeAll()
                        }
                    }
                }
            }
            // If the phone moves slowly in the pocket it may happen that another axis fulfils the betterFragmentFactor at some time
            // To avoid that previous steps are overwritten, prevent that a better axis is found after 15 steps
            // If a fragment does not fit the representative fragment after a phone movement in the pocket, a new pattern is searched in all axes again in the code above
            if currentStepDates.count >= 15 {
                reprFragmentOfAxis = [0, 1, 2]
            }
        }
        
        i += 1
    }
    
    // Based on three points, this method returns whether the point in the middle is a maxima (1), a minima(-1), or none (0)
    private func setMinimaMaxima(_ threePoints: [Double]) -> Int8 {
        if (threePoints[0] < threePoints[1] && threePoints[2] <= threePoints[1]) {
            return 1
        } else if (threePoints[0] > threePoints[1] && threePoints[2] >= threePoints[1]) {
            return -1
        } else {
            return 0
        }
    }
    
    // This function takes an array of datapoints (their y-values) and whether they are maxima, minima, or none to smooth the datapoints
    // Smoothing means that small distortions are removed while retaining the original height of maxima and minima
    private func smoothSubgraph(_ dataPoints: [Double], _ flags: [Int8]) -> ([Double], [Int8]) {
        var processedPoints = dataPoints
        var processedFlags = flags
        var foundExtreme: Bool = false
        var firstExtremePos: Int = 0
        
        for i in 0...processedPoints.count-1 {
            if foundExtreme && processedFlags[i] == processedFlags[firstExtremePos] {
                processedPoints.replaceSubrange(firstExtremePos+1...i-1, with: Array(repeating: (processedPoints[firstExtremePos] + processedPoints[i])/2, count: i-firstExtremePos-1))
                processedFlags.replaceSubrange(firstExtremePos+1...i-1, with: Array(repeating: Int8(0), count: i-firstExtremePos-1))
                if processedFlags[i] == 1 {
                    if processedPoints[firstExtremePos] > processedPoints[i] {
                        processedFlags[i] = 0
                    } else {
                        processedFlags[firstExtremePos] = 0
                    }
                } else {
                    if processedPoints[firstExtremePos] > processedPoints[i] {
                        processedFlags[firstExtremePos] = 0
                    } else {
                        processedFlags[i] = 0
                    }
                }
                return (processedPoints, processedFlags)
            }
            if !foundExtreme && processedFlags[i] != 0 {
                foundExtreme = true
                firstExtremePos = i
            }
        }
        return (processedPoints, processedFlags)
    }
    
    // Helper function to create a new fragment. The if-else block distinguished between a max-min-max and a min-max-min fragment
    private func createFragment(_ xValues: [Int], _ yValues: [Double], _ maxOrMin: Int8, _ axis: Int) -> Fragment {
        if maxOrMin == 1 {
            return Fragment((yValues[0] + yValues[2])/2, yValues[1], xValues[1] - xValues[0], xValues[2] - xValues[1], axis, .MaxMinMax)
        } else {
            return Fragment(yValues[1], (yValues[0] + yValues[2])/2, xValues[1] - xValues[0], xValues[2] - xValues[1], axis, .MinMaxMin)
        }
    }
    
    // Helper function to create a representative fragment that is composed of the average values of similar fragments
    private func createRepresentativeFragment(_ fragments: [Fragment]) -> Fragment {
        let axes: [Int] = fragments.map { $0.axis }
        guard !axes.contains(where: { $0 != axes.first! }) else {
            return Fragment()
        }
        
        let fragmentTypes: [Fragment.orders] = fragments.map { $0.fragmentType }
        guard !fragmentTypes.contains(where: { $0 != fragmentTypes.first! }) else {
            return Fragment()
        }
        
        let amplitudes: [Double] = fragments.map { $0.amplitude }
        let lengths: [Int] = fragments.map { $0.lengthTotal }
        let amplitudesMean: Double = amplitudes.reduce(0, +) / Double(amplitudes.count)
        let lengthsMean: Int = Int(round(Double(lengths.reduce(0, +)) / Double(lengths.count)))
        
        return Fragment(amplitudesMean, lengthsMean, axes.first!, fragmentTypes.first!)
    }
    
    // Helper function to populate the stepDates array with the dates of all found steps, but not for the most recent ones
    // Function considers the time shift caused by the smoothing algorithm and considers the fact that one fragment represents two steps
    private func initializeStepDates(_ fragment: Fragment, _ numberOfSteps: Int) {
        var currentDate: Date = Date()
        
        // Clear the stepDates array
        currentStepDates.removeAll()
        
        // Subtract the time shift caused by the smoothing algorithm
        let rTInSeconds: Double = updateInterval*Double(rT);
        currentDate.addTimeInterval(-rTInSeconds)
        
        // Each fragment represents two steps, which equaly one stride. Assume that the length of one step is half of the stride
        // Also, subtract one additional stepLengthInSeconds to compensate for the fact that the most recent stride does not belong to the steps in this method
        let stepLengthInSeconds: Double = Double(fragment.lengthTotal)*updateInterval/2
        currentDate.addTimeInterval(-stepLengthInSeconds)
        for _ in 0...numberOfSteps-1 {
            currentDate.addTimeInterval(-stepLengthInSeconds)
            currentStepDates.append(currentDate)
        }
    }
    
    // Helper function to add step dates to the stepDates array, similar to the initializeStepDates but for the most recent ones
    private func addStepDates(_ fragment: Fragment, _ numberOfSteps: Int) {
        var currentDate: Date = Date()
        
        // Subtract the time shift caused by the smoothing algorithm and add the last found step right away
        let rTInSeconds: Double = updateInterval*Double(rT);
        currentDate.addTimeInterval(-rTInSeconds)
        currentStepDates.append(currentDate)
        
        // Each fragment represents two steps, which equaly one stride. Assume that the length of one step is half of the stride
        let stepLengthInSeconds: Double = Double(fragment.lengthTotal)*updateInterval/2
        for _ in 0...numberOfSteps-2 {
            currentDate.addTimeInterval(-stepLengthInSeconds)
            currentStepDates.append(currentDate)
        }
    }
    
    // Compares two fragments and returns a boolean indicating whether they are similar or not
    private func areFragmentsSimilar(_ fragmentOne: Fragment, _ fragmentTwo: Fragment) -> Bool {
        let diffLength: Double = Double(abs(fragmentOne.lengthTotal - fragmentTwo.lengthTotal))/Double(fragmentOne.lengthTotal)
        let diffAmplitude: Double = abs(fragmentOne.amplitude - fragmentTwo.amplitude)/fragmentOne.amplitude
        
        return (diffLength <= dL && diffAmplitude <= dA)
    }
    
    // Simple function to return the total number of steps
    func getStepsTotal() -> Int {
        return precedingStepDates.count+currentStepDates.count
    }
    
    // Returns all steps in a given timeframe
    func getStepsBetween(startDate: Date, endDate: Date) -> Int {
        let allStepDates: [Date] = precedingStepDates+currentStepDates
        let allStepDatesBetween: [Date] = allStepDates.filter { $0 >= startDate && $0 <= endDate }
        
        return allStepDatesBetween.count
    }
    
    // Returns the estimated number of steps per minute
    // Also, compensate for time shift caused by the smoothing algorithm by only considering steps in a window where smoothed data is available
    func getStepsPerMinute() -> Int {
        let rTInSeconds: Double = updateInterval*Double(rT);
        let startDate15Seconds: Date = Date().addingTimeInterval(-(rTInSeconds+15))
        let endDate15Seconds: Date = Date().addingTimeInterval(-(rTInSeconds))
        
        return getStepsBetween(startDate: startDate15Seconds, endDate: endDate15Seconds)*4
    }
    
    // Returns the current step frequency based on a fragment
    func getStepsPerSecond(fragment: Fragment) -> Double {
        let stepDurationInSeconds: Double = Double(fragment.lengthTotal)*updateInterval*0.5
        
        return 1/stepDurationInSeconds
    }
    
}

protocol StepCounterDelegate {
    func stepCountDidChange(manager: StepCounter, count: Int, frequency: Double)
}

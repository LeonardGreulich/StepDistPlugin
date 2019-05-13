//
// stepdistplugin.swift
//
// Created by Leonard Greulich on 2/16/19.
// Copyright © 2019 Leonard Greulich. All rights reserved.
//
// Contains native interface class.
// Manages the communication with the JavaScript interface and the life cycle of the plugin.
//

@objc(stepdistplugin) class stepdistplugin : CDVPlugin, DistanceServiceDelegate {
    
    var distanceService: DistanceService!
    
    var pluginInfoEventCallbackId: String!
    var distanceEventCallbackId: String!
    
    // Plugin life cycle method. Starts the localization in order to get a GNSS fix for the step length calibration.
    // The localization also initializes the background execution.
    @objc(startLocalization:) func startLocalization(command: CDVInvokedUrlCommand) {
        pluginInfoEventCallbackId = command.callbackId
        
        guard let options = command.arguments.first as? [String: Any] else {
            return
        }
        
        if distanceService == nil {
            distanceService = DistanceService(options)
            distanceService.delegate = self
        }
        
        distanceService.startLocalization()
    }

    // Plugin life cycle method. Stops the localization and background execution.
    @objc(stopLocalization:) func stopLocalization(command: CDVInvokedUrlCommand) {
        distanceService.stopLocalization()

        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK
        )
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: pluginInfoEventCallbackId
        )

        pluginInfoEventCallbackId = nil
    }
    
    // Starts the main distance estimation and step length calibration.
    @objc(startMeasuringDistance:) func startMeasuringDistance(command: CDVInvokedUrlCommand) {     
        distanceEventCallbackId = command.callbackId
        
        if let enableGPSCalibration = command.arguments.first as? Bool {
            distanceService.startMeasuringDistance(enableGPSCalibration)
        }
        
        distanceDidChange(manager: distanceService, distanceTraveled: 0, stepsTaken: 0, relativeAltitudeGain: 0)
    }

    // Stops the main distance estimation and step length calibration.
    @objc(stopMeasuringDistance:) func stopMeasuringDistance(command: CDVInvokedUrlCommand) {
        distanceService.stopMeasuringDistance()
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK
        )
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )

        distanceEventCallbackId = nil
    }
    
    // Sets the body height and enables the heuristic formula to estimate the walking distance based on step frequency and body height.
    @objc(setBodyHeight:) func setBodyHeight(command: CDVInvokedUrlCommand) {
        if let bodyHeight = command.arguments.first as? Double {
            distanceService.saveBodyHeight(bodyHeight)
            distanceService.updatePluginInfo()
        }
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK
        )
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }
    
    // Erases all persisted data (step length, calibration date, and body height)
    @objc(resetData:) func resetData(command: CDVInvokedUrlCommand) {
        distanceService.resetData()
        distanceService.updatePluginInfo()
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK
        )
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }
    
    // Called from within the DistanceService. Sends distance, steps, and elevation to the plugin interface.
    func distanceDidChange(manager: DistanceService, distanceTraveled: Int, stepsTaken: Int, relativeAltitudeGain: Int) {
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: ["distance": distanceTraveled,
                        "steps": stepsTaken,
                        "elevation": relativeAltitudeGain]
        )
        pluginResult?.setKeepCallbackAs(true)
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: distanceEventCallbackId
        )
    }
    
    // Called from within the DistanceService. Sends status information to the plugin interface.
    func pluginInfoDidChange(manager: DistanceService, isReadyToStart: Bool, debugInfo: String, lastCalibrated: Int, stepLength: Double, bodyHeight: Double) {
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: ["isReadyToStart": isReadyToStart,
                        "debugInfo": debugInfo,
                        "lastCalibrated": lastCalibrated,
                        "stepLength": stepLength,
                        "bodyHeight": bodyHeight]
        )
        pluginResult?.setKeepCallbackAs(true)
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: pluginInfoEventCallbackId
        )
    }

}

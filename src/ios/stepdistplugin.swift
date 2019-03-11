//
//  stepdistplugin.swift
//
//  Created by Leonard Greulich on 2/16/19.
//  Copyright © 2019 Leonard Greulich. All rights reserved.
//

@objc(stepdistplugin) class stepdistplugin : CDVPlugin, DistanceServiceDelegate {
    
    var distanceService: DistanceService!
    
    var pluginInfoEventCallbackId: String!
    var distanceEventCallbackId: String!
    
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
    
    @objc(startMeasuringDistance:) func startMeasuringDistance(command: CDVInvokedUrlCommand) {     
        distanceEventCallbackId = command.callbackId
        
        distanceService.startMeasuringDistance()
    }

    @objc(stopMeasuringDistance:) func stopMeasuringDistance(command: CDVInvokedUrlCommand) {
        distanceService.stopMeasuringDistance()
        
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK
        )
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: distanceEventCallbackId
        )

        distanceEventCallbackId = nil
    }
    
    func distanceDidChange(manager: DistanceService, distanceTraveled: Int, stepsTaken: Int, relativeAltitudeGain: Int) {
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: ["distanceTraveled": distanceTraveled,
                        "stepsTaken": stepsTaken,
                        "relativeAltitudeGain": relativeAltitudeGain]
        )
        pluginResult?.setKeepCallbackAs(true)
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: distanceEventCallbackId
        )
    }
    
    func pluginInfoDidChange(manager: DistanceService, isReadyToStart: Bool, debugInfo: String, lastCalibrated: Int, stepLength: Double) {
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK,
            messageAs: ["isReadyToStart": isReadyToStart,
                        "debugInfo": debugInfo,
                        "lastCalibrated": lastCalibrated,
                        "stepLength": stepLength]
        )
        pluginResult?.setKeepCallbackAs(true)
        
        self.commandDelegate!.send(
            pluginResult,
            callbackId: pluginInfoEventCallbackId
        )
    }

}

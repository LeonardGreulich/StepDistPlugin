//
// stepdistplugin.js
//
// Created by Leonard Greulich on 2/16/19.
// Copyright © 2019 Leonard Greulich. All rights reserved.
//
// Plugin interface. Provides methods and events for a parent Cordova application.
// Moreover, it lists all parameters for the algorithm that are passed to the respective platform implementations.
// Thereby, it manages the communication with the native interface and also the life cycle of the plugin.
//

var cordova = require('cordova');
var exec = require('cordova/exec');

// Parameters for location-based side of the algorithm.
var horizontalDistanceFilter = 4;
var horizontalAccuracyFilter = 8;
var verticalDistanceFilter = 4;
var verticalAccuracyFilter = 10;
var distanceWalkedToCalibrate = 40;

// Parameters for step-counting-based side of the algorithm.
var updateInterval = 0.1;
var stepLengthFactor = 0.33;
var betterStrideFactor = 1.2;
var deviationLength = 0.35;
var deviationAmplitude = 0.35;
var minStrideAmplitude = 0.2;
var smoothingTimeframe = 6;

// Enable GNSS calibration by default.
var enableGNSSCalibration = true;

// Registers event channels for distance and plugin status events.
// Also, registers listeners for plugin life cycle methods.
var Stepdistplugin = function() {
    this.channels = {
        walkingdistance: cordova.addDocumentEventHandler("walkingdistance"),
        stepdiststatus: cordova.addDocumentEventHandler("stepdiststatus")
    }

    this.channels.walkingdistance.onHasSubscribersChange = onWalkingDistanceHasSubscibersChange;

    document.addEventListener("pause", onPause, false);
    document.addEventListener("resume", onResume, false);
    document.addEventListener("backbutton", onBackbuttonPressed, false);

    startLocalization();
}

// Starts and stops the distance estimation as soon as one listener for distance events ... 
// ... has been registered within the parent Cordova application.
var onWalkingDistanceHasSubscibersChange = function() {
    if (stepdistplugin.channels.walkingdistance.numHandlers === 1) {
        console.log("At least one walking distance listener registered");
        exec(onDistanceWalked, error, "stepdistplugin", "startMeasuringDistance", [enableGNSSCalibration]);
    } else if (stepdistplugin.channels.walkingdistance.numHandlers === 0) {
        console.log("No walking distance listener registered");
        exec(success, error, "stepdistplugin", "stopMeasuringDistance", []);
    }
}

var onPause = function() {
    console.log("On pause");
    if (stepdistplugin.channels.walkingdistance.numHandlers === 0) {
        stopLocalization();
    }
}

var onResume = function() {
    console.log("On resume");
    if (stepdistplugin.channels.walkingdistance.numHandlers === 0) {
        startLocalization();
    }
}

var onBackbuttonPressed = function() {
    console.log("Back button pressed");
    // Do not close the app when pressing back (default behavior), as it could result in an accidental stop of the algorithm.
}

// Pass parameters to the native platform implementation (Android or iOS) to ensure inter-platform comparability.
var startLocalization = function() {
    console.log("Start localization");

    var options = {
        horizontalDistanceFilter: horizontalDistanceFilter,
        horizontalAccuracyFilter: horizontalAccuracyFilter,
        verticalDistanceFilter: verticalDistanceFilter,
        verticalAccuracyFilter: verticalAccuracyFilter,
        distanceWalkedToCalibrate: distanceWalkedToCalibrate,
        updateInterval: updateInterval,
        stepLengthFactor: stepLengthFactor,
        betterStrideFactor: betterStrideFactor,
        deviationLength: deviationLength,
        deviationAmplitude: deviationAmplitude,
        minStrideAmplitude: minStrideAmplitude,
        smoothingTimeframe: smoothingTimeframe
      };
      
    exec(onPluginStatusEvent, error, "stepdistplugin", "startLocalization", [options]);
}

var stopLocalization = function() {
    console.log("Stop localization");
    exec(success, error, "stepdistplugin", "stopLocalization", []);
}

var success = function() {
    console.log("Success!");
}

var error = function() {
    console.log("Error!");
}

// Fire event that contains distance, step, and elevation information
var onDistanceWalked = function(walkingDistanceEvent) {
    cordova.fireDocumentEvent("walkingdistance", walkingDistanceEvent);
}

// Prepare and fire event that contains plugin status information
var onPluginStatusEvent = function(pluginStatusEvent) {
    cordova.fireDocumentEvent("stepdiststatus", prepareStatusEvent(pluginStatusEvent.isReadyToStart,
        pluginStatusEvent.debugInfo,
        pluginStatusEvent.lastCalibrated,
        pluginStatusEvent.stepLength,
        pluginStatusEvent.bodyHeight));
}

function prepareStatusEvent(isReadyToStart, debugInfo, lastCalibrated, stepLength, bodyHeight) {
    var lastCalibratedString;
    if (lastCalibrated === 0) {
        lastCalibratedString = null;
    } else {
        lastCalibratedString = unixTimestampToDateString(lastCalibrated);
    }

    var stepLengthString;
    if (stepLength === 0.0) {
        stepLengthString = null;
    } else {
        stepLengthString = stepLength.toFixed(2);
    }

    var bodyHeightString;
    if (bodyHeight === 0.0) {
        bodyHeightString = null;
    } else {
        bodyHeightString = bodyHeight.toFixed(2);
    }

    return {isReadyToStart: isReadyToStart,
        debugInfo: debugInfo,
        lastCalibrated: lastCalibratedString,
        stepLength: stepLengthString,
        bodyHeight: bodyHeightString};
}

function unixTimestampToDateString(timestamp) {
    var date = new Date(timestamp*1000);
    return date.toLocaleString();
}

var stepdistplugin = new Stepdistplugin();

// Methods that are exposed to the parent Cordova application
module.exports = {
    setBodyHeight: function(bodyHeight) {
        exec(success, error, "stepdistplugin", "setBodyHeight", [bodyHeight]);
    },

    disableGNSSCalibration: function(disable = true) {
        enableGNSSCalibration = !disable;
    },

    resetData: function() {
        exec(success, error, "stepdistplugin", "resetData", []);
    }
}

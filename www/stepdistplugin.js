var cordova = require('cordova');
var exec = require('cordova/exec');

// Parameters for location-based side of the algorithm
var horizontalDistanceFilter = 2;
var horizontalAccuracyFilter = 8;
var verticalDistanceFilter = 5;
var verticalAccuracyFilter = 10;
var distanceTraveledToCalibrate = 40;

// Parameters for step-counting-based side of the algorithm
var updateInterval = 0.1;
var betterStrideFactor = 1.2;
var deviationLength = 0.35;
var deviationAmplitude = 0.35;
var smoothingTimeframe = 8;

// Enable GPS calibration by default
var enableGPSCalibration = true;

var Stepdistplugin = function() {
    this.channels = {
        distancetraveled: cordova.addDocumentEventHandler("distancetraveled"),
        isreadytostart: cordova.addDocumentEventHandler("isreadytostart"),
        lastcalibration: cordova.addDocumentEventHandler("lastcalibration")
    }

    this.channels.distancetraveled.onHasSubscribersChange = onTraveledDistanceHasSubscibersChange;

    document.addEventListener("pause", onPause, false);
    document.addEventListener("resume", onResume, false);
    document.addEventListener("backbutton", onBackbuttonPressed, false);

    startLocalization();
}

var onTraveledDistanceHasSubscibersChange = function() {
    if (stepdistplugin.channels.distancetraveled.numHandlers === 1) {
        console.log("At least one traveled distance listener registered");
        exec(onDistanceTraveled, error, "stepdistplugin", "startMeasuringDistance", [enableGPSCalibration]);
    } else if (stepdistplugin.channels.distancetraveled.numHandlers === 0) {
        console.log("No traveled distance listener registered");
        exec(success, error, "stepdistplugin", "stopMeasuringDistance", []);
    }
}

var onPause = function() {
    console.log("On pause");
    if (stepdistplugin.channels.distancetraveled.numHandlers === 0) {
        stopLocalization();
    }
}

var onResume = function() {
    console.log("On resume");
    if (stepdistplugin.channels.distancetraveled.numHandlers === 0) {
        startLocalization();
    }
}

var onBackbuttonPressed = function() {
    console.log("On back button pressed");
    // Do not close the app when pressing back (default behavior), as it could result in an accidental stop of the algorithm
}

var startLocalization = function() {
    console.log("Start localization");

    var options = {
        horizontalDistanceFilter: horizontalDistanceFilter,
        horizontalAccuracyFilter: horizontalAccuracyFilter,
        verticalDistanceFilter: verticalDistanceFilter,
        verticalAccuracyFilter: verticalAccuracyFilter,
        distanceTraveledToCalibrate: distanceTraveledToCalibrate,
        updateInterval: updateInterval,
        betterStrideFactor: betterStrideFactor,
        deviationLength: deviationLength,
        deviationAmplitude: deviationAmplitude,
        smoothingTimeframe: smoothingTimeframe
      };
      
    exec(pluginInfoEvent, error, "stepdistplugin", "startLocalization", [options])
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

var pluginInfoEvent = function(pluginInfoEvent) {
    cordova.fireDocumentEvent("isreadytostart", {isReadyToStart: pluginInfoEvent.isReadyToStart});
    cordova.fireDocumentEvent("lastcalibration", prepareLastCalibrationEvent(pluginInfoEvent.debugInfo,
        pluginInfoEvent.lastCalibrated,
        pluginInfoEvent.stepLength,
        pluginInfoEvent.bodyHeight));
}

var onDistanceTraveled = function(distanceTraveledEvent) {
    cordova.fireDocumentEvent("distancetraveled", [distanceTraveledEvent]);
}

function prepareLastCalibrationEvent(debugInfo, lastCalibrated, stepLength, bodyHeight) {
    var lastCalibratedString;
    if (lastCalibrated === 0) {
        lastCalibratedString = "--"
    } else {
        lastCalibratedString = unixTimestampToDateString(lastCalibrated)
    }

    var stepLengthString;
    if (stepLength === 0.0) {
        stepLengthString = "--"
    } else {
        stepLengthString = stepLength.toFixed(2);
    }

    var bodyHeightString;
    if (bodyHeight === 0.0) {
        bodyHeightString = "--"
    } else {
        bodyHeightString = bodyHeight.toFixed(2);
    }

    return {debugInfo: debugInfo,
        lastCalibrated: lastCalibratedString,
        stepLength: stepLengthString,
        bodyHeight: bodyHeightString}
}

function unixTimestampToDateString(timestamp) {
    var date = new Date(timestamp*1000);
    return date.toLocaleString();
}

var stepdistplugin = new Stepdistplugin();

module.exports = {
    setBodyHeight: function(bodyHeight) {
        exec(success, error, "stepdistplugin", "setBodyHeight", [bodyHeight]);
    },

    disableGPSCalibration: function(disable = true) {
        enableGPSCalibration = !disable;
    },

    resetData: function() {
        exec(success, error, "stepdistplugin", "resetData", []);
    }
}

var cordova = require('cordova');
var exec = require('cordova/exec');

var distanceFilter = 3;
var accuracyFilter = 8;
var perpendicularDistanceFilter = 0.00003;
var locationsSequenceFilter = 8;
var locationsSequenceDistanceFilter = 30;

var Stepdistplugin = function() {
    this.channels = {
        distancetraveled: cordova.addDocumentEventHandler("distancetraveled"),
        isreadytostart: cordova.addDocumentEventHandler("isreadytostart"),
        lastcalibration: cordova.addDocumentEventHandler("lastcalibration")
    }

    this.channels.distancetraveled.onHasSubscribersChange = onTraveledDistanceHasSubscibersChange;

    document.addEventListener("pause", onPause, false);
    document.addEventListener("resume", onResume, false);

    startLocalization();
}

var onTraveledDistanceHasSubscibersChange = function() {
    if (stepdistplugin.channels.distancetraveled.numHandlers === 1) {
        console.log("At least one traveled distance listener registered");
        exec(onDistanceTraveled, error, "stepdistplugin", "startMeasuringDistance", [])
    } else if (stepdistplugin.channels.distancetraveled.numHandlers === 0) {
        console.log("No traveled distance listener registered");
        exec(success, error, "stepdistplugin", "stopMeasuringDistance", [])
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

var startLocalization = function() {
    console.log("Start localization");

    var options = {
        distanceFilter: distanceFilter,
        accuracyFilter: accuracyFilter,
        perpendicularDistanceFilter: perpendicularDistanceFilter,
        locationsSequenceFilter: locationsSequenceFilter,
        locationsSequenceDistanceFilter: locationsSequenceDistanceFilter
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
    cordova.fireDocumentEvent("lastcalibration", prepareLastCalibrationEvent(pluginInfoEvent.debugInfo, pluginInfoEvent.lastCalibrated, pluginInfoEvent.stepLength));
}

var onDistanceTraveled = function(distanceTraveledEvent) {
    cordova.fireDocumentEvent("distancetraveled", [distanceTraveledEvent]);
}

function prepareLastCalibrationEvent(debugInfo, lastCalibrated, stepLength) {
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

    return {debugInfo: debugInfo,
        lastCalibrated: lastCalibratedString,
        stepLength: stepLengthString}
}

function unixTimestampToDateString(timestamp) {
    var date = new Date(timestamp*1000);
    return date.toLocaleString();
}

var stepdistplugin = new Stepdistplugin();

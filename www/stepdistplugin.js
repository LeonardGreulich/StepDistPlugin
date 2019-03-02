var cordova = require('cordova');
var exec = require('cordova/exec');

var distanceFilter = 2;
var accuracyFilter = 8;
var perpendicularDistanceFilter = 0.00002;
var locationsSequenceFilter = 10;
var locationsSequenceDistanceFilter = 20;

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
    cordova.fireDocumentEvent("isreadytostart", [pluginInfoEvent.isReadyToStart]);
    cordova.fireDocumentEvent("lastcalibration", [pluginInfoEvent.isCalibrating, pluginInfoEvent.lastCalibrated, pluginInfoEvent.stepLength]);
}

var onDistanceTraveled = function(distanceTraveledEvent) {
    cordova.fireDocumentEvent("distancetraveled", [distanceTraveledEvent]);
}

var stepdistplugin = new Stepdistplugin();

# Cordova Walking Distance Estimation Plugin

A Cordova plugin which allows the walking distance estimation of persons. The plugin utilizes a step counting and step length estimation approach. Supported platforms are Android and iOS with an analogous implementation targeting inter-platform comparability of results.

## Supported Platforms

- iOS
- Android

## Usage

To use this plugin, add `stepdist` to your Cordova application using the Cordova command line interface (CLI):

    cordova plugin add cordova-plugin-stepdist

Listening to walking distance events (which automatically starts the estimation):

    var onWalkingDistanceEvent = function(walkingDistanceEvent) {
        // walkingDistanceEvent.distance
        // walkingDistanceEvent.elevation
        // walkingDistanceEvent.steps
    };
    document.addEventListener("walkingdistance", onWalkingDistanceEvent);

Stop listening to walking distance events (to stop the estimation):

    document.removeEventListener("walkingdistance", onWalkingDistanceEvent);

Listening to plugin status events (optionally, for monitoring purposes):

    var onStepDistStatusEvent = function(stepDistStatusEvent) {
        // stepDistStatusEvent.isReadyToStart
        // stepDistStatusEvent.stepLength
        // stepDistStatusEvent.lastCalibrated
        // stepDistStatusEvent.bodyHeight
    };
    document.addEventListener("stepdiststatus", onStepDistStatusEvent);

Configuration methods (optional):

    stepdist.setBodyHeight(1.89); // Specified in meters, improves accuracy
    stepdist.disableGNSSCalibration(); // Disables step length calibration
    stepdist.resetData(); // Removes body height and calibrated step length

## Background processing

The plugin provides robust background processing capabilities. It automatically registers a foreground service (background-enabled service with a foreground notification) on Android and enables background execution on iOS. It is not required that the parent Cordova application implements mechanisms for background execution.
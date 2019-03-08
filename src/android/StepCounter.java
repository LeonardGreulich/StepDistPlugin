package cordova.plugin.stepdist;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import static java.lang.Math.abs;

public class StepCounter implements SensorEventListener {

    private SensorManager sensorManager;
    private StepCounterDelegate delegate;
    private Handler handler;

    // Parameters
    private Double updateInterval = 0.1; // Sets how often new data from the motion sensors should be received
    private Double bFF = 1.3; // Better fragment factor, when a newer fragment is regarded better
    private Double dL = 0.3; // Deviation length, allowed deviation in length to regard fragments as similar
    private Double dA = 0.3; // Deviation amplitude, allowed deviation in amplitude to regard fragments as similar
    private Integer rT = 8; // Smoothing timeframe

    // Raw gravity data and information about maxima and minima
    private List<DoubleArrayList> gravityData = new ArrayList<>(); // Two dimensional array that holds all gravity datapoints, each having a x-, y-, and z-axis
    private List<IntArrayList> gravityFlag = new ArrayList<>(); // Two dimensional array that holds for each gravity point whether its a maxima (1), a minima(-1), or none (0)

    // Supplementary variables
    private Fragment representativeFragment = new Fragment(); // Holds the representative fragment as soon as one is found — every new incoming fragment is compared to this one
    private List<IntList> pastThreeExtremaX = new ArrayList<>(); // Hold the x values of the past three extrema
    private List<DoubleList> pastThreeExtremaY = new ArrayList<>(); // Holds the y values of the past three extrema
    private List<List<Fragment>> fragments = new ArrayList<>(); // Holds all found fragments
    private List<BooleanList> similarities = new ArrayList<>(); // Holds all information of comparison of fragments and whether they are similar or not
    private IntList reprFragmentOfAxis = new IntArrayList();
    private Integer i = 0;
    private List<Date> currentStepDates = new ArrayList<>();
    private List<Date> precedingStepDates = new ArrayList<>();

    // Java-specific (not on iOS implementation)
    private double x;
    private double y;
    private double z;
    private DoubleList processedPoints = new DoubleArrayList();
    private IntList processedFlags = new IntArrayList();

    public StepCounter(Context applicationContext) {
        sensorManager = (SensorManager) applicationContext.getSystemService(Context.SENSOR_SERVICE);
        handler = new Handler();
    }

    public void startStepCounting() {
        resetData();
        assert sensorManager != null;
        Sensor gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorManager.registerListener(this, gravitySensor , SensorManager.SENSOR_DELAY_GAME);
        handler.postDelayed(r, 100);
    }

    private Runnable r = new Runnable() {
        public void run() {
            processMotionData(x, y, z);
            handler.postDelayed(this, 100);
        }
    };

    public void stopStepCounting() {

    }

    private void resetData() {
        //First, reset motion data and information about maxima and minima
        gravityData = new ArrayList<>();
        gravityFlag = new ArrayList<>();

        // Second, reset supplementary variables
        pastThreeExtremaX = new ArrayList<>();
        pastThreeExtremaY = new ArrayList<>();
        fragments = new ArrayList<>();

        // Fill two-dimensional lists with empty lists
        for (int i = 0; i <= 2; i++) {
            gravityData.add(new DoubleArrayList());
            gravityFlag.add(new IntArrayList());
            gravityFlag.get(i).add(0);
            pastThreeExtremaX.add(new IntArrayList());
            pastThreeExtremaY.add(new DoubleArrayList());
            fragments.add(new ArrayList<>());
        }

        representativeFragment = new Fragment();
        reprFragmentOfAxis = new IntArrayList();
        currentStepDates = new ArrayList<>();
        precedingStepDates = new ArrayList<>();
        i = 0;

        clearSimilarities();
    }

    // Helper function to clear all similarities
    private void clearSimilarities() {
        similarities = new ArrayList<>();
        similarities.add(new BooleanArrayList());
        similarities.add(new BooleanArrayList());
        similarities.add(new BooleanArrayList());
    }

    private void processMotionData(double x, double y, double z) {
        // First, simply store the new incoming data points in the gravity and accelerometer array
        gravityData.get(0).add(x);
        gravityData.get(1).add(y);
        gravityData.get(2).add(z);

        // Second, calculate for each new incoming point whether it is an maximina (1), minima(-1), or none(0)
        if (i >= 2) {
            for (int axis = 0; axis <= 2; axis++) {
                gravityFlag.get(axis).add(setMinimaMaxima(gravityData.get(axis).subList(i-2, i+1)));
            }
        }

        // If we have enough data points to apply the smoothing algorithm ...
        if (i >= rT) {
            for (int axis = 0; axis <= 2; axis++) {
                // ... we apply the smoothing algorithm to this part for every axis
                smoothSubgraph(gravityData.get(axis).subList(i-rT, i), gravityFlag.get(axis).subList(i-rT, i));
                gravityData.get(axis).removeElements(i-rT, i);
                gravityData.get(axis).addAll(i-rT, processedPoints);
                gravityFlag.get(axis).removeElements(i-rT, i);
                gravityFlag.get(axis).addAll(i-rT, processedFlags);
                // Now we shift the point of consideration to the left, so that we only look at smoothed data -> (i-rT)
                // If this smoothe point of consideration is a minima or maxima ...
                if (gravityFlag.get(axis).getInt(i-rT)!= 0) {
                    // ... append it to the respective array
                    pastThreeExtremaX.get(axis).add(i-rT);
                    pastThreeExtremaY.get(axis).add(gravityData.get(axis).getDouble(i-rT));
                    // If we have gathered three maxima or minima, we can build our first fragment
                    if (pastThreeExtremaX.get(axis).size() >= 3 ) {
                        Fragment fragment = createFragment(pastThreeExtremaX.get(axis).toIntArray(), pastThreeExtremaY.get(axis).toDoubleArray(), gravityFlag.get(axis).getInt(i-rT), axis);
                        fragments.get(axis).add(fragment);
                        pastThreeExtremaX.get(axis).removeInt(0);
                        pastThreeExtremaY.get(axis).removeDouble(0);
                    }
                    // Once we have collected three or more fragments, we can start to compare them (the last vs the third-last to compare the same type)
                    if (fragments.get(axis).size() >= 3) {
                        similarities.get(axis).add(areFragmentsSimilar(fragments.get(axis).get(fragments.get(axis).size()-3), fragments.get(axis).get(fragments.get(axis).size()-1)));
                    }
                    // Finally, if we have collected the results of 5 or more comparisons, we can check if there is a pattern and, perhaps, ...
                    // ... set the representative fragment (or change it if we find a better one)
                    if (!reprFragmentOfAxis.contains(axis) && similarities.get(axis).size() >= 5) {
                        if (similarities.get(axis).getBoolean(similarities.get(axis).size()-5) && similarities.get(axis).getBoolean(similarities.get(axis).size()-3) && similarities.get(axis).getBoolean(similarities.get(axis).size()-1)) {
                            if (fragments.get(axis).get(fragments.get(axis).size()-1).amplitude > representativeFragment.amplitude*bFF) {
                                representativeFragment = createRepresentativeFragment(new Fragment[] {fragments.get(axis).get(fragments.get(axis).size()-5), fragments.get(axis).get(fragments.get(axis).size()-3), fragments.get(axis).get(fragments.get(axis).size()-1)});
                                reprFragmentOfAxis.add(axis);
                                initializeStepDates(representativeFragment, 6);
                            }
                        }
                    }
                    // After we have found a representative fragment we compare new incoming fragments of the same axis to it and possibly increase the counter
                    // If there is no similarity, we re-initialize the representative fragment and similarities to look for a new pattern
                    if (reprFragmentOfAxis.size() != 0 && representativeFragment.axis == axis && representativeFragment.fragmentType == fragments.get(axis).get(fragments.get(axis).size()-1).fragmentType) {
                        if (areFragmentsSimilar(representativeFragment, fragments.get(axis).get(fragments.get(axis).size()-1))) {
                            addStepDates(fragments.get(axis).get(fragments.get(axis).size()-1), 2);
                            delegate.stepCountDidChange(getStepsTotal());
                        } else {
                            representativeFragment = new Fragment();
                            reprFragmentOfAxis.clear();
                            clearSimilarities();
                            precedingStepDates.addAll(new ArrayList<>(currentStepDates));
                            currentStepDates.clear();
                        }
                    }
                }
            }
            // If the phone moves slowly in the pocket it may happen that another axis fulfils the betterFragmentFactor at some time
            // To avoid that previous steps are overwritten, prevent that a better axis is found after 15 steps
            // If a fragment does not fit the representative fragment after a phone movement in the pocket, a new pattern is searched in all axes again in the code above
            if (currentStepDates.size() >= 15) {
                reprFragmentOfAxis.add(0);
                reprFragmentOfAxis.add(1);
                reprFragmentOfAxis.add(2);
            }
        }

        i++;
    }

    // Based on three points, this method returns whether the point in the middle is a maxima (1), a minima(-1), or none (0)
    private int setMinimaMaxima(DoubleList threePoints)  {
        if (threePoints.getDouble(0) < threePoints.getDouble(1) && threePoints.getDouble(2) <= threePoints.getDouble(1)) {
            return 1;
        } else if (threePoints.getDouble(0) > threePoints.getDouble(1) && threePoints.getDouble(2) >= threePoints.getDouble(1)) {
            return -1;
        } else {
            return 0;
        }
    }

    // This function takes an array of datapoints (their y-values) and whether they are maxima, minima, or none to smooth the datapoints
    // Smoothing means that small distortions are removed while retaining the original height of maxima and minima
    private void smoothSubgraph(DoubleList points, IntList flags) {
        processedPoints = new DoubleArrayList(points);
        processedFlags = new IntArrayList(flags);
        boolean foundExtreme = false;
        int firstExtremePos = 0;

        for (int i = 0; i <= processedPoints.size()-1; i++) {
            if (foundExtreme && processedFlags.getInt(i) == processedFlags.getInt(firstExtremePos)) {
                processedPoints.removeElements(firstExtremePos+1, i);
                processedFlags.removeElements(firstExtremePos+1, i);
                int lengthOfNewDataPoints = i-firstExtremePos-1;
                double[] newDataPoints = new double[lengthOfNewDataPoints];
                int[] newFlags = new int[lengthOfNewDataPoints];
                Arrays.fill(newDataPoints, (points.getDouble(firstExtremePos) + points.getDouble(i))/2);
                Arrays.fill(newFlags, 0);
                processedPoints.addElements(firstExtremePos, newDataPoints, 0, lengthOfNewDataPoints);
                processedFlags.addElements(firstExtremePos, newFlags, 0, lengthOfNewDataPoints);
                if (processedFlags.getInt(i) == 1) {
                    if (processedPoints.getDouble(firstExtremePos) > processedPoints.getDouble(i)) {
                        processedFlags.set(i, 0);
                    } else {
                        processedFlags.set(firstExtremePos, 0);
                    }
                } else {
                    if (processedPoints.getDouble(firstExtremePos) > processedPoints.getDouble(i)) {
                        processedFlags.set(firstExtremePos, 0);
                    } else {
                        processedFlags.set(i, 0);
                    }
                }
                return;
            }
            if (!foundExtreme && processedFlags.getInt(i) != 0) {
                foundExtreme = true;
                firstExtremePos = i;
            }
        }
    }

    // Helper function to create a new fragment. The if-else block distinguished between a max-min-max and a min-max-min fragment
    private Fragment createFragment(int[] xValues, double[] yValues, int maxOrMin, int axis) {
        if (maxOrMin == 1) {
            return new Fragment((yValues[0] + yValues[2])/2, yValues[1], xValues[1] - xValues[0], xValues[2] - xValues[1], axis, Fragment.orders.MaxMinMax);
        } else {
            return new Fragment(yValues[1], (yValues[0] + yValues[2])/2, xValues[1] - xValues[0], xValues[2] - xValues[1], axis, Fragment.orders.MinMaxMin);
        }
    }

    // Helper function to create a representative fragment that is composed of the average values of similar fragments
    private Fragment createRepresentativeFragment(Fragment[] fragments) {
        double amplitudeTotal = fragments[0].amplitude+fragments[1].amplitude+fragments[2].amplitude;
        int lengthTotal = fragments[0].lengthTotal+fragments[1].lengthTotal+fragments[2].lengthTotal;
        double amplitudesMean = amplitudeTotal/3;
        int lengthsMean = (int) Math.round(lengthTotal/(double) 3);

        return new Fragment(amplitudesMean, lengthsMean, fragments[0].axis, fragments[0].fragmentType);
    }

    // Helper function to populate the stepDates array with the dates of all found steps, but not for the most recent ones
    // Function considers the time shift caused by the smoothing algorithm and considers the fact that one fragment represents two steps
    private void initializeStepDates(Fragment fragment, int numberOfSteps) {
        Date currentDate = new Date();

        // Clear the stepDates array
        currentStepDates.clear();

        // Subtract the time shift caused by the smoothing algorithm
        double rTInSeconds = updateInterval* (double) rT;
        currentDate.setTime(currentDate.getTime()-(long) rTInSeconds*1000);

        // Each fragment represents two steps, which equaly one stride. Assume that the length of one step is half of the stride
        // Also, subtract one additional stepLengthInSeconds to compensate for the fact that the most recent stride does not belong to the steps in this method
        double stepLengthInSeconds = (double) fragment.lengthTotal*updateInterval/2;
        currentDate.setTime(currentDate.getTime()-(long) stepLengthInSeconds*1000);
        for (int i=0; i < numberOfSteps; i++) {
            currentDate.setTime(currentDate.getTime()-(long) stepLengthInSeconds*1000);
            currentStepDates.add(new Date(currentDate.getTime()));
        }
    }

    // Helper function to add step dates to the stepDates array, similar to the initializeStepDates but for the most recent ones
    private void addStepDates(Fragment fragment, int numberOfSteps) {
        Date currentDate = new Date();

        // Subtract the time shift caused by the smoothing algorithm and add the last found step right away
        double rTInSeconds = updateInterval* (double) rT;
        currentDate.setTime(currentDate.getTime()-(long) rTInSeconds*1000);
        currentStepDates.add(new Date(currentDate.getTime()));

        // Each fragment represents two steps, which equaly one stride. Assume that the length of one step is half of the stride
        double stepLengthInSeconds = (double) fragment.lengthTotal*updateInterval/2;
        for (int i=0; i < numberOfSteps-1; i++) {
            currentDate.setTime(currentDate.getTime()-(long) stepLengthInSeconds*1000);
            currentStepDates.add(new Date(currentDate.getTime()));
        }
    }

    // Compares two fragments and returns a boolean indicating whether they are similar or not
    private boolean areFragmentsSimilar(Fragment fragmentOne, Fragment fragmentTwo) {
        double diffLength = (double) abs(fragmentOne.lengthTotal - fragmentTwo.lengthTotal)/ (double) fragmentOne.lengthTotal;
        double diffAmplitude = abs(fragmentOne.amplitude - fragmentTwo.amplitude)/fragmentOne.amplitude;

        return (diffLength <= dL && diffAmplitude <= dA);
    }

    public void setDelegate(StepCounterDelegate delegate) {
        this.delegate = delegate;
    }

    // Simple function to return the total number of steps
    public int getStepsTotal() {
        return precedingStepDates.size()+currentStepDates.size();
    }

    // Returns all steps in a given timeframe
    public int getStepsBetween(Date startDate,Date endDate) {
        List<Date> allStepDates = new ArrayList<>(precedingStepDates);
        allStepDates.addAll(currentStepDates);

        // As lambda expressions are not available in Android 7/Cordova
        int stepsBetween = 0;
        for (Date date : allStepDates) {
            if (date.after(startDate) && date.before(endDate)) {
                stepsBetween++;
            }
        }

        return stepsBetween;
    }

    // Returns the estimated number of steps per minute
    // Also, compensate for time shift caused by the smoothing algorithm by only considering steps in a window where smoothed data is available
    public int getStepsPerMinute() {
        Date currentDate = new Date();
        int rTInMilliseconds = updateInterval.intValue()*rT*1000;
        Date startDate15Seconds = new Date(currentDate.getTime()-(rTInMilliseconds+15));
        Date endDate15Seconds = new Date(currentDate.getTime()-(rTInMilliseconds));

        return getStepsBetween(startDate15Seconds, endDate15Seconds)*4;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        x = event.values[0];
        y = event.values[1];
        z = event.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public interface StepCounterDelegate {
        void stepCountDidChange(int count);
    }

}

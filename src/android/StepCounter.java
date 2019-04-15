package cordova.plugin.stepdist;

import android.content.Context;

import org.apache.commons.collections.primitives.ArrayDoubleList;
import org.apache.commons.collections.primitives.ArrayIntList;
import org.apache.commons.collections.primitives.DoubleList;
import org.apache.commons.collections.primitives.IntList;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.Math.abs;

public class StepCounter {
    
    private StepCounterDelegate delegate;

    // Used to align unit on iOS (g) with Android (m/s^2)
    private static final Double GRAVITY = 9.8;

    // Parameters
    private Double updateInterval; // Sets how often new data from the motion sensors should be received
    private Double bSF; // Better stride factor, when a newer stride is regarded better
    private Double dL; // Deviation length, allowed deviation in length to regard strides as similar
    private Double dA; // Deviation amplitude, allowed deviation in amplitude to regard strides as similar
    private Double mSA; // Minimum amplitude that a movement pattern is considered a stride
    private Integer rT; // Smoothing timeframe

    // Raw gravity data and information about maxima and minima
    private List<DoubleList> gravityData = new ArrayList<>(); // Two dimensional array that holds all gravity datapoints, each having a x-, y-, and z-axis
    private List<IntList> gravityFlag = new ArrayList<>();// Two dimensional array that holds for each gravity point whether its a maxima (1), a minima(-1), or none (0)

    // Supplementary variables
    private Stride representativeStride = new Stride(); // Holds the representative stride as soon as one is found — every new incoming stride is compared to this one
    private List<IntList> pastThreeExtremaX = new ArrayList<>(); // Hold the x values of the past three extrema
    private List<DoubleList> pastThreeExtremaY = new ArrayList<>(); // Holds the y values of the past three extrema
    private List<List<Stride>> strides = new ArrayList<>(); // Holds all found strides
    private List<List<Boolean>> similarities = new ArrayList<>(); // Holds all information of comparison of strides and whether they are similar or not
    private IntList reprStrideOfAxis = new ArrayIntList();
    private Integer i = 0;
    private List<Date> currentStepDates = new ArrayList<>();
    private List<Date> precedingStepDates = new ArrayList<>();

    public StepCounter(Context applicationContext, JSONObject options) throws JSONException {
        updateInterval = options.getDouble("updateInterval");
        bSF = options.getDouble("betterStrideFactor");
        dL = options.getDouble("deviationLength");
        dA = options.getDouble("deviationAmplitude");
        mSA = options.getDouble("minStrideAmplitude") * GRAVITY;
        rT = options.getInt("smoothingTimeframe");
    }

    public void resetData() {
        //First, reset motion data and information about maxima and minima
        gravityData = new ArrayList<>();
        gravityFlag = new ArrayList<>();

        // Second, reset supplementary variables
        pastThreeExtremaX = new ArrayList<>();
        pastThreeExtremaY = new ArrayList<>();
        strides = new ArrayList<>();

        // Fill two-dimensional lists with empty lists
        for (int i = 0; i <= 2; i++) {
            gravityData.add(new ArrayDoubleList());
            gravityFlag.add(new ArrayIntList());
            gravityFlag.get(i).add(0);
            pastThreeExtremaX.add(new ArrayIntList());
            pastThreeExtremaY.add(new ArrayDoubleList());
            strides.add(new ArrayList<>());
        }

        representativeStride = new Stride();
        reprStrideOfAxis = new ArrayIntList();
        currentStepDates = new ArrayList<>();
        precedingStepDates = new ArrayList<>();
        i = 0;

        clearSimilarities();
    }

    // Helper function to clear all similarities
    private void clearSimilarities() {
        similarities = new ArrayList<>();
        similarities.add(new ArrayList<>());
        similarities.add(new ArrayList<>());
        similarities.add(new ArrayList<>());
    }

    public void processMotionData(double x, double y, double z) {
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
                // Now we shift the point of consideration to the left, so that we only look at smoothed data -> (i-rT)
                // If this smoothe point of consideration is a minima or maxima ...
                if (gravityFlag.get(axis).get(i-rT)!= 0) {
                    // ... append it to the respective array
                    pastThreeExtremaX.get(axis).add(i-rT);
                    pastThreeExtremaY.get(axis).add(gravityData.get(axis).get(i-rT));
                    // If we have gathered three maxima or minima, we can build our first stride
                    if (pastThreeExtremaX.get(axis).size() >= 3 ) {
                        Stride stride = createStride(pastThreeExtremaX.get(axis).toArray(), pastThreeExtremaY.get(axis).toArray(), gravityFlag.get(axis).get(i-rT), axis);
                        strides.get(axis).add(stride);
                        pastThreeExtremaX.get(axis).removeElementAt(0);
                        pastThreeExtremaY.get(axis).removeElementAt(0);
                    }
                    // Once we have collected three or more strides, we can start to compare them (the last vs the third-last to compare the same type)
                    if (strides.get(axis).size() >= 3) {
                        similarities.get(axis).add(areStridesSimilar(strides.get(axis).get(strides.get(axis).size()-3), strides.get(axis).get(strides.get(axis).size()-1)));
                    }
                    // Finally, if we have collected the results of 5 or more comparisons, we can check if there is a pattern and, perhaps, ...
                    // ... set the representative stride (or change it if we find a better one)
                    if (!reprStrideOfAxis.contains(axis) && similarities.get(axis).size() >= 3) {
                        if (similarities.get(axis).get(similarities.get(axis).size()-3) && similarities.get(axis).get(similarities.get(axis).size()-1)) {
                            if (strides.get(axis).get(strides.get(axis).size()-1).amplitude >= mSA && strides.get(axis).get(strides.get(axis).size()-1).amplitude > representativeStride.amplitude*bSF) {
                                representativeStride = createRepresentativeStride(new Stride[] {strides.get(axis).get(strides.get(axis).size()-5), strides.get(axis).get(strides.get(axis).size()-3), strides.get(axis).get(strides.get(axis).size()-1)});
                                reprStrideOfAxis.add(axis);
                                initializeStepDates(representativeStride, 4);
                            }
                        }
                    }
                    // After we have found a representative stride we compare new incoming strides of the same axis to it and possibly increase the counter
                    // If there is no similarity, we re-initialize the representative stride and similarities to look for a new pattern
                    if (reprStrideOfAxis.size() != 0 && representativeStride.axis == axis && representativeStride.strideType == strides.get(axis).get(strides.get(axis).size()-1).strideType) {
                        if (areStridesSimilar(representativeStride, strides.get(axis).get(strides.get(axis).size()-1))) {
                            addStepDates(strides.get(axis).get(strides.get(axis).size()-1), 2);
                            delegate.stepCountDidChange(getStepsTotal(), getStepsPerSecond(strides.get(axis).get(strides.get(axis).size()-1)));
                        } else {
                            representativeStride = new Stride();
                            reprStrideOfAxis.clear();
                            clearSimilarities();
                            precedingStepDates.addAll(new ArrayList<>(currentStepDates));
                            currentStepDates.clear();
                        }
                    }
                }
            }
            // If the phone moves slowly in the pocket it may happen that another axis fulfils the betterStrideFactor at some time
            // To avoid that previous steps are overwritten, prevent that a better axis is found after 15 steps
            // If a stride does not fit the representative stride after a phone movement in the pocket, a new pattern is searched in all axes again in the code above
            if (currentStepDates.size() >= 15) {
                reprStrideOfAxis.add(0);
                reprStrideOfAxis.add(1);
                reprStrideOfAxis.add(2);
            }
        }

        i++;
    }

    // Based on three points, this method returns whether the point in the middle is a maxima (1), a minima(-1), or none (0)
    private int setMinimaMaxima(DoubleList threePoints)  {
        if (threePoints.get(0) < threePoints.get(1) && threePoints.get(2) <= threePoints.get(1)) {
            return 1;
        } else if (threePoints.get(0) > threePoints.get(1) && threePoints.get(2) >= threePoints.get(1)) {
            return -1;
        } else {
            return 0;
        }
    }

    // This function takes an array of datapoints (their y-values) and whether they are maxima, minima, or none to smooth the datapoints
    // Smoothing means that small distortions are removed while retaining the original height of maxima and minima
    private void smoothSubgraph(DoubleList points, IntList flags) {
        DoubleList rawPoints = new ArrayDoubleList(points);
        boolean foundExtreme = false;
        int firstExtremePos = 0;

        for (int i = 0; i <= points.size()-1; i++) {
            if (foundExtreme && flags.get(i) == flags.get(firstExtremePos)) {
                points.subList(firstExtremePos+1, i).clear();
                flags.subList(firstExtremePos+1, i).clear();
                int lengthOfNewDataPoints = i-firstExtremePos-1;
                for (int j = 0; j<lengthOfNewDataPoints; j++) {
                    points.add(firstExtremePos+1, (rawPoints.get(firstExtremePos) + rawPoints.get(i))/2);
                    flags.add(firstExtremePos+1, 0);
                }
                if (flags.get(i) == 1) {
                    if (points.get(firstExtremePos) > points.get(i)) {
                        flags.set(i, 0);
                    } else {
                        flags.set(firstExtremePos, 0);
                    }
                } else {
                    if (points.get(firstExtremePos) > points.get(i)) {
                        flags.set(firstExtremePos, 0);
                    } else {
                        flags.set(i, 0);
                    }
                }
                return;
            }
            if (!foundExtreme && flags.get(i) != 0) {
                foundExtreme = true;
                firstExtremePos = i;
            }
        }
    }

    // Helper function to create a new stride. The if-else block distinguished between a max-min-max and a min-max-min stride
    private Stride createStride(int[] xValues, double[] yValues, int maxOrMin, int axis) {
        if (maxOrMin == 1) {
            return new Stride((yValues[0] + yValues[2])/2, yValues[1], xValues[1] - xValues[0], xValues[2] - xValues[1], axis, Stride.orders.MaxMinMax);
        } else {
            return new Stride(yValues[1], (yValues[0] + yValues[2])/2, xValues[1] - xValues[0], xValues[2] - xValues[1], axis, Stride.orders.MinMaxMin);
        }
    }

    // Helper function to create a representative stride that is composed of the average values of similar strides
    private Stride createRepresentativeStride(Stride[] strides) {
        double amplitudeTotal = strides[0].amplitude+strides[1].amplitude+strides[2].amplitude;
        int lengthTotal = strides[0].lengthTotal+strides[1].lengthTotal+strides[2].lengthTotal;
        double amplitudesMean = amplitudeTotal/3;
        int lengthsMean = (int) Math.round(lengthTotal/(double) 3);

        return new Stride(amplitudesMean, lengthsMean, strides[0].axis, strides[0].strideType);
    }

    // Helper function to populate the stepDates array with the dates of all found steps, but not for the most recent ones
    // Function considers the time shift caused by the smoothing algorithm and considers the fact that one stride represents two steps
    private void initializeStepDates(Stride stride, int numberOfSteps) {
        Date currentDate = new Date();

        // Clear the stepDates array
        currentStepDates.clear();

        // Subtract the time shift caused by the smoothing algorithm
        double rTInSeconds = updateInterval* (double) rT;
        currentDate.setTime(currentDate.getTime()-(long) rTInSeconds*1000);

        // Each stride represents two steps, which equaly one stride. Assume that the length of one step is half of the stride
        // Also, subtract one additional stepLengthInSeconds to compensate for the fact that the most recent stride does not belong to the steps in this method
        double stepLengthInSeconds = (double) stride.lengthTotal*updateInterval/2;
        currentDate.setTime(currentDate.getTime()-(long) stepLengthInSeconds*1000);
        for (int i=0; i < numberOfSteps; i++) {
            currentDate.setTime(currentDate.getTime()-(long) stepLengthInSeconds*1000);
            currentStepDates.add(new Date(currentDate.getTime()));
        }
    }

    // Helper function to add step dates to the stepDates array, similar to the initializeStepDates but for the most recent ones
    private void addStepDates(Stride stride, int numberOfSteps) {
        Date currentDate = new Date();

        // Subtract the time shift caused by the smoothing algorithm and add the last found step right away
        double rTInSeconds = updateInterval* (double) rT;
        currentDate.setTime(currentDate.getTime()-(long) rTInSeconds*1000);
        currentStepDates.add(new Date(currentDate.getTime()));

        // Each stride represents two steps, which equaly one stride. Assume that the length of one step is half of the stride
        double stepLengthInSeconds = (double) stride.lengthTotal*updateInterval/2;
        for (int i=0; i < numberOfSteps-1; i++) {
            currentDate.setTime(currentDate.getTime()-(long) stepLengthInSeconds*1000);
            currentStepDates.add(new Date(currentDate.getTime()));
        }
    }

    // Compares two strides and returns a boolean indicating whether they are similar or not
    private boolean areStridesSimilar(Stride strideOne, Stride strideTwo) {
        double diffLength = (double) abs(strideOne.lengthTotal - strideTwo.lengthTotal)/ (double) strideOne.lengthTotal;
        double diffAmplitude = abs(strideOne.amplitude - strideTwo.amplitude)/strideOne.amplitude;

        return (diffLength <= dL && diffAmplitude <= dA);
    }

    // Setter for the delegate, which is the DistanceService
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

    // Returns the current step frequency based on a stride
    private float getStepsPerSecond(Stride stride) {
        double stepDurationInSeconds = stride.lengthTotal*updateInterval*0.5;

        return (float) (1/stepDurationInSeconds);
    }

    public interface StepCounterDelegate {
        void stepCountDidChange(int count, float frequency);
    }

}

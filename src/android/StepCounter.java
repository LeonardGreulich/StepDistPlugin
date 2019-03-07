package cordova.plugin.stepdist;

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

public class StepCounter {

    private StepCounterDelegate delegate;

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

    // Java-specific
    DoubleList processedPoints = new DoubleArrayList();
    IntList processedFlags = new IntArrayList();

    public void startStepCounting() {
        resetData();

        // motionManager.deviceMotionUpdateInterval = updateInterval
        // Start motion data and send data to main algo
    }

    public void stopStepCounting() {
        // Stop motion data
    }

    private void resetData() {
        //First, reset motion data and information about maxima and minima
        gravityData = new ArrayList<>();
        gravityFlag = new ArrayList<>();

        // Second, reset supplementary variables
        representativeFragment = new Fragment();
        pastThreeExtremaX = new ArrayList<>();
        pastThreeExtremaY = new ArrayList<>();
        fragments = new ArrayList<>();
        similarities = new ArrayList<>();
        reprFragmentOfAxis = new IntArrayList();
        i = 0;
        currentStepDates = new ArrayList<>();
        precedingStepDates = new ArrayList<>();
    }

    private void processMotionData(double x, double y, double z) {
        // First, simply store the new incoming data points in the gravity and accelerometer array
        gravityData.get(0).add(x);
        gravityData.get(1).add(y);
        gravityData.get(2).add(z);

        // Second, calculate for each new incoming point whether it is an maximina (1), minima(-1), or none(0)
        if (i >= 2) {
            for (int axis = 0; axis <= 2; axis++) {
                gravityFlag.get(axis).add(setMinimaMaxima(gravityData.get(axis).subList(i-2, i)));
            }
        }

        // The following part is solely for gravity
        // If we have enough data points to apply the smoothing algorithm ...
        if (i >= rT) {
            for (int axis = 0; axis <= 2; axis++) {
                // ... we apply the smoothing algorithm to this part for every axis
                smoothSubgraph(gravityData.get(axis).subList(i-rT, i-1), gravityFlag.get(axis).subList(i-rT, i-1));
                gravityData.get(axis).removeElements(i-rT, i-1);
                gravityData.get(axis).addAll(i-rT-1, processedPoints);
                gravityFlag.get(axis).removeElements(i-rT, i-1);
                gravityFlag.get(axis).addAll(i-rT-1, processedFlags);
                // Now we shift the point of consideration to the left, so that we only look at smoothed data -> (i-rT)
                // If this smoothe point of consideration is a minima or maxima ...
                if (gravityFlag.get(axis).getInt(i-rT)!= 0) {
                    // ... append it to the respective array
                    pastThreeExtremaX.get(axis).add(i-rT);
                    pastThreeExtremaY.get(axis).add(gravityData.get(axis).getDouble(i-rT);
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
                                representativeFragment = createRepresentativeFragment(new Fragment[] {fragments.get(axis).get(fragments.get(axis).size()-5), fragments.get(axis).get(fragments.get(axis).size()-3, fragments.get(axis).get(fragments.get(axis).size()-1});
                                reprFragmentOfAxis.add(axis);
                                initializeStepDates(representativeFragment, 6);
                            }
                        }
                    }
                    // After we have found a representative fragment we compare new incoming fragments of the same axis to it and possibly increase the counter
                    // If there is no similarity, we re-initialize the representative fragment and similarities to look for a new pattern
                    if (reprFragmentOfAxis.count != 0 && representativeFragment.axis == axis && representativeFragment.fragmentType == fragments[axis][fragments[axis].count-1].fragmentType) {
                        if self.areFragmentsSimilar(representativeFragment, fragments[axis][fragments[axis].count-1]) {
                            addStepDates(fragments[axis][fragments[axis].count-1], 2)
                            delegate.stepCountDidChange(manager: self, count: getStepsTotal())
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
    private int setMinimaMaxima(List<Double> threePoints)  {
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
        processedPoints = new DoubleArrayList(points);
        processedFlags = new IntArrayList(flags);
        boolean foundExtreme = false;
        int firstExtremePos = 0;

        for (int i = 0; i <= processedPoints.size()-1; i++) {
            if (foundExtreme && processedFlags.getInt(i) == processedFlags.getInt(firstExtremePos)) {
                processedPoints.removeElements(firstExtremePos+1, i-1);
                processedFlags.removeElements(firstExtremePos+1, i-1);
                int lengthOfNewDataPoints = i-firstExtremePos-1;
                double[] newDataPoints = new double[lengthOfNewDataPoints];
                int[] newFlags = new int[lengthOfNewDataPoints];
                Arrays.fill(newDataPoints, (processedPoints.getDouble(firstExtremePos) + processedPoints.getDouble(i))/2);
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
            if (!foundExtreme && processedFlags.get(i) != 0) {
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
        for (int i=0; i <= numberOfSteps-1; i++) {
            currentDate.setTime(currentDate.getTime()-(long) stepLengthInSeconds*1000);
            currentStepDates.add(new Date(currentDate.getTime()));
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
    private boolean areFragmentsSimilar(Fragment fragmentOne, Fragment fragmentTwo) {
        double diffLength = (double) abs(fragmentOne.lengthTotal - fragmentTwo.lengthTotal)/ (double) fragmentOne.lengthTotal;
        double diffAmplitude = abs(fragmentOne.amplitude - fragmentTwo.amplitude)/fragmentOne.amplitude;

        return (diffLength <= dL && diffAmplitude <= dA);
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

    public interface StepCounterDelegate {
        void stepCountDidChange(Integer count);
    }

}

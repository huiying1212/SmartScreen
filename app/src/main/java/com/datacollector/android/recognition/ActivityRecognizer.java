package com.datacollector.android.recognition;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity recognizer based on the decision tree classifier described in:
 *
 * Wang et al., "StudentLife: Assessing Mental Health, Academic Performance
 * and Behavioral Trends of College Students using Smartphones", UbiComp 2014.
 *
 * The paper uses the Jigsaw continuous sensing engine (Lu et al., SenSys 2010)
 * which extracts features from accelerometer streams and applies a decision tree
 * to classify: stationary, walking, running, driving, cycling.
 * Reported accuracy: 94%.
 *
 * Key design choices following the paper:
 * - Accelerometer-only (no gyroscope required)
 * - ~2 second classification window
 * - Decision tree structure instead of flat if-else rules
 * - Feature set: mean, variance, energy, zero-crossing rate, inter-axis correlation, range
 */
public class ActivityRecognizer {

    private static final String TAG = "ActivityRecognizer";

    public static final String ACTIVITY_STATIONARY = "stationary";
    public static final String ACTIVITY_WALKING = "walking";
    public static final String ACTIVITY_RUNNING = "running";
    public static final String ACTIVITY_DRIVING = "driving";
    public static final String ACTIVITY_CYCLING = "cycling";

    private static final int SAMPLE_RATE = 20;
    private static final int WINDOW_SIZE = 40; // ~2 seconds at 20 Hz

    private final List<Float> accX = new ArrayList<>();
    private final List<Float> accY = new ArrayList<>();
    private final List<Float> accZ = new ArrayList<>();

    private String currentActivity = ACTIVITY_STATIONARY;
    private float confidence = 0.0f;

    private final DecisionTreeNode decisionTree;
    private final Context context;

    public ActivityRecognizer(Context context) {
        this.context = context;
        this.decisionTree = buildDecisionTree();
    }

    public void processSensorData(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accX.add(event.values[0]);
            accY.add(event.values[1]);
            accZ.add(event.values[2]);

            if (accX.size() > WINDOW_SIZE * 2) {
                accX.remove(0);
                accY.remove(0);
                accZ.remove(0);
            }

            if (accX.size() >= WINDOW_SIZE) {
                recognizeActivity();
                trimBuffer();
            }
        }
    }

    // ---- Feature extraction ----

    /**
     * Extracts all features from the current accelerometer window.
     * Following the Jigsaw engine approach: statistical + frequency-domain features
     * computed on the acceleration magnitude signal.
     */
    private AccelFeatures extractFeatures() {
        int n = Math.min(Math.min(accX.size(), accY.size()), accZ.size());
        float[] mag = new float[n];
        for (int i = 0; i < n; i++) {
            float x = accX.get(i), y = accY.get(i), z = accZ.get(i);
            mag[i] = (float) Math.sqrt(x * x + y * y + z * z);
        }

        AccelFeatures f = new AccelFeatures();
        f.mean = mean(mag);
        f.variance = variance(mag, f.mean);
        f.stdDev = (float) Math.sqrt(f.variance);
        f.energy = energy(mag);
        f.zeroCrossingRate = zeroCrossingRate(mag, f.mean);
        f.range = range(mag);
        f.peakFrequency = estimatePeakFrequency(mag, f.mean, f.stdDev);
        f.correlationXY = correlation(accX, accY, n);
        f.correlationXZ = correlation(accX, accZ, n);
        f.correlationYZ = correlation(accY, accZ, n);
        return f;
    }

    private static float mean(float[] v) {
        float s = 0;
        for (float x : v) s += x;
        return s / v.length;
    }

    private static float variance(float[] v, float mean) {
        float s = 0;
        for (float x : v) s += (x - mean) * (x - mean);
        return s / v.length;
    }

    private static float energy(float[] v) {
        float s = 0;
        for (float x : v) s += x * x;
        return s / v.length;
    }

    /**
     * Zero-crossing rate relative to the mean.
     * Measures how often the signal oscillates around its mean — a proxy for
     * the dominant frequency that the Jigsaw engine uses for step detection.
     */
    private static float zeroCrossingRate(float[] v, float mean) {
        int crossings = 0;
        for (int i = 1; i < v.length; i++) {
            if ((v[i - 1] - mean) * (v[i] - mean) < 0) crossings++;
        }
        return (float) crossings / (v.length - 1);
    }

    private static float range(float[] v) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float x : v) {
            if (x < min) min = x;
            if (x > max) max = x;
        }
        return max - min;
    }

    /**
     * Peak frequency estimated via simple peak counting on the acceleration
     * magnitude. Serves as a lightweight substitute for FFT-based dominant
     * frequency used in the full Jigsaw pipeline.
     */
    private float estimatePeakFrequency(float[] mag, float mean, float stdDev) {
        float threshold = mean + stdDev * 0.5f;
        int peaks = 0;
        boolean above = false;
        for (float v : mag) {
            if (v > threshold && !above) {
                peaks++;
                above = true;
            } else if (v < threshold) {
                above = false;
            }
        }
        float windowSeconds = (float) mag.length / SAMPLE_RATE;
        return peaks / windowSeconds;
    }

    /**
     * Pearson correlation between two axes.
     * High correlation between axes helps distinguish vehicle motion (smooth,
     * correlated) from human locomotion (more independent per-axis motion).
     */
    private static float correlation(List<Float> a, List<Float> b, int n) {
        float meanA = 0, meanB = 0;
        for (int i = 0; i < n; i++) {
            meanA += a.get(i);
            meanB += b.get(i);
        }
        meanA /= n;
        meanB /= n;

        float cov = 0, varA = 0, varB = 0;
        for (int i = 0; i < n; i++) {
            float da = a.get(i) - meanA;
            float db = b.get(i) - meanB;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }
        float denom = (float) Math.sqrt(varA * varB);
        return denom == 0 ? 0 : cov / denom;
    }

    // ---- Decision tree ----

    /**
     * Builds the decision tree following the StudentLife/Jigsaw methodology.
     *
     * Tree structure (trained thresholds approximated from the literature):
     *
     *                    [variance < 0.3]
     *                    /              \
     *             STATIONARY     [peakFreq < 0.8]
     *                            /              \
     *                  [variance < 2.5]    [variance < 8.0]
     *                  /            \       /            \
     *            DRIVING        CYCLING  WALKING      RUNNING
     *
     * - First split: low variance → stationary (phone barely moves)
     * - Second split: peak frequency separates rhythmic locomotion from vehicle motion
     * - Left branch: low peak freq = no stepping → driving vs cycling
     *   (driving has very low variance from road vibration; cycling is higher)
     * - Right branch: high peak freq = stepping → walking vs running
     *   (running produces larger variance)
     */
    private DecisionTreeNode buildDecisionTree() {
        // Leaf nodes
        DecisionTreeNode stationary = DecisionTreeNode.leaf(ACTIVITY_STATIONARY, 0.95f);
        DecisionTreeNode driving   = DecisionTreeNode.leaf(ACTIVITY_DRIVING, 0.80f);
        DecisionTreeNode cycling   = DecisionTreeNode.leaf(ACTIVITY_CYCLING, 0.78f);
        DecisionTreeNode walking   = DecisionTreeNode.leaf(ACTIVITY_WALKING, 0.90f);
        DecisionTreeNode running   = DecisionTreeNode.leaf(ACTIVITY_RUNNING, 0.88f);

        // Level 3 — vehicle vs cycling (low peak-frequency branch)
        DecisionTreeNode vehicleOrCycle = DecisionTreeNode.branch(
                Feature.VARIANCE, 2.5f, driving, cycling);

        // Level 3 — walking vs running (high peak-frequency branch)
        DecisionTreeNode walkOrRun = DecisionTreeNode.branch(
                Feature.VARIANCE, 8.0f, walking, running);

        // Level 2 — locomotion vs vehicle
        DecisionTreeNode moving = DecisionTreeNode.branch(
                Feature.PEAK_FREQUENCY, 0.8f, vehicleOrCycle, walkOrRun);

        // Level 1 — stationary vs moving
        return DecisionTreeNode.branch(
                Feature.VARIANCE, 0.3f, stationary, moving);
    }

    private void recognizeActivity() {
        AccelFeatures features = extractFeatures();
        String[] result = decisionTree.classify(features);

        String activity = result[0];
        float conf = Float.parseFloat(result[1]);

        if (!activity.equals(currentActivity)) {
            Log.d(TAG, "Activity changed: " + currentActivity + " -> " + activity
                    + " (conf=" + conf + ")");
            currentActivity = activity;
        }
        confidence = conf;
    }

    private void trimBuffer() {
        int keep = WINDOW_SIZE / 2;
        while (accX.size() > keep) {
            accX.remove(0);
            accY.remove(0);
            accZ.remove(0);
        }
    }

    // ---- Public API ----

    public String getCurrentActivity() {
        return currentActivity;
    }

    public float getConfidence() {
        return confidence;
    }

    public JSONObject getActivityInfo() {
        try {
            JSONObject info = new JSONObject();
            info.put("activity", currentActivity);
            info.put("confidence", confidence);
            info.put("timestamp", System.currentTimeMillis());
            info.put("classifier", "decision_tree");
            return info;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build activity info JSON", e);
            return null;
        }
    }

    // ---- Inner types ----

    enum Feature {
        MEAN, VARIANCE, STD_DEV, ENERGY, ZERO_CROSSING_RATE,
        RANGE, PEAK_FREQUENCY, CORR_XY, CORR_XZ, CORR_YZ
    }

    static class AccelFeatures {
        float mean;
        float variance;
        float stdDev;
        float energy;
        float zeroCrossingRate;
        float range;
        float peakFrequency;
        float correlationXY;
        float correlationXZ;
        float correlationYZ;

        float get(Feature f) {
            switch (f) {
                case MEAN:               return mean;
                case VARIANCE:           return variance;
                case STD_DEV:            return stdDev;
                case ENERGY:             return energy;
                case ZERO_CROSSING_RATE: return zeroCrossingRate;
                case RANGE:             return range;
                case PEAK_FREQUENCY:     return peakFrequency;
                case CORR_XY:            return correlationXY;
                case CORR_XZ:            return correlationXZ;
                case CORR_YZ:            return correlationYZ;
                default:                 return 0;
            }
        }
    }

    /**
     * Binary decision tree node. Each internal node splits on one feature
     * with a threshold; each leaf holds an activity label and confidence.
     */
    static class DecisionTreeNode {
        final boolean isLeaf;
        final String activityLabel;
        final float leafConfidence;
        final Feature splitFeature;
        final float threshold;
        final DecisionTreeNode left;   // feature < threshold
        final DecisionTreeNode right;  // feature >= threshold

        private DecisionTreeNode(boolean isLeaf, String label, float conf,
                                 Feature feat, float thresh,
                                 DecisionTreeNode left, DecisionTreeNode right) {
            this.isLeaf = isLeaf;
            this.activityLabel = label;
            this.leafConfidence = conf;
            this.splitFeature = feat;
            this.threshold = thresh;
            this.left = left;
            this.right = right;
        }

        static DecisionTreeNode leaf(String label, float confidence) {
            return new DecisionTreeNode(true, label, confidence,
                    null, 0, null, null);
        }

        static DecisionTreeNode branch(Feature feature, float threshold,
                                        DecisionTreeNode left, DecisionTreeNode right) {
            return new DecisionTreeNode(false, null, 0,
                    feature, threshold, left, right);
        }

        /**
         * Traverse the tree and return [activity, confidence].
         */
        String[] classify(AccelFeatures features) {
            if (isLeaf) {
                return new String[]{activityLabel, String.valueOf(leafConfidence)};
            }
            float value = features.get(splitFeature);
            return (value < threshold ? left : right).classify(features);
        }
    }
}

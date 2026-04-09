package com.datacollector.android.collectors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.datacollector.android.recognition.ActivityRecognizer;
import com.datacollector.android.utils.CollectionConfig;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Collects activity recognition data using the decision-tree classifier
 * from the StudentLife / Jigsaw methodology.
 *
 * Per the paper, only the accelerometer is required; gyroscope is optional
 * metadata recorded for reference but not used in classification.
 */
public class ActivityRecognitionCollector extends BaseDataCollector<JSONObject> implements SensorEventListener {

    private static final String COLLECTOR_ID = "activity_recognition";

    private SensorManager sensorManager;
    private ActivityRecognizer activityRecognizer;
    private Sensor accelerometer;

    public ActivityRecognitionCollector(Context context) {
        super(context, COLLECTOR_ID);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        activityRecognizer = new ActivityRecognizer(context);

        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    protected void initializeDefaultConfiguration() {
        super.initializeDefaultConfiguration();
        try {
            configuration.put("sensor_delay", SensorManager.SENSOR_DELAY_NORMAL);
            configuration.put("use_accelerometer", true);
            configuration.put("classifier", "decision_tree");
        } catch (JSONException e) {
            Log.w(TAG, "Failed to build configuration", e);
        }
    }

    @Override
    public CollectionWeight getWeight() { return CollectionWeight.LIGHT; }

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }
        if (!CollectionConfig.getInstance(context)
                .getBoolean(CollectionConfig.KEY_ACTIVITY_ENABLED, true)) {
            return false;
        }
        if (sensorManager == null) {
            return false;
        }
        return accelerometer != null;
    }

    @Override
    protected void doStartCollection() {
        if (!isAvailable()) {
            return;
        }

        int sensorDelay = configuration.optInt("sensor_delay", SensorManager.SENSOR_DELAY_NORMAL);

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, sensorDelay);
        }
    }

    @Override
    protected void doStopCollection() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected JSONObject doCollectData() {
        if (activityRecognizer == null) {
            return null;
        }

        JSONObject activityInfo = activityRecognizer.getActivityInfo();

        if (activityInfo != null) {
            try {
                activityInfo.put("collector_id", getCollectorId());
                activityInfo.put("data_collection_time", System.currentTimeMillis());

                JSONObject sensorStatus = new JSONObject();
                sensorStatus.put("accelerometer_available", accelerometer != null);
                activityInfo.put("sensor_status", sensorStatus);
            } catch (JSONException e) {
                Log.w(TAG, "Failed to build sensor status", e);
            }
        }

        return activityInfo;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (activityRecognizer != null) {
            activityRecognizer.processSensorData(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no-op
    }

    public String getCurrentActivity() {
        return activityRecognizer != null ? activityRecognizer.getCurrentActivity() : "unknown";
    }

    public float getConfidence() {
        return activityRecognizer != null ? activityRecognizer.getConfidence() : 0.0f;
    }
}

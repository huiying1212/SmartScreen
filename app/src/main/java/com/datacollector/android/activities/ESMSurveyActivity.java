package com.datacollector.android.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.datacollector.android.R;
import com.datacollector.android.utils.UserInteractionLogger;

/**
 * 经验取样法 (ESM) 问卷 Activity。
 *
 * 由 ESMScheduler 在随机时间通过通知触发，弹出 3 道简短问题：
 *   Q1: 反思提醒有用吗？(1-5 Likert)
 *   Q2: 当前使用状态 (有目的 / 无意识 / 不确定)
 *   Q3: 浮窗表情准确吗？(1-5 Likert)
 *
 * 结果通过 UserInteractionLogger 记录，随后由 ExperimentDataUploader 上报。
 */
public class ESMSurveyActivity extends Activity {

    private RadioGroup rgQ1, rgQ2, rgQ3;
    private UserInteractionLogger logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esm_survey);

        logger = UserInteractionLogger.get(this);
        logger.log("esm_shown");

        rgQ1 = findViewById(R.id.rg_q1);
        rgQ2 = findViewById(R.id.rg_q2);
        rgQ3 = findViewById(R.id.rg_q3);

        Button btnSubmit = findViewById(R.id.btn_esm_submit);
        Button btnSkip = findViewById(R.id.btn_esm_skip);

        btnSubmit.setOnClickListener(v -> submit());
        btnSkip.setOnClickListener(v -> skip());
    }

    private void submit() {
        int q1 = getLikertValue(rgQ1);
        String q2 = getUsageState(rgQ2);
        int q3 = getLikertValue(rgQ3);

        if (q1 == 0 || q2 == null || q3 == 0) {
            Toast.makeText(this, "请回答所有问题", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            org.json.JSONObject params = new org.json.JSONObject();
            params.put("q1_reminder_helpful", q1);
            params.put("q2_usage_state", q2);
            params.put("q3_face_accurate", q3);
            logger.log("esm_submit", params);
        } catch (org.json.JSONException e) {
            logger.log("esm_submit", "q1", q1);
        }

        Toast.makeText(this, "感谢你的反馈！", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void skip() {
        logger.log("esm_skip");
        finish();
    }

    private int getLikertValue(RadioGroup rg) {
        int checkedId = rg.getCheckedRadioButtonId();
        if (checkedId == R.id.q1_1 || checkedId == R.id.q3_1) return 1;
        if (checkedId == R.id.q1_2 || checkedId == R.id.q3_2) return 2;
        if (checkedId == R.id.q1_3 || checkedId == R.id.q3_3) return 3;
        if (checkedId == R.id.q1_4 || checkedId == R.id.q3_4) return 4;
        if (checkedId == R.id.q1_5 || checkedId == R.id.q3_5) return 5;
        return 0;
    }

    private String getUsageState(RadioGroup rg) {
        int checkedId = rg.getCheckedRadioButtonId();
        if (checkedId == R.id.q2_purposeful) return "purposeful";
        if (checkedId == R.id.q2_unconscious) return "unconscious";
        if (checkedId == R.id.q2_unsure) return "unsure";
        return null;
    }
}

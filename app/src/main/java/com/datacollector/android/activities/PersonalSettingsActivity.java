package com.datacollector.android.activities;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.datacollector.android.R;
import com.datacollector.android.services.FloatingOverlayService;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.views.MoodFaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PersonalSettingsActivity extends Activity {

    private CollectionConfig config;

    private LinearLayout styleChipsContainer, iconGridContainer;
    private TextView tvTimeSlot1, tvTimeSlot2, tvTimeSlot3;
    private EditText etPersonalGoal;
    private Button btnSaveGoal;

    private static final String[][] WALLPAPER_STYLES = {
            {"唯美艺术", "风格唯美具有艺术感，色彩丰富细腻"},
            {"水墨国风", "中国水墨画风格，黑白灰为主调，留白意境深远"},
            {"印象派",   "印象派油画风格，笔触明显，光影交织变幻"},
            {"极简主义", "极简主义风格，简洁线条，大面积纯色留白"},
            {"自然风光", "写实自然风光摄影风格，高清细腻逼真"},
            {"赛博朋克", "赛博朋克风格，霓虹灯光，暗色调未来感"}
    };

    private int selectedStyleIndex = 0;
    private MoodFaceView.FaceStyle selectedFaceStyle = MoodFaceView.FaceStyle.CLASSIC;
    private final List<TextView> styleChipViews = new ArrayList<>();
    private final List<android.view.View> faceStyleItemViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_settings);

        config = CollectionConfig.getInstance(this);

        initViews();
        setupStyleChips();
        setupTimeSlots();
        setupIconGrid();
        setupPersonalGoal();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        styleChipsContainer = findViewById(R.id.style_chips_container);
        iconGridContainer = findViewById(R.id.icon_grid_container);
        tvTimeSlot1 = findViewById(R.id.tv_time_slot_1);
        tvTimeSlot2 = findViewById(R.id.tv_time_slot_2);
        tvTimeSlot3 = findViewById(R.id.tv_time_slot_3);
        etPersonalGoal = findViewById(R.id.et_personal_goal);
        btnSaveGoal = findViewById(R.id.btn_save_goal);
    }

    private void setupStyleChips() {
        String currentStyle = config.getString(CollectionConfig.KEY_WALLPAPER_STYLE, "唯美艺术");
        styleChipViews.clear();

        for (int i = 0; i < WALLPAPER_STYLES.length; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(WALLPAPER_STYLES[i][0]);
            chip.setTextSize(13);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);

            if (WALLPAPER_STYLES[i][0].equals(currentStyle)) {
                selectedStyleIndex = i;
                chip.setBackgroundResource(R.drawable.chip_selected_bg);
                chip.setTextColor(0xFF1A1A2E);
            } else {
                chip.setBackgroundResource(R.drawable.chip_unselected_bg);
                chip.setTextColor(0xFFB0B0C0);
            }

            chip.setOnClickListener(v -> selectStyle(idx));
            styleChipsContainer.addView(chip);
            styleChipViews.add(chip);
        }
    }

    private void selectStyle(int index) {
        selectedStyleIndex = index;
        config.setString(CollectionConfig.KEY_WALLPAPER_STYLE, WALLPAPER_STYLES[index][0]);

        for (int i = 0; i < styleChipViews.size(); i++) {
            TextView chip = styleChipViews.get(i);
            if (i == index) {
                chip.setBackgroundResource(R.drawable.chip_selected_bg);
                chip.setTextColor(0xFF1A1A2E);
            } else {
                chip.setBackgroundResource(R.drawable.chip_unselected_bg);
                chip.setTextColor(0xFFB0B0C0);
            }
        }
    }

    private void setupTimeSlots() {
        int[] slots = config.getWallpaperScheduleSlots();
        tvTimeSlot1.setText(formatMinuteOfDay(slots[0]));
        tvTimeSlot2.setText(formatMinuteOfDay(slots[1]));
        tvTimeSlot3.setText(formatMinuteOfDay(slots[2]));

        android.view.View slot1Parent = (android.view.View) tvTimeSlot1.getParent();
        android.view.View slot2Parent = (android.view.View) tvTimeSlot2.getParent();
        android.view.View slot3Parent = (android.view.View) tvTimeSlot3.getParent();

        slot1Parent.setOnClickListener(v -> showTimePicker(0, tvTimeSlot1,
                CollectionConfig.KEY_WALLPAPER_SCHEDULE_SLOT_1));
        slot2Parent.setOnClickListener(v -> showTimePicker(1, tvTimeSlot2,
                CollectionConfig.KEY_WALLPAPER_SCHEDULE_SLOT_2));
        slot3Parent.setOnClickListener(v -> showTimePicker(2, tvTimeSlot3,
                CollectionConfig.KEY_WALLPAPER_SCHEDULE_SLOT_3));
    }

    private void showTimePicker(int slotIndex, TextView display, String configKey) {
        int current = config.getInt(configKey,
                slotIndex == 0 ? 480 : slotIndex == 1 ? 720 : 1200);
        int hour = current / 60;
        int minute = current % 60;

        new TimePickerDialog(this, (view, h, m) -> {
            int minuteOfDay = h * 60 + m;
            config.setInt(configKey, minuteOfDay);
            display.setText(formatMinuteOfDay(minuteOfDay));
        }, hour, minute, true).show();
    }

    private String formatMinuteOfDay(int minuteOfDay) {
        return String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private void setupIconGrid() {
        String savedStyle = config.getString(CollectionConfig.KEY_FACE_STYLE, "CLASSIC");
        selectedFaceStyle = MoodFaceView.FaceStyle.fromName(savedStyle);
        faceStyleItemViews.clear();

        MoodFaceView.FaceStyle[] styles = MoodFaceView.FaceStyle.values();
        for (int i = 0; i < styles.length; i++) {
            final MoodFaceView.FaceStyle fs = styles[i];

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8), dp(8), dp(8), dp(8));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(88));
            lp.setMarginEnd(dp(8));
            item.setLayoutParams(lp);

            MoodFaceView preview = new MoodFaceView(this);
            preview.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
            preview.setFaceStyle(fs);
            preview.setStressImmediate(0.35f);
            item.addView(preview);

            TextView label = new TextView(this);
            label.setText(fs.labelCn);
            label.setTextSize(11);
            label.setTextColor(0xFFB0B0C0);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(4);
            label.setLayoutParams(labelLp);
            item.addView(label);

            if (fs == selectedFaceStyle) {
                item.setBackgroundResource(R.drawable.icon_selected_bg);
            } else {
                item.setBackgroundResource(R.drawable.icon_unselected_bg);
            }

            item.setOnClickListener(v -> selectFaceStyle(fs));
            iconGridContainer.addView(item);
            faceStyleItemViews.add(item);
        }
    }

    private void selectFaceStyle(MoodFaceView.FaceStyle fs) {
        selectedFaceStyle = fs;
        config.setString(CollectionConfig.KEY_FACE_STYLE, fs.name());

        MoodFaceView.FaceStyle[] styles = MoodFaceView.FaceStyle.values();
        for (int i = 0; i < faceStyleItemViews.size(); i++) {
            faceStyleItemViews.get(i).setBackgroundResource(
                    styles[i] == fs ? R.drawable.icon_selected_bg : R.drawable.icon_unselected_bg);
        }

        Intent intent = new Intent(this, FloatingOverlayService.class);
        intent.setAction(FloatingOverlayService.ACTION_UPDATE_FACE_STYLE);
        intent.putExtra(FloatingOverlayService.EXTRA_FACE_STYLE, fs.name());
        startService(intent);

        Toast.makeText(this, "已选择风格: " + fs.labelCn, Toast.LENGTH_SHORT).show();
    }

    private void setupPersonalGoal() {
        String savedGoal = config.getString(CollectionConfig.KEY_USER_PERSONAL_GOAL, "");
        if (savedGoal != null && !savedGoal.isEmpty()) {
            etPersonalGoal.setText(savedGoal);
        }

        btnSaveGoal.setOnClickListener(v -> {
            String goal = etPersonalGoal.getText().toString().trim();
            config.setString(CollectionConfig.KEY_USER_PERSONAL_GOAL, goal);
            Toast.makeText(this, goal.isEmpty() ? "目标已清除" : "目标已保存", Toast.LENGTH_SHORT).show();
        });
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}

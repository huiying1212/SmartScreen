package com.datacollector.android.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.datacollector.android.R;
import com.datacollector.android.services.FloatingOverlayService;
import com.datacollector.android.utils.CollectionConfig;
import com.datacollector.android.views.MoodFaceView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PersonalSettingsActivity extends Activity {

    private CollectionConfig config;

    private LinearLayout styleChipsContainer, iconGridContainer;
    private TextView tvTimeSlot1, tvTimeSlot2, tvTimeSlot3;
    private EditText etPersonalGoal;
    private Button btnSaveGoal;

    // 结构化目标 UI
    private LinearLayout structuredGoalsList;
    private TextView tvGoalsEmptyHint;
    private Button btnAddGoal;

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
        setupStructuredGoals();
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

        structuredGoalsList = findViewById(R.id.structured_goals_list);
        tvGoalsEmptyHint = findViewById(R.id.tv_goals_empty_hint);
        btnAddGoal = findViewById(R.id.btn_add_goal);
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
                chip.setTextColor(0xFFFFFFFF);
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
                chip.setTextColor(0xFFFFFFFF);
            } else {
                chip.setBackgroundResource(R.drawable.chip_unselected_bg);
                chip.setTextColor(0xFF8B89B8);
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
            preview.setGlobalAlpha(255); // Full opacity in settings preview
            preview.setStressImmediate(0f);
            item.addView(preview);

            TextView label = new TextView(this);
            label.setText(fs.labelCn);
            label.setTextSize(11);
            label.setTextColor(0xFF8B89B8);
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

    // ── 结构化目标 ────────────────────────────────────────────

    private static final String[][] CATEGORY_OPTIONS = {
            {"short_video", "短视频"},
            {"social", "社交"},
            {"entertainment", "娱乐"},
            {"games", "游戏"},
    };

    private static final String[] GOAL_TYPE_LABELS = {
            "每日屏幕总时长上限",
            "App 类别时长上限",
            "特定 App 时长上限",
    };

    private void setupStructuredGoals() {
        btnAddGoal.setOnClickListener(v -> showAddGoalDialog());
        refreshStructuredGoalsUI();
    }

    private void showAddGoalDialog() {
        // 检查哪些类型还可以添加
        JSONArray goals = loadGoals();
        boolean hasDailyLimit = false;
        for (int i = 0; i < goals.length(); i++) {
            JSONObject g = goals.optJSONObject(i);
            if (g != null && "daily_screen_limit".equals(g.optString("type"))) {
                hasDailyLimit = true;
                break;
            }
        }

        List<String> availableLabels = new ArrayList<>();
        List<Integer> availableIndices = new ArrayList<>();
        if (!hasDailyLimit) {
            availableLabels.add(GOAL_TYPE_LABELS[0]);
            availableIndices.add(0);
        }
        // 类别和 App 限制可以添加多个
        availableLabels.add(GOAL_TYPE_LABELS[1]);
        availableIndices.add(1);
        availableLabels.add(GOAL_TYPE_LABELS[2]);
        availableIndices.add(2);

        new AlertDialog.Builder(this)
                .setTitle("选择目标类型")
                .setItems(availableLabels.toArray(new String[0]), (d, which) -> {
                    int typeIndex = availableIndices.get(which);
                    switch (typeIndex) {
                        case 0: showDailyLimitPicker(); break;
                        case 1: showCategoryLimitPicker(); break;
                        case 2: showAppLimitPicker(); break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private JSONArray loadGoals() {
        try {
            return new JSONArray(config.getString(CollectionConfig.KEY_STRUCTURED_GOALS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveGoals(JSONArray goals) {
        config.setString(CollectionConfig.KEY_STRUCTURED_GOALS, goals.toString());
        refreshStructuredGoalsUI();
    }

    private void refreshStructuredGoalsUI() {
        JSONArray goals = loadGoals();

        // 空状态提示
        tvGoalsEmptyHint.setVisibility(goals.length() == 0 ? View.VISIBLE : View.GONE);

        // 刷新目标列表
        structuredGoalsList.removeAllViews();
        for (int i = 0; i < goals.length(); i++) {
            JSONObject g = goals.optJSONObject(i);
            if (g == null) continue;
            addGoalListItem(g, i);
        }
    }

    private void addGoalListItem(JSONObject goal, int index) {
        String type = goal.optString("type", "");
        int limitMin = goal.optInt("limit_minutes", 0);
        String desc;
        switch (type) {
            case "daily_screen_limit":
                desc = "📱 每日屏幕时长 ≤ " + formatLimitMinutes(limitMin);
                break;
            case "category_limit":
                desc = "📂 " + getCategoryLabel(goal.optString("category", ""))
                        + " ≤ " + formatLimitMinutes(limitMin);
                break;
            case "app_limit":
                desc = "📌 " + getAppLabel(goal.optString("package", ""))
                        + " ≤ " + formatLimitMinutes(limitMin);
                break;
            default:
                return;
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundColor(0x0D9594C4);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(6);
        row.setLayoutParams(rowLp);

        TextView tv = new TextView(this);
        tv.setText(desc);
        tv.setTextSize(13);
        tv.setTextColor(0xFF8B89B8);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tvLp);
        row.addView(tv);

        // 编辑按钮
        TextView edit = new TextView(this);
        edit.setText("编辑");
        edit.setTextSize(12);
        edit.setTextColor(0xFF7B79B2);
        edit.setPadding(dp(10), dp(4), dp(6), dp(4));
        edit.setOnClickListener(v -> editGoal(goal, index));
        row.addView(edit);

        // 删除按钮
        TextView del = new TextView(this);
        del.setText("删除");
        del.setTextSize(12);
        del.setTextColor(0xFFFF6B6B);
        del.setPadding(dp(6), dp(4), dp(4), dp(4));
        del.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage("确定删除这个目标吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        JSONArray goals = loadGoals();
                        JSONArray newGoals = new JSONArray();
                        for (int i = 0; i < goals.length(); i++) {
                            if (i != index) newGoals.put(goals.optJSONObject(i));
                        }
                        saveGoals(newGoals);
                        Toast.makeText(this, "目标已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        row.addView(del);

        structuredGoalsList.addView(row);
    }

    private void editGoal(JSONObject goal, int index) {
        String type = goal.optString("type", "");
        int currentMin = goal.optInt("limit_minutes", 60);
        String title;
        switch (type) {
            case "daily_screen_limit":
                title = "修改每日屏幕时长上限";
                break;
            case "category_limit":
                title = "修改 " + getCategoryLabel(goal.optString("category", "")) + " 时长上限";
                break;
            case "app_limit":
                title = "修改 " + getAppLabel(goal.optString("package", "")) + " 时长上限";
                break;
            default:
                return;
        }

        if ("daily_screen_limit".equals(type)) {
            // 用小时选择器
            NumberPicker picker = new NumberPicker(this);
            picker.setMinValue(1);
            picker.setMaxValue(16);
            picker.setValue(Math.max(1, Math.min(16, currentMin / 60)));
            String[] displayValues = new String[16];
            for (int i = 0; i < 16; i++) displayValues[i] = (i + 1) + " 小时";
            picker.setDisplayedValues(displayValues);

            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(picker)
                    .setPositiveButton("确定", (d, w) -> {
                        updateGoalAtIndex(index, picker.getValue() * 60);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            // 用分钟选择器
            showMinutesPicker(title, currentMin, minutes -> {
                updateGoalAtIndex(index, minutes);
            });
        }
    }

    private void updateGoalAtIndex(int index, int newLimitMinutes) {
        JSONArray goals = loadGoals();
        if (index >= 0 && index < goals.length()) {
            JSONObject g = goals.optJSONObject(index);
            if (g != null) {
                try {
                    g.put("limit_minutes", newLimitMinutes);
                } catch (JSONException ignored) {}
            }
            saveGoals(goals);
            Toast.makeText(this, "目标已更新", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDailyLimitPicker() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(1);
        picker.setMaxValue(16);
        // 查找现有值
        int currentHours = 5;
        JSONArray goals = loadGoals();
        for (int i = 0; i < goals.length(); i++) {
            JSONObject g = goals.optJSONObject(i);
            if (g != null && "daily_screen_limit".equals(g.optString("type"))) {
                currentHours = g.optInt("limit_minutes", 300) / 60;
                break;
            }
        }
        picker.setValue(currentHours);
        String[] displayValues = new String[16];
        for (int i = 0; i < 16; i++) displayValues[i] = (i + 1) + " 小时";
        picker.setDisplayedValues(displayValues);

        new AlertDialog.Builder(this)
                .setTitle("每日屏幕时长上限")
                .setView(picker)
                .setPositiveButton("确定", (d, w) -> {
                    int hours = picker.getValue();
                    setOrUpdateGoal("daily_screen_limit", null, null, hours * 60);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCategoryLimitPicker() {
        // 过滤掉已经设置了限制的类别
        JSONArray goals = loadGoals();
        List<String[]> available = new ArrayList<>();
        for (String[] opt : CATEGORY_OPTIONS) {
            boolean exists = false;
            for (int i = 0; i < goals.length(); i++) {
                JSONObject g = goals.optJSONObject(i);
                if (g != null && "category_limit".equals(g.optString("type"))
                        && opt[0].equals(g.optString("category"))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) available.add(opt);
        }

        if (available.isEmpty()) {
            Toast.makeText(this, "所有类别都已设置了限制", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[available.size()];
        for (int i = 0; i < available.size(); i++) labels[i] = available.get(i)[1];

        new AlertDialog.Builder(this)
                .setTitle("选择要限制的类别")
                .setItems(labels, (d, which) -> {
                    String categoryEn = available.get(which)[0];
                    showMinutesPicker("设置 " + labels[which] + " 时长上限", 60, minutes -> {
                        setOrUpdateGoal("category_limit", categoryEn, null, minutes);
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAppLimitPicker() {
        // 常见娱乐 App 快捷列表
        String[][] commonApps = {
                {"com.ss.android.ugc.aweme", "抖音"},
                {"com.kuaishou.nebula", "快手"},
                {"tv.danmaku.bili", "Bilibili"},
                {"com.tencent.mm", "微信"},
                {"com.tencent.mobileqq", "QQ"},
                {"com.sina.weibo", "微博"},
                {"com.zhihu.android", "知乎"},
                {"com.ss.android.article.news", "今日头条"},
        };

        // 过滤掉已经设置了限制的 App
        JSONArray goals = loadGoals();
        List<String[]> available = new ArrayList<>();
        for (String[] app : commonApps) {
            boolean exists = false;
            for (int i = 0; i < goals.length(); i++) {
                JSONObject g = goals.optJSONObject(i);
                if (g != null && "app_limit".equals(g.optString("type"))
                        && app[0].equals(g.optString("package"))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) available.add(app);
        }

        if (available.isEmpty()) {
            Toast.makeText(this, "常用 App 都已设置了限制", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[available.size()];
        for (int i = 0; i < available.size(); i++) labels[i] = available.get(i)[1];

        new AlertDialog.Builder(this)
                .setTitle("选择要限制的App")
                .setItems(labels, (d, which) -> {
                    String pkg = available.get(which)[0];
                    showMinutesPicker("设置 " + labels[which] + " 时长上限", 30, minutes -> {
                        setOrUpdateGoal("app_limit", null, pkg, minutes);
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private interface MinutesCallback {
        void onMinutesSelected(int minutes);
    }

    private void showMinutesPicker(String title, int defaultMinutes, MinutesCallback callback) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(1);
        picker.setMaxValue(24);
        int[] minuteValues = {10, 15, 30, 45, 60, 90, 120, 150, 180, 210, 240, 270,
                300, 330, 360, 390, 420, 450, 480, 540, 600, 660, 720, 780};
        // 找到最接近 defaultMinutes 的索引
        int defaultIndex = 2; // 默认 30 分钟
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < minuteValues.length; i++) {
            int diff = Math.abs(minuteValues[i] - defaultMinutes);
            if (diff < minDiff) {
                minDiff = diff;
                defaultIndex = i;
            }
        }
        picker.setValue(defaultIndex + 1);
        String[] displayValues = new String[24];
        for (int i = 0; i < 24; i++) {
            if (minuteValues[i] < 60) {
                displayValues[i] = minuteValues[i] + " 分钟";
            } else {
                int h = minuteValues[i] / 60;
                int m = minuteValues[i] % 60;
                displayValues[i] = m == 0 ? h + " 小时" : h + " 小时 " + m + " 分钟";
            }
        }
        picker.setDisplayedValues(displayValues);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(picker)
                .setPositiveButton("确定", (d, w) -> {
                    callback.onMinutesSelected(minuteValues[picker.getValue() - 1]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setOrUpdateGoal(String type, String category, String pkg, int limitMinutes) {
        JSONArray goals = loadGoals();
        JSONArray newGoals = new JSONArray();
        boolean updated = false;

        for (int i = 0; i < goals.length(); i++) {
            JSONObject g = goals.optJSONObject(i);
            if (g == null) continue;
            String gType = g.optString("type", "");
            if (gType.equals(type)) {
                // 对于 category_limit 和 app_limit，需要匹配具体的 category/package
                boolean match = false;
                if ("daily_screen_limit".equals(type)) {
                    match = true;
                } else if ("category_limit".equals(type)
                        && category != null && category.equals(g.optString("category"))) {
                    match = true;
                } else if ("app_limit".equals(type)
                        && pkg != null && pkg.equals(g.optString("package"))) {
                    match = true;
                }
                if (match) {
                    // 更新现有目标
                    try {
                        g.put("limit_minutes", limitMinutes);
                    } catch (JSONException ignored) {}
                    newGoals.put(g);
                    updated = true;
                    continue;
                }
            }
            newGoals.put(g);
        }

        if (!updated) {
            try {
                JSONObject newGoal = new JSONObject();
                newGoal.put("type", type);
                newGoal.put("limit_minutes", limitMinutes);
                if (category != null) newGoal.put("category", category);
                if (pkg != null) newGoal.put("package", pkg);
                newGoals.put(newGoal);
            } catch (JSONException ignored) {}
        }

        saveGoals(newGoals);
        Toast.makeText(this, "目标已保存", Toast.LENGTH_SHORT).show();
    }

    private String formatLimitMinutes(int minutes) {
        if (minutes <= 0) return "未设置";
        if (minutes < 60) return minutes + " 分钟";
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分钟";
    }

    private String getCategoryLabel(String categoryEn) {
        for (String[] opt : CATEGORY_OPTIONS) {
            if (opt[0].equals(categoryEn)) return opt[1];
        }
        return categoryEn;
    }

    private String getAppLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            // 简单的包名到中文名映射
            if (pkg.contains("aweme")) return "抖音";
            if (pkg.contains("kuaishou")) return "快手";
            if (pkg.contains("bili")) return "Bilibili";
            if (pkg.contains("tencent.mm")) return "微信";
            if (pkg.contains("mobileqq")) return "QQ";
            if (pkg.contains("weibo")) return "微博";
            if (pkg.contains("zhihu")) return "知乎";
            return pkg;
        }
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}

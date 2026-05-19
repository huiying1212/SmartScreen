"""
UX Questionnaire — Categorized Analysis
========================================
Category A  (Q1-Q3 + Q4):  干预感知有效性  Perceived Intervention Effectiveness
Category B  (Q5-Q8):       干预准确性与适应性  Accuracy & Adaptiveness
Category C  (Q9, Q10):     开放性问题  Open-ended Insights

Generates publication-style figures into docs/figures/.
"""
from __future__ import annotations

import os
import sys
import textwrap

import numpy as np
import matplotlib as mpl
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

# ── Paths ────────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(SCRIPT_DIR, "..", "docs", "figures")
os.makedirs(OUT_DIR, exist_ok=True)

# ── Typography ───────────────────────────────────────────────────────────────
mpl.rcParams.update({
    "font.sans-serif": ["SimHei", "Microsoft YaHei", "Arial Unicode MS"],
    "axes.unicode_minus": False,
    "figure.facecolor": "#FFFFFF",
    "axes.facecolor": "#FFFFFF",
    "savefig.facecolor": "#FFFFFF",
    "axes.edgecolor": "#D1D5DB",
    "axes.labelcolor": "#374151",
    "xtick.color": "#4B5563",
    "ytick.color": "#4B5563",
    "text.color": "#1F2937",
    "font.size": 10,
})

# ── Palette ──────────────────────────────────────────────────────────────────
C_TEXT = "#1F2937"
C_GRID = "#E5E7EB"
# Likert 5-level: desaturated sequential
LIKERT_5 = ["#9B8B96", "#B5A8A0", "#C9BFB2", "#8BA3AD", "#4A6E7F"]
# Category accent colors
C_CAT_A = "#4A6E7F"   # teal-slate
C_CAT_B = "#6B7F5E"   # sage-olive
C_PIE = ["#4A6E7F", "#8BA3AD", "#C4A574"]

UX_MAP = {"非常同意": 5, "比较同意": 4, "一般": 3, "不太同意": 2, "很不同意": 1}
UX_LABELS = ["很不同意", "不太同意", "一般", "比较同意", "非常同意"]

# ── Raw data (15 participants) ───────────────────────────────────────────────
# Q1  悬浮表情图标让我更加意识到自己的手机使用习惯
# Q2  AI 生成的反思壁纸让我对自己的日常屏幕使用产生了反思
# Q3  在使用系统期间，我有过主动放下手机的时刻
# Q4  你认为哪种干预方式更有效？ (choice)
# Q5  悬浮图标上的表情准确反映了我当时的使用状态
# Q6  气泡提示文字与我当时的实际情况相关
# Q7  AI 生成的壁纸内容与我的屏幕使用情境相关
# Q8  系统的干预程度是合适的，没有让我感到烦扰
# Q9  场景描述 (open)
# Q10 改进建议 (open)

DATA = [
    {"q1":"非常同意","q2":"比较同意","q3":"一般",  "q4":"悬浮表情图标","q5":"比较同意","q6":"非常同意","q7":"非常同意","q8":"非常同意",
     "q9":"我在晚上工作时，打开手机发现壁纸的内容和我的真实情境非常相似，这让我意识时间已经很晚了我不应该再拿起手机拖延时间了而应该快点处理好工作准备休息。",
     "q10":"我的反思表情很容易就达到疲惫的状态了，但是我每天不可避免地要花很长时间使用手机，它的状态能结合我的个人目标做调整就好了。"},
    {"q1":"非常同意","q2":"一般",  "q3":"比较同意","q4":"悬浮表情图标","q5":"比较同意","q6":"非常同意","q7":"一般",  "q8":"比较同意",
     "q9":"刷手机到半夜，悬浮表情变成了哭脸，提醒我要放下手机入睡了",
     "q10":"生成的壁纸我有点没看懂，包含手机、数据线和房间元素，是提醒我要放下手机充电吗？或许可以画一点卡通小人看手机很累或者缩在被窝里玩眼睛疼的壁纸，使用者更有代入感"},
    {"q1":"非常同意","q2":"一般",  "q3":"非常同意","q4":"悬浮表情图标","q5":"不太同意","q6":"比较同意","q7":"一般",  "q8":"非常同意",
     "q9":"刚开始刷视频的时候有个人脸浮窗在屏幕上，好像有点被注视的感觉，会有意识地少耍点视频。但习惯后该刷还是会刷的。\n悬浮图标点击一下就会显示今天已经使用了多长时间手机以及一些语段，确实会让自己有意识地减少手机沉迷。",
     "q10":"统计时间似乎是以晚上12点为一天的分割？但其实很多时候12点还没睡。\n系统设置里有采集权限选项，如果能显示选择勾选了什么权限，能对应APP的什么功能可能会好点，比如我不太清楚获取wifi和蓝牙设备的权限目的是什么。"},
    {"q1":"比较同意","q2":"一般",  "q3":"比较同意","q4":"悬浮表情图标","q5":"比较同意","q6":"非常同意","q7":"非常同意","q8":"比较同意",
     "q9":"反思图标提醒我使用手机时间已过五小时，我意识到娱乐时间过长，遂放下手机开始工作。","q10":"无"},
    {"q1":"比较同意","q2":"比较同意","q3":"非常同意","q4":"悬浮表情图标","q5":"比较同意","q6":"非常同意","q7":"比较同意","q8":"非常同意",
     "q9":"玩了很久手机，悬浮表情的文字，提醒我站起来放松身体和眼睛","q10":"可以增加悬浮表情的多样性"},
    {"q1":"非常同意","q2":"非常同意","q3":"比较同意","q4":"悬浮表情图标","q5":"非常同意","q6":"比较同意","q7":"比较同意","q8":"比较同意",
     "q9":"抖音刷时间长了没有意识到，看到图标表情变得不开心才发现已经看了很久手机。","q10":"壁纸偏好切换后，壁纸更换会有延迟。"},
    {"q1":"非常同意","q2":"比较同意","q3":"比较同意","q4":"悬浮表情图标","q5":"非常同意","q6":"比较同意","q7":"比较同意","q8":"一般",
     "q9":"限制我刷小红书时间过长导致忘了要做的正事","q10":"不同情景下的壁纸的区别明显一些，现在有点难以区分"},
    {"q1":"非常同意","q2":"一般",  "q3":"非常同意","q4":"悬浮表情图标","q5":"一般",  "q6":"非常同意","q7":"不太同意","q8":"不太同意",
     "q9":"社会系统提示的表情会推动我去减少就是手机的使用。",
     "q10":"表情悬浮系统的干扰性太强。有时候正常工作使用手机也会导致表情会出现一些变化。反馈不够精准，如果反馈足够精准，并且在表现形式上更加个性化一点会更好。"},
    {"q1":"比较同意","q2":"一般",  "q3":"非常同意","q4":"悬浮表情图标┋反思壁纸","q5":"很不同意","q6":"比较同意","q7":"一般",  "q8":"不太同意",
     "q9":"有时候会下意识点桌面的图标，上面会显示玩了多久，手机做了什么？还蛮有意思的。手机玩太多了，可能就会看看电脑或者站起来溜达溜达。",
     "q10":"建议增加隐藏图标功能，不然打游戏的时候不方便。\n桌面的背景是对象的照片与实时AI的壁纸功能也发生冲突。"},
    {"q1":"非常同意","q2":"非常同意","q3":"比较同意","q4":"悬浮表情图标","q5":"比较同意","q6":"比较同意","q7":"比较同意","q8":"非常同意",
     "q9":"告诉了我朋友的生日让我给他祝福及时","q10":"建议美化一下悬浮表情"},
    {"q1":"一般",  "q2":"一般",  "q3":"一般",  "q4":"悬浮表情图标","q5":"比较同意","q6":"一般",  "q7":"不太同意","q8":"比较同意",
     "q9":"早起壁纸提醒喝水","q10":"无"},
    {"q1":"非常同意","q2":"非常同意","q3":"非常同意","q4":"反思壁纸","q5":"非常同意","q6":"非常同意","q7":"非常同意","q8":"非常同意",
     "q9":"开车的时候它能识别到我正在驾驶诶","q10":"挺好的"},
    {"q1":"非常同意","q2":"非常同意","q3":"非常同意","q4":"悬浮表情图标┋反思壁纸","q5":"非常同意","q6":"非常同意","q7":"非常同意","q8":"非常同意",
     "q9":"看到表情变哭了我就意识到自己手机使用的时间有点长了","q10":"暂无"},
    {"q1":"非常同意","q2":"比较同意","q3":"比较同意","q4":"悬浮表情图标┋反思壁纸","q5":"比较同意","q6":"非常同意","q7":"比较同意","q8":"非常同意",
     "q9":"无","q10":"无"},
    {"q1":"非常同意","q2":"非常同意","q3":"非常同意","q4":"反思壁纸","q5":"非常同意","q6":"非常同意","q7":"比较同意","q8":"非常同意",
     "q9":"我的个人目标设置了多喝水，每次点击悬浮图标它就会提醒我，非常有用","q10":"反思壁纸可能不是很准确"},
]

N = len(DATA)

def _save(fig, name):
    path = os.path.join(OUT_DIR, name)
    fig.savefig(path, dpi=300, bbox_inches="tight", facecolor="#FFFFFF")
    plt.close(fig)
    print(f"[Saved] {name}")


# ═══════════════════════════════════════════════════════════════════════════════
#  PART A — 干预感知有效性  (Q1-Q3 Likert + Q4 Preference)
# ═══════════════════════════════════════════════════════════════════════════════
print("=" * 55)
print("Part A: 干预感知有效性 (Q1–Q3, Q4)")
print("=" * 55)

catA_keys = ["q1", "q2", "q3"]
catA_labels = [
    "Q1 悬浮图标提升\n使用习惯意识",
    "Q2 AI壁纸引发\n屏幕使用反思",
    "Q3 使用期间有过\n主动放下手机",
]
catA_matrix = np.array([[UX_MAP[d[k]] for k in catA_keys] for d in DATA])
catA_means = catA_matrix.mean(axis=0)
catA_stds = catA_matrix.std(axis=0, ddof=1)
catA_overall = catA_matrix.mean()

for lab, m, s in zip(catA_labels, catA_means, catA_stds):
    print(f"  {lab.replace(chr(10), ' ')}: M={m:.2f}, SD={s:.2f}")
print(f"  维度均值: {catA_overall:.2f}")

# ── Fig A-1: Likert stacked bar (Q1-Q3) ─────────────────────────────────────
fig, ax = plt.subplots(figsize=(9, 4.2))
n_qA = len(catA_keys)
for qi in range(n_qA):
    counts = [0] * 5
    for d in DATA:
        counts[UX_MAP[d[catA_keys[qi]]] - 1] += 1
    pcts = [c / N * 100 for c in counts]
    left = 0.0
    for li, (pct, color) in enumerate(zip(pcts, LIKERT_5)):
        ax.barh(qi, pct, left=left, color=color, height=0.55, edgecolor="#FFF", linewidth=0.4, zorder=2)
        if pct >= 8:
            fc = "#FFF" if li <= 1 or li == 4 else C_TEXT
            ax.text(left + pct / 2, qi, f"{pct:.0f}%", ha="center", va="center", fontsize=8.5, color=fc)
        left += pct

ax.set_yticks(range(n_qA))
ax.set_yticklabels(catA_labels, fontsize=9.5)
ax.set_xlabel("响应比例 (%)")
ax.set_title("干预感知有效性 — Likert 量表分布 (Q1–Q3)", fontsize=11.5, fontweight="semibold", color=C_TEXT)
ax.set_xlim(0, 100)
ax.invert_yaxis()
ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.6, zorder=0)
ax.set_axisbelow(True)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)
leg_patches = [mpatches.Patch(facecolor=c, edgecolor="none") for c in LIKERT_5]
ax.legend(leg_patches, UX_LABELS, loc="lower center", bbox_to_anchor=(0.5, -0.30),
          ncol=5, frameon=True, facecolor="#FFFFFF", edgecolor=C_GRID, fontsize=8.5)
fig.subplots_adjust(bottom=0.22)
_save(fig, "catA_likert.png")

# ── Fig A-2: Mean bar with dimension average line ───────────────────────────
fig, ax = plt.subplots(figsize=(7.5, 3.4))
yA = np.arange(n_qA)
ax.barh(yA, catA_means, xerr=catA_stds, color=C_CAT_A, edgecolor="#FFF", linewidth=0.5,
        height=0.5, capsize=2.5, error_kw={"linewidth": 0.9, "ecolor": "#4B5563", "capthick": 0.9}, zorder=2)
for i, (m, s) in enumerate(zip(catA_means, catA_stds)):
    ax.text(float(m + s) + 0.08, i, f"{m:.2f}", va="center", fontsize=9, color=C_TEXT)
ax.axvline(catA_overall, color="#9CA3AF", linestyle=(0, (4, 3)), linewidth=1, label=f"维度均值 ({catA_overall:.2f})")
ax.set_yticks(yA)
ax.set_yticklabels(catA_labels, fontsize=9.5)
ax.set_xlabel("平均得分 (1–5)")
ax.set_title("干预感知有效性 — 各题项平均得分", fontsize=11.5, fontweight="semibold", color=C_TEXT)
ax.set_xlim(0, 5.85)
ax.axvline(3, color=C_GRID, linestyle=":", linewidth=0.8, zorder=0)
ax.invert_yaxis()
ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.6, zorder=0)
ax.set_axisbelow(True)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)
ax.legend(loc="lower right", frameon=True, facecolor="#FFFFFF", edgecolor=C_GRID, fontsize=9)
plt.tight_layout()
_save(fig, "catA_means.png")

# ── Fig A-3: Q4 Intervention preference pie ─────────────────────────────────
pref_counts = {"悬浮表情图标": 0, "反思壁纸": 0, "两者都有效": 0}
for d in DATA:
    p = d["q4"]
    if "┋" in p or "｜" in p or "|" in p:
        pref_counts["两者都有效"] += 1
    elif "反思壁纸" in p and "悬浮" not in p:
        pref_counts["反思壁纸"] += 1
    else:
        pref_counts["悬浮表情图标"] += 1

labels_pie = list(pref_counts.keys())
sizes_pie = list(pref_counts.values())
total_pref = sum(sizes_pie)

print(f"\n  Q4 干预方式偏好 (N={total_pref}):")
for k, v in pref_counts.items():
    print(f"    {k}: {v} ({v / total_pref * 100:.1f}%)")

fig, ax = plt.subplots(figsize=(5.8, 4.8))
_wedges, texts, autotexts = ax.pie(
    sizes_pie, labels=labels_pie, colors=C_PIE, explode=(0.02, 0.02, 0.02),
    autopct=lambda pct, n=total_pref: f"{pct:.1f}%\n({int(round(pct * n / 100))}人)",
    startangle=90,
    wedgeprops={"linewidth": 0.8, "edgecolor": "#FFF"},
    textprops={"fontsize": 10, "color": C_TEXT},
)
for t in texts:
    t.set_fontsize(10)
for at in autotexts:
    at.set_fontsize(9.5)
    at.set_color("#FFF")
    at.set_fontweight("semibold")
ax.set_title("Q4 用户认为更有效的干预方式", fontsize=11.5, fontweight="semibold", color=C_TEXT)
plt.tight_layout()
_save(fig, "catA_preference.png")

# ═══════════════════════════════════════════════════════════════════════════════
#  PART B — 干预准确性与适应性  (Q5-Q8)
# ═══════════════════════════════════════════════════════════════════════════════
print("\n" + "=" * 55)
print("Part B: 干预准确性与适应性 (Q5–Q8)")
print("=" * 55)

catB_keys = ["q5", "q6", "q7", "q8"]
catB_labels = [
    "Q5 表情准确反映\n使用状态",
    "Q6 气泡文字与\n实际情况相关",
    "Q7 壁纸内容与\n使用情境相关",
    "Q8 干预程度合适\n不感到烦扰",
]
catB_matrix = np.array([[UX_MAP[d[k]] for k in catB_keys] for d in DATA])
catB_means = catB_matrix.mean(axis=0)
catB_stds = catB_matrix.std(axis=0, ddof=1)
catB_overall = catB_matrix.mean()

for lab, m, s in zip(catB_labels, catB_means, catB_stds):
    print(f"  {lab.replace(chr(10), ' ')}: M={m:.2f}, SD={s:.2f}")
print(f"  维度均值: {catB_overall:.2f}")

# ── Fig B-1: Likert stacked bar (Q5-Q8) ─────────────────────────────────────
fig, ax = plt.subplots(figsize=(9, 4.8))
n_qB = len(catB_keys)
for qi in range(n_qB):
    counts = [0] * 5
    for d in DATA:
        counts[UX_MAP[d[catB_keys[qi]]] - 1] += 1
    pcts = [c / N * 100 for c in counts]
    left = 0.0
    for li, (pct, color) in enumerate(zip(pcts, LIKERT_5)):
        ax.barh(qi, pct, left=left, color=color, height=0.55, edgecolor="#FFF", linewidth=0.4, zorder=2)
        if pct >= 8:
            fc = "#FFF" if li <= 1 or li == 4 else C_TEXT
            ax.text(left + pct / 2, qi, f"{pct:.0f}%", ha="center", va="center", fontsize=8.5, color=fc)
        left += pct

ax.set_yticks(range(n_qB))
ax.set_yticklabels(catB_labels, fontsize=9.5)
ax.set_xlabel("响应比例 (%)")
ax.set_title("干预准确性与适应性 — Likert 量表分布 (Q5–Q8)", fontsize=11.5, fontweight="semibold", color=C_TEXT)
ax.set_xlim(0, 100)
ax.invert_yaxis()
ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.6, zorder=0)
ax.set_axisbelow(True)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)
leg_patches_b = [mpatches.Patch(facecolor=c, edgecolor="none") for c in LIKERT_5]
ax.legend(leg_patches_b, UX_LABELS, loc="lower center", bbox_to_anchor=(0.5, -0.26),
          ncol=5, frameon=True, facecolor="#FFFFFF", edgecolor=C_GRID, fontsize=8.5)
fig.subplots_adjust(bottom=0.20)
_save(fig, "catB_likert.png")

# ── Fig B-2: Mean bar with dimension average line ───────────────────────────
fig, ax = plt.subplots(figsize=(7.5, 3.8))
yB = np.arange(n_qB)
ax.barh(yB, catB_means, xerr=catB_stds, color=C_CAT_B, edgecolor="#FFF", linewidth=0.5,
        height=0.5, capsize=2.5, error_kw={"linewidth": 0.9, "ecolor": "#4B5563", "capthick": 0.9}, zorder=2)
for i, (m, s) in enumerate(zip(catB_means, catB_stds)):
    ax.text(float(m + s) + 0.08, i, f"{m:.2f}", va="center", fontsize=9, color=C_TEXT)
ax.axvline(catB_overall, color="#9CA3AF", linestyle=(0, (4, 3)), linewidth=1, label=f"维度均值 ({catB_overall:.2f})")
ax.set_yticks(yB)
ax.set_yticklabels(catB_labels, fontsize=9.5)
ax.set_xlabel("平均得分 (1–5)")
ax.set_title("干预准确性与适应性 — 各题项平均得分", fontsize=11.5, fontweight="semibold", color=C_TEXT)
ax.set_xlim(0, 5.85)
ax.axvline(3, color=C_GRID, linestyle=":", linewidth=0.8, zorder=0)
ax.invert_yaxis()
ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.6, zorder=0)
ax.set_axisbelow(True)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)
ax.legend(loc="lower right", frameon=True, facecolor="#FFFFFF", edgecolor=C_GRID, fontsize=9)
plt.tight_layout()
_save(fig, "catB_means.png")

# ── Fig B-3: Dimension comparison radar (A vs B) ────────────────────────────
dim_names = ["干预感知有效性\n(Q1–Q3)", "干预准确性与适应性\n(Q5–Q8)"]
dim_means = [catA_overall, catB_overall]
dim_stds = [catA_matrix.mean(axis=1).std(ddof=1), catB_matrix.mean(axis=1).std(ddof=1)]

fig, ax = plt.subplots(figsize=(6, 3.6))
x_dim = np.arange(len(dim_names))
bar_colors_dim = [C_CAT_A, C_CAT_B]
bars = ax.bar(x_dim, dim_means, yerr=dim_stds, color=bar_colors_dim, edgecolor="#FFF",
              width=0.45, capsize=4, error_kw={"linewidth": 1, "ecolor": "#4B5563", "capthick": 1}, zorder=2)
for bar, m, s in zip(bars, dim_means, dim_stds):
    ax.text(bar.get_x() + bar.get_width() / 2, m + s + 0.08, f"{m:.2f}",
            ha="center", va="bottom", fontsize=10, fontweight="semibold", color=C_TEXT)
ax.set_xticks(x_dim)
ax.set_xticklabels(dim_names, fontsize=10)
ax.set_ylabel("维度平均得分 (1–5)")
ax.set_title("两个评估维度对比", fontsize=11.5, fontweight="semibold", color=C_TEXT)
ax.set_ylim(0, 5.5)
ax.axhline(3, color=C_GRID, linestyle=":", linewidth=0.8, zorder=0)
ax.yaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.6, zorder=0)
ax.set_axisbelow(True)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)
plt.tight_layout()
_save(fig, "catAB_comparison.png")

# ═══════════════════════════════════════════════════════════════════════════════
#  PART C — 开放性问题  (Q9 场景描述, Q10 改进建议)
# ═══════════════════════════════════════════════════════════════════════════════
print("\n" + "=" * 55)
print("Part C: 开放性问题 (Q9, Q10)")
print("=" * 55)

# ── Q9: Thematic coding of behavior-change scenarios ────────────────────────
q9_themes = {
    "悬浮表情触发\n时间意识":     0,
    "气泡文字/点击\n信息提醒":    0,
    "壁纸情境\n引发反思":         0,
    "主动放下手机\n/减少使用":    0,
    "个人目标\n提醒":             0,
}

q9_raw = [d["q9"] for d in DATA]
for txt in q9_raw:
    t = txt.replace("\n", "")
    if t in ("无", "暂无", ""):
        continue
    if any(kw in t for kw in ["表情", "哭脸", "图标表情", "人脸浮窗"]):
        q9_themes["悬浮表情触发\n时间意识"] += 1
    if any(kw in t for kw in ["文字", "点击", "显示", "提示", "语段"]):
        q9_themes["气泡文字/点击\n信息提醒"] += 1
    if any(kw in t for kw in ["壁纸", "驾驶", "情境"]):
        q9_themes["壁纸情境\n引发反思"] += 1
    if any(kw in t for kw in ["放下手机", "减少", "站起来", "溜达", "入睡", "休息", "工作"]):
        q9_themes["主动放下手机\n/减少使用"] += 1
    if any(kw in t for kw in ["目标", "喝水", "生日"]):
        q9_themes["个人目标\n提醒"] += 1

print("\n  Q9 系统提醒引发的行为改变场景主题编码:")
for k, v in q9_themes.items():
    print(f"    {k.replace(chr(10), ' ')}: {v} 次提及")

fig, ax = plt.subplots(figsize=(8, 4))
theme_labels = list(q9_themes.keys())
theme_counts = list(q9_themes.values())
y_t = np.arange(len(theme_labels))
ax.barh(y_t, theme_counts, color=C_CAT_A, edgecolor="#FFF", linewidth=0.5, height=0.52, zorder=2)
for i, c in enumerate(theme_counts):
    ax.text(c + 0.15, i, str(c), va="center", fontsize=10, color=C_TEXT, fontweight="semibold")
ax.set_yticks(y_t)
ax.set_yticklabels(theme_labels, fontsize=9.5)
ax.set_xlabel("提及次数 (可多主题)")
ax.set_title("Q9 系统提醒引发的行为改变场景 — 主题编码频次", fontsize=11.5, fontweight="semibold", color=C_TEXT)
ax.invert_yaxis()
ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.6, zorder=0)
ax.set_axisbelow(True)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)
max_x = max(theme_counts) + 2 if theme_counts else 5
ax.set_xlim(0, max_x)
ax.xaxis.set_major_locator(mpl.ticker.MaxNLocator(integer=True))
plt.tight_layout()
_save(fig, "catC_q9_themes.png")

# ── Q10: Improvement suggestions — thematic coding ─────────────────────────
q10_themes = {
    "壁纸内容\n可读性/准确性":   0,
    "悬浮图标\n美化/多样性":     0,
    "个性化/\n目标适配":         0,
    "干扰控制\n(隐藏/冲突)":     0,
    "权限说明\n/时间统计":       0,
}

q10_raw = [d["q10"] for d in DATA]
for txt in q10_raw:
    t = txt.replace("\n", "")
    if t in ("无", "暂无", "挺好的", ""):
        continue
    if any(kw in t for kw in ["壁纸", "没看懂", "区分", "准确", "延迟"]):
        q10_themes["壁纸内容\n可读性/准确性"] += 1
    if any(kw in t for kw in ["美化", "多样性", "表现形式", "个性化"]):
        q10_themes["悬浮图标\n美化/多样性"] += 1
    if any(kw in t for kw in ["目标", "个人", "个性"]):
        q10_themes["个性化/\n目标适配"] += 1
    if any(kw in t for kw in ["隐藏", "干扰", "冲突", "游戏", "对象"]):
        q10_themes["干扰控制\n(隐藏/冲突)"] += 1
    if any(kw in t for kw in ["权限", "统计", "12点", "功能"]):
        q10_themes["权限说明\n/时间统计"] += 1

print("\n  Q10 改进建议主题编码:")
for k, v in q10_themes.items():
    print(f"    {k.replace(chr(10), ' ')}: {v} 次提及")

fig, ax = plt.subplots(figsize=(8, 4))
theme_labels_10 = list(q10_themes.keys())
theme_counts_10 = list(q10_themes.values())
y_t10 = np.arange(len(theme_labels_10))
ax.barh(y_t10, theme_counts_10, color=C_CAT_B, edgecolor="#FFF", linewidth=0.5, height=0.52, zorder=2)
for i, c in enumerate(theme_counts_10):
    ax.text(c + 0.15, i, str(c), va="center", fontsize=10, color=C_TEXT, fontweight="semibold")
ax.set_yticks(y_t10)
ax.set_yticklabels(theme_labels_10, fontsize=9.5)
ax.set_xlabel("提及次数 (可多主题)")
ax.set_title("Q10 改进建议 — 主题编码频次", fontsize=11.5, fontweight="semibold", color=C_TEXT)
ax.invert_yaxis()
ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.6, zorder=0)
ax.set_axisbelow(True)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)
max_x10 = max(theme_counts_10) + 2 if theme_counts_10 else 5
ax.set_xlim(0, max_x10)
ax.xaxis.set_major_locator(mpl.ticker.MaxNLocator(integer=True))
plt.tight_layout()
_save(fig, "catC_q10_themes.png")

# ── Print representative quotes for thesis ──────────────────────────────────
print("\n  Q9 代表性回答 (供论文引用):")
for i, txt in enumerate(q9_raw):
    if txt not in ("无", "暂无", ""):
        wrapped = textwrap.fill(txt.replace("\n", " "), width=70)
        print(f"    U{i+1}: {wrapped}")

print("\n  Q10 代表性回答 (供论文引用):")
for i, txt in enumerate(q10_raw):
    if txt not in ("无", "暂无", "挺好的", ""):
        wrapped = textwrap.fill(txt.replace("\n", " "), width=70)
        print(f"    U{i+1}: {wrapped}")

# ═══════════════════════════════════════════════════════════════════════════════
#  SUMMARY
# ═══════════════════════════════════════════════════════════════════════════════
print("\n" + "=" * 55)
print("All figures saved to:", os.path.abspath(OUT_DIR))
print("=" * 55)

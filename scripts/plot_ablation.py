"""
Generate publication-quality ablation figures from ablation_results.txt.

Produces:
  figures/ablation_accuracy_flicker.pdf   -- accuracy + flicker/min side-by-side
  figures/ablation_per_class_f1.pdf       -- per-class F1 grouped bar chart
  figures/ablation_conf_calibration.pdf   -- avg confidence per config
  figures/ablation_combined.pdf           -- all four sub-plots on one page
  figures/ablation_tradeoff.pdf           -- accuracy vs flicker scatter (trade-off view)

Run from project root:
    python scripts/plot_ablation.py
"""

import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

# ── Data ─────────────────────────────────────────────────────────────────────

CONFIGS = ['A', 'B', 'C', 'D', 'E', 'F']
LABELS  = [
    'A: Baseline\n(no smooth,\nno penalty)',
    'B: Penalty\nonly',
    'C: Smooth-3\nonly',
    'D: Smooth-3\n+Penalty',
    'E: Smooth-5\nonly',
    'F: Full\n(Ours)',
]
LABELS_SHORT = ['A', 'B', 'C', 'D', 'E', 'F\n(Ours)']

ACCURACY   = [93.17, 93.17, 94.16, 94.05, 94.36, 94.35]
FLICKER    = [3.70,  3.70,  1.58,  1.85,  1.71,  1.40 ]
AVG_CONF   = [0.9039, 0.7024, 0.9040, 0.7053, 0.9039, 0.7048]

F1_RUNNING     = [84.43, 84.43, 86.31, 86.43, 86.66, 86.69]
F1_STATIONARY  = [99.39, 99.39, 99.39, 99.47, 99.42, 99.50]
F1_WALKING     = [91.85, 91.85, 93.05, 92.89, 93.28, 93.25]

# ── Style ─────────────────────────────────────────────────────────────────────

# Suitable for academic papers; falls back gracefully if fonts unavailable
plt.rcParams.update({
    'font.family':       'serif',
    'font.size':         9,
    'axes.titlesize':    10,
    'axes.labelsize':    9,
    'xtick.labelsize':   8,
    'ytick.labelsize':   8,
    'legend.fontsize':   8,
    'figure.dpi':        300,
    'savefig.dpi':       300,
    'savefig.bbox':      'tight',
    'savefig.pad_inches': 0.05,
    'axes.spines.top':   False,
    'axes.spines.right': False,
    'axes.grid':         True,
    'grid.alpha':        0.3,
    'grid.linestyle':    '--',
    'grid.linewidth':    0.5,
})

N = len(CONFIGS)
x = np.arange(N)

# Colour palette: grey for non-ablated configs, accent for full system
BASE_COLOR    = '#6baed6'   # blue-grey  — smooth only
PENALTY_COLOR = '#fd8d3c'   # orange     — penalty only
SMOOTH3P_COLOR= '#e31a1c'   # red        — smooth-3 + penalty
BASELINE_COLOR= '#bdbdbd'   # grey       — no smoothing
FULL_COLOR    = '#2166ac'   # dark blue  — our full system

BAR_COLORS = [
    BASELINE_COLOR,  # A
    PENALTY_COLOR,   # B
    BASE_COLOR,      # C
    PENALTY_COLOR,   # D
    BASE_COLOR,      # E
    FULL_COLOR,      # F
]

os.makedirs('figures', exist_ok=True)

# ── Helper ───────────────────────────────────────────────────────────────────

def annotate_bars(ax, bars, fmt='{:.2f}', offset=0.05, fontsize=7):
    for bar in bars:
        h = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2,
                h + offset, fmt.format(h),
                ha='center', va='bottom', fontsize=fontsize)


# ═══════════════════════════════════════════════════════════════════════════════
# Figure 1: Accuracy + Flicker side-by-side
# ═══════════════════════════════════════════════════════════════════════════════

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(7.2, 3.0))
fig.subplots_adjust(wspace=0.38)

# — Accuracy —
bars1 = ax1.bar(x, ACCURACY, color=BAR_COLORS, width=0.6, edgecolor='white', linewidth=0.5)
ax1.set_xticks(x)
ax1.set_xticklabels(LABELS_SHORT)
ax1.set_ylabel('Accuracy (%)')
ax1.set_title('(a) Classification Accuracy')
ax1.set_ylim(91.5, 95.5)
ax1.yaxis.set_major_locator(plt.MultipleLocator(0.5))
annotate_bars(ax1, bars1, fmt='{:.2f}', offset=0.04)
# Baseline reference line
ax1.axhline(ACCURACY[0], color='grey', linestyle=':', linewidth=0.8, label=f'Baseline {ACCURACY[0]:.2f}%')
ax1.legend(loc='lower right', framealpha=0.7)

# — Flicker/min —
bars2 = ax2.bar(x, FLICKER, color=BAR_COLORS, width=0.6, edgecolor='white', linewidth=0.5)
ax2.set_xticks(x)
ax2.set_xticklabels(LABELS_SHORT)
ax2.set_ylabel('Flicker Rate (transitions / min)')
ax2.set_title('(b) Output Stability (lower is better)')
ax2.set_ylim(0, 4.5)
annotate_bars(ax2, bars2, fmt='{:.2f}', offset=0.05)
ax2.axhline(FLICKER[0], color='grey', linestyle=':', linewidth=0.8, label=f'Baseline {FLICKER[0]:.2f}')
ax2.legend(loc='upper right', framealpha=0.7)

# Shared legend for bar colours
legend_elements = [
    mpatches.Patch(facecolor=BASELINE_COLOR, label='No smoothing'),
    mpatches.Patch(facecolor=BASE_COLOR,     label='Smoothing only'),
    mpatches.Patch(facecolor=PENALTY_COLOR,  label='Penalty involved'),
    mpatches.Patch(facecolor=FULL_COLOR,     label='Full system (Ours)'),
]
fig.legend(handles=legend_elements, loc='lower center', ncol=4,
           bbox_to_anchor=(0.5, -0.08), frameon=False)

fig.savefig('figures/ablation_accuracy_flicker.pdf')
fig.savefig('figures/ablation_accuracy_flicker.png')
print("Saved: figures/ablation_accuracy_flicker.pdf/.png")
plt.close(fig)


# ═══════════════════════════════════════════════════════════════════════════════
# Figure 2: Per-class F1 grouped bar chart
# ═══════════════════════════════════════════════════════════════════════════════

fig, ax = plt.subplots(figsize=(7.2, 3.4))

w = 0.22
x3 = np.arange(N)
r1 = ax.bar(x3 - w,     F1_RUNNING,    width=w, label='Running',    color='#d73027', edgecolor='white', linewidth=0.4)
r2 = ax.bar(x3,         F1_WALKING,    width=w, label='Walking',    color='#4dac26', edgecolor='white', linewidth=0.4)
r3 = ax.bar(x3 + w,     F1_STATIONARY, width=w, label='Stationary', color='#0571b0', edgecolor='white', linewidth=0.4)

ax.set_xticks(x3)
ax.set_xticklabels(LABELS_SHORT)
ax.set_ylabel('F1 Score (%)')
ax.set_title('Per-Class F1 Score across Ablation Configurations')
ax.set_ylim(80, 101)
ax.yaxis.set_major_locator(plt.MultipleLocator(2))
ax.legend(loc='lower right', ncol=3, framealpha=0.8)

# Value annotations only on outer groups to avoid clutter
for bars in [r1, r3]:
    for bar in bars:
        h = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2, h + 0.1,
                f'{h:.1f}', ha='center', va='bottom', fontsize=6.5)

# Highlight the full-system column
ax.axvspan(N - 1 - 0.45, N - 1 + 0.45, alpha=0.06, color=FULL_COLOR, zorder=0)

fig.savefig('figures/ablation_per_class_f1.pdf')
fig.savefig('figures/ablation_per_class_f1.png')
print("Saved: figures/ablation_per_class_f1.pdf/.png")
plt.close(fig)


# ═══════════════════════════════════════════════════════════════════════════════
# Figure 3: Confidence calibration
# ═══════════════════════════════════════════════════════════════════════════════

fig, ax = plt.subplots(figsize=(5.0, 2.8))

bars = ax.bar(x, [v * 100 for v in AVG_CONF], color=BAR_COLORS,
              width=0.6, edgecolor='white', linewidth=0.5)
ax.set_xticks(x)
ax.set_xticklabels(LABELS_SHORT)
ax.set_ylabel('Average Confidence Score (%)')
ax.set_title('Confidence Calibration Effect of Boundary Penalty')
ax.set_ylim(60, 96)
ax.yaxis.set_major_locator(plt.MultipleLocator(5))
annotate_bars(ax, bars, fmt='{:.1f}', offset=0.2, fontsize=7)

# Annotate the two groups
ax.annotate('No penalty\n(over-confident)', xy=(0.5, 90.5), fontsize=7.5,
            ha='center', color='#525252', style='italic')
ax.annotate('With penalty\n(calibrated)', xy=(1.5, 71.5), fontsize=7.5,
            ha='center', color='#525252', style='italic')

fig.savefig('figures/ablation_conf_calibration.pdf')
fig.savefig('figures/ablation_conf_calibration.png')
print("Saved: figures/ablation_conf_calibration.pdf/.png")
plt.close(fig)


# ═══════════════════════════════════════════════════════════════════════════════
# Figure 4: Combined 2×2 (for single-figure thesis inclusion)
# ═══════════════════════════════════════════════════════════════════════════════

fig, axes = plt.subplots(2, 2, figsize=(7.2, 5.6))
fig.subplots_adjust(hspace=0.52, wspace=0.38)
(axA, axB), (axC, axD) = axes

# ── (a) Accuracy ──
bars = axA.bar(x, ACCURACY, color=BAR_COLORS, width=0.6, edgecolor='white', linewidth=0.5)
axA.set_xticks(x); axA.set_xticklabels(LABELS_SHORT)
axA.set_ylabel('Accuracy (%)')
axA.set_title('(a) Classification Accuracy')
axA.set_ylim(91.5, 95.5)
axA.yaxis.set_major_locator(plt.MultipleLocator(1))
axA.axhline(ACCURACY[0], color='grey', linestyle=':', linewidth=0.8)
annotate_bars(axA, bars, fmt='{:.2f}', offset=0.04)

# ── (b) Flicker ──
bars = axB.bar(x, FLICKER, color=BAR_COLORS, width=0.6, edgecolor='white', linewidth=0.5)
axB.set_xticks(x); axB.set_xticklabels(LABELS_SHORT)
axB.set_ylabel('Flicker Rate (trans./min)')
axB.set_title('(b) Output Stability')
axB.set_ylim(0, 4.5)
axB.axhline(FLICKER[0], color='grey', linestyle=':', linewidth=0.8)
annotate_bars(axB, bars, fmt='{:.2f}', offset=0.05)

# ── (c) Per-class F1 ──
w = 0.22
r1 = axC.bar(x - w, F1_RUNNING,    width=w, label='Running',    color='#d73027', edgecolor='white', linewidth=0.4)
r2 = axC.bar(x,     F1_WALKING,    width=w, label='Walking',    color='#4dac26', edgecolor='white', linewidth=0.4)
r3 = axC.bar(x + w, F1_STATIONARY, width=w, label='Stationary', color='#0571b0', edgecolor='white', linewidth=0.4)
axC.set_xticks(x); axC.set_xticklabels(LABELS_SHORT)
axC.set_ylabel('F1 Score (%)')
axC.set_title('(c) Per-Class F1 Score')
axC.set_ylim(80, 102)
axC.yaxis.set_major_locator(plt.MultipleLocator(4))
axC.legend(loc='lower right', ncol=1, fontsize=7, framealpha=0.8)
axC.axvspan(N - 1 - 0.45, N - 1 + 0.45, alpha=0.06, color=FULL_COLOR, zorder=0)

# ── (d) Confidence calibration ──
bars = axD.bar(x, [v * 100 for v in AVG_CONF], color=BAR_COLORS,
               width=0.6, edgecolor='white', linewidth=0.5)
axD.set_xticks(x); axD.set_xticklabels(LABELS_SHORT)
axD.set_ylabel('Avg Confidence (%)')
axD.set_title('(d) Confidence Calibration')
axD.set_ylim(60, 96)
axD.yaxis.set_major_locator(plt.MultipleLocator(5))
annotate_bars(axD, bars, fmt='{:.1f}', offset=0.2)

# Shared legend
legend_elements = [
    mpatches.Patch(facecolor=BASELINE_COLOR, label='No smoothing'),
    mpatches.Patch(facecolor=BASE_COLOR,     label='Smoothing only'),
    mpatches.Patch(facecolor=PENALTY_COLOR,  label='Penalty involved'),
    mpatches.Patch(facecolor=FULL_COLOR,     label='Full system (Ours)'),
]
fig.legend(handles=legend_elements, loc='lower center', ncol=4,
           bbox_to_anchor=(0.5, -0.02), frameon=False, fontsize=8)

fig.savefig('figures/ablation_combined.pdf')
fig.savefig('figures/ablation_combined.png')
print("Saved: figures/ablation_combined.pdf/.png")
plt.close(fig)


# ═══════════════════════════════════════════════════════════════════════════════
# Figure 5: 精度–稳定性权衡散点图（中文版）
# A 和 B 的 (acc, flicker) 值完全相同，水平微移以便区分。
# ═══════════════════════════════════════════════════════════════════════════════

# 使用支持中文的字体（SimHei / Microsoft YaHei / STSong 按优先级回退）
import matplotlib.font_manager as fm
_zh_candidates = ['Microsoft YaHei', 'SimHei', 'STSong', 'WenQuanYi Micro Hei', 'Arial Unicode MS']
_zh_font = next((f for f in _zh_candidates
                 if any(f.lower() in fp.name.lower() for fp in fm.fontManager.ttflist)), None)
_zh_props = {'family': _zh_font} if _zh_font else {}

with plt.rc_context({'font.family': _zh_font or 'sans-serif',
                     'axes.unicode_minus': False}):

    fig, ax = plt.subplots(figsize=(6.5, 4.5))

    # Config metadata: (标注文字, 颜色, 标记形状, 点大小)
    # A 和 B 坐标完全相同，用大圆(A)叠小方块(B)表示两点重合
    configs_scatter = [
        ('基线\n（无平滑，无惩罚）',        BASELINE_COLOR, 'o', 160),   # A — 大圆，底层
        ('仅惩罚',                          PENALTY_COLOR,  's',  70),   # B — 小方块叠在A上
        ('平滑(K=3)，无惩罚',               BASE_COLOR,     'o',  80),   # C
        ('平滑(K=3) + 惩罚',                SMOOTH3P_COLOR, 's',  80),   # D
        ('平滑(K=5)，无惩罚',               BASE_COLOR,     'D',  80),   # E
        ('完整系统 — 平滑(K=5) + 惩罚',    FULL_COLOR,     '*', 200),   # F
    ]
    ZORDERS = [4, 5, 4, 4, 4, 4]   # B 的 zorder 高于 A，确保叠在上面

    for i, (desc, col, mk, sz) in enumerate(configs_scatter):
        ax.scatter(FLICKER[i], ACCURACY[i],
                   color=col, s=sz, marker=mk, zorder=ZORDERS[i],
                   edgecolors='#333333' if i == N-1 else 'white',
                   linewidths=1.2 if i == N-1 else 0.5)

    # 标注偏移（相对于实际数据点）
    anno_offsets = [
        ( 0.22, -0.10),   # 基线          → 右
        (-0.50,  0.08),   # 仅惩罚        → 左上
        (-0.55, -0.13),   # 平滑K=3无惩罚 → 左下
        ( 0.28, -0.10),   # 平滑K=3+惩罚  → 右
        ( 0.00,  0.16),   # 平滑K=5无惩罚 → 上方
        ( 0.52,  0.02),   # 完整系统      → 右侧
    ]

    ax.set_xlabel('抖动率（切换次数 / 分钟）')
    ax.set_ylabel('分类准确率（%）')
    ax.set_title('各配置的准确率–抖动率')
    ax.set_xlim(0.6, 4.5)
    ax.set_ylim(92.9, 94.65)
    ax.yaxis.set_major_locator(plt.MultipleLocator(0.5))
    ax.xaxis.set_major_locator(plt.MultipleLocator(0.5))

    # 图例 — 每个标记形状和颜色单独列出（含 Smooth-3+Penalty）
    legend_elements = [
        plt.scatter([], [], marker='o', color=BASELINE_COLOR, s=65,
                    label='基线：无平滑，无惩罚'),
        plt.scatter([], [], marker='s', color=PENALTY_COLOR,  s=65,
                    label='仅惩罚'),
        plt.scatter([], [], marker='o', color=BASE_COLOR,     s=65,
                    label='平滑(K=3)，无惩罚'),
        plt.scatter([], [], marker='s', color=SMOOTH3P_COLOR, s=65,
                    label='平滑(K=3) + 惩罚'),
        plt.scatter([], [], marker='D', color=BASE_COLOR,     s=55,
                    label='平滑(K=5)，无惩罚'),
        plt.scatter([], [], marker='*', color=FULL_COLOR,     s=160,
                    label='完整系统 — 平滑(K=5) + 惩罚（本文方法）'),
    ]
    ax.legend(handles=legend_elements, loc='lower left', fontsize=7.5, framealpha=0.88,
              handletextpad=0.4)

    fig.savefig('figures/ablation_tradeoff.pdf')
    fig.savefig('figures/ablation_tradeoff.png')
    print("Saved: figures/ablation_tradeoff.pdf/.png")
    plt.close(fig)

print("\nAll figures saved to figures/")

"""
Survey data analysis for thesis: SUS + User Experience Questionnaire.
Loads latest *.xlsx exports from repo root; generates publication-style figures.
"""
from __future__ import annotations

import glob
import os
import re
import sys

import numpy as np
import matplotlib.pyplot as plt
import matplotlib as mpl

try:
    import openpyxl
except ImportError:
    print("Please install openpyxl: pip install openpyxl", file=sys.stderr)
    raise

# ── Paths ────────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.normpath(os.path.join(SCRIPT_DIR, ".."))
OUT_DIR = os.path.join(REPO_ROOT, "docs", "figures")
os.makedirs(OUT_DIR, exist_ok=True)

# ── Typography (Chinese + minus sign) ────────────────────────────────────────
mpl.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei", "Arial Unicode MS", "Arial"]
mpl.rcParams["axes.unicode_minus"] = False
mpl.rcParams["figure.facecolor"] = "#FFFFFF"
mpl.rcParams["axes.facecolor"] = "#FFFFFF"
mpl.rcParams["savefig.facecolor"] = "#FFFFFF"
mpl.rcParams["axes.edgecolor"] = "#D1D5DB"
mpl.rcParams["axes.labelcolor"] = "#374151"
mpl.rcParams["xtick.color"] = "#374151"
mpl.rcParams["ytick.color"] = "#374151"
mpl.rcParams["text.color"] = "#1F2937"
mpl.rcParams["font.size"] = 10

# ── Palette (muted, print-friendly) ──────────────────────────────────────────
C_TEXT = "#1F2937"
C_GRID = "#E5E7EB"
C_LINE_BENCH = "#9CA3AF"   # industry benchmark
C_LINE_MEAN = "#1E3A5F"    # study mean
C_BAR_HIGH = "#3D5A80"     # SUS ≥ 68
C_BAR_MID = "#7C8EA3"      # 51–67
C_BAR_LOW = "#A67C7C"      # < 51
C_ITEM_POS = "#4A6670"     # SUS positive-worded items
C_ITEM_REV = "#8B7E74"     # SUS reverse-coded (display)
C_UX_BAR = "#5B7C8D"
C_UX_ERR = "#374151"
# Likert: low → high (sequential, low chroma)
LIKERT_COLORS = ["#8B7B82", "#A8968F", "#C4B8A8", "#7D9099", "#3D5A6B"]
C_PIE = ["#4A6670", "#7A8F99", "#C4A574"]  # slate / blue-gray / muted gold


def _newest_match(paths: list[str], pred) -> str | None:
    matched = [p for p in paths if pred(os.path.basename(p))]
    if not matched:
        return None
    return max(matched, key=os.path.getmtime)


def find_survey_workbooks(root: str) -> tuple[str | None, str | None]:
    paths = glob.glob(os.path.join(root, "*.xlsx"))
    sus_path = _newest_match(paths, lambda bn: "SUS" in bn or "系统可用性" in bn)
    ux_path = _newest_match(paths, lambda bn: "用户体验" in bn or "手机使用干预" in bn)
    return sus_path, ux_path


def numbered_question_columns(headers: list, max_num: int) -> list[tuple[int, int]]:
    """Return [(question_no, col_index), ...] sorted by question_no."""
    out: list[tuple[int, int]] = []
    for i, h in enumerate(headers):
        if h is None or not isinstance(h, str):
            continue
        m = re.match(r"^(\d+)、", h.strip())
        if not m:
            continue
        n = int(m.group(1))
        if 1 <= n <= max_num:
            out.append((n, i))
    out.sort(key=lambda x: x[0])
    return out


def load_active_sheet(path: str):
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb.active
    rows_iter = ws.iter_rows(values_only=True)
    header_row = next(rows_iter)
    headers = list(header_row)
    data = []
    for row in rows_iter:
        if row is None or all(v is None or (isinstance(v, str) and not v.strip()) for v in row):
            continue
        if row[0] is None:
            continue
        data.append(list(row))
    wb.close()
    return headers, data


# SUS: 非常同意=5 … 非常不同意=1
SUS_MAP = {"非常同意": 5, "同意": 4, "一般": 3, "不同意": 2, "非常不同意": 1}

# UX Likert (includes 比较同意 etc.)
UX_MAP = {"非常同意": 5, "比较同意": 4, "一般": 3, "不太同意": 2, "很不同意": 1}


def calc_sus_score(responses: list[str]) -> float:
    vals = [SUS_MAP[str(r).strip()] for r in responses]
    score = 0
    for i, v in enumerate(vals):
        if i % 2 == 0:
            score += v - 1
        else:
            score += 5 - v
    return score * 2.5


def main() -> None:
    sus_path, ux_path = find_survey_workbooks(REPO_ROOT)
    if not sus_path or not ux_path:
        print("Could not find both survey xlsx files in:", REPO_ROOT, file=sys.stderr)
        print("Expected filenames containing SUS/系统可用性 and 用户体验/手机使用干预", file=sys.stderr)
        sys.exit(1)

    print("SUS file:", os.path.basename(sus_path))
    print("UX file:", os.path.basename(ux_path))

    # ── Load SUS ─────────────────────────────────────────────────────────────
    sus_headers, sus_rows = load_active_sheet(sus_path)
    sus_cols = numbered_question_columns(sus_headers, 10)
    if len(sus_cols) != 10:
        print(f"Warning: expected 10 SUS items, found {len(sus_cols)}", file=sys.stderr)

    sus_raw: list[list[str]] = []
    for row in sus_rows:
        try:
            chunk = [row[i] for _, i in sus_cols]
            if any(c is None or str(c).strip() == "" for c in chunk):
                continue
            sus_raw.append([str(c).strip() for c in chunk])
        except IndexError:
            continue

    if not sus_raw:
        print("No SUS data rows.", file=sys.stderr)
        sys.exit(1)

    sus_scores = [calc_sus_score(r) for r in sus_raw]
    sus_mean = float(np.mean(sus_scores))
    sus_std = float(np.std(sus_scores, ddof=1)) if len(sus_scores) > 1 else 0.0
    sus_median = float(np.median(sus_scores))

    if sus_mean >= 80.3:
        grade = "A"
    elif sus_mean >= 68:
        grade = "B"
    elif sus_mean >= 51:
        grade = "C"
    else:
        grade = "D/F"

    print("=" * 50)
    print("SUS Analysis")
    print("=" * 50)
    print(f"N = {len(sus_scores)}")
    print(f"Individual scores: {[round(s, 1) for s in sus_scores]}")
    print(f"Mean: {sus_mean:.1f}  SD: {sus_std:.1f}  Median: {sus_median:.1f}")
    print(f"SUS Grade: {grade} (benchmark: 68)")

    # ── Figure: SUS per participant ───────────────────────────────────────────
    fig, ax = plt.subplots(figsize=(7.2, 4.6))
    n_p = len(sus_scores)
    labels = [f"U{i + 1}" for i in range(n_p)]
    bar_colors = [C_BAR_HIGH if s >= 68 else C_BAR_MID if s >= 51 else C_BAR_LOW for s in sus_scores]
    bars = ax.bar(
        labels,
        sus_scores,
        color=bar_colors,
        edgecolor="#FFFFFF",
        linewidth=0.6,
        width=0.62,
        zorder=2,
    )
    ax.axhline(68, color=C_LINE_BENCH, linestyle=(0, (4, 3)), linewidth=1.1, label="通用平均线 (68)", zorder=1)
    ax.axhline(sus_mean, color=C_LINE_MEAN, linestyle=(0, (1, 1.5)), linewidth=1.2, label=f"本研究均值 ({sus_mean:.1f})", zorder=1)
    ax.set_ylim(0, 105)
    ax.set_xlabel("参与者")
    ax.set_ylabel("SUS 得分")
    ax.set_title("系统可用性量表 (SUS) 各参与者得分", fontsize=12, fontweight="semibold", color=C_TEXT)
    ax.yaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.8, zorder=0)
    ax.set_axisbelow(True)
    for bar, score in zip(bars, sus_scores):
        ax.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + 1.2,
            f"{score:.0f}",
            ha="center",
            va="bottom",
            fontsize=9,
            color=C_TEXT,
        )
    ax.legend(loc="upper right", frameon=True, facecolor="#FFFFFF", edgecolor="#E5E7EB", fontsize=9)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    plt.tight_layout()
    fig.savefig(os.path.join(OUT_DIR, "sus_scores.png"), dpi=300, bbox_inches="tight", facecolor="#FFFFFF")
    plt.close()
    print("[Saved] sus_scores.png")

    # ── SUS items ───────────────────────────────────────────────────────────
    sus_items_short = [
        "Q1 愿意经常使用",
        "Q2 系统过于复杂(R)",
        "Q3 容易使用",
        "Q4 需要技术帮助(R)",
        "Q5 功能整合良好",
        "Q6 不一致之处多(R)",
        "Q7 容易学会",
        "Q8 用起来麻烦(R)",
        "Q9 使用中自信",
        "Q10 需学很多(R)",
    ]
    sus_matrix = np.array([[SUS_MAP[x] for x in row] for row in sus_raw])
    item_means = sus_matrix.mean(axis=0)
    item_stds = sus_matrix.std(axis=0, ddof=1) if len(sus_raw) > 1 else np.zeros(10)

    fig, ax = plt.subplots(figsize=(9, 5.2))
    y = np.arange(10)
    bar_colors_h = [C_ITEM_POS if i % 2 == 0 else C_ITEM_REV for i in range(10)]
    ax.barh(
        y,
        item_means,
        xerr=item_stds,
        color=bar_colors_h,
        edgecolor="#FFFFFF",
        linewidth=0.5,
        height=0.58,
        capsize=2.5,
        error_kw={"linewidth": 0.9, "ecolor": C_UX_ERR, "capthick": 0.9},
        zorder=2,
    )
    for i, (m, s) in enumerate(zip(item_means, item_stds)):
        ax.text(float(m + s) + 0.12, i, f"{m:.2f}", va="center", fontsize=9, color=C_TEXT)
    ax.set_yticks(y)
    ax.set_yticklabels(sus_items_short, fontsize=9.5)
    ax.set_xlabel("平均得分 (1–5)")
    ax.set_title("SUS 各题项平均得分", fontsize=12, fontweight="semibold", color=C_TEXT)
    ax.set_xlim(0, 5.85)
    ax.axvline(3, color=C_GRID, linestyle=":", linewidth=1, zorder=0)
    ax.invert_yaxis()
    ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.7, zorder=0)
    ax.set_axisbelow(True)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    plt.tight_layout()
    fig.savefig(os.path.join(OUT_DIR, "sus_items.png"), dpi=300, bbox_inches="tight", facecolor="#FFFFFF")
    plt.close()
    print("[Saved] sus_items.png")

    # ── Load UX ──────────────────────────────────────────────────────────────
    ux_headers, ux_rows = load_active_sheet(ux_path)
    ux_numbered = {n: i for n, i in numbered_question_columns(ux_headers, 10)}
    # Likert: Q1–3, Q5–8 (Q4 is intervention preference)
    ux_q_nums = [1, 2, 3, 5, 6, 7, 8]
    missing = [n for n in ux_q_nums if n not in ux_numbered]
    if missing:
        print(f"Missing UX columns: {missing}", file=sys.stderr)
        sys.exit(1)
    pref_col = ux_numbered.get(4)

    ux_questions = [
        "Q1 悬浮图标提升\n使用习惯意识",
        "Q2 AI壁纸引发\n屏幕使用反思",
        "Q3 使用期间有过\n主动放下手机",
        "Q5 表情准确反映\n使用状态",
        "Q6 气泡文字与\n实际情况相关",
        "Q7 壁纸内容与\n使用情境相关",
        "Q8 干预程度合适\n不感到烦扰",
    ]

    ux_raw: list[list[str]] = []
    pref_raw: list[str] = []
    for row in ux_rows:
        try:
            likert_vals = [str(row[ux_numbered[n]]).strip() for n in ux_q_nums]
            if any(not v for v in likert_vals):
                continue
            ux_raw.append(likert_vals)
            if pref_col is not None and row[pref_col] is not None:
                pref_raw.append(str(row[pref_col]).strip())
        except (IndexError, KeyError):
            continue

    if not ux_raw:
        print("No UX data rows.", file=sys.stderr)
        sys.exit(1)

    try:
        ux_matrix = np.array([[UX_MAP[v] for v in row] for row in ux_raw])
    except KeyError as e:
        print("Unknown UX Likert value:", e, file=sys.stderr)
        sys.exit(1)

    print("\n" + "=" * 50)
    print("UX Questionnaire Analysis")
    print("=" * 50)
    print(f"N = {len(ux_raw)}")
    ux_means = ux_matrix.mean(axis=0)
    ux_stds = ux_matrix.std(axis=0, ddof=1) if len(ux_raw) > 1 else np.zeros(7)
    for q, m, s in zip(ux_questions, ux_means, ux_stds):
        print(f"  {q.replace(chr(10), ' ')}: M={m:.2f}, SD={s:.2f}")

    n_q = len(ux_questions)
    y_pos = np.arange(n_q)

    # ── UX Likert stacked ───────────────────────────────────────────────────
    likert_labels = ["很不同意", "不太同意", "一般", "比较同意", "非常同意"]
    fig, ax = plt.subplots(figsize=(10, 6.4))
    for qi in range(n_q):
        counts = [0] * 5
        for resp in ux_raw:
            val = UX_MAP[resp[qi]]
            counts[val - 1] += 1
        pcts = [c / len(ux_raw) * 100 for c in counts]
        left = 0.0
        for li, (pct, color) in enumerate(zip(pcts, LIKERT_COLORS)):
            ax.barh(qi, pct, left=left, color=color, height=0.62, edgecolor="#FFFFFF", linewidth=0.45, zorder=2)
            if pct >= 8:
                txt_color = "#FFFFFF" if li <= 1 or li == 4 else C_TEXT
                ax.text(left + pct / 2, qi, f"{pct:.0f}%", ha="center", va="center", fontsize=8, color=txt_color)
            left += pct

    ax.set_yticks(y_pos)
    ax.set_yticklabels(ux_questions, fontsize=9.5)
    ax.set_xlabel("响应比例 (%)")
    ax.set_title("用户体验问卷各题项 Likert 量表分布", fontsize=12, fontweight="semibold", color=C_TEXT)
    ax.set_xlim(0, 100)
    ax.invert_yaxis()
    ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.7, zorder=0)
    ax.set_axisbelow(True)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    leg = ax.legend(
        [mpl.patches.Patch(facecolor=c, edgecolor="none") for c in LIKERT_COLORS],
        likert_labels,
        loc="lower center",
        bbox_to_anchor=(0.5, -0.18),
        ncol=5,
        frameon=True,
        facecolor="#FFFFFF",
        edgecolor="#E5E7EB",
        fontsize=9,
    )
    fig.subplots_adjust(bottom=0.16)
    fig.savefig(os.path.join(OUT_DIR, "ux_likert.png"), dpi=300, bbox_inches="tight", facecolor="#FFFFFF")
    plt.close()
    print("[Saved] ux_likert.png")

    # ── UX means ────────────────────────────────────────────────────────────
    fig, ax = plt.subplots(figsize=(9, 5.2))
    ax.barh(
        y_pos,
        ux_means,
        xerr=ux_stds,
        color=C_UX_BAR,
        edgecolor="#FFFFFF",
        linewidth=0.5,
        height=0.55,
        capsize=2.5,
        error_kw={"linewidth": 0.9, "ecolor": C_UX_ERR, "capthick": 0.9},
        zorder=2,
    )
    for i, (m, s) in enumerate(zip(ux_means, ux_stds)):
        ax.text(float(m + s) + 0.08, i, f"{m:.2f}", va="center", fontsize=9, color=C_TEXT)
    ax.set_yticks(y_pos)
    ax.set_yticklabels(ux_questions, fontsize=9.5)
    ax.set_xlabel("平均得分 (1–5)")
    ax.set_title("用户体验问卷各题项平均得分", fontsize=12, fontweight="semibold", color=C_TEXT)
    ax.set_xlim(0, 5.85)
    ax.axvline(3, color=C_GRID, linestyle=":", linewidth=1, zorder=0)
    ax.invert_yaxis()
    ax.xaxis.grid(True, color=C_GRID, linestyle="-", linewidth=0.7, zorder=0)
    ax.set_axisbelow(True)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    plt.tight_layout()
    fig.savefig(os.path.join(OUT_DIR, "ux_means.png"), dpi=300, bbox_inches="tight", facecolor="#FFFFFF")
    plt.close()
    print("[Saved] ux_means.png")

    # ── Intervention preference ─────────────────────────────────────────────
    pref_counts = {"悬浮表情图标": 0, "反思壁纸": 0, "两者都有效": 0}
    for p in pref_raw:
        if "┋" in p or "｜" in p or "|" in p:
            pref_counts["两者都有效"] += 1
        elif "反思壁纸" in p and "悬浮" not in p:
            pref_counts["反思壁纸"] += 1
        elif "悬浮" in p:
            pref_counts["悬浮表情图标"] += 1
        else:
            pref_counts["悬浮表情图标"] += 1

    labels = list(pref_counts.keys())
    sizes = list(pref_counts.values())
    total_pref = sum(sizes)

    if total_pref > 0:
        fig, ax = plt.subplots(figsize=(6.2, 5))
        explode = (0.02, 0.02, 0.02)
        _wedges, texts, autotexts = ax.pie(
            sizes,
            labels=labels,
            colors=C_PIE,
            explode=explode,
            autopct=lambda pct, n=total_pref: f"{pct:.1f}%\n({int(round(pct * n / 100))}人)",
            startangle=90,
            wedgeprops={"linewidth": 0.8, "edgecolor": "#FFFFFF"},
            textprops={"fontsize": 10, "color": C_TEXT},
        )
        for t in texts:
            t.set_fontsize(10)
        for at in autotexts:
            at.set_fontsize(9.5)
            at.set_color("#FFFFFF")
            at.set_fontweight("semibold")
        ax.set_title("用户认为更有效的干预方式", fontsize=12, fontweight="semibold", color=C_TEXT)
        plt.tight_layout()
        fig.savefig(os.path.join(OUT_DIR, "intervention_preference.png"), dpi=300, bbox_inches="tight", facecolor="#FFFFFF")
        plt.close()
        print("[Saved] intervention_preference.png")
    else:
        print("[Skipped] intervention_preference.png (no Q4 preference column or empty)", file=sys.stderr)

    print("\n" + "=" * 50)
    print("Summary for thesis")
    print("=" * 50)
    print(f"SUS: M={sus_mean:.1f}, SD={sus_std:.1f}, range [{min(sus_scores):.1f}, {max(sus_scores):.1f}], grade {grade}")
    print(f"SUS ≥68: {sum(1 for s in sus_scores if s >= 68)}/{len(sus_scores)}")
    print("Intervention preference:")
    if total_pref > 0:
        for k, v in pref_counts.items():
            print(f"  {k}: {v} ({v / total_pref * 100:.1f}%)")
    else:
        print("  (no preference data)")
    print(f"\nFigures: {os.path.abspath(OUT_DIR)}")


if __name__ == "__main__":
    main()

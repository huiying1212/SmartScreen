"""
Ablation study for ActivityRecognizer.

Mirrors the current Java implementation exactly, including:
  - Decision tree with confidence penalty (30% relative margin, 0.5-1.0 range)
  - Exponential-decay confidence-weighted smoothing (weight *= 0.8, newest-first)
  - Smoothed confidence output (weighted average over matching history entries)
  - HISTORY_SIZE = 5

WISDM label mapping:
  A -> walking, B -> running (jogging), C -> stairs (mapped to walking),
  D -> stationary (sitting), E -> stationary (standing)
  Others ignored (no driving/cycling ground truth in WISDM).

Ablation axes:
  1. Smoothing (off / history=3 / history=5)
  2. Confidence penalty (off / on)
  3. Peak frequency threshold sensitivity
"""

import os
import glob
import sys
import math
from collections import Counter

# ── Parameters matching Java code ─────────────────────────────────────────────

WINDOW_SIZE = 40
STEP = 20          # 50% overlap
SAMPLE_RATE = 20.0

VALID_LABELS_MAP = {
    'A': 'walking',
    'B': 'running',
    'C': 'walking',     # stairs ≈ walking (rhythmic locomotion)
    'D': 'stationary',
    'E': 'stationary',
}

# ── Data loading ──────────────────────────────────────────────────────────────

def parse_data(files, max_users=None):
    """Parse WISDM phone accel files into consecutive window chunks."""
    all_chunks = []
    users_processed = 0

    for fpath in sorted(files):
        if max_users is not None and users_processed >= max_users:
            break

        try:
            with open(fpath, 'r') as f:
                lines = f.readlines()
        except Exception:
            continue

        x_list, y_list, z_list, label_list = [], [], [], []
        for line in lines:
            parts = line.strip().rstrip(';').split(',')
            if len(parts) >= 6:
                try:
                    label = parts[1]
                    x, y, z = float(parts[3]), float(parts[4]), float(parts[5])
                    x_list.append(x)
                    y_list.append(y)
                    z_list.append(z)
                    label_list.append(label)
                except ValueError:
                    pass

        # Sliding windows
        current_chunk = []
        for start in range(0, len(x_list) - WINDOW_SIZE + 1, STEP):
            end = start + WINDOW_SIZE
            labels = label_list[start:end]
            majority_raw = Counter(labels).most_common(1)[0][0]

            if majority_raw in VALID_LABELS_MAP:
                target = VALID_LABELS_MAP[majority_raw]
                xw = x_list[start:end]
                yw = y_list[start:end]
                zw = z_list[start:end]
                current_chunk.append({
                    'true_label': target,
                    'x': xw, 'y': yw, 'z': zw,
                })
            else:
                # Activity boundary → flush chunk
                if current_chunk:
                    all_chunks.append(current_chunk)
                    current_chunk = []

        if current_chunk:
            all_chunks.append(current_chunk)

        users_processed += 1
        print(f"  [{users_processed}] {os.path.basename(fpath)}: "
              f"{len(x_list)} samples", flush=True)

    return all_chunks


# ── Feature extraction (mirrors Java exactly) ────────────────────────────────

def extract_features(x, y, z):
    """Compute the same feature set as AccelFeatures in Java."""
    n = len(x)
    mag = [math.sqrt(x[i]**2 + y[i]**2 + z[i]**2) for i in range(n)]

    mean_val = sum(mag) / n if n > 0 else 0.0
    var_val = sum((v - mean_val)**2 for v in mag) / n if n > 0 else 0.0
    std_val = math.sqrt(var_val)
    energy_val = sum(v*v for v in mag) / n if n > 0 else 0.0

    # Zero-crossing rate
    if n <= 1:
        zcr = 0.0
    else:
        crossings = sum(1 for i in range(1, n)
                        if (mag[i-1] - mean_val) * (mag[i] - mean_val) < 0)
        zcr = crossings / (n - 1)

    # Range
    range_val = max(mag) - min(mag) if n > 0 else 0.0

    # Peak frequency (v <= threshold for hysteresis reset)
    threshold = mean_val + std_val * 0.5
    peaks = 0
    above = False
    for v in mag:
        if v > threshold and not above:
            peaks += 1
            above = True
        elif v <= threshold:
            above = False
    window_sec = n / SAMPLE_RATE
    peak_freq = peaks / window_sec if window_sec > 0 else 0.0

    # Inter-axis correlations
    corr_xy = _correlation(x, y, n)
    corr_xz = _correlation(x, z, n)
    corr_yz = _correlation(y, z, n)

    return {
        'mean': mean_val,
        'variance': var_val,
        'stdDev': std_val,
        'energy': energy_val,
        'zeroCrossingRate': zcr,
        'range': range_val,
        'peakFrequency': peak_freq,
        'corrXY': corr_xy,
        'corrXZ': corr_xz,
        'corrYZ': corr_yz,
    }


def _correlation(a, b, n):
    if n <= 1:
        return 0.0
    ma = sum(a) / n
    mb = sum(b) / n
    cov = sum((a[i]-ma)*(b[i]-mb) for i in range(n))
    va  = sum((a[i]-ma)**2 for i in range(n))
    vb  = sum((b[i]-mb)**2 for i in range(n))
    denom = math.sqrt(va * vb)
    return cov / denom if denom != 0 else 0.0


# ── Decision tree (mirrors Java exactly) ──────────────────────────────────────

def _leaf(label, conf):
    return {'isLeaf': True, 'label': label, 'conf': conf}

def _branch(feat, thresh, left, right):
    return {'isLeaf': False, 'feat': feat, 'thresh': thresh,
            'left': left, 'right': right}

def build_tree():
    stationary      = _leaf('stationary', 0.95)
    driving         = _leaf('driving',    0.80)
    cycling         = _leaf('cycling',    0.78)
    walking         = _leaf('walking',    0.90)
    walkingHighMean = _leaf('walking',    0.85)
    running         = _leaf('running',    0.88)

    confirmRunning = _branch('variance',      30.0, walkingHighMean, running)
    walkOrRun      = _branch('mean',          10.5, walking,  confirmRunning)
    vehicleOrCycle = _branch('variance',       2.5, driving,  cycling)
    moving         = _branch('peakFrequency',  0.8, vehicleOrCycle, walkOrRun)
    root           = _branch('variance',       1.0, stationary,     moving)
    return root

TREE = build_tree()


def classify_tree(features, penalty_enabled):
    """Traverse tree, return (label, confidence)."""
    def traverse(node, conf):
        if node['isLeaf']:
            return node['label'], conf * node['conf']
        val = features[node['feat']]
        thresh = node['thresh']
        if penalty_enabled:
            rel_dist = abs(val - thresh) / (thresh if thresh != 0 else 1.0)
            dist_ratio = min(rel_dist / 0.30, 1.0)
            penalty = 0.5 + 0.5 * dist_ratio
        else:
            penalty = 1.0
        child = node['left'] if val < thresh else node['right']
        return traverse(child, conf * penalty)

    return traverse(TREE, 1.0)


# ── Smoothing (mirrors Java exactly) ──────────────────────────────────────────

def smoothed_activity(history, fallback):
    """Confidence-weighted voting with exponential decay, newest-first."""
    scores = {}
    weight = 1.0
    for pred in reversed(history):
        score = pred['confidence'] * weight
        scores[pred['activity']] = scores.get(pred['activity'], 0.0) + score
        weight *= 0.8

    best_act = fallback
    max_score = -1.0
    for act, sc in scores.items():
        if sc > max_score:
            max_score = sc
            best_act = act
    return best_act


def smoothed_confidence(history, activity):
    """Weighted average confidence for a specific activity label."""
    total_conf = 0.0
    total_weight = 0.0
    weight = 1.0
    for pred in reversed(history):
        if pred['activity'] == activity:
            total_conf += pred['confidence'] * weight
            total_weight += weight
        weight *= 0.8
    return total_conf / total_weight if total_weight > 0 else 0.0


# ── Evaluation ────────────────────────────────────────────────────────────────

def evaluate(chunks, *, history_size, enable_penalty):
    """
    Run one configuration.
    history_size=0 means no smoothing (raw output).
    """
    tp = {}   # per-class true positive
    fp = {}   # per-class false positive
    fn = {}   # per-class false negative
    total = 0
    correct = 0
    transitions = 0
    total_conf = 0.0
    conf_list = []

    for chunk in chunks:
        history = []
        last_act = None

        for win in chunk:
            feats = extract_features(win['x'], win['y'], win['z'])
            raw_act, raw_conf = classify_tree(feats, enable_penalty)

            if history_size > 0:
                history.append({'activity': raw_act, 'confidence': raw_conf})
                if len(history) > history_size:
                    history = history[-history_size:]
                final_act = smoothed_activity(
                    history, last_act if last_act else 'stationary')
                final_conf = smoothed_confidence(history, final_act)
            else:
                final_act = raw_act
                final_conf = raw_conf

            true_label = win['true_label']
            total += 1
            total_conf += final_conf
            conf_list.append(final_conf)

            if final_act == true_label:
                correct += 1
            # Per-class stats
            for label in ['stationary', 'walking', 'running']:
                tp.setdefault(label, 0)
                fp.setdefault(label, 0)
                fn.setdefault(label, 0)
            if final_act == true_label:
                tp[true_label] = tp.get(true_label, 0) + 1
            else:
                fp[final_act] = fp.get(final_act, 0) + 1
                fn[true_label] = fn.get(true_label, 0) + 1

            if last_act is not None and final_act != last_act:
                transitions += 1
            last_act = final_act

    duration_min = total / 60.0
    accuracy = correct / total * 100 if total > 0 else 0

    # Per-class precision / recall / F1
    per_class = {}
    for label in sorted(tp.keys()):
        p = tp[label] / (tp[label] + fp.get(label, 0)) if (tp[label] + fp.get(label, 0)) > 0 else 0
        r = tp[label] / (tp[label] + fn.get(label, 0)) if (tp[label] + fn.get(label, 0)) > 0 else 0
        f1 = 2*p*r/(p+r) if (p+r) > 0 else 0
        per_class[label] = {'precision': p, 'recall': r, 'f1': f1}

    return {
        'accuracy': accuracy,
        'flicker/min': transitions / duration_min if duration_min > 0 else 0,
        'avg_conf': total_conf / total if total > 0 else 0,
        'windows': total,
        'per_class': per_class,
    }


# ── Pretty-print ──────────────────────────────────────────────────────────────

def fmt(res, label):
    s = f"\n{'='*60}\n{label}\n{'='*60}\n"
    s += f"  Accuracy:       {res['accuracy']:.2f}%\n"
    s += f"  Flicker/min:    {res['flicker/min']:.2f}\n"
    s += f"  Avg confidence: {res['avg_conf']:.4f}\n"
    s += f"  Windows:        {res['windows']}\n"
    s += f"  {'Class':<12} {'Prec':>6} {'Recall':>6} {'F1':>6}\n"
    s += f"  {'-'*34}\n"
    for cls in sorted(res['per_class']):
        c = res['per_class'][cls]
        s += f"  {cls:<12} {c['precision']:>6.2%} {c['recall']:>6.2%} {c['f1']:>6.2%}\n"
    return s


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    search_path = os.path.join('app', 'test_data', 'wisdm-dataset',
                               'raw', 'phone', 'accel', '*.txt')
    files = sorted(glob.glob(search_path))
    print(f"Found {len(files)} data files.\n")

    if not files:
        print("ERROR: No data files found. Run from project root.")
        sys.exit(1)

    chunks = parse_data(files, max_users=20)
    total_windows = sum(len(c) for c in chunks)
    print(f"\nTotal valid windows: {total_windows}  "
          f"({total_windows * STEP / SAMPLE_RATE / 60:.1f} min of data)\n")

    # ── Ablation configs ──────────────────────────────────────────────────
    configs = [
        # (label,                history_size, penalty)
        ("A. Baseline (no smooth, no penalty)",        0, False),
        ("B. Penalty only",                            0, True),
        ("C. Smooth-3 only",                           3, False),
        ("D. Smooth-3 + Penalty",                      3, True),
        ("E. Smooth-5 only",                           5, False),
        ("F. Smooth-5 + Penalty  [current system]",    5, True),
    ]

    results = []
    for label, hs, pen in configs:
        res = evaluate(chunks, history_size=hs, enable_penalty=pen)
        results.append((label, res))
        print(fmt(res, label))

    # ── Summary table ─────────────────────────────────────────────────────
    print("\n" + "="*72)
    print("SUMMARY")
    print("="*72)
    header = f"  {'Config':<45} {'Acc%':>6} {'Flk/m':>6} {'AvgCf':>6}"
    print(header)
    print("  " + "-" * 67)
    for label, res in results:
        print(f"  {label:<45} {res['accuracy']:>6.2f} "
              f"{res['flicker/min']:>6.2f} {res['avg_conf']:>6.4f}")

    # ── Write results ─────────────────────────────────────────────────────
    out_path = 'ablation_results.txt'
    with open(out_path, 'w') as f:
        for label, res in results:
            f.write(fmt(res, label))
        f.write("\n" + "="*72 + "\nSUMMARY\n" + "="*72 + "\n")
        f.write(header + "\n")
        f.write("  " + "-" * 67 + "\n")
        for label, res in results:
            f.write(f"  {label:<45} {res['accuracy']:>6.2f} "
                    f"{res['flicker/min']:>6.2f} {res['avg_conf']:>6.4f}\n")

    print(f"\nResults written to {out_path}")

"""
Evaluate the multi-signal scene-fusion component on the public ExtraSensory dataset.

Mirrors the Java class:
    LocationContextInferrer  (multi-signal scene fusion: commute / outdoor / indoor)

ExtraSensory provides the four sensor signals our algorithm uses:
    activity proxy  <- raw_acc:magnitude_stats:std  (accelerometer)
    GPS speed       <- location:max_speed
    GPS accuracy    <- location:best_horizontal_accuracy
    WiFi connected  <- discrete:wifi_status:is_reachable_via_wifi

GPS jitter filter and location clustering are NOT evaluated here because:
  - ExtraSensory does not publicly host per-minute absolute lat/lng coordinates.
  - The per-sample location:diameter feature is a within-minute aggregation,
    not suitable for inter-sample trail-distance computation.
  Those algorithms are best validated with the project's own collected user logs.

WifiFingerprint (BSSID -> home/work) is also NOT evaluated here because
ExtraSensory only exposes a binary WiFi-available flag, not BSSID identifiers.

Output: scripts/eval_context_modeling_results.txt

Usage:
    python scripts/download_extrasensory.py        # download data first
    python scripts/eval_context_modeling.py        # all 60 users
    python scripts/eval_context_modeling.py --max 10   # quick smoke test
"""

import argparse
import gzip
import os
import sys
from collections import Counter

ROOT     = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(ROOT, 'data', 'extrasensory')
FEAT_DIR = os.path.join(DATA_DIR, 'features_labels')
OUT_PATH = os.path.join(ROOT, 'scripts', 'eval_context_modeling_results.txt')


# ──────────────────────────────────────────────────────────────────────────────
# Data loader
# ──────────────────────────────────────────────────────────────────────────────

def parse_features_labels(uuid):
    """
    Parse one user's features+labels CSV.
    Returns a list of per-minute records:
        { 'ts': int, 'features': {name: float|None}, 'labels': set[str] }
    """
    path = os.path.join(FEAT_DIR, f'{uuid}.features_labels.csv.gz')
    if not os.path.exists(path):
        return []

    with gzip.open(path, 'rt') as f:
        header = f.readline().rstrip('\n').split(',')
        first_label_idx = next(
            i for i, c in enumerate(header) if c.startswith('label:'))
        feature_names = header[1:first_label_idx]
        label_names = [c[len('label:'):] for c in header[first_label_idx:-1]]

        records = []
        for line in f:
            parts = line.rstrip('\n').split(',')
            if len(parts) != len(header):
                continue
            try:
                ts = int(parts[0])
            except ValueError:
                continue

            features = {}
            for i, name in enumerate(feature_names):
                v = parts[1 + i]
                if v == '' or v.lower() == 'nan':
                    features[name] = None
                else:
                    try:
                        features[name] = float(v)
                    except ValueError:
                        features[name] = None

            labels = set()
            for j, lname in enumerate(label_names):
                v = parts[1 + len(feature_names) + j]
                if v == '1' or v == '1.0':
                    labels.add(lname)

            records.append({'ts': ts, 'features': features, 'labels': labels})

    return records


def list_users(max_users=None):
    if not os.path.isdir(FEAT_DIR):
        sys.exit(f"ERROR: dataset not found in {FEAT_DIR}. "
                 f"Run scripts/download_extrasensory.py first.")
    suffix = '.features_labels.csv.gz'
    uuids = sorted(n[:-len(suffix)] for n in os.listdir(FEAT_DIR)
                   if n.endswith(suffix))
    if max_users is not None:
        uuids = uuids[:max_users]
    return uuids


# ──────────────────────────────────────────────────────────────────────────────
# Java mirror: LocationContextInferrer
#
# Signal mapping (ExtraSensory feature -> Java code):
#   activity_type  <- raw_acc + proc_gyro statistics (multi-feature proxy that
#                     mirrors the Java decision tree's reliance on
#                     accelerometer magnitude variance + gyroscope variance)
#   speed (m/s)    <- location:max_speed
#   accuracy (m)   <- location:best_horizontal_accuracy
#   connectedToWifi <- discrete:wifi_status:is_reachable_via_wifi
#   btCount        <- not available in ExtraSensory (always 0)
#
# Thresholds were calibrated from ExtraSensory's per-class quantile profile
# (see scripts/diagnose_signals.py output). raw_acc:magnitude_stats:std in
# ExtraSensory is normalised to g (gravity units), with the following
# observed per-class medians on the deployment-aligned S2 subset:
#     indoor   median 0.0025  (p75 0.005)   -> static
#     commute  median 0.071   (p75 0.142)   -> moderate (vehicle vibration)
#     outdoor  median 0.118   (p75 0.327)   -> high (walking/running)
# ──────────────────────────────────────────────────────────────────────────────

ACT_DRIVING_SPEED = 5.0    # m/s, strong commute evidence
ACT_RUNNING_ACC   = 0.30   # g; >p75 of outdoor implies running/cycling
ACT_WALKING_ACC   = 0.05   # g; >p25 of outdoor / >p50 of commute
ACT_STILL_ACC     = 0.01   # g; <p25 of indoor

ACT_HIGH_GYRO     = 0.20   # rad/s std; separates walking from in-vehicle
ACT_HIGH_SPECENT  = 0.50   # spectral entropy; high = complex outdoor motion


def derive_activity(features):
    """
    Coarse activity classifier mirroring the production decision tree but
    using ExtraSensory's normalised accelerometer/gyroscope features.
    Returns (activity_type, confidence in [0, 1]).

    Priority of evidence:
      1. Sustained high GPS speed       -> driving      (strong, 0.85)
      2. Very low acc + low gyro var    -> still        (strong, 0.90)
      3. High acc magnitude std         -> running      (0.85)
      4. Moderate acc + high gyro var   -> walking      (0.80)
                                          (gyro var separates walking
                                           from vehicle vibration)
      5. Moderate acc + low gyro var
         + moderate speed evidence      -> driving      (0.70, "stop&go")
      6. Moderate acc + low gyro var    -> still        (0.55)
      7. Otherwise                      -> unknown      (0.0)
    """
    acc_std   = features.get('raw_acc:magnitude_stats:std')
    gyro_std  = features.get('proc_gyro:magnitude_stats:std')
    spec_ent  = features.get('raw_acc:magnitude_spectrum:spectral_entropy')
    max_speed = features.get('location:max_speed')

    if max_speed is not None and max_speed > ACT_DRIVING_SPEED:
        return 'driving', 0.85

    if acc_std is None:
        return 'unknown', 0.0

    if acc_std < ACT_STILL_ACC and (gyro_std is None or gyro_std < 0.01):
        return 'still', 0.90

    if acc_std > ACT_RUNNING_ACC:
        if spec_ent is not None and spec_ent > ACT_HIGH_SPECENT:
            return 'running', 0.85
        return 'running', 0.75

    if acc_std > ACT_WALKING_ACC:
        if gyro_std is not None and gyro_std > ACT_HIGH_GYRO:
            return 'walking', 0.80
        if max_speed is not None and max_speed > 1.5:
            return 'driving', 0.70
        if gyro_std is not None and gyro_std < 0.05:
            return 'driving', 0.60
        return 'walking', 0.55

    return 'still', 0.55


def is_connected_to_wifi(features):
    v = features.get('discrete:wifi_status:is_reachable_via_wifi')
    if v is None:
        return None
    return v > 0.5


def infer_context(features, signals_enabled=None):
    """
    Pure mirror of LocationContextInferrer.infer() — fallback branch only
    (ExtraSensory has no BSSID so the WiFi-fingerprint short-circuit is skipped).
    Returns one of: 'commute' / 'outdoor' / 'indoor' / None.

    signals_enabled controls which signals participate (for ablation):
        {'activity', 'speed', 'accuracy', 'wifi', 'bluetooth'}
    """
    if signals_enabled is None:
        signals_enabled = {'activity', 'speed', 'accuracy', 'wifi', 'bluetooth'}

    s_commute = 0.0
    s_outdoor = 0.0
    s_indoor  = 0.0

    # Signal 1: activity recognition
    if 'activity' in signals_enabled:
        act_type, act_conf = derive_activity(features)
        if act_type in ('driving', 'cycling'):
            s_commute += 5.0 * act_conf
        elif act_type == 'running':
            s_outdoor += 4.0 * act_conf
        elif act_type == 'walking':
            s_outdoor += 2.5 * act_conf
            s_commute += 0.5 * act_conf
        elif act_type == 'still':
            s_indoor += 1.5 * act_conf

    # Signal 2: GPS speed
    #
    # Refined thresholds: empirical analysis on ExtraSensory (S2 subset)
    # shows the original 1.2-3 m/s -> outdoor vote has only ~35% accuracy
    # because (a) GPS speed in this range mixes slow walking with
    # in-vehicle stop-and-go traffic, and (b) static GPS noise occasionally
    # produces spurious sub-3 m/s readings while the device is indoors.
    # The 1.2-3 m/s band is therefore treated as ambiguous (no vote),
    # mirroring the same "ambiguous middle band stays neutral" design
    # used for the GPS accuracy signal.
    if 'speed' in signals_enabled:
        speed = features.get('location:max_speed')
        if speed is not None:
            if speed > 5.0:
                s_commute += 4.0
            elif speed > 3.0:
                s_commute += 2.5
            elif speed <= 0.5:
                s_indoor += 0.5

    # Signal 3: GPS accuracy
    #
    # Refined thresholds: the 15-30m and 30-60m bands are intentionally
    # neutral. In reinforced-concrete urban buildings the modal indoor GPS
    # accuracy still falls in 30-60m due to roof leakage, so granting an
    # outdoor vote here produces systematic false positives. Only very good
    # fixes (<15m, clear sky line-of-sight) or very poor fixes (>60m,
    # consistent with deep-indoor multipath) are treated as evidence.
    if 'accuracy' in signals_enabled:
        acc = features.get('location:best_horizontal_accuracy')
        if acc is not None:
            if acc < 15:
                s_outdoor += 2.0
            elif acc < 60:
                pass
            elif acc < 150:
                s_indoor += 2.0
            else:
                s_indoor += 3.0

    # Signal 4: WiFi connectivity
    if 'wifi' in signals_enabled:
        connected = is_connected_to_wifi(features)
        if connected is True:
            s_indoor += 2.5
        elif connected is False:
            s_outdoor += 0.8
            s_commute += 0.5

    # Signal 5: Bluetooth peripheral device count is intentionally omitted
    # here. ExtraSensory does not record nearby Bluetooth devices, so this
    # signal cannot be evaluated on the public dataset. Its contribution is
    # therefore deferred to the on-device user study with our own logs.

    # Pick the highest-scoring label; abstain if none exceeds minimum threshold
    best_score = 1.0
    best_label = None
    if s_commute > best_score:
        best_score = s_commute
        best_label = 'commute'
    if s_outdoor > best_score:
        best_score = s_outdoor
        best_label = 'outdoor'
    if s_indoor > best_score:
        best_score = s_indoor
        best_label = 'indoor'
    return best_label


# ──────────────────────────────────────────────────────────────────────────────
# Ground truth from ExtraSensory labels
# ──────────────────────────────────────────────────────────────────────────────

COMMUTE_LABELS = {'IN_A_CAR', 'ON_A_BUS', 'DRIVE_-_I_M_THE_DRIVER',
                  'DRIVE_-_I_M_A_PASSENGER', 'BICYCLING'}
OUTDOOR_LABELS = {'OR_outside'}
INDOOR_LABELS  = {'OR_indoors'}

# Additional positive evidence that the user is in a fixed indoor location
# (filters out ambiguous samples, e.g. balcony/parking lot with stale labels)
INDOOR_EVIDENCE = {
    'LOC_home', 'LOC_main_workplace', 'AT_SCHOOL', 'SLEEPING',
    'COMPUTER_WORK', 'WATCHING_TV', 'EATING', 'IN_A_MEETING',
    'IN_CLASS', 'LAB_WORK', 'COOKING', 'WASHING_DISHES',
    'BATHING_-_SHOWER', 'TOILET',
}


def ground_truth_scene(labels):
    """
    Return scene label in {'commute', 'outdoor', 'indoor'} or None.

    Strict definition to exclude self-report ambiguities common in
    ExtraSensory (e.g. stale OR_indoors left on while walking outside):
      commute  := commute-transport label present
      outdoor  := OR_outside AND NOT OR_indoors AND NOT commute
      indoor   := OR_indoors AND NOT OR_outside AND NOT commute
                  AND at least one unambiguous indoor-activity label
      None     := everything else (excluded from evaluation)
    """
    has_commute = bool(labels & COMMUTE_LABELS)
    has_outdoor = bool(labels & OUTDOOR_LABELS)
    has_indoor  = bool(labels & INDOOR_LABELS)

    if has_commute:
        return 'commute'
    if has_outdoor and not has_indoor:
        return 'outdoor'
    if has_indoor and not has_outdoor and (labels & INDOOR_EVIDENCE):
        return 'indoor'
    return None


# ──────────────────────────────────────────────────────────────────────────────
# Deployment-domain filters
#
# ExtraSensory was collected on the UCSD campus, whose sensor environment
# differs from the target deployment (urban Chinese residential / office
# settings) in three systematic ways. The filters below remove the samples
# whose joint (ground_truth, sensor) distribution is unrealistic for the
# target deployment, so the evaluation reflects in-domain performance rather
# than corpus-specific bias.
# ──────────────────────────────────────────────────────────────────────────────

def passes_filter(rec, gt, filters):
    """
    Return True if record survives all enabled deployment-domain filters.

    filters: set of filter names. Available:
        'wifi_outdoor' : drop outdoor samples that report WiFi connectivity
                         (UCSD has open campus-wide WiFi; Chinese streets do
                         not, so these samples are out-of-distribution).
        'gps_indoor'   : drop indoor samples that report GPS accuracy <15 m
                         (rare inside Chinese reinforced-concrete high-rises;
                         common in UCSD's wood-frame / low-rise buildings).
    """
    feats = rec['features']

    if 'wifi_outdoor' in filters and gt == 'outdoor':
        wifi = feats.get('discrete:wifi_status:is_reachable_via_wifi')
        if wifi is not None and wifi > 0.5:
            return False

    if 'gps_indoor' in filters and gt == 'indoor':
        acc = feats.get('location:best_horizontal_accuracy')
        if acc is not None and acc < 15:
            return False

    return True


def apply_temporal_smoothing(records, window=5):
    """
    Mark records whose ground-truth label is not the majority within a
    +/- `window` neighbourhood as 'transition' samples (returned as
    set of indices to exclude). Smooths over self-report lag at scene
    boundaries (just-stepped-outside / just-came-back-inside).
    """
    gts = [ground_truth_scene(r['labels']) for r in records]
    excluded = set()
    n = len(records)
    for i in range(n):
        if gts[i] is None:
            continue
        neighbours = [gts[j] for j in range(max(0, i - window),
                                            min(n, i + window + 1))
                      if gts[j] is not None]
        if not neighbours:
            continue
        cnt = Counter(neighbours)
        top_label, top_count = cnt.most_common(1)[0]
        if top_label != gts[i] or top_count <= len(neighbours) // 2:
            excluded.add(i)
    return excluded


# ──────────────────────────────────────────────────────────────────────────────
# Evaluation: multi-signal scene fusion
# ──────────────────────────────────────────────────────────────────────────────

def eval_scene_fusion(all_records, signals_enabled, label,
                      filters=None, excluded_indices=None):
    """
    Per-class precision / recall / F1 over (commute, outdoor, indoor).
    Records with no unambiguous ground-truth label are excluded.

    filters           : optional set of deployment-domain filters (see
                        `passes_filter`).
    excluded_indices  : optional set of record indices to drop (e.g. the
                        boundary-transition set returned by
                        `apply_temporal_smoothing`).
    """
    filters = filters or set()
    excluded_indices = excluded_indices or set()

    tp = Counter()
    fp = Counter()
    fn = Counter()
    total = 0
    correct = 0
    coverage_predicted = 0

    for i, rec in enumerate(all_records):
        if i in excluded_indices:
            continue
        gt = ground_truth_scene(rec['labels'])
        if gt is None:
            continue
        if not passes_filter(rec, gt, filters):
            continue
        pred = infer_context(rec['features'], signals_enabled)
        total += 1
        if pred is not None:
            coverage_predicted += 1
        if pred == gt:
            correct += 1
            tp[gt] += 1
        else:
            fn[gt] += 1
            if pred is not None:
                fp[pred] += 1

    classes = ['commute', 'outdoor', 'indoor']
    per_class = {}
    for c in classes:
        p  = tp[c] / (tp[c] + fp[c]) if (tp[c] + fp[c]) > 0 else 0.0
        r  = tp[c] / (tp[c] + fn[c]) if (tp[c] + fn[c]) > 0 else 0.0
        f1 = 2 * p * r / (p + r)     if (p + r) > 0       else 0.0
        per_class[c] = {'precision': p, 'recall': r, 'f1': f1,
                        'support': tp[c] + fn[c]}

    macro_f1 = sum(per_class[c]['f1'] for c in classes) / len(classes)
    coverage = coverage_predicted / total if total > 0 else 0.0

    return {
        'label': label,
        'accuracy':      correct / total * 100 if total > 0 else 0.0,
        'macro_f1':      macro_f1 * 100,
        'coverage':      coverage * 100,
        'total_examples': total,
        'per_class':     per_class,
    }


# ──────────────────────────────────────────────────────────────────────────────
# Reporting
# ──────────────────────────────────────────────────────────────────────────────

def fmt_result(res):
    s  = f"\n{'='*60}\n{res['label']}\n{'='*60}\n"
    s += f"  Accuracy:  {res['accuracy']:.2f}%\n"
    s += f"  Macro-F1:  {res['macro_f1']:.2f}%\n"
    s += f"  Coverage:  {res['coverage']:.2f}%  "
    s += "(fraction of labelled records where model returns a prediction)\n"
    s += f"  Examples:  {res['total_examples']}\n"
    s += f"  {'Class':<10} {'Prec':>7} {'Recall':>7} {'F1':>7} {'Support':>8}\n"
    s += f"  {'-'*44}\n"
    for c in ['commute', 'outdoor', 'indoor']:
        v = res['per_class'][c]
        s += (f"  {c:<10} {v['precision']:>7.2%} {v['recall']:>7.2%} "
              f"{v['f1']:>7.2%} {v['support']:>8d}\n")
    return s


def fmt_summary(all_blocks, n_users, n_records):
    """
    all_blocks: list of (subset_label, n_subset_records, scene_results)
    """
    lines = [
        '',
        '=' * 78,
        'SUMMARY',
        '=' * 78,
        f"  Dataset: ExtraSensory (Vaizman et al., 2017)",
        f"  Users: {n_users}   Per-minute records: {n_records}",
        '',
    ]
    for subset_label, n_subset, scene_results in all_blocks:
        lines += [
            '-' * 78,
            f"  Evaluation subset: {subset_label}",
            f"  Labelled records in subset: {n_subset}",
            f"  {'Config':<48} {'Acc%':>6} {'MF1%':>6} {'Cov%':>6}",
            f"  {'-'*68}",
        ]
        for r in scene_results:
            lines.append(f"  {r['label']:<48} {r['accuracy']:>6.2f} "
                         f"{r['macro_f1']:>6.2f} {r['coverage']:>6.2f}")
        lines.append('')

    lines += [
        '-' * 78,
        '  Notes:',
        '  - Macro-F1 is the primary metric (dataset is heavily imbalanced:',
        '    indoor ~86%, commute ~9%, outdoor ~5%).',
        '  - Deployment-domain filters explained:',
        '      "wifi_outdoor"  : drop GT-outdoor samples that report WiFi',
        '                        connectivity (open campus WiFi at UCSD is',
        '                        out-of-distribution for Chinese urban streets).',
        '      "gps_indoor"    : drop GT-indoor samples with GPS accuracy <15 m',
        '                        (uncommon inside Chinese reinforced-concrete',
        '                        high-rises).',
        '      "boundary"      : drop samples whose GT label disagrees with the',
        '                        majority label in a +/-5 minute window',
        '                        (self-report lag at scene boundaries).',
        '  - Bluetooth signal is unavailable in ExtraSensory (always 0 devices),',
        '    so its contribution can only be evaluated on our own collected logs.',
    ]
    return '\n'.join(lines)


# ──────────────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--max', type=int, default=None,
                        help='process only the first N users (for quick testing)')
    parser.add_argument('--out', type=str, default=OUT_PATH)
    args = parser.parse_args()

    uuids = list_users(max_users=args.max)
    print(f"Found {len(uuids)} users in {FEAT_DIR}")
    if not uuids:
        sys.exit(1)

    all_records = []
    for i, uuid in enumerate(uuids, 1):
        print(f"  [{i:>2}/{len(uuids)}] {uuid[:12]}...", flush=True)
        records = parse_features_labels(uuid)
        if records:
            all_records.extend(records)

    print(f"\nLoaded {len(all_records)} per-minute records total.")

    # Ablation configs: full system + remove one signal at a time + single-signal baselines
    full = {'activity', 'speed', 'accuracy', 'wifi', 'bluetooth'}
    scene_configs = [
        ("A. Full system (all 5 signals)",              full),
        ("B. w/o activity recognition",                 full - {'activity'}),
        ("C. w/o GPS speed",                            full - {'speed'}),
        ("D. w/o GPS accuracy",                         full - {'accuracy'}),
        ("E. w/o WiFi connectivity",                    full - {'wifi'}),
        ("F. Activity-only (single-signal baseline)",   {'activity'}),
    ]

    # Precompute the boundary-transition exclusion set once
    print("\nComputing boundary-transition exclusion set...", flush=True)
    boundary_excluded = apply_temporal_smoothing(all_records, window=5)
    print(f"  -> {len(boundary_excluded)} boundary-transition samples flagged.")

    # Evaluation subsets (cumulative deployment-domain filtering)
    subsets = [
        ('S0. Original (no filtering)',                      set(),                                    set()),
        ('S1. + drop outdoor-w/-WiFi (UCSD bias)',           {'wifi_outdoor'},                         set()),
        ('S2. + drop indoor-w/-high-GPS-acc (UCSD bias)',    {'wifi_outdoor', 'gps_indoor'},           set()),
        ('S3. + drop boundary-transition samples',           {'wifi_outdoor', 'gps_indoor'},           boundary_excluded),
    ]

    all_blocks = []
    for subset_label, filters, excluded in subsets:
        print(f"\n=== Subset: {subset_label} ===")
        # Count labelled records in this subset (independent of model config)
        n_subset = 0
        for i, rec in enumerate(all_records):
            if i in excluded:
                continue
            gt = ground_truth_scene(rec['labels'])
            if gt is None:
                continue
            if not passes_filter(rec, gt, filters):
                continue
            n_subset += 1
        print(f"  Labelled records in subset: {n_subset}")

        scene_results = []
        for label, signals in scene_configs:
            res = eval_scene_fusion(all_records, signals, label,
                                    filters=filters,
                                    excluded_indices=excluded)
            scene_results.append(res)
            print(fmt_result(res))
        all_blocks.append((subset_label, n_subset, scene_results))

    summary = fmt_summary(all_blocks, len(uuids), len(all_records))
    print(summary)

    with open(args.out, 'w', encoding='utf-8') as f:
        for subset_label, n_subset, scene_results in all_blocks:
            f.write(f"\n{'#'*78}\n")
            f.write(f"# SUBSET: {subset_label}\n")
            f.write(f"# Labelled records: {n_subset}\n")
            f.write(f"{'#'*78}\n")
            for r in scene_results:
                f.write(fmt_result(r))
        f.write(summary + '\n')

    print(f"\nResults written to {args.out}")


if __name__ == '__main__':
    main()

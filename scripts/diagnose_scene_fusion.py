"""
Diagnostic for the multi-signal scene fusion evaluation.

Answers three questions:
  1. What's the per-class confusion matrix?
  2. What's the class distribution in the ground truth?
  3. Per-signal isolation: when only signal X is enabled, what does it predict?

Run AFTER scripts/eval_context_modeling.py has its data downloaded.
"""

import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from eval_context_modeling import (   # noqa: E402
    list_users, parse_features_labels,
    infer_context, ground_truth_scene,
    derive_activity, is_connected_to_wifi,
)


def build_confusion(records, signals_enabled):
    classes = ['commute', 'outdoor', 'indoor']
    cm = {gt: Counter() for gt in classes}
    cm_none = Counter()  # gt → count when prediction is None

    for rec in records:
        gt = ground_truth_scene(rec['labels'])
        if gt is None:
            continue
        pred = infer_context(rec['features'], signals_enabled)
        if pred is None:
            cm_none[gt] += 1
        else:
            cm[gt][pred] += 1

    return cm, cm_none


def fmt_confusion(cm, cm_none):
    classes = ['commute', 'outdoor', 'indoor']
    s = ''
    header = 'GT / Pred'
    s += f"  {header:<12}"
    for c in classes:
        s += f" {c:>10}"
    s += f" {'(none)':>10} {'Total':>10} {'Recall':>8}\n"
    for gt in classes:
        row_total = sum(cm[gt].values()) + cm_none[gt]
        recall = cm[gt][gt] / row_total if row_total > 0 else 0
        s += f"  {gt:<12}"
        for c in classes:
            s += f" {cm[gt][c]:>10d}"
        s += f" {cm_none[gt]:>10d} {row_total:>10d} {recall:>7.2%}\n"
    s += '\n'
    return s


def main():
    uuids = list_users(max_users=None)
    print(f"Loading {len(uuids)} users...")
    all_records = []
    for i, uuid in enumerate(uuids, 1):
        all_records.extend(parse_features_labels(uuid))

    # Q1. Ground truth class distribution
    print("\n=== Q1. Ground-truth class distribution ===")
    gt_counts = Counter()
    for rec in all_records:
        gt = ground_truth_scene(rec['labels'])
        if gt is not None:
            gt_counts[gt] += 1
    total = sum(gt_counts.values())
    for c in ['commute', 'outdoor', 'indoor']:
        cnt = gt_counts[c]
        print(f"  {c:<10} {cnt:>8d}  ({cnt / total * 100:5.2f}%)")
    print(f"  TOTAL      {total:>8d}")

    # Q2. Confusion matrix on full system
    print("\n=== Q2. Confusion matrix: A. Full system ===")
    full = {'activity', 'speed', 'accuracy', 'wifi', 'bluetooth'}
    cm, cm_none = build_confusion(all_records, full)
    print(fmt_confusion(cm, cm_none))

    print("=== Q2b. Confusion matrix: F. Activity-only baseline ===")
    cm, cm_none = build_confusion(all_records, {'activity'})
    print(fmt_confusion(cm, cm_none))

    # Q3. Single-signal isolation: what does each signal alone predict?
    print("=== Q3. Single-signal predictions on outdoor ground-truth records ===")
    signals = [
        ('activity-only', {'activity'}),
        ('speed-only',    {'speed'}),
        ('accuracy-only', {'accuracy'}),
        ('wifi-only',     {'wifi'}),
    ]
    print(f"  {'Signal':<14} {'-> commute':>12} {'-> outdoor':>12} "
          f"{'-> indoor':>12} {'-> none':>10}")
    for name, sigs in signals:
        cnt = Counter()
        for rec in all_records:
            if ground_truth_scene(rec['labels']) != 'outdoor':
                continue
            pred = infer_context(rec['features'], sigs)
            cnt[pred or 'none'] += 1
        print(f"  {name:<14} {cnt['commute']:>12d} {cnt['outdoor']:>12d} "
              f"{cnt['indoor']:>12d} {cnt['none']:>10d}")

    print("\n=== Q4. Where does the WiFi signal come from on outdoor records? ===")
    wifi_on  = 0
    wifi_off = 0
    wifi_unknown = 0
    for rec in all_records:
        if ground_truth_scene(rec['labels']) != 'outdoor':
            continue
        v = is_connected_to_wifi(rec['features'])
        if v is True:
            wifi_on += 1
        elif v is False:
            wifi_off += 1
        else:
            wifi_unknown += 1
    total = wifi_on + wifi_off + wifi_unknown
    print(f"  Outdoor records:  {total}")
    print(f"  WiFi connected:   {wifi_on:>6d} ({wifi_on/total*100:5.2f}%) "
          f"<-- these get +2.5 indoor, hurting outdoor recall")
    print(f"  WiFi disconnect:  {wifi_off:>6d} ({wifi_off/total*100:5.2f}%)")
    print(f"  WiFi unknown:     {wifi_unknown:>6d} ({wifi_unknown/total*100:5.2f}%)")


if __name__ == '__main__':
    main()

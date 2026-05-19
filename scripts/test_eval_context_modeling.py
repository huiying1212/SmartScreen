"""
Quick self-tests for scripts/eval_context_modeling.py.
Runs without ExtraSensory data -- exercises the pure-function building blocks.

Usage:
    python scripts/test_eval_context_modeling.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from eval_context_modeling import (   # noqa: E402
    infer_context, ground_truth_scene,
    derive_activity, is_connected_to_wifi,
)


def test_infer_context_indoor_typical():
    # Sitting on a couch at home: low acc-std, no GPS speed,
    # WiFi connected, GPS accuracy 80 m (indoor-like).
    feat = {
        'raw_acc:magnitude_stats:std': 0.05,
        'location:max_speed': 0.0,
        'location:best_horizontal_accuracy': 80.0,
        'discrete:wifi_status:is_reachable_via_wifi': 1.0,
    }
    label = infer_context(feat)
    assert label == 'indoor', label
    print("  [OK] infer_context: typical indoor scene -> 'indoor'")


def test_infer_context_commute_in_car():
    # Driving on the highway
    feat = {
        'raw_acc:magnitude_stats:std': 0.3,
        'location:max_speed': 22.0,                # ~80 km/h
        'location:best_horizontal_accuracy': 8.0,  # open sky GPS
        'discrete:wifi_status:is_reachable_via_wifi': 0.0,
    }
    label = infer_context(feat)
    assert label == 'commute', label
    print("  [OK] infer_context: highway driving -> 'commute'")


def test_infer_context_outdoor_walking():
    # Walking outdoors with good GPS, no WiFi
    feat = {
        'raw_acc:magnitude_stats:std': 2.0,
        'location:max_speed': 1.4,
        'location:best_horizontal_accuracy': 8.0,
        'discrete:wifi_status:is_reachable_via_wifi': 0.0,
    }
    label = infer_context(feat)
    assert label == 'outdoor', label
    print("  [OK] infer_context: open-air walking -> 'outdoor'")


def test_infer_context_abstains_on_weak_evidence():
    # No location data at all, no WiFi, phone just sitting still
    # Evidence too weak to exceed threshold of 1.0 for any class
    feat = {
        'raw_acc:magnitude_stats:std': None,
        'location:max_speed': None,
        'location:best_horizontal_accuracy': None,
        'discrete:wifi_status:is_reachable_via_wifi': None,
    }
    label = infer_context(feat)
    assert label is None, label
    print("  [OK] infer_context: insufficient evidence -> None (abstain)")


def test_ground_truth_priority():
    assert ground_truth_scene({'IN_A_CAR', 'OR_indoors'}) == 'commute'
    assert ground_truth_scene({'OR_outside'}) == 'outdoor'
    assert ground_truth_scene({'OR_indoors', 'OR_outside'}) is None  # ambiguous
    # Indoor requires positive indoor-activity evidence
    assert ground_truth_scene({'OR_indoors', 'LOC_home'}) == 'indoor'
    assert ground_truth_scene({'OR_indoors'}) is None  # no positive evidence
    print("  [OK] ground_truth_scene priority and strict indoor rules")


def test_ablation_wifi_removed():
    # Without WiFi signal, an indoor+WiFi scenario should score lower
    feat = {
        'raw_acc:magnitude_stats:std': 0.05,
        'location:max_speed': 0.0,
        'location:best_horizontal_accuracy': 80.0,
        'discrete:wifi_status:is_reachable_via_wifi': 1.0,
    }
    with_wifi    = infer_context(feat, {'activity', 'speed', 'accuracy', 'wifi'})
    without_wifi = infer_context(feat, {'activity', 'speed', 'accuracy'})
    assert with_wifi == 'indoor'
    # Without WiFi the indoor evidence is weaker; result may be indoor or None
    # but must NOT be 'outdoor' or 'commute' given the other signals
    assert without_wifi in ('indoor', None), without_wifi
    print("  [OK] ablation: removing WiFi signal weakens indoor confidence")


if __name__ == '__main__':
    print("Running unit tests for eval_context_modeling.py")
    test_infer_context_indoor_typical()
    test_infer_context_commute_in_car()
    test_infer_context_outdoor_walking()
    test_infer_context_abstains_on_weak_evidence()
    test_ground_truth_priority()
    test_ablation_wifi_removed()
    print("\nAll tests passed.")

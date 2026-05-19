"""
Download the public ExtraSensory dataset for context-modeling evaluation.

We need two parts:
  1. Per-user features+labels (csv.gz, ~280 MB total) — primary data with PS
     (phone state, including WiFi availability) and Loc features per minute.
  2. Per-user absolute coordinates (csv.gz, ~50 MB total) — needed for
     GPS jitter filtering and location-clustering evaluation.

Data is cached in `data/extrasensory/`. Re-runs skip files that already exist.

Usage:
    python scripts/download_extrasensory.py            # download everything
    python scripts/download_extrasensory.py --max 10   # only first 10 users
    python scripts/download_extrasensory.py --check    # verify cached files
"""

import argparse
import os
import sys
import time
import urllib.request
import zipfile

ROOT      = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR  = os.path.join(ROOT, 'data', 'extrasensory')
FEAT_DIR  = os.path.join(DATA_DIR, 'features_labels')
COORD_DIR = os.path.join(DATA_DIR, 'absolute_location')

FEATURES_LABELS_ZIP_URL = (
    'http://extrasensory.ucsd.edu/data/primary_data_files/'
    'ExtraSensory.per_uuid_features_labels.zip'
)

# The absolute-location zip is NOT publicly hosted at a fixed URL.
# The ExtraSensory website describes this part of the data but does not
# provide a download link (as of 2026-05). Contact the authors at
# extrasensory@eng.ucsd.edu to request access.
# Our evaluation script gracefully falls back to relative-location features
# when absolute coordinates are unavailable.
ABSOLUTE_LOCATION_ZIP_URL = None


def _download_with_progress(url, out_path):
    """Stream-download with simple progress reporting."""
    print(f"  download: {os.path.basename(out_path)}")
    print(f"    from {url}")
    start = time.time()

    def _hook(block_num, block_size, total_size):
        if total_size <= 0:
            return
        read = block_num * block_size
        pct = min(100.0, read * 100.0 / total_size)
        elapsed = time.time() - start
        speed = read / 1024.0 / 1024.0 / max(elapsed, 0.1)
        sys.stdout.write(
            f"\r    {pct:5.1f}%  "
            f"({read / 1024 / 1024:7.1f} / {total_size / 1024 / 1024:7.1f} MB)  "
            f"{speed:5.1f} MB/s  "
        )
        sys.stdout.flush()

    urllib.request.urlretrieve(url, out_path, reporthook=_hook)
    print()


def download_zip(url, target_dir, zip_name):
    os.makedirs(target_dir, exist_ok=True)
    zip_path = os.path.join(DATA_DIR, zip_name)

    if os.path.exists(zip_path):
        print(f"  [cache] {zip_name} already present "
              f"({os.path.getsize(zip_path) / 1024 / 1024:.1f} MB)")
    else:
        _download_with_progress(url, zip_path)

    print(f"  extracting into {target_dir}")
    with zipfile.ZipFile(zip_path, 'r') as zf:
        zf.extractall(target_dir)
    print(f"  [done] extracted {len(os.listdir(target_dir))} files")


def list_cached_users(directory, suffix='.features_labels.csv.gz'):
    if not os.path.isdir(directory):
        return []
    names = [n for n in os.listdir(directory) if n.endswith(suffix)]
    uuids = sorted(n[:-len(suffix)] for n in names)
    return uuids


def check_status():
    print("\n=== ExtraSensory cache status ===")
    feat_users  = list_cached_users(FEAT_DIR,  '.features_labels.csv.gz')
    coord_users = list_cached_users(COORD_DIR, '.absolute_locations.csv.gz')

    print(f"  Features+labels files: {len(feat_users)} users in {FEAT_DIR}")
    print(f"  Absolute location files: {len(coord_users)} users in {COORD_DIR}")

    common = set(feat_users) & set(coord_users)
    print(f"  Users with BOTH features and coords: {len(common)}")
    print()

    if not feat_users:
        print("  -> Run without --check to download.")
    return feat_users, coord_users


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true',
                        help='only print cache status, do not download')
    parser.add_argument('--features-only', action='store_true',
                        help='skip absolute-location data')
    args = parser.parse_args()

    os.makedirs(DATA_DIR, exist_ok=True)

    if args.check:
        check_status()
        return

    print("=== Downloading ExtraSensory: features + labels ===")
    download_zip(
        FEATURES_LABELS_ZIP_URL, FEAT_DIR,
        'ExtraSensory.per_uuid_features_labels.zip',
    )

    if not args.features_only:
        print("\n=== Absolute location coordinates ===")
        print("  NOTE: The ExtraSensory absolute-location data is not publicly")
        print("  hosted at a fixed URL. Contact extrasensory@eng.ucsd.edu to")
        print("  request access. The evaluation script works without it,")
        print("  using relative-location features from the primary data instead.")

    check_status()


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""
Generate UC10 attack-config variants for the timing sweep pipeline.

For each (variant x burstLevel x probLevel) combination, this writes a
JSON file under config/attacks/uc10_timing/ whose filename encodes the
sweep coordinates so the pipeline can reference it via ${variant}/${burst}/${prob}
placeholders:

    config/attacks/uc10_timing/<variant>_b<burstLevel>_p<probLevel>.json

The generated files are otherwise identical to the corresponding
config/attacks/uc10_variants/<variant>.json, but with burstInterval and
selectionProb overridden.

Usage:
    python tools/generate_uc10_timing_attacks.py
    python tools/generate_uc10_timing_attacks.py --variants uc10_replay_real uc10_backoff_real
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = REPO_ROOT / "config" / "attacks" / "uc10_variants"
TARGET_DIR = REPO_ROOT / "config" / "attacks" / "uc10_timing"

# Default sweep grid. Keys ("short", "mid", "long" / "lo", "mid", "hi") are the
# tokens used by the pipeline ${burst}/${prob} placeholders.
DEFAULT_BURST_LEVELS = {
    "short": {"min": 50, "max": 150},
    "mid":   {"min": 200, "max": 500},
    "long":  {"min": 600, "max": 1200},
}

DEFAULT_PROB_LEVELS = {
    "lo":  0.25,
    "mid": 0.50,
    "hi":  0.75,
}

DEFAULT_VARIANTS = [
    "uc10_replay_real",
    "uc10_replay_fake",
    "uc10_backoff_real",
    "uc10_backoff_fake",
    "uc10_batchdump_real",
    "uc10_batchdump_fake",
    "uc10_doubledrop_real",
    "uc10_doubledrop_fake",
]


def load_base(variant: str) -> dict:
    src = SOURCE_DIR / f"{variant}.json"
    if not src.exists():
        raise FileNotFoundError(f"Base attack config not found: {src}")
    with src.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def write_variant(variant: str, burst_id: str, burst_range: dict,
                  prob_id: str, prob_value: float) -> Path:
    cfg = load_base(variant)
    cfg["burstInterval"] = {"min": int(burst_range["min"]),
                            "max": int(burst_range["max"])}
    cfg["selectionProb"] = {"value": float(prob_value)}
    out = TARGET_DIR / f"{variant}_b{burst_id}_p{prob_id}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as fh:
        json.dump(cfg, fh, indent=2)
        fh.write("\n")
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--variants", nargs="+", default=DEFAULT_VARIANTS,
                    help="UC10 variants to expand (default: all 8).")
    ap.add_argument("--dry-run", action="store_true",
                    help="List what would be written without creating files.")
    args = ap.parse_args()

    written = 0
    for variant in args.variants:
        for burst_id, burst_range in DEFAULT_BURST_LEVELS.items():
            for prob_id, prob_value in DEFAULT_PROB_LEVELS.items():
                if args.dry_run:
                    print(f"[dry-run] {variant} burst={burst_id} prob={prob_id}")
                else:
                    path = write_variant(variant, burst_id, burst_range,
                                         prob_id, prob_value)
                    print(f"wrote {path.relative_to(REPO_ROOT)}")
                written += 1

    print(f"\n{'(dry-run) ' if args.dry_run else ''}"
          f"{written} attack config files "
          f"({len(args.variants)} variants x "
          f"{len(DEFAULT_BURST_LEVELS)} burst levels x "
          f"{len(DEFAULT_PROB_LEVELS)} prob levels).")


if __name__ == "__main__":
    main()

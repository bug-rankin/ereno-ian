#!/usr/bin/env python3
"""
Analyze the UC10 timing-sweep results recorded by ExperimentTracker.

Parses sweep coordinates (variant, burst, prob, msgs, seed) from the model
output paths logged in target/tracking/models.csv, then aggregates
training_time_ms across the axes.

Usage:
    python tools/analyze_uc10_timing.py
    python tools/analyze_uc10_timing.py --tracking-dir target/tracking \
        --output-dir target/uc10_timing/analysis
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

import pandas as pd

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TRACKING_DIR = REPO_ROOT / "target" / "tracking"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "target" / "uc10_timing" / "analysis"

# Matches paths produced by pipeline_uc10_timing_sweep.json:
#   target/uc10_timing/models/<variant>_b<burst>_p<prob>_m<msgs>_s<seed>/...
PATH_RE = re.compile(
    r"uc10_timing[\\/]models[\\/]"
    r"(?P<variant>uc10_[a-z]+_(?:real|fake))"
    r"_b(?P<burst>short|mid|long)"
    r"_p(?P<prob>lo|mid|hi)"
    r"_m(?P<msgs>\d+)"
    r"_s(?P<seed>\d+)"
)

BURST_ORDER = ["short", "mid", "long"]
PROB_ORDER = ["lo", "mid", "hi"]


def parse_axes(df: pd.DataFrame, path_col: str) -> pd.DataFrame:
    extracted = df[path_col].astype(str).str.extract(PATH_RE)
    keep = extracted.dropna(subset=["variant"]).index
    out = df.loc[keep].copy()
    out[["variant", "burst", "prob", "msgs", "seed"]] = extracted.loc[keep]
    out["msgs"] = out["msgs"].astype(int)
    out["seed"] = out["seed"].astype(int)
    out["burst"] = pd.Categorical(out["burst"], categories=BURST_ORDER, ordered=True)
    out["prob"] = pd.Categorical(out["prob"], categories=PROB_ORDER, ordered=True)
    return out


def summarize(df: pd.DataFrame, group_cols: list[str], time_col: str) -> pd.DataFrame:
    g = df.groupby(group_cols, observed=True)[time_col]
    summary = g.agg(["count", "mean", "std", "min", "max"]).reset_index()
    summary = summary.rename(columns={
        "mean": f"{time_col}_mean",
        "std":  f"{time_col}_std",
        "min":  f"{time_col}_min",
        "max":  f"{time_col}_max",
        "count": "n",
    })
    return summary


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--tracking-dir", default=str(DEFAULT_TRACKING_DIR),
                    help="Directory containing models.csv (default: target/tracking).")
    ap.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR),
                    help="Where to write summary CSVs.")
    args = ap.parse_args()

    tracking_dir = Path(args.tracking_dir)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    models_csv = tracking_dir / "models.csv"
    if not models_csv.exists():
        raise SystemExit(f"models.csv not found in {tracking_dir}; "
                         f"run the timing pipeline first.")

    models = pd.read_csv(models_csv)
    swept = parse_axes(models, "model_path")
    if swept.empty:
        raise SystemExit("No rows in models.csv matched the uc10_timing path pattern. "
                         "Did the sweep pipeline actually run?")

    swept["training_time_ms"] = pd.to_numeric(swept["training_time_ms"],
                                              errors="coerce")
    swept = swept.dropna(subset=["training_time_ms"])

    # Persist enriched per-model rows.
    enriched_path = output_dir / "models_swept.csv"
    swept.to_csv(enriched_path, index=False)
    print(f"wrote {enriched_path.relative_to(REPO_ROOT)} ({len(swept)} rows)")

    # Per-classifier summaries grouped by each axis individually + the full key.
    summaries = {
        "by_classifier":              ["classifier_name"],
        "by_classifier_burst":        ["classifier_name", "burst"],
        "by_classifier_prob":         ["classifier_name", "prob"],
        "by_classifier_msgs":         ["classifier_name", "msgs"],
        "by_classifier_variant":     ["classifier_name", "variant"],
        "by_full_key":                ["classifier_name", "variant", "burst",
                                       "prob", "msgs"],
    }
    for name, cols in summaries.items():
        summary = summarize(swept, cols, "training_time_ms")
        path = output_dir / f"timing_{name}.csv"
        summary.to_csv(path, index=False)
        print(f"wrote {path.relative_to(REPO_ROOT)}")

    # Console preview.
    print("\nTraining time (ms) per classifier x burst x prob x msgs:")
    pivot = (swept
             .groupby(["classifier_name", "burst", "prob", "msgs"], observed=True)
             ["training_time_ms"].mean()
             .round(1)
             .unstack("msgs"))
    print(pivot.to_string())


if __name__ == "__main__":
    main()

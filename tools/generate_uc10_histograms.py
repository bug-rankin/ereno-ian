"""Visualizations for the UC10 delayed-replay variant cross-evaluation.

Reads target/uc10_variants/comprehensive_results.csv (3,840 rows = 8 train
variants x 8 test variants x 2 classifiers x 3 train seeds x 10 test seeds)
and produces:

  1. 8x8 mean-F1 / mean-accuracy heatmaps (one per classifier)
  2. 8x8 std-F1 heatmaps showing seed-induced variance per cell
  3. Self-vs-cross F1 distribution (diagonal vs off-diagonal)
  4. Per-variant generalization bar chart (mean off-diagonal F1 by train variant)

Outputs are written next to the CSV under target/uc10_variants/plots/.
"""
from __future__ import annotations

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

CSV_PATH = Path("target/uc10_variants/comprehensive_results.csv")
OUT_DIR = CSV_PATH.parent / "plots"

VARIANT_ORDER = [
    "uc10_replay_real",
    "uc10_replay_fake",
    "uc10_backoff_real",
    "uc10_backoff_fake",
    "uc10_batchdump_real",
    "uc10_batchdump_fake",
    "uc10_doubledrop_real",
    "uc10_doubledrop_fake",
]
# Suffix legend:
#   /R = replaceWithFake:false  -> captured frames are delayed; their original
#                                  time slots are left empty (visible gap).
#   /F = replaceWithFake:true   -> same delay, but the emptied slots are
#                                  back-filled with synthetic GOOSE so the
#                                  live flow looks continuous.
SHORT_LABELS = {
    "uc10_replay_real": "replay/R",
    "uc10_replay_fake": "replay/F",
    "uc10_backoff_real": "backoff/R",
    "uc10_backoff_fake": "backoff/F",
    "uc10_batchdump_real": "batch/R",
    "uc10_batchdump_fake": "batch/F",
    "uc10_doubledrop_real": "double/R",
    "uc10_doubledrop_fake": "double/F",
}
METRIC_VMIN, METRIC_VMAX = 0.0, 1.0


def load() -> pd.DataFrame:
    if not CSV_PATH.exists():
        raise SystemExit(f"CSV not found at {CSV_PATH}")
    df = pd.read_csv(CSV_PATH)
    expected = {"trainingAttack1", "modelName", "trainingSeed",
                "testAttack", "testSeed", "accuracy", "precision",
                "recall", "f1"}
    missing = expected - set(df.columns)
    if missing:
        raise SystemExit(f"CSV missing columns: {missing}")
    return df


def _annotate(ax, matrix: np.ndarray) -> None:
    rows, cols = matrix.shape
    for i in range(rows):
        for j in range(cols):
            v = matrix[i, j]
            color = "white" if v < 0.55 else "black"
            ax.text(j, i, f"{v:.2f}", ha="center", va="center",
                    color=color, fontsize=8)


def _heatmap(ax, matrix: np.ndarray, title: str, cmap: str,
             vmin: float, vmax: float) -> None:
    im = ax.imshow(matrix, cmap=cmap, vmin=vmin, vmax=vmax, aspect="equal")
    ax.set_xticks(range(len(VARIANT_ORDER)))
    ax.set_yticks(range(len(VARIANT_ORDER)))
    ax.set_xticklabels([SHORT_LABELS[v] for v in VARIANT_ORDER],
                       rotation=45, ha="right", fontsize=8)
    ax.set_yticklabels([SHORT_LABELS[v] for v in VARIANT_ORDER], fontsize=8)
    ax.set_xlabel("Test variant")
    ax.set_ylabel("Train variant")
    ax.set_title(title, fontsize=11, fontweight="bold")
    _annotate(ax, matrix)
    return im


def cell_matrix(df: pd.DataFrame, classifier: str, metric: str,
                agg: str) -> np.ndarray:
    sub = df[df["modelName"] == classifier]
    pivot = (sub.groupby(["trainingAttack1", "testAttack"])[metric]
                .agg(agg)
                .unstack("testAttack"))
    pivot = pivot.reindex(index=VARIANT_ORDER, columns=VARIANT_ORDER)
    return pivot.to_numpy(dtype=float)


def plot_mean_heatmaps(df: pd.DataFrame) -> None:
    classifiers = sorted(df["modelName"].unique())
    for metric in ("f1", "accuracy"):
        fig, axes = plt.subplots(1, len(classifiers),
                                 figsize=(7 * len(classifiers), 6.5))
        if len(classifiers) == 1:
            axes = [axes]
        fig.suptitle(f"UC10 cross-variant mean {metric} (averaged over seeds)",
                     fontsize=13, fontweight="bold")
        last_im = None
        for ax, clf in zip(axes, classifiers):
            m = cell_matrix(df, clf, metric, "mean")
            last_im = _heatmap(ax, m, clf, "viridis",
                               METRIC_VMIN, METRIC_VMAX)
        cbar = fig.colorbar(last_im, ax=axes, shrink=0.85, pad=0.02)
        cbar.set_label(f"mean {metric}")
        out = OUT_DIR / f"heatmap_mean_{metric}.png"
        fig.savefig(out, dpi=150, bbox_inches="tight")
        plt.close(fig)
        print(f"  wrote {out}")


def plot_std_heatmaps(df: pd.DataFrame) -> None:
    classifiers = sorted(df["modelName"].unique())
    fig, axes = plt.subplots(1, len(classifiers),
                             figsize=(7 * len(classifiers), 6.5))
    if len(classifiers) == 1:
        axes = [axes]
    fig.suptitle("UC10 per-cell F1 std-dev across seeds (lower = more stable)",
                 fontsize=13, fontweight="bold")
    # std colour scale derived from data so small variances stay visible
    stacked = np.stack([cell_matrix(df, c, "f1", "std") for c in classifiers])
    vmax = float(np.nanmax(stacked)) if np.isfinite(np.nanmax(stacked)) else 0.1
    vmax = max(vmax, 1e-3)
    last_im = None
    for ax, clf in zip(axes, classifiers):
        m = cell_matrix(df, clf, "f1", "std")
        last_im = _heatmap(ax, m, clf, "magma_r", 0.0, vmax)
    cbar = fig.colorbar(last_im, ax=axes, shrink=0.85, pad=0.02)
    cbar.set_label("std(F1)")
    out = OUT_DIR / "heatmap_std_f1.png"
    fig.savefig(out, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  wrote {out}")


def plot_self_vs_cross(df: pd.DataFrame) -> None:
    df = df.copy()
    df["scenario"] = np.where(df["trainingAttack1"] == df["testAttack"],
                              "self (train==test)", "cross (train!=test)")
    classifiers = sorted(df["modelName"].unique())
    fig, axes = plt.subplots(1, len(classifiers),
                             figsize=(7 * len(classifiers), 5))
    if len(classifiers) == 1:
        axes = [axes]
    fig.suptitle("UC10 F1 distribution: self vs cross variant",
                 fontsize=13, fontweight="bold")
    for ax, clf in zip(axes, classifiers):
        sub = df[df["modelName"] == clf]
        self_f1 = sub.loc[sub["scenario"] == "self (train==test)", "f1"]
        cross_f1 = sub.loc[sub["scenario"] == "cross (train!=test)", "f1"]
        bins = np.linspace(0, 1, 41)
        ax.hist(cross_f1, bins=bins, alpha=0.6, color="steelblue",
                label=f"cross  (n={len(cross_f1)})", edgecolor="black")
        ax.hist(self_f1, bins=bins, alpha=0.6, color="darkorange",
                label=f"self   (n={len(self_f1)})", edgecolor="black")
        ax.axvline(self_f1.mean(), color="darkorange", linestyle="--",
                   linewidth=1.5)
        ax.axvline(cross_f1.mean(), color="steelblue", linestyle="--",
                   linewidth=1.5)
        ax.set_title(f"{clf}\nself mean={self_f1.mean():.3f} | "
                     f"cross mean={cross_f1.mean():.3f}",
                     fontsize=11, fontweight="bold")
        ax.set_xlabel("F1")
        ax.set_ylabel("Count")
        ax.legend()
        ax.grid(True, alpha=0.3)
    out = OUT_DIR / "self_vs_cross_f1.png"
    fig.savefig(out, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  wrote {out}")


def plot_generalization_bars(df: pd.DataFrame) -> None:
    cross = df[df["trainingAttack1"] != df["testAttack"]]
    grouped = (cross.groupby(["modelName", "trainingAttack1"])["f1"]
                    .agg(["mean", "std"])
                    .reset_index())
    classifiers = sorted(df["modelName"].unique())
    fig, ax = plt.subplots(figsize=(11, 5.5))
    x = np.arange(len(VARIANT_ORDER))
    width = 0.8 / max(len(classifiers), 1)
    for i, clf in enumerate(classifiers):
        sub = (grouped[grouped["modelName"] == clf]
               .set_index("trainingAttack1")
               .reindex(VARIANT_ORDER))
        ax.bar(x + i * width - 0.4 + width / 2,
               sub["mean"].to_numpy(),
               width=width,
               yerr=sub["std"].to_numpy(),
               capsize=3,
               label=clf,
               edgecolor="black")
    ax.set_xticks(x)
    ax.set_xticklabels([SHORT_LABELS[v] for v in VARIANT_ORDER],
                       rotation=30, ha="right")
    ax.set_ylabel("Mean off-diagonal F1 (+/- std)")
    ax.set_xlabel("Training variant")
    ax.set_title("UC10 cross-variant generalization per training variant",
                 fontsize=12, fontweight="bold")
    ax.set_ylim(0, 1.05)
    ax.grid(True, axis="y", alpha=0.3)
    ax.legend()
    out = OUT_DIR / "generalization_per_train_variant.png"
    fig.savefig(out, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  wrote {out}")


def print_summary(df: pd.DataFrame) -> None:
    print("\n=== UC10 variant evaluation summary ===")
    print(f"Rows: {len(df)}")
    print(f"Train variants: {df['trainingAttack1'].nunique()} | "
          f"Test variants: {df['testAttack'].nunique()} | "
          f"Classifiers: {df['modelName'].nunique()} | "
          f"Train seeds: {df['trainingSeed'].nunique()} | "
          f"Test seeds: {df['testSeed'].nunique()}")
    by_clf = (df.assign(scenario=np.where(
                df["trainingAttack1"] == df["testAttack"], "self", "cross"))
                .groupby(["modelName", "scenario"])["f1"]
                .agg(["mean", "std", "count"]))
    print("\nMean F1 (self vs cross):")
    print(by_clf.round(4).to_string())


def main() -> None:
    df = load()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Loaded {len(df)} rows from {CSV_PATH}")
    print(f"Writing plots to {OUT_DIR}")
    plot_mean_heatmaps(df)
    plot_std_heatmaps(df)
    plot_self_vs_cross(df)
    plot_generalization_bars(df)
    print_summary(df)


if __name__ == "__main__":
    main()

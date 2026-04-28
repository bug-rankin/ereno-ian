"""Generate the UC10 parameter sweep CSV for experiment planning.

Each row = one model configuration. Columns include the variant name and all
tunable parameters (excluding enabled, shiftSendTimestamp, orderBy, replaceWithFake).
Three levels per parameter (low / standard / high) with full Cartesian combination.
"""
import csv
import itertools
from pathlib import Path

LEVELS = {
    "burstInterval":   [(50, 150),    (200, 500),    (800, 1500)],
    "burstMax":        [10,           100,           500],
    "selectionProb":   [0.2,          0.5,           0.9],
    "networkDelayMs":  [(20.0, 80.0), (50.0, 200.0), (200.0, 600.0)],
    "rateMultiplier":  [1.1,          2.0,           4.0],   # backoff only
    "microGapMs":      [1.0,          5.0,           20.0],  # batch_dump only
}

VARIANT_PARAMS = {
    "delayed_replay":             ["burstInterval", "burstMax", "selectionProb", "networkDelayMs"],
    "delayed_replay_backoff":     ["burstInterval", "burstMax", "selectionProb", "networkDelayMs", "rateMultiplier"],
    "delayed_replay_batch_dump":  ["burstInterval", "burstMax", "selectionProb", "networkDelayMs", "microGapMs"],
    "delayed_replay_double_drop": ["burstInterval", "burstMax", "selectionProb", "networkDelayMs"],
}

HEADER = [
    "config_id", "variant",
    "burstInterval_min", "burstInterval_max",
    "burstMax",
    "selectionProb",
    "networkDelayMs_min", "networkDelayMs_max",
    "rateMultiplier",
    "microGapMs",
]


def fmt(v):
    return "" if v is None else v


def main():
    out = Path(__file__).resolve().parent.parent / "config" / "uc10_sweep_plan.csv"
    rows = []
    cid = 0
    for variant, params in VARIANT_PARAMS.items():
        grids = [LEVELS[p] for p in params]
        for combo in itertools.product(*grids):
            cid += 1
            values = dict(zip(params, combo))
            bi = values.get("burstInterval", (None, None))
            nd = values.get("networkDelayMs", (None, None))
            rows.append({
                "config_id": f"cfg_{cid:04d}",
                "variant": variant,
                "burstInterval_min": bi[0],
                "burstInterval_max": bi[1],
                "burstMax": values.get("burstMax"),
                "selectionProb": values.get("selectionProb"),
                "networkDelayMs_min": nd[0],
                "networkDelayMs_max": nd[1],
                "rateMultiplier": values.get("rateMultiplier"),
                "microGapMs": values.get("microGapMs"),
            })

    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=HEADER)
        w.writeheader()
        for r in rows:
            w.writerow({k: fmt(v) for k, v in r.items()})

    print(f"Wrote {len(rows)} rows to {out}")
    by_variant = {}
    for r in rows:
        by_variant[r["variant"]] = by_variant.get(r["variant"], 0) + 1
    for v, n in by_variant.items():
        print(f"  {v}: {n}")


if __name__ == "__main__":
    main()

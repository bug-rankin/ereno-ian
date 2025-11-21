#!/usr/bin/env python3
"""Optuna prototype to optimize attack parameters by calling the Java ExperimentRunner.

Usage: python tools/optuna_opt.py [--trials N] [--study-name name]

Requirements: optuna (pip install optuna)
"""
import argparse
import json
import os
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

import optuna

REPO_ROOT = Path(__file__).resolve().parents[2]
BASE_CONFIG = REPO_ROOT / "config" / "configparams.json"
JAR = REPO_ROOT / "target" / "ERENO-1.0-SNAPSHOT-shaded.jar"

# load base config once for parameter introspection
with open(BASE_CONFIG, 'r', encoding='utf-8') as _f:
    BASE_CFG_JSON = json.load(_f)

# list to record mapping from optuna param names to config paths
PARAM_MAP = []

# selected attack (set in main)
SELECT_ATTACK = None


def write_config(base_cfg_path, out_path, patch):
    with open(base_cfg_path, 'r', encoding='utf-8') as f:
        cfg = json.load(f)
    # deep merge patch into cfg (simple nested dict assignment)
    def merge(d, u):
        for k, v in u.items():
            if isinstance(v, dict):
                d[k] = merge(d.get(k, {}), v)
            else:
                d[k] = v
        return d

    cfg = merge(cfg, patch)
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(cfg, f, indent=2)


def run_experiment(config_path: Path, outdir: Path, seed: int, classifier: str = 'j48', attack: str = 'random_replay'):
    outdir.mkdir(parents=True, exist_ok=True)
    cmd = [
        'java', '-cp', str(JAR),
        'br.ufu.facom.ereno.experiment.ExperimentRunner',
        str(config_path), str(outdir), classifier, attack, str(seed), str(0.7)
    ]
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    out = proc.stdout
    # find first JSON blob in output
    jstart = out.find('{')
    if jstart < 0:
        raise RuntimeError(f"No JSON output from runner. stdout:\n{out}")
    jtxt = out[jstart:].strip()
    try:
        res = json.loads(jtxt)
    except json.JSONDecodeError:
        # try to trim trailing garbage
        for i in range(len(jtxt), 0, -1):
            try:
                candidate = jtxt[:i]
                return json.loads(candidate)
            except Exception:
                continue
        raise
    return res


def objective(trial: optuna.trial.Trial):
    # objective builds suggestions dynamically for the selected attack only
    if SELECT_ATTACK is None:
        raise RuntimeError("SELECT_ATTACK not set; pass --attack in CLI and set SELECT_ATTACK in main()")

    attack_key = SELECT_ATTACK
    attack_cfg = BASE_CFG_JSON.get('attacksParams', {}).get(attack_key, {})

    # helper to set nested values into the patch
    patch = {"attacksParams": {}, "gooseFlow": {"numberOfMessages": 2000}}

    def set_in_patch(p, path, value):
        cur = p.setdefault('attacksParams', {})
        # path is like ['attack','delayMs','min']
        if len(path) == 0:
            return
        attack = path[0]
        if attack not in cur:
            cur[attack] = {}
        node = cur[attack]
        for k in path[1:-1]:
            if k not in node or not isinstance(node[k], dict):
                node[k] = {}
            node = node[k]
        node[path[-1]] = value

    # traverse attack config and create trial suggestions for numeric leaves and min/max pairs
    def traverse(path_prefix, node):
        # path_prefix is list of keys from attack root (attack_key already excluded)
        if isinstance(node, dict):
            # min/max pair special handling
            if 'min' in node and 'max' in node and isinstance(node['min'], (int, float)) and isinstance(node['max'], (int, float)):
                base_min = node['min']
                base_max = node['max']
                param_min_name = '_'.join([attack_key] + path_prefix + ['min'])
                # choose integer vs float
                if isinstance(base_min, int) and isinstance(base_max, int):
                    low = max(0, int(base_min))
                    high = max(low + 1, int(base_max))
                    v_min = trial.suggest_int(param_min_name, low, high - 1)
                    param_max_name = param_min_name.replace('_min', '_max')
                    v_max = trial.suggest_int(param_max_name, v_min + 1, max(v_min + 1, int(base_max)))
                else:
                    low = float(base_min)
                    high = float(base_max)
                    v_min = trial.suggest_float(param_min_name, low, max(low + 1e-6, high))
                    param_max_name = param_min_name.replace('_min', '_max')
                    v_max = trial.suggest_float(param_max_name, max(v_min + 1e-6, low), high)
                # record and set
                PARAM_MAP.append((param_min_name, [attack_key] + path_prefix + ['min']))
                PARAM_MAP.append((param_max_name, [attack_key] + path_prefix + ['max']))
                set_in_patch(patch, [attack_key] + path_prefix + ['min'], v_min)
                set_in_patch(patch, [attack_key] + path_prefix + ['max'], v_max)
                return
            # otherwise iterate children
            for k, v in node.items():
                traverse(path_prefix + [k], v)
        elif isinstance(node, bool):
            param_name = '_'.join([attack_key] + path_prefix)
            val = trial.suggest_categorical(param_name, [True, False])
            PARAM_MAP.append((param_name, [attack_key] + path_prefix))
            set_in_patch(patch, [attack_key] + path_prefix, val)
        elif isinstance(node, (int, float)):
            param_name = '_'.join([attack_key] + path_prefix)
            # heuristic for probability-like values
            if 0.0 <= float(node) <= 1.0:
                val = trial.suggest_float(param_name, 0.0, 1.0)
            else:
                # integer vs float
                if isinstance(node, int):
                    low = max(0, int(node // 2))
                    high = max(low + 1, int(node * 3 + 1))
                    val = trial.suggest_int(param_name, low, high)
                else:
                    low = max(0.0, float(node) / 2.0)
                    high = max(low + 1e-6, float(node) * 3.0)
                    val = trial.suggest_float(param_name, low, high)
            PARAM_MAP.append((param_name, [attack_key] + path_prefix))
            set_in_patch(patch, [attack_key] + path_prefix, val)
        else:
            # skip arrays and strings for now
            return

    traverse([], attack_cfg)

    # set a seed so results are deterministic per trial
    patch['randomSeed'] = trial.number + int(time.time())

    tmpdir = Path(tempfile.mkdtemp(prefix='optuna_run_'))
    cfg_path = tmpdir / 'cfg.json'
    write_config(BASE_CONFIG, cfg_path, patch)

    try:
        res = run_experiment(cfg_path, tmpdir, seed=patch['randomSeed'])
    except Exception as e:
        print('Experiment failed:', e)
        # return high penalty
        shutil.rmtree(tmpdir, ignore_errors=True)
        # return an out-of-band penalty > 1.0 so it cannot be confused with a valid F1 score
        return 2.0

    # read f1 metric printed by ExperimentRunner
    metric = float(res.get('metric_f1', 1.0))
    # minimize F1 (lower F1 means attack is harder to detect)
    # save trial artifacts
    with open(tmpdir / 'result.json', 'w', encoding='utf-8') as f:
        json.dump({'params': trial.params, 'result': res}, f, indent=2)

    # keep the tmpdir for inspection; optuna will manage best candidate separately
    return metric


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--trials', type=int, default=20)
    parser.add_argument('--study-name', type=str, default='ereno_opt')
    parser.add_argument('--attack', type=str, default='randomReplay', help='Attack key inside attacksParams to optimize (e.g. randomReplay, greyhole)')
    parser.add_argument('--storage', type=str, default=None,
                        help='Optional sqlite storage for resuming studies, e.g. sqlite:///optuna.db')
    args = parser.parse_args()

    # set selected attack globally for the objective
    global SELECT_ATTACK
    SELECT_ATTACK = args.attack

    study = optuna.create_study(direction='minimize', study_name=args.study_name, storage=args.storage, load_if_exists=True)
    study.optimize(objective, n_trials=args.trials)

    print('Best value:', study.best_value)
    print('Best params:', study.best_params)

    # write best config to file using discovered parameter map for the selected attack
    best_cfg = Path('target') / 'opt_best_config.json'
    best_patch = {'attacksParams': {}}

    # Build a mapping of expected param names -> config paths (same naming as in objective)
    def build_param_map_for_attack(attack_key, node, path_prefix=None, out=None):
        if out is None:
            out = {}
        if path_prefix is None:
            path_prefix = []
        if isinstance(node, dict):
            if 'min' in node and 'max' in node and isinstance(node['min'], (int, float)) and isinstance(node['max'], (int, float)):
                param_min = '_'.join([attack_key] + path_prefix + ['min'])
                param_max = param_min.replace('_min', '_max')
                out[param_min] = [attack_key] + path_prefix + ['min']
                out[param_max] = [attack_key] + path_prefix + ['max']
                return out
            for k, v in node.items():
                build_param_map_for_attack(attack_key, v, path_prefix + [k], out)
        elif isinstance(node, bool) or isinstance(node, (int, float)):
            param = '_'.join([attack_key] + path_prefix)
            out[param] = [attack_key] + path_prefix
        return out

    attack_cfg = BASE_CFG_JSON.get('attacksParams', {}).get(SELECT_ATTACK, {})
    param_map = build_param_map_for_attack(SELECT_ATTACK, attack_cfg)

    # populate best_patch from study.best_params where available
    for pname, path in param_map.items():
        if pname in study.best_params:
            # set in nested dict
            cur = best_patch['attacksParams'].setdefault(path[0], {})
            for k in path[1:-1]:
                cur = cur.setdefault(k, {})
            cur[path[-1]] = study.best_params[pname]

    write_config(BASE_CONFIG, best_cfg, best_patch)
    print('Best config written to', best_cfg)


if __name__ == '__main__':
    main()
